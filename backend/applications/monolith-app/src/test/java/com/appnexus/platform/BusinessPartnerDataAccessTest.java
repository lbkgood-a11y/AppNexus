package com.appnexus.platform;
import com.appnexus.masterdata.application.masterdata.MasterDataService;
import com.appnexus.platform.security.operation.OperationRegistry;


import com.appnexus.platform.authorization.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import com.appnexus.platform.persistence.platformscope.MyBatisPlatformAdministration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static com.appnexus.masterdata.api.masterdata.MasterData.*;

/** 真实MySQL客商闭环：声明、行范围、子树、字段、明细、租户及失败回滚使用生产执行器。 */
@SpringBootTest(properties="app.public-url=http://localhost:18080")
@ActiveProfiles("local")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class BusinessPartnerDataAccessTest extends DatabaseTestSupport {
    @Autowired com.appnexus.platform.businessobject.BusinessObjectManagement businessObjects;
    @Autowired OperationRegistry operations;
    @Autowired com.appnexus.masterdata.adapter.integration.partner.BusinessPartnerDeclaration partnerDeclaration;
    @Autowired com.appnexus.masterdata.application.masterdata.StatusHistoryQuery statusHistory;
    @Autowired MasterDataService service;@Autowired MyBatisPlatformAdministration administration;
    @Autowired RoleManagement roles;@Autowired RolePermissionManagement permissions;@Autowired SubjectRoleManagement subjects;
    @Autowired MemberAuthorizationManagement members;@Autowired DataAccessCatalog catalog;@Autowired FieldCatalog fields;
    @Autowired JdbcTemplate jdbc;
    @Autowired EffectiveAuthorizationQuery authorizationExplanation;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired org.mybatis.spring.SqlSessionTemplate sql;
    @Autowired com.appnexus.masterdata.adapter.persistence.MasterRecordMapper partnerRecords;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
    /** 仿真开发遗漏，只带权限参数但 SQL 未拼入时必须在 MyBatis 层拒绝。 */
    @com.appnexus.persistence.mybatis.PlatformScoped(tables={"md_partner"})
    public interface UnguardedSqlProbe {
        @com.appnexus.persistence.mybatis.GuardedScope(predicate="access",resource="masterdata.partner",tenant="tenant")
        @org.apache.ibatis.annotations.Select("SELECT COUNT(*) FROM md_partner WHERE tenant_id=UUID_TO_BIN(#{tenant})")
        long count(@org.apache.ibatis.annotations.Param("tenant") String tenant,
           @org.apache.ibatis.annotations.Param("access") com.appnexus.platform.authorization.RowAccessControl.Predicate access);
    }

    /** 漏接 SQL 与绕过应用事务直接修改均由执行门禁拒绝。 */
    @Test void standardMapperRejectsOmittedPredicateAndMissingWritePermit(){
        sql.getConfiguration().addMapper(UnguardedSqlProbe.class);
        String tenant=UUID.randomUUID().toString(),id=UUID.randomUUID().toString();
        var subject=AccessAuthorization.Subject.tenant(UUID.randomUUID(),UUID.fromString(tenant),UUID.randomUUID());
        var plan=new AccessPlan(subject,"masterdata.partner:read","masterdata.partner",
            new DataScopeAuthorization.DataScope(DataScopeAuthorization.ScopeType.ALL,Set.of()),Map.of(),java.time.Instant.now());
        var explicitAll=new com.appnexus.platform.authorization.RowAccessControl.Predicate("",List.of(),plan);
        assertThatThrownBy(()->sql.getMapper(UnguardedSqlProbe.class).count(tenant,explicitAll))
            .hasRootCauseMessage("SCOPED_SQL_PLAN_REQUIRED");
        assertThatThrownBy(()->partnerRecords.status(tenant,Kind.PARTNER,id,false,0,subject.accountId().toString()))
            .hasRootCauseMessage("SCOPED_WRITE_PLAN_REQUIRED");
    }

    /** 真实角色及成员策略写入与客商查询验证统一范围，并拒绝旧顶层策略。 */
    @Test void allAndRulesAreTheOnlyEffectiveTopLevelScopes() throws Exception {
        UUID actor=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM identity_account WHERE username='admin-test'",String.class));
        administration.initializeAdministrator("admin-test");
        UUID owner=account(actor),user=account(actor);
        UUID tenant=administration.createTenant(actor,UUID.randomUUID(),"统一范围验收",owner).id();
        administration.addMember(actor,tenant,user);
        var ownerContext=new VerifiedTenantContext(owner,tenant,member(tenant,owner),1);
        var userContext=new VerifiedTenantContext(user,tenant,member(tenant,user),1);
        UUID owningOrganization=organization(tenant,"范围测试组织",null);
        String first=service.create(ownerContext,Kind.PARTNER,input("FIRST",owningOrganization,Map.of(),null,null)).get("id");
        String second=service.create(ownerContext,Kind.PARTNER,input("SECOND",owningOrganization,Map.of(),null,null)).get("id");
        UUID otherOwner=account(actor);
        UUID otherTenant=administration.createTenant(actor,UUID.randomUUID(),"范围验收外租户",otherOwner).id();
        var foreignContext=new VerifiedTenantContext(otherOwner,otherTenant,member(otherTenant,otherOwner),1);
        UUID foreignOrg=organization(otherTenant,"外租户组织",null);
        service.create(foreignContext,Kind.PARTNER,input("FOREIGN",foreignOrg,Map.of(),null,null));
        var context=new RoleManagement.Context(actor,tenant,null);
        var role=roles.create(context,new RoleManagement.Create("scope_"+UUID.randomUUID().toString().replace("-",""),"范围验收角色",null,100));
        UUID read=permission("masterdata.partner:read");
        var all=new RolePermissionManagement.DataPolicy(read,DataScopeAuthorization.ScopeType.ALL,"masterdata.partner",Set.of());
        var saved=permissions.replaceAuthorization(context,role.id(),role.version(),Set.of(read),Set.of(all),"验证 ALL");
        var assignment=subjects.memberRoles(context,userContext.memberId());
        subjects.replaceMemberRoles(context,userContext.memberId(),assignment.authorityVersion(),Set.of(role.id()),"继承角色范围");
        assertThat(service.list(userContext,Kind.PARTNER,"",null,1,20).rows()).extracting(Row::id)
            .containsExactlyInAnyOrder(first,second);

        var rule=new DataGrantRules.Rule(DataGrantRules.SubjectType.ROLE_MEMBERS,Set.of(),Set.of(UUID.fromString(first)),List.of(),false);
        var rules=new RolePermissionManagement.DataPolicy(read,DataScopeAuthorization.ScopeType.RULES,"masterdata.partner",Set.of(),List.of(rule));
        var defaults = Map.of("masterdata.partner",
            new RolePermissionManagement.ObjectScope(DataScopeAuthorization.ScopeType.RULES,List.of(rule)));
        var configured = permissions.replaceAuthorization(context,role.id(),saved.roleVersion(),
            Set.of(read),Set.of(all),Set.of(),defaults,"保留原客商操作覆盖");
        assertThat(configured.inheritedPermissionIds()).isEmpty();
        assertThat(service.list(userContext,Kind.PARTNER,"",null,1,20).rows()).extracting(Row::id)
            .containsExactlyInAnyOrder(first,second);

        var payload = Map.of("ids", Set.of(read), "dataPolicies", Set.of(), "fieldPolicies", Set.of(),
            "version", configured.roleVersion(), "reason", "移除覆盖并继承客商默认范围",
            "dataAccessVersion", catalog.version(), "objectScopes", defaults);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/bff/platform/tenants/{tenant}/roles/{role}/permissions",tenant,role.id())
                .with(authenticated(actor.toString(),0))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(new tools.jackson.databind.ObjectMapper().writeValueAsString(payload)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.inheritedPermissionIds[0]").value(read.toString()));
        var reread = permissions.getAuthorization(context,role.id());
        assertThat(reread.objectScopes()).isEqualTo(defaults);
        assertThat(reread.inheritedPermissionIds()).containsExactly(read);
        assertThat(reread.dataPolicies()).containsExactly(rules);
        assertThat(service.list(userContext,Kind.PARTNER,"",null,1,20).rows()).extracting(Row::id)
            .containsExactly(first);
        assertThatThrownBy(()->permissions.replaceAuthorization(context,role.id(),configured.roleVersion(),
            Set.of(read),Set.of(all),Set.of(),defaults,"验证过期版本"))
            .isInstanceOf(RoleManagementException.class).extracting("code").isEqualTo("ROLE_VERSION_CONFLICT");

        assertThat(authorizationExplanation.tenantMember(tenant,userContext.memberId()).getFirst().sources().getFirst().scopeSource())
            .isEqualTo(EffectiveAuthorizationQuery.ScopeSource.ROLE_DEFAULT);
        var changedDefault = permissions.replaceAuthorization(context,role.id(),reread.roleVersion(),
            Set.of(read),Set.of(),Set.of(),Map.of("masterdata.partner",
                new RolePermissionManagement.ObjectScope(DataScopeAuthorization.ScopeType.ALL,List.of())),"调整默认范围");
        assertThat(service.list(userContext,Kind.PARTNER,"",null,1,20).rows()).extracting(Row::id)
            .containsExactlyInAnyOrder(first,second);

        var grant=members.get(context,userContext.memberId());
        members.replace(context,userContext.memberId(),new MemberAuthorizationManagement.Grant(role.id(),Set.of(read),Set.of(rules),null,changedDefault.roleVersion(),false),
            grant.authorityVersion(),"成员按指定记录授权");
        assertThat(service.list(userContext,Kind.PARTNER,"",null,1,20).rows()).extracting(Row::id).containsExactly(first);

        assertThat(authorizationExplanation.tenantMember(tenant,userContext.memberId()).getFirst().sources().getFirst().scopeSource())
            .isEqualTo(EffectiveAuthorizationQuery.ScopeSource.MEMBER_OVERRIDE);
        var explicit = members.get(context,userContext.memberId());
        var memberDefault = new MemberAuthorizationManagement.Grant(role.id(),Set.of(read),Set.of(),null,
            changedDefault.roleVersion(),false,defaults,Set.of());
        var custom = members.replace(context,userContext.memberId(),memberDefault,explicit.authorityVersion(),"继承成员对象默认");
        assertThat(custom.grants().getFirst().inheritedPermissionIds()).containsExactly(read);
        assertThat(service.list(userContext,Kind.PARTNER,"",null,1,20).rows()).extracting(Row::id).containsExactly(first);
        assertThat(authorizationExplanation.tenantMember(tenant,userContext.memberId()).getFirst().sources().getFirst().scopeSource())
            .isEqualTo(EffectiveAuthorizationQuery.ScopeSource.MEMBER_DEFAULT);

        var memberOverride = new MemberAuthorizationManagement.Grant(role.id(),Set.of(read),Set.of(rules),null,
            changedDefault.roleVersion(),false,Map.of("masterdata.partner",
                new RolePermissionManagement.ObjectScope(DataScopeAuthorization.ScopeType.ALL,List.of())),Set.of());
        var overwritten = members.replace(context,userContext.memberId(),memberOverride,custom.authorityVersion(),"成员操作覆盖对象默认");
        assertThat(overwritten.grants().getFirst().inheritedPermissionIds()).isEmpty();
        assertThat(service.list(userContext,Kind.PARTNER,"",null,1,20).rows()).extracting(Row::id).containsExactly(first);
        assertThat(authorizationExplanation.tenantMember(tenant,userContext.memberId()).getFirst().sources().getFirst().scopeSource())
            .isEqualTo(EffectiveAuthorizationQuery.ScopeSource.MEMBER_OVERRIDE);

        var current=members.get(context,userContext.memberId());
        for(var retired:List.of(DataScopeAuthorization.ScopeType.SELF,DataScopeAuthorization.ScopeType.ORGANIZATION,DataScopeAuthorization.ScopeType.ORGANIZATION_SUBTREE)){
            var policy=new RolePermissionManagement.DataPolicy(read,retired,"masterdata.partner",Set.of());
            assertThatThrownBy(()->permissions.replaceAuthorization(context,role.id(),changedDefault.roleVersion(),Set.of(read),Set.of(policy),"拒绝旧范围"))
                .isInstanceOf(RuntimeException.class);
            assertThatThrownBy(()->members.replace(context,userContext.memberId(),new MemberAuthorizationManagement.Grant(
                role.id(),Set.of(read),Set.of(policy),null,changedDefault.roleVersion(),false),current.authorityVersion(),"拒绝旧范围"))
                .isInstanceOf(RuntimeException.class);
        }
        assertThat(service.list(userContext,Kind.PARTNER,"",null,1,20).rows()).extracting(Row::id).containsExactly(first);
    }

    /** 完整工作流验证一次共享夹具，避免用伪数据目录或绕过生产权限端口。 */
    @Test void declarationDrivenPartnerRowsFieldsAndDetailsAreEnforced() throws Exception {
        UUID actor=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM identity_account WHERE username='admin-test'",String.class));
        administration.initializeAdministrator("admin-test");
        var directory=businessObjects.catalog(new com.appnexus.contracts.businessobject.BusinessObjects.Context(null,actor.toString(),UUID.randomUUID().toString()),true);
        assertThat(directory.stream().map(row->(com.appnexus.contracts.businessobject.BusinessObjects.Descriptor)row.get("descriptor")).filter(d->d.objectType().equals("BusinessPartner")).toList()).singleElement().satisfies(d->{
            assertThat(d.category()).isEqualTo(com.appnexus.contracts.businessobject.BusinessObjects.Category.MASTER_DATA);
            assertThat(d.dataAccess()).isNotNull();assertThat(d.operations()).hasSize(5);
            assertThat(d.operations()).allSatisfy(op->assertThat(op.supportedServices()).isEmpty());
            assertThat(partnerDeclaration.commandOperations()).containsExactlyInAnyOrder(
                "list","create","update","readStatusHistory","setStatus");
            assertThat(partnerDeclaration.commandRuntime().inputTypes().keySet())
                .isEqualTo(partnerDeclaration.commandOperations());
        });
        assertThat(operations.objects().stream().filter(o->o.code().equals("BusinessPartner")).toList()).singleElement().satisfies(o->assertThat(o.operations()).hasSize(5));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/bff/platform/business-objects").with(authenticated(actor.toString(),0)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[?(@.descriptor.objectType == 'BusinessPartner')].descriptor.nameZh").value(org.hamcrest.Matchers.contains("客商管理")));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/bff/platform/business-objects").with(authenticated(actor.toString(),0)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[?(@.descriptor.objectType == 'Warehouse')].descriptor.dataAccess.standardCrud.ownershipField")
                .value(org.hamcrest.Matchers.contains("organizationId")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[?(@.descriptor.objectType == 'Warehouse')].descriptor.dataAccess.standardCrud.operations[0:]")
                .value(org.hamcrest.Matchers.containsInAnyOrder("list","create","update","setStatus")));

        UUID owner=account(actor),user=account(actor),otherOwner=account(actor);
        UUID tenant=administration.createTenant(actor,UUID.randomUUID(),"客商数据权限验收",owner).id();
        UUID otherTenant=administration.createTenant(actor,UUID.randomUUID(),"外租户",otherOwner).id();
        administration.addMember(actor,tenant,user);
        var c=new VerifiedTenantContext(owner,tenant,member(tenant,owner),1);
        var u=new VerifiedTenantContext(user,tenant,member(tenant,user),1);
        var foreign=new VerifiedTenantContext(otherOwner,otherTenant,member(otherTenant,otherOwner),1);
        UUID a=organization(tenant,"厦门事业部",null),child=organization(tenant,"A下级",a),b=organization(tenant,"B",null),outside=organization(otherTenant,"外组织",null);
        String first=service.create(c,Kind.PARTNER,input("FIRST",a,Map.of("phone","13800138000","legalId","ID-123456789"),List.of(new DetailInput(null,Map.of("contactType","BUSINESS","contactName","联系人","phone","13900139000","address","地址","isDefault","true"))),List.of(new DetailInput(null,Map.of("taxAccount","false","basicAccount","true","isDefault","true","bankName","银行","accountNumber","000012345678","currency","CNY"))))).get("id");
        String second=service.create(c,Kind.PARTNER,input("CHILD",child,Map.of(),null,null)).get("id");
        String blocked=service.create(c,Kind.PARTNER,input("BLOCKED",b,Map.of(),null,null)).get("id");
        var rc=new RoleManagement.Context(actor,tenant,null);var role=roles.create(rc,new RoleManagement.Create("dp_"+UUID.randomUUID().toString().replace("-",""),"客商组织操作员",null,100));
        var ids=new HashSet<UUID>();var policies=new HashSet<RolePermissionManagement.DataPolicy>();
        for(String action:List.of("read","create","update","set_status")){
            UUID id=permission("masterdata.partner:"+action);ids.add(id);
            policies.add(switch(action){
                case "create" -> new RolePermissionManagement.DataPolicy(id,DataScopeAuthorization.ScopeType.ALL,"masterdata.partner",Set.of());
                case "read" -> recordsPolicy(id,first,second);
                default -> recordsPolicy(id,first);
            });
        }
        var saved=permissions.replaceAuthorization(rc,role.id(),role.version(),ids,policies,"客商按组织授权");
        var initial=subjects.memberRoles(rc,u.memberId());subjects.replaceMemberRoles(rc,u.memberId(),initial.authorityVersion(),Set.of(role.id()),"角色默认继承");
        assertThat(catalog.list(PermissionCatalog.ScopeType.TENANT)).anyMatch(d->d.objectType().equals("BusinessPartner")&&d.version()==8);
        assertThat(fields.list(PermissionCatalog.ScopeType.TENANT)).anyMatch(f->f.resource().equals("masterdata.partner.bank")&&f.field().equals("accountNumber")&&f.modes().contains(FieldSecurity.Mode.READ_ONLY));
        assertThat(service.list(u,Kind.PARTNER,"",null,1,20).total()).isEqualTo(2);
        // 指定记录与条件取AND，规则取OR；当前角色及功能权限仍是前置门槛。
        var readPermission=permission("masterdata.partner:read");
        var recordAndCondition=new DataGrantRules.Rule(DataGrantRules.SubjectType.MEMBER,Set.of(u.memberId()),
            Set.of(UUID.fromString(first),UUID.fromString(blocked)),List.of(new DataGrantRules.Condition("code",DataGrantRules.Operator.EQ,List.of("FIRST"))),false);
        var conditionOnly=new DataGrantRules.Rule(DataGrantRules.SubjectType.ROLE_MEMBERS,Set.of(),Set.of(),
            List.of(new DataGrantRules.Condition("code",DataGrantRules.Operator.IN,List.of("CHILD"))),false);
        var notThisMember=new DataGrantRules.Rule(DataGrantRules.SubjectType.MEMBER,Set.of(c.memberId()),Set.of(UUID.fromString(blocked)),List.of(),false);
        var combined=new RolePermissionManagement.DataPolicy(readPermission,DataScopeAuthorization.ScopeType.RULES,"masterdata.partner",Set.of(),List.of(recordAndCondition,conditionOnly,notThisMember));
        var combinedPolicies=new HashSet<>(policies);combinedPolicies.removeIf(p->p.permissionId().equals(readPermission));combinedPolicies.add(combined);
        saved=permissions.replaceAuthorization(rc,role.id(),saved.roleVersion(),ids,combinedPolicies,"验证组合授权");
        assertThat(permissions.getAuthorization(rc,role.id()).dataPolicies()).contains(combined);
        assertThat(service.list(u,Kind.PARTNER,"",null,1,20).rows()).extracting(Row::id).containsExactly(second,first);
        assertThat(service.list(u,Kind.PARTNER,"",null,1,20).total()).isEqualTo(2);
        assertThat(service.list(u,Kind.PARTNER,"",null,1,20,blocked).rows()).isEmpty();
        assertThat(service.list(foreign,Kind.PARTNER,"",null,1,20,first).rows()).isEmpty();
        // 成员专属规则只对可信当前成员生效，替换和恢复继承都经过真实授权端口。
        var memberRule=new DataGrantRules.Rule(DataGrantRules.SubjectType.MEMBER,Set.of(u.memberId()),
            Set.of(UUID.fromString(first)),List.of(),false);
        var individualPolicy=new RolePermissionManagement.DataPolicy(readPermission,DataScopeAuthorization.ScopeType.RULES,
            "masterdata.partner",Set.of(),List.of(memberRule));
        var grantState=members.get(rc,u.memberId());
        var memberPolicies=new HashSet<>(policies);
        memberPolicies.removeIf(p->p.permissionId().equals(readPermission));
        memberPolicies.add(individualPolicy);
        members.replace(rc,u.memberId(),new MemberAuthorizationManagement.Grant(role.id(),ids,memberPolicies,null,saved.roleVersion(),false),
            grantState.authorityVersion(),"成员指定记录");
        assertThat(members.get(rc,u.memberId()).grants().getFirst().dataPolicies()).contains(individualPolicy);
        assertThat(service.list(u,Kind.PARTNER,"",null,1,20).rows()).extracting(Row::id).containsExactly(first);
        var restore=members.get(rc,u.memberId());
        members.replace(rc,u.memberId(),new MemberAuthorizationManagement.Grant(role.id(),ids,Set.of(),null,saved.roleVersion(),true),restore.authorityVersion(),"恢复角色组合规则");
        assertThat(service.list(u,Kind.PARTNER,"",null,1,20).total()).isEqualTo(2);
        var badRules=new RolePermissionManagement.DataPolicy(readPermission,DataScopeAuthorization.ScopeType.RULES,"masterdata.partner",Set.of(),List.of(
            new DataGrantRules.Rule(DataGrantRules.SubjectType.MEMBER,Set.of(foreign.memberId()),Set.of(UUID.fromString(first)),List.of(),false)));
        var invalidPolicies=new HashSet<>(combinedPolicies);invalidPolicies.removeIf(p->p.permissionId().equals(readPermission));invalidPolicies.add(badRules);
        long beforeInvalid=saved.roleVersion();
        assertThatThrownBy(()->permissions.replaceAuthorization(rc,role.id(),beforeInvalid,ids,invalidPolicies,"拒绝跨租户主体")).isInstanceOf(IllegalArgumentException.class);
        assertThat(permissions.getAuthorization(rc,role.id()).roleVersion()).isEqualTo(beforeInvalid);
        String supplier=service.create(c,Kind.PARTNER,input("SUPPLIER-XM",a,Map.of("partnerType","SUPPLIER"),null,null)).get("id");
        var exactConditions=List.of(
            new DataGrantRules.Condition("partnerType",DataGrantRules.Operator.EQ,List.of("CUSTOMER")));
        var exactPolicies=new HashSet<>(combinedPolicies);
        exactPolicies.removeIf(policy->policy.permissionId().equals(readPermission));
        exactPolicies.add(new RolePermissionManagement.DataPolicy(readPermission,DataScopeAuthorization.ScopeType.RULES,"masterdata.partner",Set.of(),List.of(
            new DataGrantRules.Rule(DataGrantRules.SubjectType.ROLE_MEMBERS,Set.of(),Set.of(UUID.fromString(first),UUID.fromString(supplier)),exactConditions,false))));
        saved=permissions.replaceAuthorization(rc,role.id(),saved.roleVersion(),ids,exactPolicies,"指定记录且客商类型为客户");
        assertThat(service.list(u,Kind.PARTNER,"",null,1,1).rows()).extracting(Row::id).containsExactly(first);
        assertThat(service.list(u,Kind.PARTNER,"",null,1,1).total()).isEqualTo(1);
        assertThat(service.list(u,Kind.PARTNER,"",null,1,20,supplier).rows()).isEmpty();
        assertThat(service.list(u,Kind.PARTNER,"",null,1,20,blocked).rows()).isEmpty();
        var invalidDictionaryPolicies=new HashSet<>(exactPolicies);
        invalidDictionaryPolicies.removeIf(policy->policy.permissionId().equals(readPermission));
        invalidDictionaryPolicies.add(new RolePermissionManagement.DataPolicy(readPermission,DataScopeAuthorization.ScopeType.RULES,"masterdata.partner",Set.of(),List.of(
            new DataGrantRules.Rule(DataGrantRules.SubjectType.ROLE_MEMBERS,Set.of(),Set.of(),List.of(
                new DataGrantRules.Condition("partnerType",DataGrantRules.Operator.EQ,List.of("UNKNOWN_CUSTOMER"))),false))));
        long exactVersion=saved.roleVersion();
        assertThatThrownBy(()->permissions.replaceAuthorization(rc,role.id(),exactVersion,ids,invalidDictionaryPolicies,"拒绝未知字典项"))
            .hasMessage("DATA_GRANT_DICTIONARY_VALUE_INVALID");
        var ownerSession=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/bff/tenants/current")
            .with(authenticated(owner.toString(),0)).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
            .contentType("application/json").content("{\"tenantId\":\""+tenant+"\"}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/access/data-policy-metadata")
            .param("conditionOptions","true").cookie(ownerSession.getResponse().getCookie("JSESSIONID")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.partner_type[*].value").value(org.hamcrest.Matchers.hasItem("CUSTOMER")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.warehouse_type[*].value").value(org.hamcrest.Matchers.hasItem("PHYSICAL")));
        String own=service.create(u,Kind.PARTNER,input("OWN-CREATED",a,Map.of(),null,null)).get("id");
        var currentAccountRule=new DataGrantRules.Rule(DataGrantRules.SubjectType.ROLE_MEMBERS,Set.of(),Set.of(),List.of(
            new DataGrantRules.Condition("createdBy",DataGrantRules.Operator.EQ,List.of(),DataGrantRules.Parameter.CURRENT_ACCOUNT),
            new DataGrantRules.Condition("partnerType",DataGrantRules.Operator.EQ,List.of("CUSTOMER"))),false);
        var dynamicPolicies=new HashSet<>(exactPolicies);
        dynamicPolicies.removeIf(policy->policy.permissionId().equals(readPermission));
        dynamicPolicies.add(new RolePermissionManagement.DataPolicy(readPermission,DataScopeAuthorization.ScopeType.RULES,
            "masterdata.partner",Set.of(),List.of(currentAccountRule)));
        saved=permissions.replaceAuthorization(rc,role.id(),saved.roleVersion(),ids,dynamicPolicies,"验证当前账号动态参数");
        assertThat(permissions.getAuthorization(rc,role.id()).dataPolicies()).containsAll(dynamicPolicies);
        var accountRows=service.list(u,Kind.PARTNER,"",null,1,20);
        assertThat(accountRows.total()).isEqualTo(1);
        assertThat(accountRows.rows()).extracting(Row::id).containsExactly(own);
        jdbc.update("DELETE FROM md_partner WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",tenant.toString(),own);
        jdbc.update("DELETE FROM md_partner WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",tenant.toString(),supplier);
        saved=permissions.replaceAuthorization(rc,role.id(),saved.roleVersion(),ids,policies,"恢复指定记录范围验证写入");
        var selected=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/bff/tenants/current").with(authenticated(user.toString(),0)).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content("{\"tenantId\":\""+tenant+"\"}")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn();
        var cookie=selected.getResponse().getCookie("JSESSIONID");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/master-data/partner").cookie(cookie).param("id",blocked)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.rows").isEmpty());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/master-data/partner").cookie(cookie).param("dataAccess","true")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.organizations").isEmpty());

        assertThat(service.list(u,Kind.PARTNER,"",null,1,20,blocked).rows()).isEmpty();
        assertThat(service.list(foreign,Kind.PARTNER,"",null,1,20,first).rows()).isEmpty();
        assertThatThrownBy(()->service.update(u,Kind.PARTNER,blocked,new Input("BLOCKED","越权",Map.of("partnerType","CUSTOMER"),0))).isInstanceOf(Rejection.class);
        assertThatThrownBy(()->service.setStatus(u,Kind.PARTNER,second,new Status(false,0))).isInstanceOf(Rejection.class);
        assertThatThrownBy(()->service.create(u,Kind.PARTNER,input("FOREIGN",outside,Map.of(),null,null))).isInstanceOf(Rejection.class);
        assertThatThrownBy(()->service.create(u,Kind.PARTNER,input("NO_ORG",null,Map.of(),null,null))).isInstanceOf(Rejection.class);
        assertThatThrownBy(()->service.update(u,Kind.PARTNER,first,new Input("FIRST","非法移动",Map.of("partnerType","CUSTOMER"),0,null,null,outside.toString()))).isInstanceOf(Rejection.class);
        assertThat(service.list(c,Kind.PARTNER,"",null,1,1,first).rows().getFirst().version()).isZero();
        var restricted=Set.of(new FieldSecurity.Policy("masterdata.partner","phone",FieldSecurity.Mode.MASKED),new FieldSecurity.Policy("masterdata.partner","legalId",FieldSecurity.Mode.HIDDEN),new FieldSecurity.Policy("masterdata.partner.contact","phone",FieldSecurity.Mode.MASKED),new FieldSecurity.Policy("masterdata.partner.bank","accountNumber",FieldSecurity.Mode.READ_ONLY));
        saved=permissions.replaceAuthorization(rc,role.id(),saved.roleVersion(),ids,policies,restricted,"字段限制");
        var row=service.list(u,Kind.PARTNER,"",null,1,1,first).rows().getFirst();
        assertThat(row.attributes()).containsEntry("phone","138****8000").doesNotContainKey("legalId");
        assertThat(row.contacts().getFirst().values()).containsEntry("phone","139****9000");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/master-data/partner").cookie(cookie).param("id",first)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.rows[0].attributes.phone").value("138****8000")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.rows[0].attributes.legalId").doesNotExist());
        assertThat(row.bankAccounts().getFirst().values()).containsEntry("accountNumber","000012345678");
        assertThat(row.fieldRestrictions().get("masterdata.partner.bank")).containsEntry("accountNumber",FieldSecurity.Mode.READ_ONLY);
        service.update(u,Kind.PARTNER,first,new Input("FIRST","合法修改",Map.of("partnerType","CUSTOMER","remarks","已修改"),0,null,null,null));
        assertThat(jdbc.queryForObject("SELECT phone FROM md_partner WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",String.class,tenant.toString(),first)).isEqualTo("13800138000");
        assertThatThrownBy(()->service.update(u,Kind.PARTNER,first,new Input("FIRST","遮盖回传",Map.of("partnerType","CUSTOMER","phone","138****8000"),1))).isInstanceOf(Rejection.class).hasMessage("DATA_FIELD_WRITE_DENIED");
        assertThatThrownBy(()->service.update(u,Kind.PARTNER,first,new Input("FIRST","删明细绕过",Map.of("partnerType","CUSTOMER"),1,List.of(),null))).isInstanceOf(Rejection.class).hasMessage("DATA_FIELD_WRITE_DENIED");
        assertThatThrownBy(()->service.update(u,Kind.PARTNER,first,new Input("FIRST","改银行绕过",Map.of("partnerType","CUSTOMER"),1,null,List.of(new DetailInput(row.bankAccounts().getFirst().id(),Map.of("accountNumber","changed")))))).isInstanceOf(Rejection.class).hasMessage("DATA_FIELD_WRITE_DENIED");
        var unchanged=service.list(c,Kind.PARTNER,"",null,1,1,first).rows().getFirst();assertThat(unchanged.version()).isEqualTo(1);assertThat(unchanged.name()).isEqualTo("合法修改");
        var historySubject = com.appnexus.masterdata.api.masterdata.StatusHistory.Subject.PARTNER;
        service.setStatus(c, Kind.PARTNER, first, new Status(false, unchanged.version()));
        service.setStatus(c, Kind.PARTNER, first, new Status(true, unchanged.version() + 1));
        assertThat(statusHistory.list(u, historySubject, first, 1, 1).total()).isEqualTo(2);
        assertThat(statusHistory.list(u, historySubject, first, 1, 1).rows())
            .singleElement().satisfies(event -> {
                assertThat(event.beforeEnabled()).isFalse();
                assertThat(event.afterEnabled()).isTrue();
                assertThat(event.createdBy()).isEqualTo(owner.toString());
            });
        assertThatThrownBy(() -> statusHistory.list(u, historySubject, blocked, 1, 20))
            .isInstanceOf(Rejection.class);
        assertThatThrownBy(() -> statusHistory.list(foreign, historySubject, first, 1, 20))
            .isInstanceOf(Rejection.class);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/master-data/partner/{id}/status-history", first).cookie(cookie).param("size","1"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.total").value(2))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.rows[0].afterEnabled").value(true));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/master-data/partner/{id}/status-history", blocked).cookie(cookie))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        var grant=members.get(rc,u.memberId());var branch=grant.grants().getFirst();
        var custom=policies.stream().map(p->p.permissionId().equals(readPermission)?recordsPolicy(readPermission,blocked):p).collect(java.util.stream.Collectors.toSet());
        members.replace(rc,u.memberId(),new MemberAuthorizationManagement.Grant(role.id(),ids,custom,null,saved.roleVersion(),false),grant.authorityVersion(),"成员自定义B");
        assertThat(service.list(u,Kind.PARTNER,"",null,1,20).rows()).extracting(Row::id).containsExactly(blocked);
        var customGrant=members.get(rc,u.memberId());members.replace(rc,u.memberId(),new MemberAuthorizationManagement.Grant(role.id(),ids,Set.of(),null,saved.roleVersion(),true),customGrant.authorityVersion(),"恢复继承");
        assertThat(service.list(u,Kind.PARTNER,"",null,1,20).total()).isEqualTo(2);
        // 真实管理HTTP契约保存并返回完整规则，避免只有Java内部调用支持新模型。
        var body=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("ids",ids,"dataPolicies",combinedPolicies,
            "version",saved.roleVersion(),"reason","HTTP组合授权验收","dataAccessVersion",catalog.version()));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/bff/platform/tenants/"+tenant+"/roles/"+role.id()+"/permissions")
            .with(authenticated(actor.toString(),0)).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
            .contentType("application/json").content(body))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.dataPolicies[?(@.permissionId == '"+readPermission+"')].rules[0].subjectType").value(org.hamcrest.Matchers.contains("MEMBER")));
        assertThat(service.list(u,Kind.PARTNER,"",null,1,20).total()).isEqualTo(2);
        var current=permissions.getAuthorization(rc,role.id());
        var expiring=members.get(rc,u.memberId());
        members.replace(rc,u.memberId(),new MemberAuthorizationManagement.Grant(role.id(),ids,Set.of(),java.time.Instant.now().minusSeconds(60),current.roleVersion(),true),expiring.authorityVersion(),"过期组合授权不生效");
        assertThatThrownBy(()->service.list(u,Kind.PARTNER,"",null,1,20)).isInstanceOf(Rejection.class);
        var expired=members.get(rc,u.memberId());
        members.replace(rc,u.memberId(),new MemberAuthorizationManagement.Grant(role.id(),ids,Set.of(),null,current.roleVersion(),true),expired.authorityVersion(),"恢复长期组合授权");
        assertThat(service.list(u,Kind.PARTNER,"",null,1,20).total()).isEqualTo(2);
        permissions.replaceAuthorization(rc,role.id(),current.roleVersion(),Set.of(),Set.of(),"撤销功能同时停止组合访问");
        assertThatThrownBy(()->service.list(u,Kind.PARTNER,"",null,1,20)).isInstanceOf(Rejection.class);
    }
    /** 同一组合应用用真实角色策略验证五个类型化公共命令与原客商入口。 */
    @Test void typedPartnerCommandsReuseRealAuthorization() throws Exception {
        UUID admin=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM identity_account WHERE username='admin-test'",String.class));
        administration.initializeAdministrator("admin-test");
        UUID owner=account(admin),user=account(admin);
        UUID tenant=administration.createTenant(admin,UUID.randomUUID(),"客商类型化命令验收",owner).id();
        administration.addMember(admin,tenant,user);
        UUID organization=organization(tenant,"客商归属",null);
        var ownerCookie=commandSession(owner,tenant);
        var firstInput=input("COMMAND-A",organization,Map.of("phone","13800138000"),null,null);
        String first=commandId(ownerCookie,firstInput);
        String second=commandId(ownerCookie,input("COMMAND-B",organization,Map.of(),null,null));
        var roleContext=new RoleManagement.Context(admin,tenant,null);
        var role=roles.create(roleContext,new RoleManagement.Create("cmd_"+UUID.randomUUID().toString().replace("-",""),"客商命令受限用户",null,100));
        UUID read=permission("masterdata.partner:read"),update=permission("masterdata.partner:update");
        permissions.replaceAuthorization(roleContext,role.id(),role.version(),Set.of(read,update),
            Set.of(recordsPolicy(read,first),recordsPolicy(update,first)),
            Set.of(new FieldSecurity.Policy("masterdata.partner","phone",FieldSecurity.Mode.MASKED)),"指定客商范围");
        var userMember=member(tenant,user);
        var membership=subjects.memberRoles(roleContext,userMember);
        subjects.replaceMemberRoles(roleContext,userMember,membership.authorityVersion(),Set.of(role.id()),"指定客商范围");
        var userCookie=commandSession(user,tenant);
        runCommand(userCookie,"list",null,0,Map.of()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.output.result.total").value(1))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.output.result.rows[0].id").value(first))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.output.result.rows[0].attributes.phone").value("138****8000"));
        runCommand(userCookie,"create",null,0,firstInput).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        runCommand(userCookie,"update",second,0,input("COMMAND-B",organization,Map.of(),null,null))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        runCommand(userCookie,"update",first,0,new Input("COMMAND-A","命令更新",Map.of("partnerType","CUSTOMER"),0,null,null,null))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        runCommand(ownerCookie,"setStatus",first,1,Map.of("enabled",false,"version",1))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        runCommand(userCookie,"readStatusHistory",first,0,Map.of())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.output.result.total").value(1));
        runCommand(userCookie,"readStatusHistory",second,0,Map.of())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        runCommand(userCookie,"list",null,0,Map.of("tenantId",UUID.randomUUID().toString()))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("BUSINESS_OBJECT_COMMAND_INVALID"));
        runCommand(userCookie,"list",null,0,null)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("BUSINESS_OBJECT_COMMAND_REQUIRED"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/master-data/partner")
            .cookie(userCookie).param("id",first))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.rows[0].name").value("命令更新"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.rows[0].enabled").value(false));
        checkCodingCommands(admin,tenant,ownerCookie,userCookie);
        checkOrganizationCommands(tenant,ownerCookie,userCookie);
        runCommand(ownerCookie,"TenantMember","authorizationOptions",null,0,Map.of())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.output.result").isArray());
        runCommand(userCookie,"TenantMember","authorizationOptions",null,0,Map.of())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }

    /** 复用同一隔离租户验证编码五操作、原专用 API、写冲突及缺失管理权限。 */
    private void checkCodingCommands(UUID admin,UUID tenant,jakarta.servlet.http.Cookie owner,jakarta.servlet.http.Cookie reader)throws Exception{
        var context=new com.appnexus.contracts.businessobject.BusinessObjects.Context(null,admin.toString(),UUID.randomUUID().toString());
        businessObjects.grant(context,"BusinessPartner",tenant.toString(),true);
        var rule=Map.of("revision",-1,"name","公共命令规则","rule",Map.of("reset","NEVER","zoneId","Asia/Shanghai",
            "segments",List.of(Map.of("kind","SEQUENCE","value","","width",5))));
        runCommand(owner,"CodingRule","save","COMMAND_RULE",0,rule)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        runCommand(owner,"CodingRule","save","COMMAND_RULE",0,rule)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
        runCommand(owner,"CodingRule","list",null,0,Map.of())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.output.result[0].ruleKey").value("COMMAND_RULE"));
        var target=Map.of("targetObjectType","BusinessPartner");
        runCommand(owner,"CodingEncoder","ruleOptions",null,0,target)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.output.result[0].ruleVersion").value(1));
        var encoder=Map.of("targetObjectType","BusinessPartner","input",Map.of("revision",-1,"field","code","name","公共编码器",
            "enabled",true,"ruleKey","COMMAND_RULE","ruleVersion",1));
        runCommand(owner,"CodingEncoder","save","COMMAND_ENCODER",0,encoder)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        runCommand(owner,"CodingEncoder","list",null,0,target)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.output.result[0].encoderKey").value("COMMAND_ENCODER"));
        runCommand(reader,"CodingEncoder","list",null,0,target)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/business-objects/BusinessPartner/encoders").cookie(owner))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].encoderKey").value("COMMAND_ENCODER"));
    }

    /** 在真实组织用例上执行十个组织单元操作及人员目录查询。 */
    private void checkOrganizationCommands(UUID tenant,jakarta.servlet.http.Cookie owner,jakarta.servlet.http.Cookie reader)throws Exception{
        var flat=runCommand(owner,"OrganizationUnit","createFlat",null,0,Map.of("name","公共平面组织"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn().getResponse();
        var json=new tools.jackson.databind.ObjectMapper();
        String root=json.readTree(flat.getContentAsString()).path("output").path("result").path("id").asText();
        runCommand(owner,"OrganizationUnit","listFlat",null,0,Map.of())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.output.result[*].id").value(org.hamcrest.Matchers.hasItem(root)));
        runCommand(owner,"OrganizationUnit","rename",root,0,Map.of("name","公共平面改名"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        String type=jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM tenant_organization_type WHERE tenant_id=UUID_TO_BIN(?) AND code='BUSINESS'",String.class,tenant.toString());
        var created=runCommand(owner,"OrganizationUnit","create",null,0,Map.of("code","CMD-UNIT","typeId",type,"parentId",root,"name","公共领域组织"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn().getResponse();
        String child=json.readTree(created.getContentAsString()).path("output").path("result").path("id").asText();
        runCommand(owner,"OrganizationUnit","list",null,0,Map.of())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.output.result[*].id").value(org.hamcrest.Matchers.hasItem(child)));
        runCommand(owner,"OrganizationUnit","inspectImpact",root,0,Map.of())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.output.result.children").value(1));
        runCommand(owner,"OrganizationUnit","summary",null,0,Map.of())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        runCommand(owner,"OrganizationUnit","update",child,0,Map.of("name","公共领域更新","enabled",true,"version",0))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        runCommand(owner,"OrganizationUnit","update",child,1,Map.of("name","版本不匹配","enabled",true,"version",0))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        var move=new LinkedHashMap<String,Object>();move.put("parentId",null);move.put("version",1);
        runCommand(owner,"OrganizationUnit","move",child,1,move)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        runCommand(reader,"OrganizationUnit","delete",root,0,Map.of())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        runCommand(owner,"OrganizationUnit","delete",root,0,Map.of())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_organization_unit WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",Integer.class,tenant.toString(),root)).isZero();
        runCommand(owner,"PersonDirectory","list",null,0,Map.of())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        runCommand(reader,"PersonDirectory","list",null,0,Map.of())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/organization/domain/units").cookie(owner))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[*].id").value(org.hamcrest.Matchers.hasItem(child)));
    }

    private jakarta.servlet.http.Cookie commandSession(UUID account,UUID tenant)throws Exception{
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/bff/tenants/current")
            .with(authenticated(account.toString(),0)).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
            .contentType("application/json").content("{\"tenantId\":\""+tenant+"\"}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andReturn().getResponse().getCookie("JSESSIONID");
    }

    private String commandId(jakarta.servlet.http.Cookie cookie,Input input)throws Exception{
        var response=runCommand(cookie,"create",null,0,input)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn().getResponse();
        return new tools.jackson.databind.ObjectMapper().readTree(response.getContentAsString()).path("output").path("result").path("id").asText();
    }

    private org.springframework.test.web.servlet.ResultActions runCommand(jakarta.servlet.http.Cookie cookie,String operation,
            String id,long basis,Object command)throws Exception{
        return runCommand(cookie,"BusinessPartner",operation,id,basis,command);
    }

    private org.springframework.test.web.servlet.ResultActions runCommand(jakarta.servlet.http.Cookie cookie,String type,String operation,
            String id,long basis,Object command)throws Exception{
        var body=new LinkedHashMap<String,Object>();
        body.put("objectType",type);body.put("objectId",id);body.put("operationCode",operation);
        body.put("operationId",type+"."+operation);body.put("basisVersion",basis);body.put("command",command);
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/business-object-executions")
            .cookie(cookie).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
            .header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json")
            .content(new tools.jackson.databind.ObjectMapper().writeValueAsString(body)));
    }

    private Input input(String code,UUID org,Map<String,String> values,List<DetailInput> contacts,List<DetailInput> bank){var attrs=new HashMap<>(values);attrs.putIfAbsent("partnerType","CUSTOMER");return new Input(code,code,attrs,0,contacts,bank,org==null?null:org.toString());}
    private UUID account(UUID actor){UUID id=UUID.randomUUID();administration.createUser(actor,id,"data-"+id,"数据权限验收", encoder.encode("isolated-data-access-test"));return id;}
    private UUID member(UUID tenant,UUID account){return UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM tenant_member WHERE tenant_id=UUID_TO_BIN(?) AND account_id=UUID_TO_BIN(?)",String.class,tenant.toString(),account.toString()));}
    private UUID permission(String code){return UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM access_permission WHERE code=?",String.class,code));}
    /** 测试夹具直接写组织，不作为生产跨模块数据库调用示例。 */
    private UUID organization(UUID tenant,String name,UUID parent){
        String type=jdbc.query("SELECT BIN_TO_UUID(id) FROM tenant_organization_type WHERE tenant_id=UUID_TO_BIN(?) AND code='BUSINESS'",r->r.next()?r.getString(1):null,tenant.toString());
        if(type==null){type=UUID.randomUUID().toString();jdbc.update("INSERT INTO tenant_organization_type(tenant_id,id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'BUSINESS','业务组织')",tenant.toString(),type);}
        UUID id=UUID.randomUUID();jdbc.update("INSERT INTO tenant_organization_unit(tenant_id,id,code,type_id,name,parent_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,UUID_TO_BIN(?),?,UUID_TO_BIN(?))",tenant.toString(),id.toString(),"DP-"+id,type,name,parent==null?null:parent.toString());return id;
    }
    /** 固定记录范围使用当前支持的组合策略，避免依赖历史组织权限。 */
    private static RolePermissionManagement.DataPolicy recordsPolicy(UUID permission,String... records){
        var ids=Arrays.stream(records).map(UUID::fromString).collect(java.util.stream.Collectors.toSet());
        var rule=new DataGrantRules.Rule(DataGrantRules.SubjectType.ROLE_MEMBERS,Set.of(),ids,List.of(),false);
        return new RolePermissionManagement.DataPolicy(permission,DataScopeAuthorization.ScopeType.RULES,
            "masterdata.partner",Set.of(),List.of(rule));
    }


    /** 复现已声明本人范围但 SQL 漏绑的 Mapper，必须在实际执行前拒绝。 */
    @com.appnexus.persistence.mybatis.PlatformScoped(tables={"leave_poc_request"})
    public interface MissingOwnerFilterProbe {
        @com.appnexus.persistence.mybatis.GuardedScope(tenant="tenant",resource="leave.request",actions="list",ownerRead="owner")
        @org.apache.ibatis.annotations.Select("SELECT COUNT(*) FROM leave_poc_request WHERE tenant_id=#{tenant}")
        long count(@org.apache.ibatis.annotations.Param("tenant") String tenant,
            @org.apache.ibatis.annotations.Param("owner") com.appnexus.persistence.mybatis.ScopedSql.OwnerPredicate owner);
    }

    /** 真实请假 Mapper 验证同账号多人员、历史空人员及跨租户边界。 */
    @Test void leaveOwnerQueryRejectsMissingOrMismatchedScope(){
        UUID tenant=UUID.randomUUID(),foreign=UUID.randomUUID(),actor=UUID.randomUUID(),
            person=UUID.randomUUID(),otherPerson=UUID.randomUUID(),otherAccount=UUID.randomUUID();
        String own=seedLeaveScopeRow(tenant,actor,person);
        String legacy=seedLeaveScopeRow(tenant,actor,null);
        seedLeaveScopeRow(tenant,actor,otherPerson);
        String anotherApplicantsRecord=seedLeaveScopeRow(tenant,otherAccount,person);
        seedLeaveScopeRow(foreign,actor,person);
        seedLeaveCycle(tenant,own);
        seedLeaveCycle(tenant,anotherApplicantsRecord);
        var type=com.appnexus.leave.adapter.persistence.LeaveRecordMapper.class;
        if(!sql.getConfiguration().hasMapper(type))sql.getConfiguration().addMapper(type);
        sql.getConfiguration().addMapper(MissingOwnerFilterProbe.class);
        var repository=new com.appnexus.leave.adapter.persistence.MyBatisLeaveRepository(sql.getMapper(type));
        assertThatThrownBy(()->repository.ownRequests(tenant.toString(),actor.toString(),person.toString()))
            .hasRootCauseMessage("SCOPED_SQL_OWNER_REQUIRED");
        var request=new org.springframework.mock.web.MockHttpServletRequest();
        request.setAttribute(VerifiedTenantContext.class.getName(),
            new VerifiedTenantContext(actor,tenant,UUID.randomUUID(),1,person,null));
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
            new org.springframework.web.context.request.ServletRequestAttributes(request));
        try{
            var owner=new com.appnexus.persistence.mybatis.ScopedSql.OwnerPredicate("applicant","person_id",
                actor.toString(),person.toString());
            assertThatThrownBy(()->sql.getMapper(MissingOwnerFilterProbe.class).count(tenant.toString(),owner))
                .hasRootCauseMessage("SCOPED_SQL_OWNER_REQUIRED");
            assertThat(repository.ownRequests(tenant.toString(),actor.toString(),person.toString()))
                .extracting(row->row.get("id")).containsExactlyInAnyOrder(own,legacy);
            assertThat(repository.approvalCycles(tenant.toString(),own,actor.toString())).hasSize(1);
            assertThat(repository.approvalCycles(tenant.toString(),anotherApplicantsRecord,actor.toString())).isEmpty();
            assertThat(repository.appliedApprovals(tenant.toString(),own,actor.toString())).hasSize(1);
            assertThat(repository.appliedApprovals(tenant.toString(),anotherApplicantsRecord,actor.toString())).isEmpty();
            assertThatThrownBy(()->repository.ownRequests(foreign.toString(),actor.toString(),person.toString()))
                .hasRootCauseMessage("SCOPED_SQL_OWNER_REQUIRED");
            assertThatThrownBy(()->repository.ownRequests(tenant.toString(),otherAccount.toString(),person.toString()))
                .hasRootCauseMessage("SCOPED_SQL_OWNER_REQUIRED");
            assertThatThrownBy(()->repository.ownRequests(tenant.toString(),actor.toString(),otherPerson.toString()))
                .hasRootCauseMessage("SCOPED_SQL_OWNER_REQUIRED");
            request.setAttribute(VerifiedTenantContext.class.getName(),
                new VerifiedTenantContext(actor,tenant,UUID.randomUUID(),1));
            assertThat(repository.ownRequests(tenant.toString(),actor.toString(),null))
                .extracting(row->row.get("id")).containsExactly(legacy);
        }finally{
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        }
    }

    /** 在隔离库写入一条已应用审批，供历史过滤正反向验证。 */
    private void seedLeaveCycle(UUID tenant,String record){
        String id=UUID.randomUUID().toString();
        jdbc.update("""
            INSERT INTO leave_poc_approval_cycle
              (tenant_id,id,object_id,basis_version,instance_id,basis_snapshot,delivery_id,result)
            VALUES(?,?,?,0,?,JSON_OBJECT(),?,'APPROVED')
            """,tenant.toString(),id,record,id,id);
    }

    /** @return 隔离测试库中的最小请假记录键 */
    private String seedLeaveScopeRow(UUID tenant,UUID account,UUID person){
        String id=UUID.randomUUID().toString();
        jdbc.update("""
            INSERT INTO leave_poc_request
              (tenant_id,id,applicant,person_id,organization_id,leave_type,start_date,end_date,days,reason,create_key)
            VALUES(?,?,?,?,?,'PERSONAL','2026-11-26','2026-11-26',1,'fixture',?)
            """,tenant.toString(),id,account.toString(),Objects.toString(person,null),UUID.randomUUID().toString(),id);
        return id;
    }

}
