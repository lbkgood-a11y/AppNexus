package com.appnexus.platform;

import com.appnexus.platform.tenant.PlatformAdministration;
import com.appnexus.platform.persistence.platformscope.MyBatisPlatformAdministration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/** 独立数据库验证平台身份、完整开通链路、幂等、停用及审计事务；平台不自动获得业务成员资格。 */
@SpringBootTest(properties="app.public-url=http://localhost:18080")
@ActiveProfiles("local")
@AutoConfigureMockMvc
class PlatformAdministrationTest extends DatabaseTestSupport {
 @Autowired MyBatisPlatformAdministration service;
 @Autowired JdbcTemplate jdbc;
 @Autowired PasswordEncoder encoder;
 @Autowired MockMvc mvc;
 UUID actor;
 @BeforeEach void initialize(){
  actor=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM identity_account WHERE username='admin-test'",String.class));
  service.initializeAdministrator("admin-test");
 }
 private UUID user(){UUID id=UUID.randomUUID();service.createUser(actor,id,"user-"+id,"Test User",encoder.encode("test-password-2026"));return id;}

 /** 强制下线使旧 Cookie 版本失效，且操作者授权、CSRF、本人保护不能绕过。 */
 @Test void administratorRevocationInvalidatesOldSessionAndIsAudited() throws Exception {
  UUID target=user(),ordinary=user();
  var old=authenticated(target.toString(),0);
  mvc.perform(get("/bff/session").with(old)).andExpect(status().isOk());
  mvc.perform(post("/bff/platform/users/"+target+"/revoke-sessions").with(authenticated(actor.toString(),0))).andExpect(status().isForbidden());
  mvc.perform(post("/bff/platform/users/"+target+"/revoke-sessions").with(authenticated(ordinary.toString(),0)).with(csrf())).andExpect(status().isForbidden());
  mvc.perform(post("/bff/platform/users/"+target+"/revoke-sessions").with(authenticated(actor.toString(),0)).with(csrf())).andExpect(status().isOk());
  mvc.perform(get("/bff/session").with(old)).andExpect(status().isUnauthorized());
  assertThat(jdbc.queryForObject("SELECT version FROM identity_account WHERE id=UUID_TO_BIN(?)",Long.class,target.toString())).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_administration_audit WHERE target_id=UUID_TO_BIN(?) AND action='USER_SESSIONS_REVOKED'",Integer.class,target.toString())).isEqualTo(1);
  assertThatThrownBy(()->service.revokeUserSessions(actor,actor)).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void tenantCreationIsAtomicIdempotentAndDoesNotAddPlatformOperator(){
  UUID administrator=user(),request=UUID.randomUUID();
  var tenant=service.createTenant(actor,request,"Managed Tenant",administrator);
  assertThat(service.createTenant(actor,request,"Managed Tenant",administrator).id()).isEqualTo(tenant.id());
  assertThat(service.members(actor,tenant.id(),1,20,"").items()).hasSize(1);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM organization_capability_grant WHERE tenant_id=UUID_TO_BIN(?)",Integer.class,tenant.id().toString())).isZero();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_member WHERE tenant_id=UUID_TO_BIN(?) AND account_id=UUID_TO_BIN(?)",Integer.class,tenant.id().toString(),actor.toString())).isZero();
  assertThatThrownBy(()->service.createTenant(actor,request,"Different Tenant",administrator)).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void tenantCanBeRenamedAndStateWritesAreIdempotent(){
  var tenant=service.createTenant(actor,UUID.randomUUID()," Original Tenant ",user());
  assertThat(tenant.name()).isEqualTo("Original Tenant");
  var updated=service.updateTenant(actor,tenant.id()," Renamed Tenant ");
  assertThat(updated.name()).isEqualTo("Renamed Tenant");
  service.updateTenant(actor,tenant.id(),"Renamed Tenant");
  service.setTenantEnabled(actor,tenant.id(),false);
  service.setTenantEnabled(actor,tenant.id(),false);
  assertThat(service.tenants(actor,1,20,"Renamed").items().getFirst().enabled()).isFalse();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_administration_audit WHERE target_id=UUID_TO_BIN(?) AND action='TENANT_UPDATED'",Integer.class,tenant.id().toString())).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_administration_audit WHERE target_id=UUID_TO_BIN(?) AND action='TENANT_DISABLED'",Integer.class,tenant.id().toString())).isEqualTo(1);
 }
 @Test void ordinaryAccountCannotManagePlatformAndHttpRequiresCsrf() throws Exception {
  UUID ordinary=user();
  assertThatThrownBy(()->service.users(ordinary,1,20,"")).isInstanceOf(SecurityException.class);
  mvc.perform(get("/bff/platform/users").with(authenticated(ordinary.toString(),0))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PLATFORM_DENIED"));
  mvc.perform(get("/bff/platform/tenants").with(authenticated(actor.toString(),0))).andExpect(status().isOk());
  mvc.perform(post("/bff/platform/users").with(authenticated(actor.toString(),0)).contentType("application/json").content("{}")).andExpect(status().isForbidden());
  mvc.perform(post("/bff/platform/users").with(authenticated(actor.toString(),0)).with(csrf()).contentType("application/json").content("{\"username\":\"invalid\",\"displayName\":\"Test\",\"password\":\"short\"}")).andExpect(status().isBadRequest());
 }
 @Test void accountContactsAreNormalizedUniqueAndResetVerificationWhenChanged() throws Exception {
  UUID id=UUID.randomUUID();
  var created=service.createUser(actor,id,"contact-"+id,"Contact User"," User@Example.COM "," +8613800138000 ",encoder.encode("test-password-2026"));
  assertThat(created.email()).isEqualTo("user@example.com");
  assertThat(created.phoneNumber()).isEqualTo("+8613800138000");
  assertThat(created.emailVerified()).isFalse();
  jdbc.update("UPDATE identity_account SET email_verified=TRUE,phone_verified=TRUE WHERE id=UUID_TO_BIN(?)",id.toString());
  var unchanged=service.updateUser(actor,id,"Contact Renamed","user@example.com","+8613800138000");
  assertThat(unchanged.emailVerified()).isTrue();
  assertThat(unchanged.phoneVerified()).isTrue();
  var changed=service.updateUser(actor,id,"Contact Renamed","next@example.com","+14155552671");
  assertThat(changed.emailVerified()).isFalse();
  assertThat(changed.phoneVerified()).isFalse();
  // 联系方式不参与目录筛选，避免字段受限时通过结果计数推断原值；目录按显示名验证更新结果。
  assertThat(service.users(actor,1,20,"Contact Renamed").items()).extracting(PlatformAdministration.User::id).contains(id);
  assertThatThrownBy(()->service.createUser(actor,UUID.randomUUID(),"duplicate-"+id,"Duplicate","NEXT@example.com",null,encoder.encode("test-password-2026"))).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
  mvc.perform(put("/bff/platform/users/"+id).with(authenticated(actor.toString(),0)).with(csrf()).contentType("application/json").content("{\"displayName\":\"HTTP User\",\"email\":\"http@example.com\",\"phoneNumber\":\"+442079460018\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("http@example.com")).andExpect(jsonPath("$.phoneVerified").value(false));
 }
 @Test void membershipHasNoImplicitGrantsAndReenableDoesNotRestoreRevokedGrants(){
  var tenant=service.createTenant(actor,UUID.randomUUID(),"Members Tenant",user());UUID target=user();
  service.addMember(actor,tenant.id(),target);
  var member=service.members(actor,tenant.id(),1,100,"").items().stream().filter(m->m.accountId().equals(target)).findFirst().orElseThrow();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM organization_capability_grant WHERE member_id=UUID_TO_BIN(?)",Integer.class,member.memberId().toString())).isZero();
  jdbc.update("INSERT INTO organization_capability_grant(tenant_id,member_id,capability,granted_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'READ',UUID_TO_BIN(?))",tenant.id().toString(),member.memberId().toString(),member.memberId().toString());
  service.setMemberEnabled(actor,tenant.id(),member.memberId(),false);
  service.setMemberEnabled(actor,tenant.id(),member.memberId(),true);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM organization_capability_grant WHERE member_id=UUID_TO_BIN(?) AND revoked_at IS NULL",Integer.class,member.memberId().toString())).isZero();
  assertThatThrownBy(()->service.setMemberEnabled(actor,UUID.randomUUID(),member.memberId(),false)).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void removedMemberRejoinsWithSameIdentityAndWithoutOldAuthorization(){
  var tenant=service.createTenant(actor,UUID.randomUUID(),"Rejoin Tenant",user());UUID account=user();
  service.addMember(actor,tenant.id(),account);
  var original=service.members(actor,tenant.id(),1,100,"").items().stream().filter(m->m.accountId().equals(account)).findFirst().orElseThrow();
  String type=UUID.randomUUID().toString(),unit=UUID.randomUUID().toString(),position=UUID.randomUUID().toString(),assignment=UUID.randomUUID().toString();
  jdbc.update("INSERT INTO tenant_organization_type(tenant_id,id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,?)",tenant.id().toString(),type,"TYPE-"+type.substring(0,8),"类型");
  jdbc.update("INSERT INTO tenant_organization_unit(tenant_id,id,code,type_id,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,UUID_TO_BIN(?),?)",tenant.id().toString(),unit,"UNIT-"+unit.substring(0,8),type,"组织");
  jdbc.update("INSERT INTO tenant_organization_position(tenant_id,id,organization_id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,?)",tenant.id().toString(),position,unit,"POS-"+position.substring(0,8),"岗位");
  jdbc.update("INSERT INTO tenant_organization_assignment(tenant_id,id,member_id,position_id,starts_at) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),CURRENT_TIMESTAMP(6))",tenant.id().toString(),assignment,original.memberId().toString(),position);
  jdbc.update("INSERT INTO tenant_organization_leader(tenant_id,organization_id,assignment_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",tenant.id().toString(),unit,assignment);
  jdbc.update("INSERT INTO access_member_permission(tenant_id,member_id,permission_code,granted_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'organization.unit:read',UUID_TO_BIN(?))",tenant.id().toString(),original.memberId().toString(),original.memberId().toString());
  service.removeMember(actor,tenant.id(),original.memberId());
  var removed=service.members(actor,tenant.id(),1,100,"").items().stream().filter(m->m.accountId().equals(account)).findFirst().orElseThrow();
  assertThat(removed.removed()).isTrue();assertThat(removed.enabled()).isFalse();
  assertThat(jdbc.queryForObject("SELECT enabled FROM tenant_organization_assignment WHERE id=UUID_TO_BIN(?)",Boolean.class,assignment)).isFalse();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_organization_leader WHERE assignment_id=UUID_TO_BIN(?)",Integer.class,assignment)).isZero();
  assertThatThrownBy(()->service.setMemberEnabled(actor,tenant.id(),original.memberId(),true)).isInstanceOf(IllegalArgumentException.class);
  service.addMember(actor,tenant.id(),account);
  var rejoined=service.members(actor,tenant.id(),1,100,"").items().stream().filter(m->m.accountId().equals(account)).findFirst().orElseThrow();
  assertThat(rejoined.memberId()).isEqualTo(original.memberId());assertThat(rejoined.enabled()).isTrue();assertThat(rejoined.removed()).isFalse();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM access_member_permission WHERE tenant_id=UUID_TO_BIN(?) AND member_id=UUID_TO_BIN(?)",Integer.class,tenant.id().toString(),original.memberId().toString())).isZero();
 }
 @Test void administratorAccountsAndMembershipsAreProtected(){
  UUID admin=user();var tenant=service.createTenant(actor,UUID.randomUUID(),"Protected Tenant",admin);
  var member=service.members(actor,tenant.id(),1,20,"").items().getFirst();
  assertThatThrownBy(()->service.setUserEnabled(actor,actor,false)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->service.setUserEnabled(actor,admin,false)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->service.setMemberEnabled(actor,tenant.id(),member.memberId(),false)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->service.removeMember(actor,tenant.id(),member.memberId())).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void bootstrapDoesNotRestoreRevokedPlatformAuthority(){
  jdbc.update("UPDATE platform_administrator SET enabled=FALSE WHERE account_id=UUID_TO_BIN(?)",actor.toString());
  try{service.initializeAdministrator("admin-test");assertThat(service.isAdministrator(actor)).isFalse();}
  finally{jdbc.update("UPDATE platform_administrator SET enabled=TRUE WHERE account_id=UUID_TO_BIN(?)",actor.toString());}
 }
 @Test void auditFailureRollsBackEntireTenantCreation(){
  UUID admin=user(),request=UUID.randomUUID(),testActor=user();
  jdbc.update("INSERT INTO platform_administrator(account_id) VALUES(UUID_TO_BIN(?))",testActor.toString());
  jdbc.execute("ALTER TABLE platform_administration_audit ADD CONSTRAINT reject_tenant_creation CHECK(actor_account_id<>X'"+testActor.toString().replace("-","")+"')");
  try{assertThatThrownBy(()->service.createTenant(testActor,request,"Rollback Tenant",admin)).isInstanceOf(RuntimeException.class);}
  finally{jdbc.execute("ALTER TABLE platform_administration_audit DROP CHECK reject_tenant_creation");}
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_tenant_creation WHERE request_id=UUID_TO_BIN(?)",Integer.class,request.toString())).isZero();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_member WHERE account_id=UUID_TO_BIN(?)",Integer.class,admin.toString())).isZero();
  service.createTenant(testActor,request,"Rollback Tenant",admin);
 }
}
