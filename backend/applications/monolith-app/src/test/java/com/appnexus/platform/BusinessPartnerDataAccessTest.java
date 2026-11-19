package com.appnexus.platform;

import com.appnexus.masterdata.*;
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
import static com.appnexus.masterdata.MasterData.*;

/** 真实MySQL客商闭环：声明、行范围、子树、字段、明细、租户及失败回滚使用生产执行器。 */
@SpringBootTest(properties="app.public-url=http://localhost:18080")
@ActiveProfiles("local")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class BusinessPartnerDataAccessTest extends DatabaseTestSupport {
    @Autowired com.appnexus.platform.businessobject.BusinessObjectManagement businessObjects;
    @Autowired OperationRegistry operations;
    @Autowired MasterDataService service;@Autowired MyBatisPlatformAdministration administration;
    @Autowired RoleManagement roles;@Autowired RolePermissionManagement permissions;@Autowired SubjectRoleManagement subjects;
    @Autowired MemberAuthorizationManagement members;@Autowired DataAccessCatalog catalog;@Autowired FieldCatalog fields;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
    /** 完整工作流验证一次共享夹具，避免用伪数据目录或绕过生产权限端口。 */
    @Test void declarationDrivenPartnerRowsFieldsAndDetailsAreEnforced() throws Exception {
        UUID actor=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM identity_account WHERE username='admin-test'",String.class));
        administration.initializeAdministrator("admin-test");
        var directory=businessObjects.catalog(new com.appnexus.contracts.businessobject.BusinessObjects.Context(null,actor.toString(),UUID.randomUUID().toString()),true);
        assertThat(directory.stream().map(row->(com.appnexus.contracts.businessobject.BusinessObjects.Descriptor)row.get("descriptor")).filter(d->d.objectType().equals("BusinessPartner")).toList()).singleElement().satisfies(d->{
            assertThat(d.category()).isEqualTo(com.appnexus.contracts.businessobject.BusinessObjects.Category.MASTER_DATA);
            assertThat(d.dataAccess()).isNotNull();assertThat(d.operations()).hasSize(4);
            assertThat(d.operations()).allSatisfy(op->assertThat(op.supportedServices()).containsExactly("BUSINESS"));
        });
        assertThat(operations.objects().stream().filter(o->o.code().equals("BusinessPartner")).toList()).singleElement().satisfies(o->assertThat(o.operations()).hasSize(4));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/bff/platform/business-objects").with(authenticated(actor.toString(),0)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[?(@.descriptor.objectType == 'BusinessPartner')].descriptor.nameZh").value(org.hamcrest.Matchers.contains("客商管理")));

        UUID owner=account(actor),user=account(actor),otherOwner=account(actor);
        UUID tenant=administration.createTenant(actor,UUID.randomUUID(),"客商数据权限验收",owner).id();
        UUID otherTenant=administration.createTenant(actor,UUID.randomUUID(),"外租户",otherOwner).id();
        administration.addMember(actor,tenant,user);
        var c=new VerifiedTenantContext(owner,tenant,member(tenant,owner),1);
        var u=new VerifiedTenantContext(user,tenant,member(tenant,user),1);
        var foreign=new VerifiedTenantContext(otherOwner,otherTenant,member(otherTenant,otherOwner),1);
        UUID a=organization(tenant,"A",null),child=organization(tenant,"A下级",a),b=organization(tenant,"B",null),outside=organization(otherTenant,"外组织",null);
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
        assertThat(catalog.list(PermissionCatalog.ScopeType.TENANT)).anyMatch(d->d.objectType().equals("BusinessPartner")&&d.version()==1);
        assertThat(fields.list(PermissionCatalog.ScopeType.TENANT)).anyMatch(f->f.resource().equals("masterdata.partner.bank")&&f.field().equals("accountNumber")&&f.modes().contains(FieldSecurity.Mode.READ_ONLY));
        assertThat(service.list(u,Kind.PARTNER,"",null,1,20).total()).isEqualTo(2);
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
        var grant=members.get(rc,u.memberId());var branch=grant.grants().getFirst();
        var custom=policies.stream().map(p->new RolePermissionManagement.DataPolicy(p.permissionId(),DataScopeAuthorization.ScopeType.ORGANIZATION,"masterdata.partner",Set.of(b))).collect(java.util.stream.Collectors.toSet());
        members.replace(rc,u.memberId(),new MemberAuthorizationManagement.Grant(role.id(),ids,custom,null,saved.roleVersion(),false),grant.authorityVersion(),"成员自定义B");
        assertThat(service.list(u,Kind.PARTNER,"",null,1,20).rows()).extracting(Row::id).containsExactly(blocked);
        var customGrant=members.get(rc,u.memberId());members.replace(rc,u.memberId(),new MemberAuthorizationManagement.Grant(role.id(),ids,Set.of(),null,saved.roleVersion(),true),customGrant.authorityVersion(),"恢复继承");
        assertThat(service.list(u,Kind.PARTNER,"",null,1,20).total()).isEqualTo(2);
    }
    private Input input(String code,UUID org,Map<String,String> values,List<DetailInput> contacts,List<DetailInput> bank){var attrs=new HashMap<>(values);attrs.put("partnerType","CUSTOMER");return new Input(code,code,attrs,0,contacts,bank,org==null?null:org.toString());}
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
