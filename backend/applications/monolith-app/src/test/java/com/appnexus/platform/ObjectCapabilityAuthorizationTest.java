package com.appnexus.platform;
import com.appnexus.contracts.businessobject.BusinessObjects.*;

import com.appnexus.contracts.businessobject.*;
import com.appnexus.platform.authorization.*;
import com.appnexus.platform.persistence.platformscope.MyBatisPlatformAdministration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

/** 真实授权中心与MySQL验证对象能力矩阵，不用允许全部的功能、范围或字段替身。 */
@SpringBootTest(properties={"app.workflow.engine.enabled=true","app.workflow.worker.enabled=false"})
@ActiveProfiles("local")
class ObjectCapabilityAuthorizationTest extends WorkflowDatabaseTestSupport {
  @Autowired BusinessObjectPlatform platform;
  @Autowired BusinessObjectExecution execution;
  @Autowired com.appnexus.contracts.workflow.WorkflowPlatform workflowPlatform;
  @Autowired MyBatisPlatformAdministration administration;
  @Autowired RoleManagement roles;
  @Autowired RolePermissionManagement permissions;
  @Autowired SubjectRoleManagement subjects;
  @Autowired MemberAuthorizationManagement members;
  @Autowired List<Provider> providers;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
  @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;

  @Test void realAuthorizationMatrixFailsClosedAndRevocationIsImmediate(){
    UUID admin=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM identity_account WHERE username='admin-test'",String.class));
    administration.initializeAdministrator("admin-test");
    UUID owner=account(admin),user=account(admin),outsider=account(admin);
    UUID tenant=administration.createTenant(admin,UUID.randomUUID(),"对象能力授权矩阵",owner).id();
    UUID other=administration.createTenant(admin,UUID.randomUUID(),"对象能力外租户",outsider).id();
    administration.addMember(admin,tenant,user);
    UUID member=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM tenant_member WHERE tenant_id=UUID_TO_BIN(?) AND account_id=UUID_TO_BIN(?)",String.class,tenant.toString(),user.toString()));
    UUID a=organization(tenant,"A"),b=organization(tenant,"B");
    var context=new Context(tenant.toString(),user.toString(),UUID.randomUUID().toString());
    var roleContext=new RoleManagement.Context(admin,tenant,null);
    var role=roles.create(roleContext,new RoleManagement.Create("matrix_"+UUID.randomUUID().toString().replace("-",""),"对象能力测试",null,100));
    var memberRoles=subjects.memberRoles(roleContext,member);
    subjects.replaceMemberRoles(roleContext,member,memberRoles.authorityVersion(),Set.of(role.id()),"矩阵角色");
    var leave=providers.stream().filter(p->p.descriptor().objectType().equals("LeaveRequest")).findFirst().orElseThrow().descriptor();
    var product=providers.stream().filter(p->p.descriptor().objectType().equals("ProductApproval")).findFirst().orElseThrow().descriptor();
    var printSnapshot=new BusinessObjectRuntime.Snapshot(0,a.toString(),Map.of("days",2,"requiresHr",false,"documentNumber","private-number","startDate","2026-11-01","endDate","2026-11-02","state","DRAFT","reason","private-reason"));
    var productSnapshot=new BusinessObjectRuntime.Snapshot(1,a.toString(),Map.of("productVersion",1));
    UUID print=permission("leave.request:print"),submit=permission("leave.request:submit"),productSubmit=permission("masterdata.product.approval:submit");
    var ids=Set.of(print,submit,productSubmit);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM access_permission WHERE code='masterdata.product:submit_approval'",Integer.class)).isZero();
    assertThat(platform.operationAllowed(context,"LeaveRequest.print","leave.request:print")).isFalse();
    assertDenied(()->platform.authorizeRecord(context,leave,"print",printSnapshot));
    assertThatThrownBy(()->permissions.replaceAuthorization(roleContext,role.id(),role.version(),ids,Set.of(),"缺少范围的配置必须拒绝")).isInstanceOf(RuntimeException.class);
    // 模拟历史不完整授权，验证运行时也失败关闭；生产配置入口不能创建这种状态。
    for(UUID permission:ids)jdbc.update("INSERT INTO access_role_permission(role_id,permission_id,scope_type,granted_by,tenant_id) SELECT id,UUID_TO_BIN(?),'TENANT',UUID_TO_BIN(?),tenant_id FROM access_role WHERE id=UUID_TO_BIN(?)",permission.toString(),admin.toString(),role.id().toString());
    var noScope=permissions.getAuthorization(roleContext,role.id());
    assertThat(platform.operationAllowed(context,"LeaveRequest.print","leave.request:print")).isTrue();
    assertDenied(()->platform.authorizeRecord(context,leave,"print",printSnapshot));
    String record=UUID.randomUUID().toString();
    jdbc.update("INSERT INTO leave_poc_request(tenant_id,id,applicant,organization_id,leave_type,start_date,end_date,days,reason,create_key) VALUES(?,?,?,?,'PERSONAL','2026-11-01','2026-11-02',2,'private-reason',?)",tenant.toString(),record,user.toString(),a.toString(),UUID.randomUUID().toString());
    long resolutions=jdbc.queryForObject("SELECT COUNT(*) FROM business_object_resolution WHERE tenant_id=?",Long.class,tenant.toString());
    assertDenied(()->execution.execute(context,new BusinessObjectExecution.Request("LeaveRequest",record,"print","LeaveRequest.print",0)));
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_object_resolution WHERE tenant_id=?",Long.class,tenant.toString())).isEqualTo(resolutions);
    assertThat(jdbc.queryForObject("SELECT state FROM leave_poc_request WHERE tenant_id=? AND id=?",String.class,tenant.toString(),record)).isEqualTo("DRAFT");
    var policies=Set.of(new RolePermissionManagement.DataPolicy(print,DataScopeAuthorization.ScopeType.ORGANIZATION,"leave.request",Set.of(a)),
      new RolePermissionManagement.DataPolicy(submit,DataScopeAuthorization.ScopeType.ORGANIZATION,"leave.request",Set.of(a)),
      new RolePermissionManagement.DataPolicy(productSubmit,DataScopeAuthorization.ScopeType.ORGANIZATION,"masterdata.product.approval",Set.of(a)));
    var granted=permissions.replaceAuthorization(roleContext,role.id(),noScope.roleVersion(),ids,policies,"只授A组织");
    assertThat(platform.authorizeRecord(context,leave,"print",printSnapshot).values()).doesNotContainKey("reason");
    assertThat(platform.authorizeRecord(context,product,"submit",productSnapshot).values()).containsKey("productVersion");
    assertDenied(()->platform.authorizeRecord(context,leave,"print",new BusinessObjectRuntime.Snapshot(0,b.toString(),printSnapshot.values())));
    assertDenied(()->platform.authorizeRecord(new Context(other.toString(),user.toString(),UUID.randomUUID().toString()),leave,"print",printSnapshot));
    assertDenied(()->platform.authorizeRecord(new Context(tenant.toString(),"partner-untrusted",UUID.randomUUID().toString()),leave,"print",printSnapshot));
    UUID child=organization(tenant,"A-child");
    jdbc.update("UPDATE tenant_organization_unit SET parent_id=UUID_TO_BIN(?) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",a.toString(),tenant.toString(),child.toString());
    var subtreePolicies=Set.of(new RolePermissionManagement.DataPolicy(print,DataScopeAuthorization.ScopeType.ORGANIZATION_SUBTREE,"leave.request",Set.of(a)),
      new RolePermissionManagement.DataPolicy(submit,DataScopeAuthorization.ScopeType.ORGANIZATION,"leave.request",Set.of(a)),
      new RolePermissionManagement.DataPolicy(productSubmit,DataScopeAuthorization.ScopeType.ORGANIZATION,"masterdata.product.approval",Set.of(a)));
    var subtree=permissions.replaceAuthorization(roleContext,role.id(),granted.roleVersion(),ids,subtreePolicies,"组织子树");
    assertThat(platform.authorizeRecord(context,leave,"print",new BusinessObjectRuntime.Snapshot(0,child.toString(),printSnapshot.values())).values()).containsKey("days");
    assertDenied(()->platform.authorizeRecord(context,leave,"print",new BusinessObjectRuntime.Snapshot(0,b.toString(),printSnapshot.values())));
    var allPolicies=Set.of(new RolePermissionManagement.DataPolicy(print,DataScopeAuthorization.ScopeType.ALL,"leave.request",Set.of()),
      new RolePermissionManagement.DataPolicy(submit,DataScopeAuthorization.ScopeType.ORGANIZATION,"leave.request",Set.of(a)),
      new RolePermissionManagement.DataPolicy(productSubmit,DataScopeAuthorization.ScopeType.ORGANIZATION,"masterdata.product.approval",Set.of(a)));
    var all=permissions.replaceAuthorization(roleContext,role.id(),subtree.roleVersion(),ids,allPolicies,"ALL仍限当前租户");
    assertThat(platform.authorizeRecord(context,leave,"print",new BusinessObjectRuntime.Snapshot(0,b.toString(),printSnapshot.values())).values()).containsKey("days");
    assertDenied(()->platform.authorizeRecord(new Context(other.toString(),user.toString(),UUID.randomUUID().toString()),leave,"print",printSnapshot));
    granted=permissions.replaceAuthorization(roleContext,role.id(),all.roleVersion(),ids,policies,"恢复指定组织");
    var fieldPolicies=Set.of(new FieldSecurity.Policy("leave.request","documentNumber",FieldSecurity.Mode.MASKED),new FieldSecurity.Policy("masterdata.product.approval","productVersion",FieldSecurity.Mode.HIDDEN));
    var restricted=permissions.replaceAuthorization(roleContext,role.id(),granted.roleVersion(),ids,policies,fieldPolicies,"字段限制");
    assertThat(platform.authorizeRecord(context,leave,"print",printSnapshot).values().get("documentNumber")).isEqualTo("***");
    assertDenied(()->platform.authorizeRecord(context,product,"submit",productSnapshot));
    var hidden=permissions.replaceAuthorization(roleContext,role.id(),restricted.roleVersion(),ids,policies,Set.of(new FieldSecurity.Policy("leave.request","days",FieldSecurity.Mode.HIDDEN)),"隐藏审批依据");
    assertDenied(()->platform.authorizeRecord(context,leave,"submit",printSnapshot));
    var protectedTask=workflowPlatform.projectData(new com.appnexus.contracts.workflow.WorkflowContext(tenant.toString(),user.toString(),UUID.randomUUID().toString()),"leave.request",Map.of("days",2,"requiresHr",false));
    assertThat(protectedTask).doesNotContainKey("days");
    var approvalSchema=leave.operations().stream().filter(op->op.operationCode().equals("submit")).findFirst().orElseThrow().interaction().capabilities().stream().filter(cap->cap.service()==InteractionContract.Service.WORKFLOW).findFirst().orElseThrow().schema();
    assertThatThrownBy(()->approvalSchema.encode(protectedTask)).isInstanceOf(com.appnexus.contracts.data.DataContract.Violation.class).hasMessage("DATA_FIELD_REQUIRED");
    var restored=permissions.replaceAuthorization(roleContext,role.id(),hidden.roleVersion(),ids,policies,Set.of(),"恢复字段");
    var originalGrant=members.get(roleContext,member);
    var customPolicies=Set.of(new RolePermissionManagement.DataPolicy(print,DataScopeAuthorization.ScopeType.ORGANIZATION,"leave.request",Set.of(b)),
      new RolePermissionManagement.DataPolicy(submit,DataScopeAuthorization.ScopeType.ORGANIZATION,"leave.request",Set.of(b)),
      new RolePermissionManagement.DataPolicy(productSubmit,DataScopeAuthorization.ScopeType.ORGANIZATION,"masterdata.product.approval",Set.of(b)));
    members.replace(roleContext,member,new MemberAuthorizationManagement.Grant(role.id(),ids,customPolicies,null,restored.roleVersion(),false),originalGrant.authorityVersion(),"成员自定义B组织");
    assertDenied(()->platform.authorizeRecord(context,leave,"print",printSnapshot));
    assertThat(platform.authorizeRecord(context,leave,"print",new BusinessObjectRuntime.Snapshot(0,b.toString(),printSnapshot.values())).values()).containsKey("days");
    var customGrant=members.get(roleContext,member);
    members.replace(roleContext,member,new MemberAuthorizationManagement.Grant(role.id(),ids,Set.of(),null,restored.roleVersion(),true),customGrant.authorityVersion(),"恢复角色继承");
    assertThat(platform.authorizeRecord(context,leave,"print",printSnapshot).values()).containsKey("days");
    var grant=members.get(roleContext,member);
    members.replace(roleContext,member,new MemberAuthorizationManagement.Grant(role.id(),ids,Set.of(),java.time.Instant.now().minusSeconds(60),restored.roleVersion(),true),grant.authorityVersion(),"过期拒绝");
    assertDenied(()->platform.authorizeRecord(context,leave,"print",printSnapshot));
    var expired=members.get(roleContext,member);
    members.replace(roleContext,member,new MemberAuthorizationManagement.Grant(role.id(),ids,Set.of(),null,restored.roleVersion(),true),expired.authorityVersion(),"恢复继承");
    assertThat(platform.authorizeRecord(context,leave,"print",printSnapshot).values().get("documentNumber")).isEqualTo("private-number");
    jdbc.update("UPDATE tenant_member SET enabled=FALSE WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",tenant.toString(),member.toString());
    try{assertDenied(()->platform.authorizeRecord(context,leave,"print",printSnapshot));}
    finally{jdbc.update("UPDATE tenant_member SET enabled=TRUE WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",tenant.toString(),member.toString());}
    permissions.replaceAuthorization(roleContext,role.id(),restored.roleVersion(),Set.of(),Set.of(),"撤权立即拒绝");
    assertThat(platform.operationAllowed(context,"LeaveRequest.print","leave.request:print")).isFalse();
    assertDenied(()->platform.authorizeRecord(context,leave,"print",printSnapshot));
  }
  private void assertDenied(Runnable action){assertThatThrownBy(action::run).isInstanceOf(Rejection.class).hasMessage("BUSINESS_OBJECT_DATA_ACCESS_DENIED");}
  private UUID permission(String code){return UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM access_permission WHERE code=?",String.class,code));}
  private UUID account(UUID admin){UUID id=UUID.randomUUID();administration.createUser(admin,id,"matrix-"+id,"矩阵测试",encoder.encode("isolated-matrix-password"));return id;}
  private UUID organization(UUID tenant,String name){
    String type=jdbc.query("SELECT BIN_TO_UUID(id) FROM tenant_organization_type WHERE tenant_id=UUID_TO_BIN(?) AND code='BUSINESS'",r->r.next()?r.getString(1):null,tenant.toString());
    if(type==null){type=UUID.randomUUID().toString();jdbc.update("INSERT INTO tenant_organization_type(tenant_id,id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'BUSINESS','业务组织')",tenant.toString(),type);}
    UUID id=UUID.randomUUID();jdbc.update("INSERT INTO tenant_organization_unit(tenant_id,id,code,name,type_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,UUID_TO_BIN(?))",tenant.toString(),id.toString(),id.toString(),name,type);return id;
  }
}
