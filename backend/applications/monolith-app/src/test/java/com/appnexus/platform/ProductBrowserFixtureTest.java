package com.appnexus.platform;

import java.nio.file.*;
import java.math.BigDecimal;
import com.appnexus.bulkwarehousing.api.*;
import com.appnexus.masterdata.api.masterdata.MasterData;
import com.appnexus.masterdata.api.masterdata.MasterDataManagement;
import com.appnexus.masterdata.api.product.ProductCatalogData;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 人工浏览器验收专用隔离宿主；仅在系统参数product.browser.qa=true时启动，默认跳过，使用一次性MySQL/Valkey与测试账号。
 * 不增加生产登录入口，不重置管理员密码；会话只写忽略目录，生命周期由停止标志控制。
 */
@Tag("browser-fixture")
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="product.browser.qa",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT,properties={"server.port=${product.qa.backend.port:18089}","app.public-url=http://localhost:${product.qa.frontend.port:18848}","app.frontend-url=http://localhost:${product.qa.frontend.port:18848}"})
@ActiveProfiles("local")
class ProductBrowserFixtureTest extends DatabaseTestSupport {
 @Autowired JdbcTemplate jdbc;
 @Autowired com.appnexus.platform.businessobject.CodingManagement browserCoding;
 @Autowired com.appnexus.platform.businessobject.BusinessObjectManagement browserObjects;
 @Autowired com.appnexus.platform.persistence.platformscope.MyBatisPlatformAdministration browserAdministration;
 @Autowired com.appnexus.platform.persistence.organization.OrganizationAdministratorBootstrap bootstrap;
 @Autowired com.appnexus.masterdata.application.product.ProductCatalogService catalog;
 @Autowired com.appnexus.masterdata.application.product.ProductService products;
 @Autowired com.appnexus.masterdata.application.product.ProductAttributeService attributes;
 @Autowired com.appnexus.masterdata.application.product.ProductTemplateService templates;

