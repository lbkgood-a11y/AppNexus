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

 @Test void tenantCreationIsAtomicIdempotentAndDoesNotAddPlatformOperator(){
  UUID administrator=user(),request=UUID.randomUUID();
  var tenant=service.createTenant(actor,request,"Managed Tenant",administrator);
  assertThat(service.createTenant(actor,request,"Managed Tenant",administrator).id()).isEqualTo(tenant.id());
  assertThat(service.members(actor,tenant.id(),1,20,"").items()).hasSize(1);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM organization_capability_grant WHERE tenant_id=UUID_TO_BIN(?)",Integer.class,tenant.id().toString())).isEqualTo(5);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_member WHERE tenant_id=UUID_TO_BIN(?) AND account_id=UUID_TO_BIN(?)",Integer.class,tenant.id().toString(),actor.toString())).isZero();
  assertThatThrownBy(()->service.createTenant(actor,request,"Different Tenant",administrator)).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void ordinaryAccountCannotManagePlatformAndHttpRequiresCsrf() throws Exception {
  UUID ordinary=user();
  assertThatThrownBy(()->service.users(ordinary,1,20,"")).isInstanceOf(SecurityException.class);
  mvc.perform(get("/bff/platform/users").with(authenticated(ordinary.toString(),0))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PLATFORM_DENIED"));
  mvc.perform(get("/bff/platform/tenants").with(authenticated(actor.toString(),0))).andExpect(status().isOk());
  mvc.perform(post("/bff/platform/users").with(authenticated(actor.toString(),0)).contentType("application/json").content("{}")).andExpect(status().isForbidden());
  mvc.perform(post("/bff/platform/users").with(authenticated(actor.toString(),0)).with(csrf()).contentType("application/json").content("{\"username\":\"invalid\",\"displayName\":\"Test\",\"password\":\"short\"}")).andExpect(status().isBadRequest());
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
 @Test void administratorAccountsAndMembershipsAreProtected(){
  UUID admin=user();var tenant=service.createTenant(actor,UUID.randomUUID(),"Protected Tenant",admin);
  var member=service.members(actor,tenant.id(),1,20,"").items().getFirst();
  assertThatThrownBy(()->service.setUserEnabled(actor,actor,false)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->service.setUserEnabled(actor,admin,false)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->service.setMemberEnabled(actor,tenant.id(),member.memberId(),false)).isInstanceOf(IllegalArgumentException.class);
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
