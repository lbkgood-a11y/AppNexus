package com.appnexus.platform;

import com.appnexus.platform.authorization.RoleManagement;
import com.appnexus.platform.authorization.RoleManagementException;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.authorization.RolePermissionManagement;
import com.appnexus.platform.authorization.SubjectRoleManagement;
import com.appnexus.platform.authorization.DataScopeAuthorization;
import com.appnexus.platform.authorization.EffectiveAuthorizationQuery;
import com.appnexus.platform.persistence.platformscope.MyBatisPlatformAdministration;
import com.appnexus.platform.persistence.platformscope.MyBatisRoleManagement;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 使用真实 MySQL 验证角色生命周期、编码保留、租户隔离、管理权威、CSRF 与审计事务。 */
@SpringBootTest(properties="app.public-url=http://localhost:18080")
@ActiveProfiles("local")
@AutoConfigureMockMvc
class RoleManagementTest extends DatabaseTestSupport {
 @Autowired MyBatisRoleManagement roles;
 @Autowired RolePermissionManagement rolePermissions;
 @Autowired AccessAuthorization authorization;
 @Autowired SubjectRoleManagement subjectRoles;
 @Autowired DataScopeAuthorization dataScopes;
 @Autowired EffectiveAuthorizationQuery effectiveAuthorizations;
 @Autowired MyBatisPlatformAdministration administration;
 @Autowired JdbcTemplate jdbc;
 @Autowired PasswordEncoder encoder;
 @Autowired MockMvc mvc;
 UUID actor;
 @BeforeEach void initialize(){actor=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM identity_account WHERE username='admin-test'",String.class));administration.initializeAdministrator("admin-test");}
 private RoleManagement.Context platform(){return new RoleManagement.Context(actor,null,null);}
 private String code(){return "role_"+UUID.randomUUID().toString().replace("-","");}
 private UUID user(){UUID id=UUID.randomUUID();administration.createUser(actor,id,"role-user-"+id,"Role User",encoder.encode("test-password-2026"));return id;}

 @Test void platformLifecycleUsesOptimisticLockAndPermanentlyReservesCode(){
  String code=code();var created=roles.create(platform(),new RoleManagement.Create(code,"项目经理","项目管理",100));
  assertThat(created.status()).isEqualTo(RoleManagement.Status.ENABLED);
  var updated=roles.update(platform(),created.id(),new RoleManagement.Update("项目负责人",null,90,created.version()));
  assertThat(updated.version()).isEqualTo(1);
  assertThatThrownBy(()->roles.update(platform(),created.id(),new RoleManagement.Update("过期更新",null,90,0))).isInstanceOf(RoleManagementException.class).extracting("code").isEqualTo("ROLE_VERSION_CONFLICT");
  var disabled=roles.changeStatus(platform(),created.id(),updated.version(),RoleManagement.Status.DISABLED,"岗位调整");
  roles.delete(platform(),created.id(),disabled.version(),code,"岗位撤销");
  assertThatThrownBy(()->roles.create(platform(),new RoleManagement.Create(code,"重复角色",null,100))).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM access_role_audit WHERE role_id=UUID_TO_BIN(?)",Integer.class,created.id().toString())).isEqualTo(4);
 }

 @Test void tenantContextsAllowSameCodeButCannotCrossTenant(){
  UUID adminA=user(),adminB=user();
  var tenantA=administration.createTenant(actor,UUID.randomUUID(),"Role Tenant A",adminA);
  var tenantB=administration.createTenant(actor,UUID.randomUUID(),"Role Tenant B",adminB);
  UUID memberA=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM tenant_member WHERE tenant_id=UUID_TO_BIN(?) AND account_id=UUID_TO_BIN(?)",String.class,tenantA.id().toString(),adminA.toString()));
  UUID memberB=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM tenant_member WHERE tenant_id=UUID_TO_BIN(?) AND account_id=UUID_TO_BIN(?)",String.class,tenantB.id().toString(),adminB.toString()));
  var contextA=new RoleManagement.Context(adminA,tenantA.id(),memberA);var contextB=new RoleManagement.Context(adminB,tenantB.id(),memberB);
  String shared=code();var roleA=roles.create(contextA,new RoleManagement.Create(shared,"租户 A 角色",null,100));
  var roleB=roles.create(contextB,new RoleManagement.Create(shared,"租户 B 角色",null,100));
  assertThat(roleA.id()).isNotEqualTo(roleB.id());
  assertThat(roles.list(contextA,new RoleManagement.Query(shared,null,null,1,20)).items()).extracting(RoleManagement.Role::id).containsExactly(roleA.id());
  assertThatThrownBy(()->roles.update(contextA,roleB.id(),new RoleManagement.Update("越权",null,100,roleB.version()))).isInstanceOf(RoleManagementException.class).extracting("code").isEqualTo("ROLE_SCOPE_NOT_FOUND");
 }

 @Test void ordinaryAccountIsDeniedAndHttpWritesRequireCsrf() throws Exception {
  UUID ordinary=user();
  assertThatThrownBy(()->roles.list(new RoleManagement.Context(ordinary,null,null),new RoleManagement.Query("",null,null,1,20))).isInstanceOf(SecurityException.class);
  mvc.perform(get("/bff/platform/roles").with(authenticated(ordinary.toString(),0))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ROLE_MANAGEMENT_DENIED"));
  mvc.perform(post("/bff/platform/roles").with(authenticated(actor.toString(),0)).contentType("application/json").content("{}" )).andExpect(status().isForbidden());
  mvc.perform(post("/bff/platform/roles").with(authenticated(actor.toString(),0)).with(csrf()).contentType("application/json").content("{\"code\":\"INVALID-CODE\",\"name\":\"无效角色\",\"sortOrder\":100}" )).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ROLE_INPUT"));
 }
 @Test void platformHttpCreatesCustomRole() throws Exception {
  String code=code();
  mvc.perform(post("/bff/platform/roles").with(authenticated(actor.toString(),0)).with(csrf()).contentType("application/json")
      .content("{\"code\":\""+code+"\",\"name\":\"HTTP 角色\",\"description\":\"接口创建验证\",\"sortOrder\":100}"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(code))
      .andExpect(jsonPath("$.roleType").value("CUSTOM")).andExpect(jsonPath("$.status").value("ENABLED"));
 }

 @Test void rolePermissionReplacementDrivesUnifiedAuthorizationAndRejectsCrossScopePermission(){
  var role=roles.create(platform(),new RoleManagement.Create(code(),"授权测试角色",null,100));
  UUID platformPermission=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM access_permission WHERE code='identity.account:read'",String.class));
  UUID tenantPermission=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM access_permission WHERE code='organization.unit:read'",String.class));

  var assigned=rolePermissions.replace(platform(),role.id(),role.version(),java.util.Set.of(platformPermission),"验证角色权限闭环");
  var subjectAssignment=subjectRoles.accountRoles(actor,actor);
  subjectRoles.replaceAccountRoles(actor,actor,subjectAssignment.authorityVersion(),java.util.Set.of(role.id()),"验证账号角色闭环");

  assertThat(assigned.permissionIds()).containsExactly(platformPermission);
  assertThat(authorization.hasPermission(AccessAuthorization.Subject.platform(actor),"identity.account:read")).isTrue();
  assertThat(authorization.hasPermission(AccessAuthorization.Subject.platform(actor),"organization.unit:read")).isFalse();
  assertThatThrownBy(()->rolePermissions.replace(platform(),role.id(),assigned.roleVersion(),java.util.Set.of(tenantPermission),"尝试跨域授权"))
      .isInstanceOf(RoleManagementException.class).extracting("code").isEqualTo("PERMISSION_SCOPE_MISMATCH");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM access_authorization_audit WHERE target_id=UUID_TO_BIN(?)",Integer.class,role.id().toString())).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM access_authorization_audit WHERE target_id=UUID_TO_BIN(?)",Integer.class,actor.toString())).isEqualTo(1);
 }

 @Test void tenantRoleAtomicallyRequiresAndResolvesDataPolicy(){
  UUID account=user();var tenant=administration.createTenant(actor,UUID.randomUUID(),"Policy Tenant",account);
  UUID member=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM tenant_member WHERE tenant_id=UUID_TO_BIN(?) AND account_id=UUID_TO_BIN(?)",String.class,tenant.id().toString(),account.toString()));
  var managed=new RoleManagement.Context(actor,tenant.id(),null);var role=roles.create(managed,new RoleManagement.Create(code(),"数据范围角色",null,100));
  UUID permission=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM access_permission WHERE code='organization.unit:read'",String.class));
  assertThatThrownBy(()->rolePermissions.replace(managed,role.id(),role.version(),java.util.Set.of(permission),"缺少范围"))
      .isInstanceOf(RoleManagementException.class).extracting("code").isEqualTo("DATA_SCOPE_REQUIRED");
  var saved=rolePermissions.replaceAuthorization(managed,role.id(),role.version(),java.util.Set.of(permission),java.util.Set.of(
      new RolePermissionManagement.DataPolicy(permission,DataScopeAuthorization.ScopeType.ALL,"organization.unit",java.util.Set.of())),"配置租户全部组织范围");
  var memberAssignment=subjectRoles.memberRoles(managed,member);subjectRoles.replaceMemberRoles(managed,member,memberAssignment.authorityVersion(),java.util.Set.of(role.id()),"分配数据范围角色");
  var subject=AccessAuthorization.Subject.tenant(account,tenant.id(),member);
  assertThat(authorization.hasPermission(subject,"organization.unit:read")).isTrue();
  assertThat(dataScopes.resolveDataScope(subject,"organization.unit:read","organization.unit").type()).isEqualTo(DataScopeAuthorization.ScopeType.ALL);
  assertThat(rolePermissions.getAuthorization(managed,role.id()).dataPolicies()).isEqualTo(saved.dataPolicies());
  var explanation=effectiveAuthorizations.tenantMember(tenant.id(),member);
  assertThat(explanation).singleElement().satisfies(item->{assertThat(item.permissionCode()).isEqualTo("organization.unit:read");assertThat(item.effective()).isTrue();assertThat(item.sources()).singleElement().satisfies(source->{assertThat(source.roleId()).isEqualTo(role.id());assertThat(source.scopeType()).isEqualTo(DataScopeAuthorization.ScopeType.ALL);assertThat(source.inactiveReason()).isNull();});});
 }
}
