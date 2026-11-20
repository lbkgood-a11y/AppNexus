package com.appnexus.platform;

import com.appnexus.platform.businessobject.BusinessObjectManagement;

import com.appnexus.platform.businessobject.CodingManagement;

import com.appnexus.contracts.businessobject.*;
import static com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.BusinessCoding.*;
import com.appnexus.platform.persistence.platformscope.MyBatisPlatformAdministration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 完整宿主测试：真实成员与功能权限、对象授权、页面停用、Cookie/CSRF、幂等及跨租户拒绝。 */
@SpringBootTest
@ActiveProfiles("local")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class CodingCapabilityTest extends DatabaseTestSupport {
  @Autowired CodingManagement coding;
  @Autowired BusinessObjectManagement objects;
  @Autowired MyBatisPlatformAdministration administration;
  @Autowired JdbcTemplate jdbc;
  @Autowired com.appnexus.platform.authorization.RoleManagement roles;
  @Autowired com.appnexus.platform.authorization.RolePermissionManagement permissions;
  @Autowired com.appnexus.platform.authorization.SubjectRoleManagement subjects;
  @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
  @Autowired org.springframework.test.web.servlet.MockMvc mvc;
  /** 配置、发号与前端使用相同真实 HTTP 协议，失败不增加发号记录。 */
  @Test void tenantOwnerCanConfigureAndIssueButStrangerAndDisabledPageCannot() throws Exception {
    UUID admin=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM identity_account WHERE username='admin-test'",String.class));
    administration.initializeAdministrator("admin-test");
    UUID owner=account(admin),stranger=account(admin),otherOwner=account(admin);
    UUID tenant=administration.createTenant(admin,UUID.randomUUID(),"编码能力验收",owner).id();
    UUID other=administration.createTenant(admin,UUID.randomUUID(),"编码隔离验收",otherOwner).id();
    administration.addMember(admin,tenant,stranger);
    var manager=new Context(tenant.toString(),owner.toString(),UUID.randomUUID().toString());
    var platform=new Context(null,admin.toString(),UUID.randomUUID().toString());
    var rule=new Rule(List.of(new Segment("TEXT","PU",0),new Segment("DATE","yyyyMMdd",0),new Segment("SEQUENCE","",6)),"DAY","Asia/Shanghai");
    coding.saveRule(manager,"purchaseRule",new CodingManagement.RuleInput(-1,"采购编码规则",rule));
    var input=new CodingManagement.Input(-1,"code","采购编码",true,"purchaseRule",1);
    // 对象授权缺失时不能通过直接调用配置接口绕过目录筛选。
    objects.grant(platform,"BusinessPartner",tenant.toString(),false);
    assertThatThrownBy(()->coding.save(manager,"BusinessPartner","purchase",input)).isInstanceOf(Rejection.class);
    objects.grant(platform,"BusinessPartner",tenant.toString(),true);
    coding.save(manager,"BusinessPartner","purchase",input);
    assertThat(coding.options(manager,"BusinessPartner","code")).hasSize(1);
    var outsider=new Context(tenant.toString(),stranger.toString(),UUID.randomUUID().toString());
    assertThatThrownBy(()->coding.issue(outsider,"BusinessPartner","purchase")).hasMessage("CODING_ACCESS_DENIED");
    assertThatThrownBy(()->coding.configurations(outsider,"BusinessPartner")).hasMessage("CODING_ACCESS_DENIED");
    assertThatThrownBy(()->coding.issue(new Context(other.toString(),otherOwner.toString(),UUID.randomUUID().toString()),"BusinessPartner","purchase")).hasMessage("CODING_UNAVAILABLE");
    var selected=mvc.perform(post("/bff/tenants/current").with(authenticated(owner.toString(),0)).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content("{\"tenantId\":\""+tenant+"\"}")).andExpect(status().isOk()).andReturn();
    var ruleCookie=selected.getResponse().getCookie("JSESSIONID");
    mvc.perform(get("/api/coding-rules").cookie(ruleCookie)).andExpect(status().isOk()).andExpect(jsonPath("$[0].ruleKey").value("purchaseRule"));
    mvc.perform(put("/api/coding-rules/httpRule").cookie(ruleCookie).contentType("application/json").content("{}" )).andExpect(status().isForbidden());
    var ruleBody=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(new CodingManagement.RuleInput(-1,"HTTP规则",rule));
    mvc.perform(put("/api/coding-rules/httpRule").cookie(ruleCookie).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content(ruleBody)).andExpect(status().isOk());
    mvc.perform(put("/api/coding-rules/httpRule").cookie(ruleCookie).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content(ruleBody)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CODING_VERSION_CONFLICT"));
    assertThatThrownBy(()->coding.rules(outsider)).hasMessage("CODING_ACCESS_DENIED");
    assertThatThrownBy(()->coding.saveRule(outsider,"denied",new CodingManagement.RuleInput(-1,"拒绝",rule))).hasMessage("CODING_ACCESS_DENIED");
    var cookie=selected.getResponse().getCookie("JSESSIONID");String request=UUID.randomUUID().toString(),path="/api/business-objects/BusinessPartner/encoders/purchase/issue";
    mvc.perform(post(path).cookie(cookie).header("Idempotency-Key",request)).andExpect(status().isForbidden());
    String result=mvc.perform(post(path).cookie(cookie).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).header("Idempotency-Key",request)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern("PU[0-9]{8}000001"))).andReturn().getResponse().getContentAsString();
    mvc.perform(post(path).cookie(cookie).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).header("Idempotency-Key",request)).andExpect(status().isOk()).andExpect(content().json(result));
    // 平台控制验证后恢复，避免共享测试上下文影响其他功能。
    long revision=((Number)objects.pageRelationships(platform,"BusinessPartner").get("revision")).longValue();
    try{
      objects.controlPage(platform,"BusinessPartner",revision,"BusinessPartner.create",false,"编码能力反向验收");
      assertThatThrownBy(()->coding.issue(manager,"BusinessPartner","purchase")).hasMessage("BUSINESS_OBJECT_PAGE_RELATION_DISABLED");
    }finally{
      long current=((Number)objects.pageRelationships(platform,"BusinessPartner").get("revision")).longValue();objects.controlPage(platform,"BusinessPartner",current,"BusinessPartner.create",true,"恢复验收控制");
    }
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_object_code_issue WHERE tenant_id=UUID_TO_BIN(?)",Integer.class,tenant.toString())).isEqualTo(1);
    assertThat(coding.configurations(manager,"BusinessPartner").getFirst().get("createdBy").toString()).isEqualToIgnoringCase(owner.toString());
    // 普通成员只授予目标新增操作即可协助发号；不授予编码配置管理权，撤权后同键重放也必须拒绝。
    var roleContext=new com.appnexus.platform.authorization.RoleManagement.Context(admin,tenant,null);
    var role=roles.create(roleContext,new com.appnexus.platform.authorization.RoleManagement.Create("coding_"+UUID.randomUUID().toString().replace("-",""),"编码录入员",null,100));
    UUID permission=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM access_permission WHERE code='masterdata.partner:create'",String.class));
    permissions.replaceAuthorization(roleContext,role.id(),role.version(),Set.of(permission),Set.of(new com.appnexus.platform.authorization.RolePermissionManagement.DataPolicy(permission,com.appnexus.platform.authorization.DataScopeAuthorization.ScopeType.ALL,"masterdata.partner",Set.of())),"编码协助只授予目标操作");
    UUID member=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM tenant_member WHERE tenant_id=UUID_TO_BIN(?) AND account_id=UUID_TO_BIN(?)",String.class,tenant.toString(),stranger.toString()));
    var initial=subjects.memberRoles(roleContext,member);subjects.replaceMemberRoles(roleContext,member,initial.authorityVersion(),Set.of(role.id()),"录入员角色");
    // 只授予独立规则管理权的成员可维护规则，但不能维护对象绑定或发业务号码。
    var ruleRole=roles.create(roleContext,new com.appnexus.platform.authorization.RoleManagement.Create("rule_"+UUID.randomUUID().toString().replace("-",""),"规则维护员",null,100));
    UUID rulePermission=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM access_permission WHERE code='coding.rule:manage'",String.class));
    permissions.replaceAuthorization(roleContext,ruleRole.id(),ruleRole.version(),Set.of(rulePermission),Set.of(),"独立规则维护");
    var beforeRuleRole=subjects.memberRoles(roleContext,member);subjects.replaceMemberRoles(roleContext,member,beforeRuleRole.authorityVersion(),Set.of(ruleRole.id()),"规则管理独立准入");
    coding.saveRule(outsider,"memberRule",new CodingManagement.RuleInput(-1,"成员规则",rule));
    assertThat(coding.rules(outsider)).isNotEmpty();
    assertThatThrownBy(()->coding.configurations(outsider,"BusinessPartner")).hasMessage("CODING_ACCESS_DENIED");
    assertThatThrownBy(()->coding.issue(outsider,"BusinessPartner","purchase")).hasMessage("CODING_ACCESS_DENIED");
    var beforeBusinessRole=subjects.memberRoles(roleContext,member);subjects.replaceMemberRoles(roleContext,member,beforeBusinessRole.authorityVersion(),Set.of(role.id()),"恢复业务录入角色");
    assertThat(coding.options(outsider,"BusinessPartner","code")).hasSize(1);
    assertThat(coding.issue(outsider,"BusinessPartner","purchase")).endsWith("000002");
    assertThatThrownBy(()->coding.configurations(outsider,"BusinessPartner")).hasMessage("CODING_ACCESS_DENIED");
    var current=subjects.memberRoles(roleContext,member);subjects.replaceMemberRoles(roleContext,member,current.authorityVersion(),Set.of(),"撤销录入角色");
    assertThatThrownBy(()->coding.issue(outsider,"BusinessPartner","purchase")).hasMessage("CODING_ACCESS_DENIED");
  }
  private UUID account(UUID admin){UUID id=UUID.randomUUID();administration.createUser(admin,id,"coding-"+id,"编码能力验收",passwords.encode("isolated-coding-test"));return id;}
}
