package com.appnexus.platform;
import com.appnexus.platform.authorization.*;
import java.util.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** 使用真实 MySQL 验证授权默认拒绝、目标成员范围和追加审计。 */
@SpringBootTest(properties="app.public-url=http://localhost:18080")
@ActiveProfiles("local")
@AutoConfigureMockMvc
class OrganizationGrantManagementTest extends DatabaseTestSupport {
 @Autowired OrganizationGrantManagement grants;
 @Autowired OrganizationAuthorization authorization;
 @Autowired JdbcTemplate jdbc;
 @Autowired MockMvc mvc;
 @Autowired com.appnexus.platform.persistence.organization.OrganizationAdministratorBootstrap bootstrap;
 /** 旧账号组织写入口已移除，账号授权目录不再携带组织归属。 */
 @Test void accountOrganizationWriteProtocolIsRemoved(){
  assertThat(Arrays.stream(OrganizationGrantManagement.class.getMethods()).map(java.lang.reflect.Method::getName)).doesNotContain("assignOrganization");
  assertThat(Arrays.stream(OrganizationGrantManagement.Member.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName)).containsExactly("memberId","displayName");
 }
 private UUID tenant() {
  UUID id=UUID.randomUUID();
  jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),?)",id.toString(),"授权测试");
  return id;
 }
 private UUID member(UUID tenant) {
  UUID id=UUID.randomUUID(),account=UUID.randomUUID();
  jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,?,?)",account.toString(),"grant-"+account,"授权成员","unused");
  jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",
    id.toString(),tenant.toString(),account.toString());
  return id;
 }
 private UUID type(UUID tenant){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO tenant_organization_type(tenant_id,id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'BUSINESS','业务组织')",tenant.toString(),id.toString());return id;}
 private void authority(UUID tenant,UUID member){bootstrap.initialize(tenant,member);}
 @Test void grantLifecycleChecksAuthorityAndTenantAndAppendsAudit() {
  UUID tenant=tenant(),actor=member(tenant),target=member(tenant),foreign=member(tenant());
  assertThatThrownBy(()->grants.grant(tenant,target,actor,OrganizationCapability.READ,null)).isInstanceOf(SecurityException.class);
  authority(tenant,actor);
  assertThatThrownBy(()->grants.grant(tenant,foreign,actor,OrganizationCapability.READ,null)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->grants.grant(tenant,target,actor,OrganizationCapability.GRANT,null)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->grants.grant(tenant,target,actor,OrganizationCapability.READ,Instant.now().minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
  grants.grant(tenant,target,actor,OrganizationCapability.READ,null);
  assertThat(authorization.allows(tenant,target,OrganizationCapability.READ)).isTrue();
  assertThat(grants.list(tenant,target)).hasSize(1);
  assertThat(grants.revoke(tenant,target,actor,OrganizationCapability.READ)).isTrue();
  assertThat(grants.revoke(tenant,target,actor,OrganizationCapability.READ)).isFalse();
  assertThat(authorization.allows(tenant,target,OrganizationCapability.READ)).isFalse();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM organization_grant_audit WHERE tenant_id=UUID_TO_BIN(?)",Integer.class,tenant.toString())).isEqualTo(2);
 }
 @Test void grantHttpRequiresAuthentication() throws Exception {
  mvc.perform(get("/api/organization/grants/"+UUID.randomUUID())).andExpect(status().isUnauthorized());
 }

 /** 日志写入失败必须回滚授权本身，不能产生无审计的成功授权。 */
 @Test void auditFailureRollsBackGrant() {
  UUID tenant=tenant(),actor=member(tenant),target=member(tenant);
  authority(tenant,actor);
  jdbc.execute("ALTER TABLE organization_grant_audit ADD CONSTRAINT test_reject_audit CHECK (actor_member_id <> X'"+actor.toString().replace("-","")+"')");
  try {
    assertThatThrownBy(()->grants.grant(tenant,target,actor,OrganizationCapability.READ,null)).isInstanceOf(RuntimeException.class);
    assertThat(authorization.allows(tenant,target,OrganizationCapability.READ)).isFalse();
  } finally { jdbc.execute("ALTER TABLE organization_grant_audit DROP CHECK test_reject_audit"); }
 }

 /** 普通已登录租户成员不能通过 HTTP 自助授权，也不能伪造操作者。 */
 @Test void ordinaryMemberCannotGrantThroughHttp() throws Exception {
  UUID tenant=tenant(),account=UUID.randomUUID(),actor=UUID.randomUUID(),target=member(tenant);
  jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,?,?)",account.toString(),"grant-http-"+account,"grant-http","unused");
  jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",actor.toString(),tenant.toString(),account.toString());
  var result=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/bff/tenants/current")
    .with(authenticated(account.toString(),0)).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
    .contentType("application/json").content("{\"tenantId\":\""+tenant+"\"}")).andReturn();
  var cookie=result.getResponse().getCookie("JSESSIONID");
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/organization/grants")
    .cookie(cookie).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
    .contentType("application/json").content("{\"memberId\":\""+target+"\",\"capability\":\"READ\"}"))
    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("GRANT_DENIED"));
  assertThat(authorization.allows(tenant,target,OrganizationCapability.READ)).isFalse();
 }
}