 /** 客商浏览器验收使用一次性数据库和两个真实权限主体，文件仅保存短期测试会话。 */
 @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="test",matches=".*#partnerPageExportBrowserHost.*")
 @Test void partnerPageExportBrowserHost() throws Exception {
  var actor=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM identity_account WHERE username='admin-test'",String.class));
  browserAdministration.initializeAdministrator("admin-test");
  var owner=UUID.randomUUID();
  var reader=UUID.randomUUID();
  var tenant=UUID.randomUUID();
  jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'客商导出验收')",tenant.toString());
  var members=new HashMap<UUID,UUID>();
  for(var account:List.of(owner,reader)){
   browserAdministration.createUser(actor,account,"qa-"+account,"客商导出验收",
       partnerBrowserPasswordEncoder.encode(UUID.randomUUID().toString()));
   var member=UUID.randomUUID();
   members.put(account,member);
   jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",
       member.toString(),tenant.toString(),account.toString());
  }
  bootstrap.initialize(tenant,members.get(owner));
  new org.springframework.transaction.support.TransactionTemplate(partnerBrowserTransactions)
      .executeWithoutResult(status->partnerBrowserDictionary.seed(tenant,actor));
  UUID type=UUID.randomUUID(),organization=UUID.randomUUID();
  jdbc.update("INSERT INTO tenant_organization_type(tenant_id,id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'QA','验收类型')",tenant.toString(),type.toString());
  jdbc.update("INSERT INTO tenant_organization_unit(tenant_id,id,code,type_id,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'QA',UUID_TO_BIN(?),'验收组织')",tenant.toString(),organization.toString(),type.toString());
  var context=new VerifiedTenantContext(owner,tenant,members.get(owner),1);
  for(int i=1;i<=23;i++){
   String code="QA-EXPORT-%02d".formatted(i);
   var contacts=List.of(new MasterData.DetailInput(null,Map.of("contactType","BUSINESS","contactName","验收联系人",
       "phone","13900139000","address","验收地址","isDefault","true")));
   var banks=List.of(new MasterData.DetailInput(null,Map.of("taxAccount","false","basicAccount","true",
       "isDefault","true","bankName","验收银行","accountNumber","000012345678","currency","CNY")));
   partnerBrowserService.create(context,MasterData.Kind.PARTNER,new MasterData.Input(code,code,
       Map.of("partnerType","CUSTOMER","phone","13800138000"),0,contacts,banks,organization.toString()));
  }
  var roleContext=new com.appnexus.platform.authorization.RoleManagement.Context(actor,tenant,null);
  var readRole=partnerBrowserRoles.create(roleContext,new com.appnexus.platform.authorization.RoleManagement.Create("qa_read","只读验收",null,100));
  UUID read=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM access_permission WHERE code='masterdata.partner:read'",String.class));
  var policy=new com.appnexus.platform.authorization.RolePermissionManagement.DataPolicy(read,
      com.appnexus.platform.authorization.DataScopeAuthorization.ScopeType.ALL,"masterdata.partner",Set.of());
  partnerBrowserPermissions.replaceAuthorization(roleContext,readRole.id(),readRole.version(),Set.of(read),Set.of(policy),Set.of(
      new com.appnexus.platform.authorization.FieldSecurity.Policy("masterdata.partner","phone",
          com.appnexus.platform.authorization.FieldSecurity.Mode.MASKED),
      new com.appnexus.platform.authorization.FieldSecurity.Policy("masterdata.partner","legalId",
          com.appnexus.platform.authorization.FieldSecurity.Mode.HIDDEN),
      new com.appnexus.platform.authorization.FieldSecurity.Policy("masterdata.partner.contact","phone",
          com.appnexus.platform.authorization.FieldSecurity.Mode.MASKED),
      new com.appnexus.platform.authorization.FieldSecurity.Policy("masterdata.partner.bank","accountNumber",
          com.appnexus.platform.authorization.FieldSecurity.Mode.HIDDEN)),"浏览器只读及字段验收");
  var assignment=partnerBrowserSubjects.memberRoles(roleContext,members.get(reader));
  partnerBrowserSubjects.replaceMemberRoles(roleContext,members.get(reader),assignment.authorityVersion(),Set.of(readRole.id()),"浏览器只读验收");
  var ownerSession=historySession(tenant,owner,members.get(owner));
  var readerSession=historySession(tenant,reader,members.get(reader));
  var platformSession=historySession(null,actor,null);
  var directory=Path.of(System.getProperty("product.qa.dir"));
  Files.createDirectories(directory);
  var sessionFile=directory.resolve("partner-session.json");
  try{
   var encoder=Base64.getEncoder();
   var json=new tools.jackson.databind.ObjectMapper();
   Files.writeString(sessionFile,json.writeValueAsString(Map.of(
       "owner",encoder.encodeToString(ownerSession.getId().getBytes(java.nio.charset.StandardCharsets.UTF_8)),
       "reader",encoder.encodeToString(readerSession.getId().getBytes(java.nio.charset.StandardCharsets.UTF_8)),
       "platform",encoder.encodeToString(platformSession.getId().getBytes(java.nio.charset.StandardCharsets.UTF_8)))));
   long deadline=System.nanoTime()+java.time.Duration.ofMinutes(8).toNanos();
   while(System.nanoTime()<deadline&&!Files.exists(directory.resolve("stop")))Thread.sleep(500);
  }finally{
   Files.deleteIfExists(sessionFile);
   sessionRepository.deleteById(ownerSession.getId());
   sessionRepository.deleteById(readerSession.getId());
   sessionRepository.deleteById(platformSession.getId());
  }
 }
 @Autowired MasterDataManagement partnerBrowserService;
 @Autowired com.appnexus.platform.authorization.RoleManagement partnerBrowserRoles;
 @Autowired com.appnexus.platform.authorization.RolePermissionManagement partnerBrowserPermissions;
 @Autowired com.appnexus.platform.authorization.SubjectRoleManagement partnerBrowserSubjects;
 @Autowired org.springframework.security.crypto.password.PasswordEncoder partnerBrowserPasswordEncoder;
 @Autowired com.appnexus.platform.persistence.configuration.MyBatisDictionaryProvisioning partnerBrowserDictionary;
 @Autowired org.springframework.transaction.PlatformTransactionManager partnerBrowserTransactions;
 /** 一次性租户中的真实 HTTP 验收，随后保留同一服务器供两个页面的浏览器验证。 */
 @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="test",matches=".*#measurementBrowserHost.*")
 @Test void measurementBrowserHost() throws Exception {
  UUID tenant=UUID.randomUUID(),product=UUID.randomUUID(),base=UUID.randomUUID(),box=UUID.randomUUID();
  UUID admin=UUID.randomUUID(),adminMember=UUID.randomUUID(),denied=UUID.randomUUID(),deniedMember=UUID.randomUUID();
  jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'计量验收租户')",tenant.toString());
  for(var identity:Map.of(admin,adminMember,denied,deniedMember).entrySet()){
   jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'计量验收账号','unused')",identity.getKey().toString(),"measurement-"+identity.getKey());
   jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",identity.getValue().toString(),tenant.toString(),identity.getKey().toString());
  }
  bootstrap.initialize(tenant,adminMember);
  jdbc.update("INSERT INTO md_product_unit_group(tenant_id,code,name) VALUES(UUID_TO_BIN(?),'COUNT','计数'),(UUID_TO_BIN(?),'PACKAGE','包装')",tenant.toString(),tenant.toString());
  jdbc.update("INSERT INTO md_product_unit(id,tenant_id,code,name,dimension_code) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'BOTTLE','瓶','COUNT'),(UUID_TO_BIN(?),UUID_TO_BIN(?),'BOX','箱','PACKAGE')",base.toString(),tenant.toString(),box.toString(),tenant.toString());
  jdbc.update("INSERT INTO md_product_v1(id,tenant_id,code,name,product_form,base_unit_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'QA-MEASURE','包装计量验收商品','PHYSICAL',UUID_TO_BIN(?))",product.toString(),tenant.toString(),base.toString());
  var adminSession=historySession(tenant,admin,adminMember);
  var deniedSession=historySession(tenant,denied,deniedMember);
  var directory=Path.of(System.getProperty("product.qa.dir"));
  Files.createDirectories(directory);
  var sessionFile=directory.resolve("measurement-session.json");
  var encoder=Base64.getEncoder();
  String cookie=encoder.encodeToString(adminSession.getId().getBytes(java.nio.charset.StandardCharsets.UTF_8));
  String deniedCookie=encoder.encodeToString(deniedSession.getId().getBytes(java.nio.charset.StandardCharsets.UTF_8));
  try{
   String path="/api/master-data/product/"+product+"/structure-records/measurement";
   String groups="/api/master-data/product-unit/groups";
   var json=new tools.jackson.databind.ObjectMapper();
   var csrf=json.readTree(measurementRequest("GET","/bff/csrf",cookie,null,null,null).body());
   String header=csrf.path("headerName").asText();
   String token=csrf.path("token").asText();
   Assertions.assertFalse(token.isBlank());
   String payload=json.writeValueAsString(Map.of("version",0,"units",List.of(Map.of("unitId",box,"scene","ARRIVAL","method","PACKAGING","defaultUnit",true)),"packaging",List.of(Map.of("code","PACK-6","name","六瓶箱","unitId",box,"quantity","6"))));
   Assertions.assertEquals(401,measurementRequest("GET",path,null,null,null,null).statusCode());
   Assertions.assertEquals(403,measurementRequest("GET",path,deniedCookie,null,null,null).statusCode());
   Assertions.assertEquals(403,measurementRequest("GET",groups,deniedCookie,null,null,null).statusCode());
   Assertions.assertEquals(200,measurementRequest("GET",groups,cookie,null,null,null).statusCode());
   var created=measurementRequest("POST",groups,cookie,"{\"code\":\"MASS\",\"name\":\"质量\"}",header,token);
   Assertions.assertEquals(201,created.statusCode(),created.body());
   Assertions.assertEquals(204,measurementRequest("PUT",groups+"/MASS",cookie,"{\"name\":\"质量计量\",\"version\":0}",header,token).statusCode());
   var filtered=measurementRequest("GET","/api/master-data/product-unit?groupCode=PACKAGE",cookie,null,null,null);
   Assertions.assertEquals(200,filtered.statusCode(),filtered.body());
   Assertions.assertEquals(1,json.readTree(filtered.body()).path("total").asInt());
   Assertions.assertEquals("BOX",json.readTree(filtered.body()).path("rows").get(0).path("code").asText());
   Assertions.assertEquals(403,measurementRequest("PUT",path,cookie,payload,null,null).statusCode());
   var saved=measurementRequest("PUT",path,cookie,payload,header,token);
   Assertions.assertEquals(200,saved.statusCode(),saved.body());
   var trial=measurementRequest("GET",path+"/calculation?code=PACK-6&quantity=2",cookie,null,null,null);
   Assertions.assertEquals(200,trial.statusCode(),trial.body());
   Assertions.assertEquals("12",json.readTree(trial.body()).path("exact").asText());
   Assertions.assertEquals(403,measurementRequest("GET",path+"/calculation?code=PACK-6&quantity=2",deniedCookie,null,null,null).statusCode());
   Assertions.assertEquals(409,measurementRequest("PUT",path,cookie,payload,header,token).statusCode());
   Files.writeString(sessionFile,json.writeValueAsString(Map.of("cookie",cookie,"product",product,"tenant",tenant)));
   long deadline=System.nanoTime()+java.time.Duration.ofMinutes(15).toNanos();
   while(System.nanoTime()<deadline&&!Files.exists(directory.resolve("stop")))Thread.sleep(500);
  }finally{
   Files.deleteIfExists(sessionFile);
   sessionRepository.deleteById(adminSession.getId());
   sessionRepository.deleteById(deniedSession.getId());
  }
 }

 /** 向隔离真实服务器发请求，凭据限一次性测试会话。 */
 private static java.net.http.HttpResponse<String> measurementRequest(String method,String path,String cookie,String body,String header,String token)throws Exception{
  var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:"+Integer.getInteger("product.qa.backend.port",18089)+path));
  if(cookie!=null)request.header("Cookie","JSESSIONID="+cookie);
  if(header!=null)request.header(header,token);
  if(body!=null)request.header("Content-Type","application/json");
  request.method(method,body==null?java.net.http.HttpRequest.BodyPublishers.noBody():java.net.http.HttpRequest.BodyPublishers.ofString(body));
  return java.net.http.HttpClient.newHttpClient().send(request.build(),java.net.http.HttpResponse.BodyHandlers.ofString());
 }
 /** 隔离的真实历史分页夹具。普通成员拥有登录会话，但未授予商品读取权限。 */
 @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="test",matches=".*#productHistoryBrowserHost.*")
 @Test void productHistoryBrowserHost() throws Exception {
  UUID tenant=UUID.randomUUID(),product=UUID.randomUUID();
  UUID reader=UUID.randomUUID(),readerMember=UUID.randomUUID();
  UUID denied=UUID.randomUUID(),deniedMember=UUID.randomUUID();
  jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'商品历史浏览器验收')",tenant.toString());
  for(var identity:Map.of(reader,readerMember,denied,deniedMember).entrySet()){
   String account=identity.getKey().toString();
   jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'商品历史验收账号','unused')",account,"history-browser-"+account);
   jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",identity.getValue().toString(),tenant.toString(),account);
  }
  bootstrap.initialize(tenant,readerMember);
  jdbc.update("INSERT INTO md_product_v1(id,tenant_id,code,name,version) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'QA-HISTORY','历史分页验收商品',24)",product.toString(),tenant.toString());
  // 一次性数据库中准备 0–24 版本，验证真实 Mapper 的排序、总数与分页。
  for(int version=0;version<=24;version++){
   jdbc.update("INSERT INTO md_product_v1_change(id,tenant_id,product_id,to_version,event_type,after_snapshot) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,JSON_OBJECT('name','历史分页验收商品','status','DRAFT','version',?,'structureRevision',0))",UUID.randomUUID().toString(),tenant.toString(),product.toString(),version,version==0?"CREATE":"UPDATE",version);
  }
  var readerSession=historySession(tenant,reader,readerMember);
  var deniedSession=historySession(tenant,denied,deniedMember);
  var directory=Path.of(System.getProperty("product.qa.dir"));
  Files.createDirectories(directory);
  var sessionFile=directory.resolve("history-sessions.json");
  var encoder=Base64.getEncoder();
  try{
   var fixture=Map.of("product",product.toString(),"reader",encoder.encodeToString(readerSession.getId().getBytes(java.nio.charset.StandardCharsets.UTF_8)),"denied",encoder.encodeToString(deniedSession.getId().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
   Files.writeString(sessionFile,new tools.jackson.databind.ObjectMapper().writeValueAsString(fixture));
   long deadline=System.nanoTime()+java.time.Duration.ofMinutes(10).toNanos();
   while(System.nanoTime()<deadline&&!Files.exists(directory.resolve("stop")))Thread.sleep(500);
  }finally{
   Files.deleteIfExists(sessionFile);
   sessionRepository.deleteById(readerSession.getId());
   sessionRepository.deleteById(deniedSession.getId());
  }
 }

 /** @return 仅用于一次性测试库的已认证成员会话，测试结束立即回收 */
 private org.springframework.session.Session historySession(UUID tenant,UUID account,UUID member){
  var session=sessionRepository.createSession();
  var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
  var principal=org.springframework.security.core.userdetails.User.withUsername(account.toString()).password("").authorities("ACCOUNT").build();
  context.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities()));
  session.setAttribute("SPRING_SECURITY_CONTEXT",context);
  session.setAttribute("appnexus.credentialVersion",0L);
  if(tenant!=null&&member!=null){
   session.setAttribute("appnexus.tenant",tenant.toString());
   session.setAttribute("appnexus.member",member.toString());
   session.setAttribute("appnexus.tenantVersion",1L);
  }
  sessionRepository.save(session);
  return session;
 }

 @Test void isolatedBrowserHost()throws Exception{
  UUID tenant=UUID.randomUUID(),account=UUID.randomUUID(),member=UUID.randomUUID();
  jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'商品V1浏览器验收')",tenant.toString());
  jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'商品验收测试账号','unused')",account.toString(),"product-browser-"+account);
  jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",member.toString(),tenant.toString(),account.toString());
  bootstrap.initialize(tenant,member);
  String organization=ProductOrganizationFixture.create(jdbc,tenant);
  // 一次性真实目录种子仅用于隔离浏览器验收，全部通过领域命令，不写共享库或伪造接口返回。
  var trusted=new com.appnexus.platform.tenant.VerifiedTenantContext(account,tenant,member,1);
  var gram=catalog.createUnit(trusted,new com.appnexus.masterdata.api.product.ProductCatalogData.UnitInput("QA-G","验收克","MASS",3,0));
  catalog.createUnit(trusted,new com.appnexus.masterdata.api.product.ProductCatalogData.UnitInput("QA-KG","验收千克","MASS",3,0));
  var category=catalog.createCategory(trusted,new com.appnexus.masterdata.api.product.ProductCatalogData.CategoryInput("QA-EXT","扩展验收分类",null,0));
  var attribute=attributes.createAttribute(trusted,new com.appnexus.masterdata.api.product.ProductAttributeData.AttributeInput("diameter",null,0)).get("id");
  var revision=attributes.createRevision(trusted,attribute,new com.appnexus.masterdata.api.product.ProductAttributeData.RevisionInput("验收直径","DECIMAL",gram.id(),2,"{}",0)).get("id");attributes.publishRevision(trusted,revision,1);
  var template=templates.create(trusted,new com.appnexus.masterdata.api.product.ProductTemplateData.TemplateInput("QA-SPEC","验收规格模板","SPEC",0)).get("id");
  var templateVersion=templates.createVersion(trusted,template,new com.appnexus.masterdata.api.product.ProductTemplateData.VersionInput("规格验收",List.of(new com.appnexus.masterdata.api.product.ProductTemplateData.ItemInput(attribute,revision,null,null,true,false,false,0)),0)).get("id");templates.publish(trusted,templateVersion,1);
  var slots=new LinkedHashMap<String,String>();for(String purpose:List.of("BASIC","SPEC","QUALITY","EXTENSION"))slots.put(purpose,null);slots.put("SPEC",templateVersion);
  var config=catalog.publish(trusted,category.id(),new com.appnexus.masterdata.api.product.ProductCatalogData.Publish(slots,false,0));
  var request=new org.springframework.mock.web.MockHttpServletRequest();
  request.setAttribute(com.appnexus.platform.tenant.VerifiedTenantContext.class.getName(),trusted);
  org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
  try{
   products.create(trusted,new com.appnexus.masterdata.api.product.ProductMainData.Input("QA-EXT-P","扩展浏览器验收商品",null,category.id(),config.id(),"PHYSICAL",gram.id(),null,null,null,null,null,organization));
  }finally{
   org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
  }

  var session=sessionRepository.createSession();
  var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
  context.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(org.springframework.security.core.userdetails.User.withUsername(account.toString()).password("").authorities("ACCOUNT").build(),null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ACCOUNT"))));
  session.setAttribute("SPRING_SECURITY_CONTEXT",context);session.setAttribute("appnexus.credentialVersion",0L);
  session.setAttribute("appnexus.tenant",tenant.toString());session.setAttribute("appnexus.member",member.toString());session.setAttribute("appnexus.tenantVersion",1L);sessionRepository.save(session);
  String cookie=Base64.getEncoder().encodeToString(session.getId().getBytes(java.nio.charset.StandardCharsets.UTF_8));
  var directory=Path.of(System.getProperty("product.qa.dir"));Files.createDirectories(directory);
  Files.writeString(directory.resolve("session.json"),new tools.jackson.databind.ObjectMapper().writeValueAsString(Map.of("cookie",cookie,"tenant",tenant.toString())));
  try{long deadline=System.nanoTime()+java.time.Duration.ofMinutes(60).toNanos();while(System.nanoTime()<deadline&&!Files.exists(directory.resolve("stop")))Thread.sleep(1000);}
  finally{Files.deleteIfExists(directory.resolve("session.json"));sessionRepository.deleteById(session.getId());}
 }
}
