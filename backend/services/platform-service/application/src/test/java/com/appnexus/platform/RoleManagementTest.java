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
 @Autowired com.appnexus.platform.authorization.MemberAuthorizationManagement memberAuthorizations;
 @Autowired MyBatisPlatformAdministration administration;
 @Autowired JdbcTemplate jdbc;
 @Autowired PasswordEncoder encoder;
 @Autowired MockMvc mvc;
 UUID actor;
 @BeforeEach void initialize(){actor=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM identity_account WHERE username='admin-test'",String.class));administration.initializeAdministrator("admin-test");}
 private RoleManagement.Context platform(){return new RoleManagement.Context(actor,null,null);}
 private String code(){return "role_"+UUID.randomUUID().toString().replace("-","");}
 private UUID user(){UUID id=UUID.randomUUID();administration.createUser(actor,id,"role-user-"+id,"Role User",encoder.encode("test-password-2026"));return id;}

 /** 普通成员通过字典专属角色读取，无 GRANT 仍可访问；写入必须具备对应操作权限，撤权立即拒绝。 */
 @Test void dictionaryPermissionsAllowReadWithoutGrantAndRejectWrites() throws Exception {
  UUID owner=user(),account=user();var tenant=administration.createTenant(actor,UUID.randomUUID(),"Dictionary tenant",owner);
  administration.addMember(actor,tenant.id(),account);UUID member=member(tenant.id(),account);
  var c=new RoleManagement.Context(actor,tenant.id(),null);var role=roles.create(c,new RoleManagement.Create(code(),"字典只读",null,100));
  UUID read=permission("configuration.dictionary:read");rolePermissions.replaceAuthorization(c,role.id(),role.version(),java.util.Set.of(read),java.util.Set.of(),"字典查看");
  var assigned=subjectRoles.memberRoles(c,member);subjectRoles.replaceMemberRoles(c,member,assigned.authorityVersion(),java.util.Set.of(role.id()),"分配字典只读");
  var selection=mvc.perform(post("/bff/tenants/current").with(authenticated(account.toString(),0)).with(csrf()).contentType("application/json").content("{\"tenantId\":\""+tenant.id()+"\"}")).andExpect(status().isOk()).andReturn();
  var cookie=selection.getResponse().getCookie("JSESSIONID");
  mvc.perform(get("/api/configuration/dictionaries").cookie(cookie)).andExpect(status().isOk());
  mvc.perform(get("/api/configuration/dictionaries/capabilities").cookie(cookie)).andExpect(status().isOk()).andExpect(content().json("[\"read\"]"));
  mvc.perform(post("/api/configuration/dictionaries").cookie(cookie).with(csrf()).contentType("application/json").content("{\"code\":\"test_dictionary\",\"name\":\"Test\",\"sortOrder\":100}")).andExpect(status().isForbidden());
  var currentRole=rolePermissions.getAuthorization(c,role.id());
  rolePermissions.replaceAuthorization(c,role.id(),currentRole.roleVersion(),java.util.Set.of(read,permission("configuration.dictionary:create")),java.util.Set.of(),"允许创建字典");
  // 角色增权不会扩大旧快照，显式重新授权才允许新增操作。
  var old=subjectRoles.memberRoles(c,member);subjectRoles.replaceMemberRoles(c,member,old.authorityVersion(),java.util.Set.of(),"重新授权前撤销");
  var empty=subjectRoles.memberRoles(c,member);subjectRoles.replaceMemberRoles(c,member,empty.authorityVersion(),java.util.Set.of(role.id()),"审核新增字典操作");
  var created=mvc.perform(post("/api/configuration/dictionaries").cookie(cookie).with(csrf()).contentType("application/json").content("{\"code\":\"test_dictionary\",\"name\":\"Test\",\"sortOrder\":100}")).andExpect(status().isOk()).andReturn();
  String dictionaryId=jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM tenant_dictionary WHERE tenant_id=UUID_TO_BIN(?) AND code='test_dictionary'",String.class,tenant.id().toString());
  mvc.perform(post("/api/configuration/dictionaries/"+dictionaryId+"/items").cookie(cookie).with(csrf()).contentType("application/json").content("{\"code\":\"item_one\",\"value\":\"one\",\"name\":\"One\",\"sortOrder\":100}")).andExpect(status().isForbidden());
  var current=subjectRoles.memberRoles(c,member);subjectRoles.replaceMemberRoles(c,member,current.authorityVersion(),java.util.Set.of(),"撤销字典权限");
  mvc.perform(get("/api/configuration/dictionaries").cookie(cookie)).andExpect(status().isForbidden());
 }

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

 /** 目标解释必须复核当前租户管理权与成员归属，不能通过目标 UUID 穿透另一租户。 */
 @Test void memberExplanationRequiresTrustedTenantAndRejectsForeignMember() throws Exception {
  UUID account=user(),foreignAccount=user();
  var tenant=administration.createTenant(actor,UUID.randomUUID(),"Explanation A",account);
  var foreignTenant=administration.createTenant(actor,UUID.randomUUID(),"Explanation B",foreignAccount);
  String member=jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM tenant_member WHERE tenant_id=UUID_TO_BIN(?) AND account_id=UUID_TO_BIN(?)",String.class,tenant.id().toString(),account.toString());
  String foreignMember=jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM tenant_member WHERE tenant_id=UUID_TO_BIN(?) AND account_id=UUID_TO_BIN(?)",String.class,foreignTenant.id().toString(),foreignAccount.toString());
  mvc.perform(get("/api/access/members/"+member+"/effective-permissions")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/access/members/"+member+"/authorizations")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/access/members/"+member+"/effective-permissions").with(authenticated(account.toString(),0))).andExpect(status().isConflict());
  var selection=mvc.perform(post("/bff/tenants/current").with(authenticated(account.toString(),0)).with(csrf()).contentType("application/json").content("{\"tenantId\":\""+tenant.id()+"\"}")).andExpect(status().isOk()).andReturn();
  var cookie=selection.getResponse().getCookie("JSESSIONID");
  mvc.perform(get("/api/access/members/"+member+"/effective-permissions").cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$[0].permissionCode").isString());
  mvc.perform(get("/api/access/members/"+foreignMember+"/effective-permissions").cookie(cookie)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SUBJECT_NOT_FOUND"));
  mvc.perform(get("/api/access/members/"+foreignMember+"/authorizations").cookie(cookie)).andExpect(status().isNotFound());
  mvc.perform(put("/api/access/members/"+member+"/authorizations").cookie(cookie).contentType("application/json").content("{}")).andExpect(status().isForbidden());
  UUID ordinary=user();administration.addMember(actor,tenant.id(),ordinary);
  var ordinarySelection=mvc.perform(post("/bff/tenants/current").with(authenticated(ordinary.toString(),0)).with(csrf()).contentType("application/json").content("{\"tenantId\":\""+tenant.id()+"\"}")).andExpect(status().isOk()).andReturn();
  mvc.perform(get("/api/access/members/"+member+"/effective-permissions").cookie(ordinarySelection.getResponse().getCookie("JSESSIONID"))).andExpect(status().isForbidden());
  mvc.perform(get("/api/access/members/"+member+"/authorizations").cookie(ordinarySelection.getResponse().getCookie("JSESSIONID"))).andExpect(status().isForbidden());
 }

 @Test void rolePermissionReplacementDrivesUnifiedAuthorizationAndRejectsCrossScopePermission(){
  var role=roles.create(platform(),new RoleManagement.Create(code(),"授权测试角色",null,100));
  UUID platformPermission=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM access_permission WHERE code='identity.account:read'",String.class));
  UUID tenantPermission=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM access_permission WHERE code='organization.unit:read'",String.class));

  var assigned=rolePermissions.replace(platform(),role.id(),role.version(),java.util.Set.of(platformPermission),"验证角色权限闭环");
  int auditsBefore=jdbc.queryForObject("SELECT COUNT(*) FROM access_authorization_audit WHERE target_id=UUID_TO_BIN(?)",Integer.class,actor.toString());
  var subjectAssignment=subjectRoles.accountRoles(actor,actor);
  subjectRoles.replaceAccountRoles(actor,actor,subjectAssignment.authorityVersion(),java.util.Set.of(role.id()),"验证账号角色闭环");

  assertThat(assigned.permissionIds()).containsExactly(platformPermission);
  assertThat(authorization.hasPermission(AccessAuthorization.Subject.platform(actor),"identity.account:read")).isTrue();
  assertThat(authorization.hasPermission(AccessAuthorization.Subject.platform(actor),"organization.unit:read")).isFalse();
  assertThatThrownBy(()->rolePermissions.replace(platform(),role.id(),assigned.roleVersion(),java.util.Set.of(tenantPermission),"尝试跨域授权"))
      .isInstanceOf(RoleManagementException.class).extracting("code").isEqualTo("PERMISSION_SCOPE_MISMATCH");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM access_authorization_audit WHERE target_id=UUID_TO_BIN(?)",Integer.class,role.id().toString())).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM access_authorization_audit WHERE target_id=UUID_TO_BIN(?)",Integer.class,actor.toString())).isEqualTo(auditsBefore+1);
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

 /** 同一角色的两个成员独立保存范围，角色扩大默认范围与新增操作均不得扩权到旧快照。 */
 @Test void independentMemberSnapshotsPreventRoleGrowthAndRespectExpiry(){
  UUID owner=user(),first=user(),second=user();var tenant=administration.createTenant(actor,UUID.randomUUID(),"Snapshot Tenant",owner);
  administration.addMember(actor,tenant.id(),first);administration.addMember(actor,tenant.id(),second);
  var context=new RoleManagement.Context(actor,tenant.id(),null);
  UUID memberA=member(tenant.id(),first),memberB=member(tenant.id(),second);
  UUID organizationA=organization(tenant.id(),"Snapshot A",null),organizationB=organization(tenant.id(),"Snapshot B",null);
  UUID read=permission("organization.unit:read"),update=permission("organization.unit:update");
  var role=roles.create(context,new RoleManagement.Create(code(),"独立范围",null,100));
  var configured=rolePermissions.replaceAuthorization(context,role.id(),role.version(),java.util.Set.of(read),java.util.Set.of(new RolePermissionManagement.DataPolicy(read,DataScopeAuthorization.ScopeType.ORGANIZATION,"organization.unit",java.util.Set.of(organizationA))),"初始范围");
  for(UUID member:java.util.List.of(memberA,memberB)){var current=subjectRoles.memberRoles(context,member);subjectRoles.replaceMemberRoles(context,member,current.authorityVersion(),java.util.Set.of(role.id()),"分配初始范围");}
  var snapshot=memberAuthorizations.get(context,memberB);var branch=snapshot.grants().getFirst();
  memberAuthorizations.replace(context,memberB,new com.appnexus.platform.authorization.MemberAuthorizationManagement.Grant(role.id(),branch.permissionIds(),java.util.Set.of(new RolePermissionManagement.DataPolicy(read,DataScopeAuthorization.ScopeType.ORGANIZATION,"organization.unit",java.util.Set.of(organizationB))),null,configured.roleVersion()),snapshot.authorityVersion(),"成员 B 范围");
  var subjectA=AccessAuthorization.Subject.tenant(first,tenant.id(),memberA);var subjectB=AccessAuthorization.Subject.tenant(second,tenant.id(),memberB);
  assertThat(dataScopes.resolveDataScope(subjectA,"organization.unit:read","organization.unit").organizationIds()).containsExactly(organizationA);
  assertThat(dataScopes.resolveDataScope(subjectB,"organization.unit:read","organization.unit").organizationIds()).containsExactly(organizationB);
  rolePermissions.replaceAuthorization(context,role.id(),configured.roleVersion(),java.util.Set.of(read,update),java.util.Set.of(new RolePermissionManagement.DataPolicy(read,DataScopeAuthorization.ScopeType.ALL,"organization.unit",java.util.Set.of()),new RolePermissionManagement.DataPolicy(update,DataScopeAuthorization.ScopeType.ALL,"organization.unit",java.util.Set.of())),"扩大角色默认值");
  assertThat(authorization.hasPermission(subjectA,"organization.unit:update")).isFalse();
  assertThat(dataScopes.resolveDataScope(subjectA,"organization.unit:read","organization.unit").organizationIds()).containsExactly(organizationA);
  administration.setMemberEnabled(actor,tenant.id(),memberA,false);administration.setMemberEnabled(actor,tenant.id(),memberA,true);
  assertThat(authorization.hasPermission(subjectA,"organization.unit:read")).isFalse();
  assertThat(memberAuthorizations.get(context,memberA).grants()).isEmpty();
  var fresh=memberAuthorizations.get(context,memberB);var currentRole=rolePermissions.getAuthorization(context,role.id());
  memberAuthorizations.replace(context,memberB,new com.appnexus.platform.authorization.MemberAuthorizationManagement.Grant(role.id(),branch.permissionIds(),branch.dataPolicies(),java.time.Instant.now().minusSeconds(60),currentRole.roleVersion()),fresh.authorityVersion(),"验证到期");
  assertThat(authorization.hasPermission(subjectB,"organization.unit:read")).isFalse();
  assertThat(dataScopes.resolveDataScope(subjectB,"organization.unit:read","organization.unit").type()).isEqualTo(DataScopeAuthorization.ScopeType.DENY);
  assertThat(effectiveAuthorizations.tenantMember(tenant.id(),memberB)).singleElement().satisfies(value->{assertThat(value.effective()).isFalse();assertThat(value.sources().getFirst().inactiveReason()).isEqualTo("AUTHORIZATION_EXPIRED");});
  assertThatThrownBy(()->memberAuthorizations.replace(context,memberB,branch,fresh.authorityVersion(),"过期版本")).isInstanceOf(RoleManagementException.class);
 }

 /** 精确根节点不能被另一个子树分支扩大；多根重叠不产生重复，旧组织权限不能回退其他资源。 */
 @Test void mixedOrganizationScopesPreserveBranchesAndLegacyResourceBoundary(){
  UUID owner=user(),account=user();var tenant=administration.createTenant(actor,UUID.randomUUID(),"Union Tenant",owner);administration.addMember(actor,tenant.id(),account);
  UUID member=member(tenant.id(),account),root=organization(tenant.id(),"Exact root",null),child=organization(tenant.id(),"Exact child",root),tree=organization(tenant.id(),"Tree",null),descendant=organization(tenant.id(),"Tree child",tree);
  var context=new RoleManagement.Context(actor,tenant.id(),null);UUID read=permission("organization.unit:read");
  var exact=roles.create(context,new RoleManagement.Create(code(),"精确",null,100));var subtree=roles.create(context,new RoleManagement.Create(code(),"子树",null,100));
  rolePermissions.replaceAuthorization(context,exact.id(),exact.version(),java.util.Set.of(read),java.util.Set.of(new RolePermissionManagement.DataPolicy(read,DataScopeAuthorization.ScopeType.ORGANIZATION,"organization.unit",java.util.Set.of(root))),"精确范围");
  rolePermissions.replaceAuthorization(context,subtree.id(),subtree.version(),java.util.Set.of(read),java.util.Set.of(new RolePermissionManagement.DataPolicy(read,DataScopeAuthorization.ScopeType.ORGANIZATION_SUBTREE,"organization.unit",java.util.Set.of(tree,descendant))),"子树范围");
  var assigned=subjectRoles.memberRoles(context,member);subjectRoles.replaceMemberRoles(context,member,assigned.authorityVersion(),java.util.Set.of(exact.id(),subtree.id()),"混合授权");
  var subject=AccessAuthorization.Subject.tenant(account,tenant.id(),member);var scope=dataScopes.resolveDataScope(subject,"organization.unit:read","organization.unit");
  assertThat(scope.organizationIds()).containsExactlyInAnyOrder(root,tree,descendant).doesNotContain(child);
  assertThat(dataScopes.resolveDataScope(AccessAuthorization.Subject.tenant(owner,tenant.id(),member(tenant.id(),owner)),"customer:read","customer").type()).isEqualTo(DataScopeAuthorization.ScopeType.DENY);
 }

 /** 多角色强制脱敏不能被普通角色原文能力绕过，列表、更新响应及搜索计数均遵守约束。 */
 @Test void fieldSecurityMasksOnServerAndNeverWritesDisplayPlaceholder(){
  UUID target=UUID.randomUUID();String suffix=target.toString().replace("-","");String email="private-"+suffix+"@example.test";
  administration.createUser(actor,target,"field-"+suffix,"Field Target",email,"+8613812345678",encoder.encode("test-password-2026"));
  var role=roles.create(platform(),new RoleManagement.Create(code(),"强制脱敏",null,100));
  rolePermissions.replaceAuthorization(platform(),role.id(),role.version(),java.util.Set.of(),java.util.Set.of(),java.util.Set.of(new com.appnexus.platform.authorization.FieldSecurity.Policy("identity.account","email",com.appnexus.platform.authorization.FieldSecurity.Mode.MASKED),new com.appnexus.platform.authorization.FieldSecurity.Policy("identity.account","phoneNumber",com.appnexus.platform.authorization.FieldSecurity.Mode.HIDDEN)),"联系方式限制");
  var assigned=subjectRoles.accountRoles(actor,actor);var ids=new java.util.HashSet<>(assigned.roleIds());ids.add(role.id());subjectRoles.replaceAccountRoles(actor,actor,assigned.authorityVersion(),ids,"应用强制限制");
  var user=administration.users(actor,1,20,"field-"+suffix).items().getFirst();
  assertThat(user.email()).isEqualTo("p***@example.test");assertThat(user.phoneNumber()).isNull();assertThat(user.restrictedFields()).containsExactlyInAnyOrder("email","phoneNumber");
  administration.updateUser(actor,target,"Updated Field Target",user.email(),null);
  assertThat(jdbc.queryForObject("SELECT email FROM identity_account WHERE id=UUID_TO_BIN(?)",String.class,target.toString())).isEqualTo(email);
  assertThat(administration.users(actor,1,20,email).total()).isZero();
  subjectRoles.replaceAccountRoles(actor,actor,assigned.authorityVersion()+1,assigned.roleIds(),"测试撤销限制");
 }

 private UUID permission(String code){return UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM access_permission WHERE code=?",String.class,code));}
 private UUID member(UUID tenant,UUID account){return UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM tenant_member WHERE tenant_id=UUID_TO_BIN(?) AND account_id=UUID_TO_BIN(?)",String.class,tenant.toString(),account.toString()));}
 /** 直接写测试夹具不经过业务授权，租户与类型均明确固定；不作为生产写入路径。 */
 private UUID organization(UUID tenant,String name,UUID parent){
  String type=jdbc.query("SELECT BIN_TO_UUID(id) FROM tenant_organization_type WHERE tenant_id=UUID_TO_BIN(?) AND code='BUSINESS'",result->result.next()?result.getString(1):null,tenant.toString());
  if(type==null){type=UUID.randomUUID().toString();jdbc.update("INSERT INTO tenant_organization_type(tenant_id,id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'BUSINESS','业务组织')",tenant.toString(),type);}
  UUID id=UUID.randomUUID();jdbc.update("INSERT INTO tenant_organization_unit(tenant_id,id,code,type_id,name,parent_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,UUID_TO_BIN(?),?,UUID_TO_BIN(?))",tenant.toString(),id.toString(),"TEST-"+id,type,name,parent==null?null:parent.toString());return id;
 }
}
