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
 @Autowired com.appnexus.platform.persistence.organization.OrganizationAdministratorBootstrap bootstrap;
 @Autowired com.appnexus.masterdata.application.product.ProductCatalogService catalog;
 @Autowired com.appnexus.masterdata.application.product.ProductService products;
 @Autowired com.appnexus.masterdata.application.product.ProductAttributeService attributes;
 @Autowired com.appnexus.masterdata.application.product.ProductTemplateService templates;
 @Autowired MasterDataManagement warehouseFixtureMaster;
 @Autowired InboundReceiving warehouseFixtureReceiving;
 @Autowired InboundDisposition warehouseFixtureDisposition;
 @Autowired CoilOutbound warehouseFixtureCoils;
 @Autowired OutboundStock warehouseFixtureStock;
 @Autowired CoilTitleTransfer warehouseFixtureTitles;
 @Autowired org.springframework.security.crypto.password.PasswordEncoder warehouseFixturePasswords;

 /** 以真实业务命令准备在库、冻结及已占整卷，供工作区浏览器验收。 */
 @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="test",matches=".*#warehouseTitleBrowserHost.*")
 @Test void warehouseTitleBrowserHost() throws Exception {
  UUID tenant=UUID.randomUUID(), ownerAccount=UUID.randomUUID(), ownerMember=UUID.randomUUID();
  UUID deniedAccount=UUID.randomUUID(), deniedMember=UUID.randomUUID(), foreignTenant=UUID.randomUUID();
  jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'仓储货权验收租户'),(UUID_TO_BIN(?),'拒绝范围租户')",
      tenant.toString(),foreignTenant.toString());
  for (var identity:Map.of(ownerAccount,ownerMember,deniedAccount,deniedMember).entrySet()) {
   jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'仓储测试成员','unused')",
       identity.getKey().toString(),"warehouse-browser-"+identity.getKey());
   jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",
       identity.getValue().toString(),tenant.toString(),identity.getKey().toString());
  }
  bootstrap.initialize(tenant,ownerMember);
  DictionaryFixtures.seed(jdbc,tenant,ownerAccount);
  var context=new VerifiedTenantContext(ownerAccount,tenant,ownerMember,1);
  var request=new org.springframework.mock.web.MockHttpServletRequest();
  request.setAttribute(VerifiedTenantContext.class.getName(),context);
  org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
      new org.springframework.web.context.request.ServletRequestAttributes(request));
  String ownerA=UUID.randomUUID().toString(),ownerB=UUID.randomUUID().toString(),product=UUID.randomUUID().toString();
  String spec=UUID.randomUUID().toString(),sku=UUID.randomUUID().toString(),foreignWarehouse=UUID.randomUUID().toString();
  String warehouse;
  var extra=new LinkedHashMap<String,String>();
  if (Boolean.getBoolean("warehouse.relogin.browser.qa")) {
   // 隔离宿主为本次验收创建临时测试凭据，正式登录链使用真实密码编码器。
   String temporaryPassword="Qa!"+UUID.randomUUID();
   jdbc.update("UPDATE identity_account SET password_hash=? WHERE id=UUID_TO_BIN(?)",
       warehouseFixturePasswords.encode(temporaryPassword),ownerAccount.toString());
   extra.put("loginUsername","warehouse-browser-"+ownerAccount);
   extra.put("loginPassword",temporaryPassword);
  }
  try {
   String organization=ProductOrganizationFixture.create(jdbc,tenant);
   jdbc.update("""
       INSERT INTO md_partner(id,tenant_id,code,name,partner_type,organization_id)
       VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'WAREHOUSE-A','钢卷货主甲','CUSTOMER',UUID_TO_BIN(?)),
         (UUID_TO_BIN(?),UUID_TO_BIN(?),'WAREHOUSE-B','钢卷货主乙','CUSTOMER',UUID_TO_BIN(?))
       """,ownerA,tenant.toString(),organization,ownerB,tenant.toString(),organization);
   if(Boolean.getBoolean("reference.catalog.browser.qa")){
    String person=UUID.randomUUID().toString(),position=UUID.randomUUID().toString();
    jdbc.update("INSERT INTO md_person(id,tenant_id,code,name,gender) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'REF-QA-PERSON','批量名称验收人员','MALE')",person,tenant.toString());
    jdbc.update("INSERT INTO tenant_organization_position(tenant_id,id,organization_id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),'REF-QA-POSITION','批量名称验收岗位')",tenant.toString(),position,organization);
    jdbc.update("INSERT INTO tenant_person_organization(tenant_id,id,person_id,organization_id,is_primary) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(UUID()),UUID_TO_BIN(?),UUID_TO_BIN(?),TRUE)",tenant.toString(),person,organization);
    jdbc.update("INSERT INTO tenant_person_position(tenant_id,id,person_id,position_id,starts_at) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(UUID()),UUID_TO_BIN(?),UUID_TO_BIN(?),UTC_TIMESTAMP(6))",tenant.toString(),person,position);
    extra.put("referencePerson",person);
    extra.put("referencePosition",position);

    // 合成已授权档案供真实浏览器回显及 100 行 HTTP 页并发计时。
    var markets=new ArrayList<Object[]>();
    var partners=new ArrayList<Object[]>();
    int sampleRows=Integer.getInteger("reference.catalog.qa.rows",500);
    for(int i=0;i<sampleRows;i++){
      String code="REF_QA_"+i;
      markets.add(new Object[]{UUID.randomUUID().toString(),tenant.toString(),code,"验收国别 "+i});
      partners.add(new Object[]{UUID.randomUUID().toString(),tenant.toString(),code,"验收客商 "+i,organization,code});
    }
    jdbc.batchUpdate("INSERT INTO md_country_region(id,tenant_id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,?)",markets);
    jdbc.batchUpdate("""
        INSERT INTO md_partner(id,tenant_id,code,name,partner_type,organization_id,country_region)
        VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,'CUSTOMER',UUID_TO_BIN(?),?)
        """,partners);
   }
   jdbc.update("""
       INSERT INTO md_warehouse(id,tenant_id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'FOREIGN-W','外部仓')
       """,foreignWarehouse,foreignTenant.toString());
   warehouse=warehouseFixtureMaster.create(context,MasterData.Kind.WAREHOUSE,
       new MasterData.Input("TITLE-BROWSER-W","钢卷验收仓",Map.of(),0)).get("id");
   String area=warehouseFixtureMaster.create(context,MasterData.Kind.STORAGE_AREA,
       new MasterData.Input("TITLE-BROWSER-A","钢卷验收区",Map.of("parentCode","TITLE-BROWSER-W"),0)).get("id");
   String location=warehouseFixtureMaster.create(context,MasterData.Kind.STORAGE_LOCATION,
       new MasterData.Input("TITLE-BROWSER-L","钢卷验收位",Map.of("parentCode","TITLE-BROWSER-A"),0)).get("id");
   var position=new InboundReceiptReferences.Position(warehouse,area,location,0,0,0);
   var unit=catalog.createUnit(context,new ProductCatalogData.UnitInput("TITLE-KG","千克","MASS",3,0));
   var category=catalog.createCategory(context,new ProductCatalogData.CategoryInput("TITLE-COIL","仓储验收分类",null,0));
   var slots=new LinkedHashMap<String,String>();
   for(String purpose:List.of("BASIC","SPEC","QUALITY","EXTENSION")) slots.put(purpose,null);
   var config=catalog.publish(context,category.id(),new ProductCatalogData.Publish(slots,false,0));
   jdbc.update("""
       INSERT INTO md_product_v1(id,tenant_id,code,name,product_form,base_unit_id,category_id,category_config_id,
           specification_mode,status,structure_state,organization_id)
       VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'TITLE-P','验收热卷','PHYSICAL',UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),
           'CUSTOM','ENABLED','COMPLETE',UUID_TO_BIN(?))
       """,product,tenant.toString(),unit.id(),category.id(),config.id(),organization);
   jdbc.update("""
       INSERT INTO md_product_specification(id,tenant_id,product_id,config_id,mode,name)
       VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),'CUSTOM','验收品规')
       """,spec,tenant.toString(),product,config.id());
   jdbc.update("""
       INSERT INTO md_product_sku(id,tenant_id,product_id,specification_id,code)
       VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),'TITLE-SKU')
       """,sku,tenant.toString(),product,spec);
   var item=new InboundReceiptReferences.Item(product,InboundReceiptReferences.IdentityKind.SKU,sku,0);
   String notice=warehouseFixtureId();
   if (Boolean.getBoolean("warehouse.link.browser.qa")) {
    // 串联验证仅准备接收仓主数据，全部履约和返仓单据由浏览器产生。
    extra.put("linkedWarehouse",createReturnWarehouse(context,"LINK-RETURN-W","串联接收"));
   } else if (Boolean.getBoolean("warehouse.transit.browser.qa")) {
    prepareBrowserTransit(context,position,item,ownerA,unit,extra);
    if (Boolean.getBoolean("warehouse.documents.browser.qa")) {
     String pending=warehouseFixtureId(),outbound=warehouseFixtureId(),authorization=warehouseFixtureId(),trip=warehouseFixtureId();
     var cap=new BigDecimal("100000");
     warehouseFixtureReceiving.issue(context,new InboundReceiving.Notice(pending,warehouse,ownerA,item,unit.id(),cap));
     warehouseFixtureCoils.issue(context,new CoilOutbound.Notice(outbound,warehouse,ownerA,item,unit.id(),unit.code(),cap));
     warehouseFixtureCoils.authorize(context,new CoilOutbound.Authorization(authorization,outbound,ownerA,
         java.time.Instant.now().plusSeconds(3600),cap));
     warehouseFixtureCoils.plan(context,new CoilOutbound.Trip(trip,authorization,"DOCUMENT-EMPTY",cap));
     String coil=prepareBrowserCoil(context,position,item,ownerA,unit,pending,"21000","DOCUMENT-TITLE");
     String transfer=warehouseFixtureId(),restriction=warehouseFixtureId();
     warehouseFixtureTitles.transfer(context,new CoilTitleTransfer.Command(transfer,coil,warehouse,ownerA,ownerB,0,warehouseFixtureId()));
     warehouseFixtureStock.freeze(context,new OutboundStock.Freeze(restriction,coil,warehouseFixtureId()));
     String unreceived=warehouseFixtureId();
     warehouseFixtureReceiving.issue(context,new InboundReceiving.Notice(unreceived,warehouse,ownerA,item,unit.id(),cap));
     extra.putAll(Map.of("unreceived",unreceived,"unallocated",outbound,"emptyTrip",trip,"emptyAuthorization",authorization,
         "transfer",transfer,"restriction",restriction,"sourceLocation",location));
    }

   } else if (!Boolean.getBoolean("warehouse.flow.browser.qa")) {
    warehouseFixtureReceiving.issue(context,new InboundReceiving.Notice(notice,warehouse,ownerA,item,unit.id(),new BigDecimal("69000")));
    prepareBrowserCoil(context,position,item,ownerA,unit,notice,"51000","BROWSER-COIL-READY");
    String frozen=prepareBrowserCoil(context,position,item,ownerA,unit,notice,"11000","BROWSER-COIL-FROZEN");
    String occupied=prepareBrowserCoil(context,position,item,ownerA,unit,notice,"7000","BROWSER-COIL-OCCUPIED");
    warehouseFixtureStock.freeze(context,new OutboundStock.Freeze(warehouseFixtureId(),frozen,warehouseFixtureId()));
    String outbound=warehouseFixtureId(),authorization=warehouseFixtureId(),trip=warehouseFixtureId();
    var cap=new BigDecimal("100000");
    warehouseFixtureCoils.issue(context,new CoilOutbound.Notice(outbound,warehouse,ownerA,item,unit.id(),unit.code(),cap));
    warehouseFixtureCoils.authorize(context,new CoilOutbound.Authorization(authorization,outbound,ownerA,
        java.time.Instant.now().plusSeconds(3600),cap));
    warehouseFixtureCoils.plan(context,new CoilOutbound.Trip(trip,authorization,"UI-VEHICLE",cap));
    warehouseFixtureCoils.allocate(context,new CoilOutbound.Allocate(warehouseFixtureId(),trip,occupied));
   }
  } finally {
   org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
  }
  var adminSession=historySession(tenant,ownerAccount,ownerMember);
  var deniedSession=historySession(tenant,deniedAccount,deniedMember);
  var encoder=Base64.getEncoder();
  var directory=Path.of(System.getProperty("product.qa.dir"));
  Files.createDirectories(directory);
  var sessionFile=directory.resolve("warehouse-session.json");
  try {
   extra.putAll(Map.of(
       "cookie",encoder.encodeToString(adminSession.getId().getBytes(java.nio.charset.StandardCharsets.UTF_8)),
       "deniedCookie",encoder.encodeToString(deniedSession.getId().getBytes(java.nio.charset.StandardCharsets.UTF_8)),
       "ownerA",ownerA,"ownerB",ownerB,"warehouse",warehouse,"product",product,"sku",sku,"foreignWarehouse",foreignWarehouse));
   Files.writeString(sessionFile,new tools.jackson.databind.ObjectMapper().writeValueAsString(extra));
   long deadline=System.nanoTime()+java.time.Duration.ofMinutes(15).toNanos();
   while (System.nanoTime()<deadline && !Files.exists(directory.resolve("stop"))) Thread.sleep(500);
  } finally {
   Files.deleteIfExists(sessionFile);
   sessionRepository.deleteById(adminSession.getId());
   sessionRepository.deleteById(deniedSession.getId());
  }
 }

 /** 为前端返仓用例准备已完成的两卷出仓，浏览器自行登记发运及处置。 */
 private void prepareBrowserTransit(VerifiedTenantContext context,InboundReceiptReferences.Position source,
     InboundReceiptReferences.Item item,String owner,ProductCatalogData.Unit unit,Map<String,String> fixture) {
  String inbound=warehouseFixtureId();
  warehouseFixtureReceiving.issue(context,new InboundReceiving.Notice(inbound,source.warehouseId(),owner,item,
      unit.id(),new BigDecimal("68000")));
  String first=prepareBrowserCoil(context,source,item,owner,unit,inbound,"51000","RETURN-UI-51");
  String second=prepareBrowserCoil(context,source,item,owner,unit,inbound,"17000","RETURN-UI-17");
  String notice=warehouseFixtureId(),authorization=warehouseFixtureId(),trip=warehouseFixtureId();
  var cap=new BigDecimal("72000");
  warehouseFixtureCoils.issue(context,new CoilOutbound.Notice(notice,source.warehouseId(),owner,item,unit.id(),unit.code(),cap));
  warehouseFixtureCoils.authorize(context,new CoilOutbound.Authorization(authorization,notice,owner,
      java.time.Instant.now().plusSeconds(3600),cap));
  String vehicle="RETURN-UI-TRUCK";
  warehouseFixtureCoils.plan(context,new CoilOutbound.Trip(trip,authorization,vehicle,cap));
  var a=new CoilOutbound.Allocate(warehouseFixtureId(),trip,first);
  var b=new CoilOutbound.Allocate(warehouseFixtureId(),trip,second);
  warehouseFixtureCoils.allocate(context,a);
  warehouseFixtureCoils.allocate(context,b);
  warehouseFixtureCoils.verifyVehicleLimits(context,new CoilOutbound.VehicleLimits(trip,vehicle,unit.id(),
      warehouseFixtureId(),cap,new BigDecimal("92000")));
  var review=warehouseFixtureCoils.reviewWeighing(context,new CoilOutbound.Weighing(warehouseFixtureId(),trip,
      warehouseFixtureId(),vehicle,unit.id(),new BigDecimal("92000"),new BigDecimal("20000"),
      List.of(new CoilOutbound.WeighedCoil(a.id(),new BigDecimal("53000")),
          new CoilOutbound.WeighedCoil(b.id(),new BigDecimal("19000")))));
  String handover=warehouseFixtureId();
  warehouseFixtureCoils.handover(context,new CoilOutbound.Handover(handover,trip,review.id(),
      warehouseFixtureId(),vehicle,owner,List.of(first,second)));
  fixture.putAll(Map.of("postedInbound",inbound,"postedNotice",notice,"postedAuthorization",authorization,"postedTrip",trip));
  fixture.putAll(Map.of("handover",handover,"first",first,"second",second,
      "oldWarehouse",createReturnWarehouse(context,"RETURN-UI-OLD","原接收"),
      "newWarehouse",createReturnWarehouse(context,"RETURN-UI-NEW","改派接收")));
 }

 private String createReturnWarehouse(VerifiedTenantContext context,String code,String label) {
  String warehouse=warehouseFixtureMaster.create(context,MasterData.Kind.WAREHOUSE,
      new MasterData.Input(code,label+"仓",Map.of(),0)).get("id");
  String area=code+"-A";
  warehouseFixtureMaster.create(context,MasterData.Kind.STORAGE_AREA,
      new MasterData.Input(area,label+"区",Map.of("parentCode",code),0));
  warehouseFixtureMaster.create(context,MasterData.Kind.STORAGE_LOCATION,
      new MasterData.Input(code+"-L",label+"位",Map.of("parentCode",area),0));
  return warehouse;
 }

 /** @return 使用真实接管、过账和质检后可受仓储门禁管理的整卷身份 */
 private String prepareBrowserCoil(VerifiedTenantContext context,InboundReceiptReferences.Position position,
     InboundReceiptReferences.Item item,String owner,ProductCatalogData.Unit unit,String notice,String quantity,String serial) {
  String custody=warehouseFixtureId(),receipt=warehouseFixtureId();
  warehouseFixtureReceiving.takeCustody(context,new InboundReceiving.Custody(custody,notice,position,warehouseFixtureId()));
  warehouseFixtureReceiving.receive(context,new InboundReceiving.Receipt(receipt,custody,warehouseFixtureId(),
      new InboundReceiptReferences.Request(position,item,owner,unit.id(),quantity,new InboundReceiptReferences.SameUnit())));
  warehouseFixtureDisposition.registerTitle(context,new InboundDisposition.TitleDecision(receipt,warehouseFixtureId(),
      java.time.Instant.now().minusSeconds(60)));
  warehouseFixtureDisposition.releaseQuality(context,new InboundDisposition.Decision(receipt,warehouseFixtureId()));
  warehouseFixtureCoils.identify(context,new CoilOutbound.Coil(receipt,serial,unit.code()));
  return receipt;
 }

 private static String warehouseFixtureId() { return UUID.randomUUID().toString(); }

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
  session.setAttribute("appnexus.tenant",tenant.toString());
  session.setAttribute("appnexus.member",member.toString());
  session.setAttribute("appnexus.tenantVersion",1L);
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
