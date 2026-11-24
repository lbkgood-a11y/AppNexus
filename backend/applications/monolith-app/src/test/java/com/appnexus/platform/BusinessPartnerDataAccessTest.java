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
    @Autowired com.appnexus.masterdata.application.masterdata.StatusHistoryQuery statusHistory;
    @Autowired MasterDataService service;@Autowired MyBatisPlatformAdministration administration;
    @Autowired RoleManagement roles;@Autowired RolePermissionManagement permissions;@Autowired SubjectRoleManagement subjects;
    @Autowired MemberAuthorizationManagement members;@Autowired DataAccessCatalog catalog;@Autowired FieldCatalog fields;
    @Autowired JdbcTemplate jdbc;
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

    /** 完整工作流验证一次共享夹具，避免用伪数据目录或绕过生产权限端口。 */
    @Test void declarationDrivenPartnerRowsFieldsAndDetailsAreEnforced() throws Exception {
        UUID actor=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM identity_account WHERE username='admin-test'",String.class));
        administration.initializeAdministrator("admin-test");
        var directory=businessObjects.catalog(new com.appnexus.contracts.businessobject.BusinessObjects.Context(null,actor.toString(),UUID.randomUUID().toString()),true);
        assertThat(directory.stream().map(row->(com.appnexus.contracts.businessobject.BusinessObjects.Descriptor)row.get("descriptor")).filter(d->d.objectType().equals("BusinessPartner")).toList()).singleElement().satisfies(d->{
            assertThat(d.category()).isEqualTo(com.appnexus.contracts.businessobject.BusinessObjects.Category.MASTER_DATA);
            assertThat(d.dataAccess()).isNotNull();assertThat(d.operations()).hasSize(5);
            assertThat(d.operations()).allSatisfy(op->assertThat(op.supportedServices()).containsExactly("BUSINESS"));
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
            policies.add(new RolePermissionManagement.DataPolicy(id,action.equals("read")||action.equals("create")?DataScopeAuthorization.ScopeType.ORGANIZATION_SUBTREE:DataScopeAuthorization.ScopeType.ORGANIZATION,"masterdata.partner",Set.of(a)));
        }
        var saved=permissions.replaceAuthorization(rc,role.id(),role.version(),ids,policies,"客商按组织授权");
        var initial=subjects.memberRoles(rc,u.memberId());subjects.replaceMemberRoles(rc,u.memberId(),initial.authorityVersion(),Set.of(role.id()),"角色默认继承");
        assertThat(catalog.list(PermissionCatalog.ScopeType.TENANT)).anyMatch(d->d.objectType().equals("BusinessPartner")&&d.version()==6);
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
        // 组织被授权方与客商归属不同：B组织成员可以看归属A的FIRST。
        UUID person=UUID.randomUUID();
        jdbc.update("INSERT INTO md_person(id,tenant_id,code,name,employment_status) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,'组合授权人员','ACTIVE')",person.toString(),tenant.toString(),"GRANT-"+person);
        jdbc.update("INSERT INTO md_person_account(tenant_id,id,person_id,member_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",tenant.toString(),UUID.randomUUID().toString(),person.toString(),u.memberId().toString());
        UUID affiliation=UUID.randomUUID();
        jdbc.update("INSERT INTO tenant_person_organization(tenant_id,id,person_id,organization_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",tenant.toString(),affiliation.toString(),person.toString(),b.toString());
        var orgRule=new DataGrantRules.Rule(DataGrantRules.SubjectType.ORGANIZATION,Set.of(b),Set.of(UUID.fromString(first)),List.of(),false);
        var organizationPolicy=new RolePermissionManagement.DataPolicy(readPermission,DataScopeAuthorization.ScopeType.RULES,"masterdata.partner",Set.of(),List.of(orgRule));
        var grantState=members.get(rc,u.memberId());
        var memberPolicies=new HashSet<>(policies);memberPolicies.removeIf(p->p.permissionId().equals(readPermission));memberPolicies.add(organizationPolicy);
        members.replace(rc,u.memberId(),new MemberAuthorizationManagement.Grant(role.id(),ids,memberPolicies,null,saved.roleVersion(),false),grantState.authorityVersion(),"成员组织组合授权");
        assertThat(members.get(rc,u.memberId()).grants().getFirst().dataPolicies()).contains(organizationPolicy);
        assertThat(service.list(u,Kind.PARTNER,"",null,1,20).rows()).extracting(Row::id).containsExactly(first);
        jdbc.update("UPDATE tenant_person_organization SET enabled=FALSE WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",tenant.toString(),affiliation.toString());
        assertThat(service.list(u,Kind.PARTNER,"",null,1,20).rows()).isEmpty();
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
            new DataGrantRules.Condition("organizationId",DataGrantRules.Operator.EQ,List.of(a.toString())),
            new DataGrantRules.Condition("partnerType",DataGrantRules.Operator.EQ,List.of("CUSTOMER")));
        var exactPolicies=new HashSet<>(combinedPolicies);
        exactPolicies.removeIf(policy->policy.permissionId().equals(readPermission));
        exactPolicies.add(new RolePermissionManagement.DataPolicy(readPermission,DataScopeAuthorization.ScopeType.RULES,"masterdata.partner",Set.of(),List.of(
            new DataGrantRules.Rule(DataGrantRules.SubjectType.ROLE_MEMBERS,Set.of(),Set.of(),exactConditions,false))));
        saved=permissions.replaceAuthorization(rc,role.id(),saved.roleVersion(),ids,exactPolicies,"厦门事业部且客商类型为客户");
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
        UUID selectedPerson=UUID.randomUUID();
        var selectedAtXiamen=new VerifiedTenantContext(u.accountId(),u.tenantId(),u.memberId(),u.version(),selectedPerson,a);
        for(var parameter:List.of(DataGrantRules.Parameter.CURRENT_ACCOUNT,DataGrantRules.Parameter.CURRENT_ORGANIZATION,
                DataGrantRules.Parameter.CURRENT_ORGANIZATION_SUBTREE)){
            String field=parameter==DataGrantRules.Parameter.CURRENT_ACCOUNT?"createdBy":"organizationId";
            var conditions=List.of(new DataGrantRules.Condition(field,DataGrantRules.Operator.EQ,List.of(),parameter),
                new DataGrantRules.Condition("partnerType",DataGrantRules.Operator.EQ,List.of("CUSTOMER")));
            var rule=new DataGrantRules.Rule(DataGrantRules.SubjectType.ROLE_MEMBERS,Set.of(),Set.of(),conditions,false);
            var dynamicPolicies=new HashSet<>(exactPolicies);
            dynamicPolicies.removeIf(policy->policy.permissionId().equals(readPermission));
            dynamicPolicies.add(new RolePermissionManagement.DataPolicy(readPermission,DataScopeAuthorization.ScopeType.RULES,
                "masterdata.partner",Set.of(),List.of(rule)));
            saved=permissions.replaceAuthorization(rc,role.id(),saved.roleVersion(),ids,dynamicPolicies,"验证动态参数 "+parameter);
            assertThat(permissions.getAuthorization(rc,role.id()).dataPolicies()).containsAll(dynamicPolicies);
            var result=service.list(selectedAtXiamen,Kind.PARTNER,"",null,1,20);
            var expected=switch(parameter){
                case CURRENT_ACCOUNT -> Set.of(own);
                case CURRENT_ORGANIZATION -> Set.of(first,own);
                case CURRENT_ORGANIZATION_SUBTREE -> Set.of(first,own,second);
                default -> throw new IllegalStateException();
            };
            assertThat(result.total()).isEqualTo(expected.size());
            assertThat(result.rows()).extracting(Row::id).containsExactlyInAnyOrderElementsOf(expected);
            assertThat(service.list(u,Kind.PARTNER,"",null,1,20).total())
                .isEqualTo(parameter==DataGrantRules.Parameter.CURRENT_ACCOUNT?1:0);
        }
        jdbc.update("DELETE FROM md_partner WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",tenant.toString(),own);
        jdbc.update("DELETE FROM md_partner WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",tenant.toString(),supplier);
        saved=permissions.replaceAuthorization(rc,role.id(),saved.roleVersion(),ids,policies,"恢复原组织范围验证写入");
        var selected=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/bff/tenants/current").with(authenticated(user.toString(),0)).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content("{\"tenantId\":\""+tenant+"\"}")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn();
        var cookie=selected.getResponse().getCookie("JSESSIONID");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/master-data/partner").cookie(cookie).param("id",blocked)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.rows").isEmpty());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/master-data/partner").cookie(cookie).param("dataAccess","true")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.organizations.length()").value(2));

        assertThat(service.list(u,Kind.PARTNER,"",null,1,20,blocked).rows()).isEmpty();
        assertThat(service.list(foreign,Kind.PARTNER,"",null,1,20,first).rows()).isEmpty();
        assertThatThrownBy(()->service.update(u,Kind.PARTNER,blocked,new Input("BLOCKED","越权",Map.of("partnerType","CUSTOMER"),0))).isInstanceOf(Rejection.class);
        assertThatThrownBy(()->service.setStatus(u,Kind.PARTNER,second,new Status(false,0))).isInstanceOf(Rejection.class);
        assertThatThrownBy(()->service.create(u,Kind.PARTNER,input("DENIED",b,Map.of(),null,null))).isInstanceOf(Rejection.class);
        assertThatThrownBy(()->service.create(u,Kind.PARTNER,input("FOREIGN",outside,Map.of(),null,null))).isInstanceOf(Rejection.class);
        assertThatThrownBy(()->service.create(u,Kind.PARTNER,input("NO_ORG",null,Map.of(),null,null))).isInstanceOf(Rejection.class);
        assertThatThrownBy(()->service.update(u,Kind.PARTNER,first,new Input("FIRST","非法移动",Map.of("partnerType","CUSTOMER"),0,null,null,b.toString()))).isInstanceOf(Rejection.class);
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
        var custom=policies.stream().map(p->new RolePermissionManagement.DataPolicy(p.permissionId(),DataScopeAuthorization.ScopeType.ORGANIZATION,"masterdata.partner",Set.of(b))).collect(java.util.stream.Collectors.toSet());
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
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.dataPolicies[?(@.scopeType == 'RULES')].rules[0].subjectType").value(org.hamcrest.Matchers.contains("MEMBER")));
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
}
