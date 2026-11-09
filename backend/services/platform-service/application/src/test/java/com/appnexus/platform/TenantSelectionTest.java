package com.appnexus.platform;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
/** 真实数据库下校验租户目录、成员资格及客户端伪造选择被拒绝。 */
@SpringBootTest(properties="app.public-url=http://localhost:18080")
@ActiveProfiles("local")
@AutoConfigureMockMvc
class TenantSelectionTest extends DatabaseTestSupport {
 @Autowired MockMvc mvc;
 @Autowired JdbcTemplate jdbc;
 @Autowired com.appnexus.platform.persistence.organization.OrganizationAdministratorBootstrap bootstrap;
 /** 同一账号切换仍受有效成员资格限制，旧页面版本不可在新租户中执行写入。 */
 @Test void switchingRequiresExpectedVersionAndRejectsStaleBusinessRequests() throws Exception {
  String account=UUID.randomUUID().toString(),first=UUID.randomUUID().toString(),second=UUID.randomUUID().toString();
  jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'switch','unused')",account,"switch-"+account);
  for(String tenant:List.of(first,second)){
   jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'Switch Tenant')",tenant);
   jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",UUID.randomUUID().toString(),tenant,account);
  }
  var selected=mvc.perform(post("/bff/tenants/current").with(authenticated(account,0)).with(csrf()).contentType("application/json").content("{\"tenantId\":\""+first+"\"}")).andExpect(status().isOk()).andReturn();
  var cookie=selected.getResponse().getCookie("JSESSIONID");
  mvc.perform(post("/bff/tenants/current").cookie(cookie).with(csrf()).contentType("application/json").content("{\"tenantId\":\""+second+"\"}")).andExpect(status().isConflict());
  var switched=mvc.perform(post("/bff/tenants/current").cookie(cookie).with(csrf()).contentType("application/json").content("{\"tenantId\":\""+second+"\",\"expectedVersion\":1}")).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2)).andReturn();
  var next=switched.getResponse().getCookie("JSESSIONID");
  mvc.perform(get("/api/organization/capabilities").cookie(next).header("X-Tenant-Context-Version","1")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TENANT_CONTEXT_CHANGED"));
  mvc.perform(get("/api/organization/capabilities").cookie(next).header("X-Tenant-Context-Version","2")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
  mvc.perform(post("/api/organization/units").cookie(next).with(csrf()).contentType("application/json").content("{\"name\":\"stale\"}")).andExpect(status().isConflict());
 }
 /** 实际 Cookie 会话覆盖初始化后的 CRUD、CSRF、跨租户目标隔离和撤权立即生效。 */
 @Test void initializedAdministratorCanManageOnlyCurrentTenant() throws Exception {
  String account=UUID.randomUUID().toString(),tenant=UUID.randomUUID().toString(),member=UUID.randomUUID().toString();
  jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,?,?)",account,"admin-org-"+account,"organization admin","unused");
  jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'organization-test')",tenant);
  jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",member,tenant,account);
  bootstrap.initialize(UUID.fromString(tenant),UUID.fromString(member));
  var cookie=session(account,tenant,member);
  mvc.perform(get("/api/organization/capabilities").cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(5));
  mvc.perform(get("/api/organization/grants/members").cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$[0].memberId").value(member));
  mvc.perform(post("/api/organization/units").cookie(cookie).contentType("application/json").content("{\"name\":\"test\"}")).andExpect(status().isForbidden());
  mvc.perform(post("/api/organization/units").cookie(cookie).with(csrf()).contentType("application/json").content("{\"name\":\"test\"}")).andExpect(status().isCreated());
  String id=jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM tenant_organization_unit WHERE tenant_id=UUID_TO_BIN(?)",String.class,tenant);
  mvc.perform(put("/api/organization/units/"+id).cookie(cookie).with(csrf()).contentType("application/json").content("{\"name\":\"renamed\"}")).andExpect(status().isNoContent());
  mvc.perform(get("/api/organization/units").cookie(cookie)).andExpect(jsonPath("$[0].name").value("renamed"));
  mvc.perform(put("/api/organization/units/"+UUID.randomUUID()).cookie(cookie).with(csrf()).contentType("application/json").content("{\"name\":\"foreign\"}")).andExpect(status().isNotFound());
  mvc.perform(delete("/api/organization/units/"+id).cookie(cookie).with(csrf())).andExpect(status().isNoContent());
  jdbc.update("DELETE ap FROM access_member_authorization_permission ap JOIN access_permission p ON p.id=ap.permission_id WHERE ap.tenant_id=UUID_TO_BIN(?) AND ap.member_id=UUID_TO_BIN(?) AND p.code='organization.unit:create'",tenant,member);
  mvc.perform(post("/api/organization/units").cookie(cookie).with(csrf()).contentType("application/json").content("{\"name\":\"denied\"}")).andExpect(status().isForbidden());
 }
 @Test void membershipAndTenantMustBothBeActive() throws Exception {
  String account=UUID.randomUUID().toString(),tenant=UUID.randomUUID().toString(),member=UUID.randomUUID().toString();
  jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,?,?)",account,"tenant-"+account,"member","unused");
  jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),?)",tenant,"测试租户");
  jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",member,tenant,account);
  mvc.perform(get("/bff/tenants").with(authenticated(account,0))).andExpect(status().isOk())
   .andExpect(jsonPath("$[0].tenantId").value(tenant));
  mvc.perform(get("/bff/tenants/current").with(authenticated(account,0))).andExpect(status().isConflict());
  mvc.perform(get("/bff/tenants/current").param("optional","true").with(authenticated(account,0))).andExpect(status().isNoContent()).andExpect(header().string("Cache-Control","no-store"));
  mvc.perform(post("/bff/tenants/current").with(authenticated(account,0)).contentType("application/json").content("{\"tenantId\":\""+tenant+"\"}"))
   .andExpect(status().isForbidden());
  mvc.perform(post("/bff/tenants/current").with(authenticated(account,0)).with(csrf()).contentType("application/json").content("{\"tenantId\":\""+UUID.randomUUID()+"\"}"))
   .andExpect(status().isForbidden());
  mvc.perform(post("/bff/tenants/current").with(authenticated(account,0)).with(csrf()).contentType("application/json").content("{\"tenantId\":\""+tenant+"\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.memberId").value(member)).andExpect(jsonPath("$.version").value(1));
  jdbc.update("UPDATE tenant_member SET enabled=FALSE WHERE id=UUID_TO_BIN(?)",member);
  mvc.perform(get("/bff/tenants").with(authenticated(account,0))).andExpect(jsonPath("$.length()").value(0));
  jdbc.update("UPDATE tenant_member SET enabled=TRUE WHERE id=UUID_TO_BIN(?)",member);
  jdbc.update("UPDATE platform_tenant SET enabled=FALSE WHERE id=UUID_TO_BIN(?)",tenant);
  mvc.perform(post("/bff/tenants/current").with(authenticated(account,0)).with(csrf()).contentType("application/json").content("{\"tenantId\":\""+tenant+"\"}"))
   .andExpect(status().isForbidden());
  mvc.perform(get("/bff/tenants")).andExpect(status().isUnauthorized());
 }

 /** 请求上下文只认会话绑定，忽略伪造头/参数；结束后不残留到后续请求。 */
 @Test void businessRequestsRequireVerifiedSessionContext() throws Exception {
  String account=UUID.randomUUID().toString(),tenant=UUID.randomUUID().toString(),member=UUID.randomUUID().toString();
  jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,?,?)",account,"ctx-"+account,"context","unused");
  jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),?)",tenant,"执行边界");
  jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",member,tenant,account);
  mvc.perform(get("/api/workspace/context")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/workspace/context").with(authenticated(account,0)).header("X-Tenant-Id",tenant).param("tenantId",tenant))
   .andExpect(status().isConflict());
  var setup=mvc.perform(post("/bff/tenants/current").with(authenticated(account,0)).with(csrf())
   .contentType("application/json").content("{\"tenantId\":\""+tenant+"\"}")).andReturn();
  var cookie=setup.getResponse().getCookie("JSESSIONID");
  org.assertj.core.api.Assertions.assertThat(cookie).isNotNull();
  var result=mvc.perform(get("/api/workspace/context").cookie(cookie).header("X-Tenant-Id",UUID.randomUUID().toString()))
   .andExpect(status().isOk()).andExpect(jsonPath("$.tenantId").value(tenant))
   .andExpect(jsonPath("$.memberId").value(member)).andExpect(header().string("X-Tenant-Context-Version","1")).andReturn();
  org.assertj.core.api.Assertions.assertThat(result.getRequest().getAttribute("com.appnexus.platform.tenant.VerifiedTenantContext")).isNull();
  mvc.perform(get("/api/workspace/context").with(authenticated(account,0))).andExpect(status().isConflict());
  jdbc.update("UPDATE tenant_member SET enabled=FALSE WHERE id=UUID_TO_BIN(?)",member);
  mvc.perform(get("/api/workspace/context").cookie(cookie)).andExpect(status().isConflict());
 }
 @Test void organizationCrudGateDeniesTenantMemberWithoutExplicitCapabilityGrant() throws Exception {
  String account=UUID.randomUUID().toString(),tenant=UUID.randomUUID().toString(),member=UUID.randomUUID().toString();
  jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,?,?)",account,"org-"+account,"org","unused");
  jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),?)",tenant,"组织授权边界");
  jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",member,tenant,account);
  var session=session(account,tenant,member);
  mvc.perform(get("/api/organization/units").cookie(session)).andExpect(status().isForbidden())
    .andExpect(jsonPath("$.code").value("OPERATION_NOT_AUTHORIZED"));
  mvc.perform(post("/api/organization/units").cookie(session).with(csrf()).contentType("application/json").content("{\"name\":\"不应创建\"}"))
    .andExpect(status().isForbidden());
  org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_organization_unit WHERE tenant_id=UUID_TO_BIN(?)",Integer.class,tenant)).isZero();
 }
 private jakarta.servlet.http.Cookie session(String account,String tenant,String member) {
  var value=sessionRepository.createSession();
  var security=SecurityContextHolder.createEmptyContext();
  security.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(User.withUsername(account).password("").authorities("ACCOUNT").build(),null,java.util.List.of(new SimpleGrantedAuthority("ACCOUNT"))));
  value.setAttribute("SPRING_SECURITY_CONTEXT",security);value.setAttribute("appnexus.credentialVersion",0L);
  value.setAttribute("appnexus.tenant",tenant);value.setAttribute("appnexus.member",member);value.setAttribute("appnexus.tenantVersion",1L);
  sessionRepository.save(value);
  return new jakarta.servlet.http.Cookie("JSESSIONID",java.util.Base64.getEncoder().encodeToString(value.getId().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
 }

}
