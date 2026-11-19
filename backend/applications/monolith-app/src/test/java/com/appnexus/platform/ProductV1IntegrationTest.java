package com.appnexus.platform;

import static org.assertj.core.api.Assertions.*;
import com.appnexus.masterdata.product.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** 完整组合应用及真实RBAC验证商品配置/草稿；不修改共享库或管理员凭据。 */
@SpringBootTest
@ActiveProfiles("local")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class ProductV1IntegrationTest extends DatabaseTestSupport {
 @Autowired JdbcTemplate jdbc;
 @Autowired org.springframework.test.web.servlet.MockMvc mvc;
 @Autowired ProductCatalogService catalog;
 @Autowired ProductV1Service products;
 @Autowired OperationRegistry registry;
 @Autowired com.appnexus.platform.persistence.organization.OrganizationAdministratorBootstrap bootstrap;

 @Test void realTenantRolesAndRevocationProtectCatalogDraftAndHistory() throws Exception {
  UUID tenant=UUID.randomUUID(),account=UUID.randomUUID(),member=UUID.randomUUID(),ordinaryAccount=UUID.randomUUID(),ordinaryMember=UUID.randomUUID();
  jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'商品V1隔离测试租户')",tenant.toString());
  for(var pair:List.of(Map.entry(account,member),Map.entry(ordinaryAccount,ordinaryMember))){
   jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'商品测试成员','unused')",pair.getKey().toString(),"product-"+pair.getKey());
   jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",pair.getValue().toString(),tenant.toString(),pair.getKey().toString());
  }
  bootstrap.initialize(tenant,member);
  var admin=new VerifiedTenantContext(account,tenant,member,1);
  var ordinary=new VerifiedTenantContext(ordinaryAccount,tenant,ordinaryMember,1);
  var category=catalog.createCategory(admin,new CatalogData.CategoryInput("ROOT","商品分类",null,0));
  assertThat(category.audit().createdBy()).isEqualTo(account.toString());
  var slots=new LinkedHashMap<String,String>();for(String purpose:List.of("BASIC","SPEC","QUALITY","EXTENSION"))slots.put(purpose,null);
  var config=catalog.publish(admin,category.id(),new CatalogData.Publish(slots,false,0));
  var draft=products.create(admin,new com.appnexus.masterdata.api.product.ProductMainData.Input("SERVICE","无规格服务",null,category.id(),config.id(),"SERVICE",null,null,null,null,null));
  String id=(String)draft.get("id");
  assertThat(draft.get("status")).isEqualTo("DRAFT");
  assertThat(products.updateStatus(admin,id,new ProductV1Service.Status("ENABLED",0)).get("status")).isEqualTo("ENABLED");
  assertThatThrownBy(()->products.read(ordinary,id)).hasMessage("MASTER_DATA_DENIED");
  assertThatThrownBy(()->catalog.createCategory(ordinary,new CatalogData.CategoryInput("NO","拒绝",null,0))).hasMessage("MASTER_DATA_DENIED");
  assertThatThrownBy(()->products.create(ordinary,new com.appnexus.masterdata.api.product.ProductMainData.Input("NO","拒绝",null,null,null,null,null,null,null,null,null))).hasMessage("MASTER_DATA_DENIED");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM md_product_v1_change WHERE tenant_id=UUID_TO_BIN(?) AND product_id=UUID_TO_BIN(?)",Integer.class,tenant.toString(),id)).isEqualTo(2);
  var json=new tools.jackson.databind.ObjectMapper();
  var selected=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/bff/tenants/current")
   .with(authenticated(account.toString(),0)).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
   .contentType("application/json").content(json.writeValueAsString(Map.of("tenantId",tenant.toString())))).andReturn().getResponse();
  assertThat(selected.getStatus()).isEqualTo(200);
  var cookie=selected.getCookie("JSESSIONID");
  // 列表已授权行才投影记录审计，供公共ManagementTable显示审计查询，不增加自由表查询入口。
  var listing=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/master-data/product").cookie(cookie)).andReturn().getResponse();
  assertThat(listing.getStatus()).isEqualTo(200);
  assertThat(listing.getHeader("Cache-Control")).contains("no-store");
  var listed=json.readTree(listing.getContentAsString()).path("rows").get(0).path("audit");
  assertThat(listed.path("createdBy").asText()).isEqualTo(account.toString());
  assertThat(listed.path("createdAt").asText()).isNotBlank();
  // 真实HTTP图片链路：原始二进制、CSRF、受权读取和父CAS，不以模拟上传代替接口验收。
  String imageProduct=(String)products.create(admin,new com.appnexus.masterdata.api.product.ProductMainData.Input("HTTP-IMAGE","图片接口",null,null,null,null,null,null,null,null,null)).get("id");
  var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
  assertThat(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/master-data/product/"+imageProduct+"/images?version=0").cookie(cookie).contentType("image/png").content(bytes.toByteArray())).andReturn().getResponse().getStatus()).isEqualTo(403);
  var uploaded=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/master-data/product/"+imageProduct+"/images?version=0").cookie(cookie).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("image/png").content(bytes.toByteArray())).andReturn().getResponse();assertThat(uploaded.getStatus()).isEqualTo(200);String image=json.readTree(uploaded.getContentAsString()).path("id").asText();
  var imageRead=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/master-data/product/"+imageProduct+"/images/"+image+"/content").cookie(cookie)).andReturn().getResponse();assertThat(imageRead.getStatus()).isEqualTo(200);assertThat(imageRead.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");assertThat(imageRead.getHeader("Cache-Control")).contains("no-store");assertThat(imageRead.getContentAsByteArray()).isNotEmpty();
  var detail=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/master-data/product/"+id).cookie(cookie)).andReturn().getResponse();
  assertThat(detail.getStatus()).isEqualTo(200);assertThat(detail.getHeader("Cache-Control")).contains("no-store");
  assertThat(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/master-data/product/"+id)).andReturn().getResponse().getStatus()).isEqualTo(401);
  assertThat(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/master-data/product/"+id+"/status").cookie(cookie).contentType("application/json").content(json.writeValueAsString(Map.of("status","DISABLED","version",1)))).andReturn().getResponse().getStatus()).isEqualTo(403);
  assertThat(products.read(admin,id).get("status")).isEqualTo("ENABLED");
  var ordinarySession=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/bff/tenants/current")
   .with(authenticated(ordinaryAccount.toString(),0)).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
   .contentType("application/json").content(json.writeValueAsString(Map.of("tenantId",tenant.toString())))).andReturn().getResponse();
  assertThat(ordinarySession.getStatus()).isEqualTo(200);
  assertThat(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/master-data/product/"+id).cookie(ordinarySession.getCookie("JSESSIONID"))).andReturn().getResponse().getStatus()).isEqualTo(403);
  String oldId=UUID.randomUUID().toString();
  String original="  legacy specification 0005  ";
  jdbc.update("INSERT INTO md_product(id,tenant_id,code,name,barcode,specification,unit,enabled,version,created_by,created_at,updated_by,updated_at) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'OLD-PRODUCT','旧商品','000099',?,'历史单位',TRUE,2,NULL,NULL,NULL,NULL)",oldId,tenant.toString(),original);
  var staged=products.stageLegacy(admin,oldId);
  assertThat(staged.get("legacySpecificationOriginal")).isEqualTo(original);
  assertThat(staged.get("createdAt")).isNull();assertThat(staged.get("updatedAt")).isNull();
  assertThatThrownBy(()->products.stageLegacy(ordinary,oldId)).hasMessage("MASTER_DATA_DENIED");
  products.reviewLegacySpecification(admin,oldId,new ProductV1Service.LegacyReview("DEFERRED_SPEC","保留真实规格",2L));
  assertThat(products.read(admin,oldId).get("legacySpecificationOriginal")).isEqualTo(original);
  assertThat(products.read(admin,oldId).get("structureState")).isEqualTo("LEGACY_PENDING");
  assertThat(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/master-data/product/"+id+"/status").cookie(cookie).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("status","DISABLED","version",1)))).andReturn().getResponse().getStatus()).isEqualTo(200);
  assertThat(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/master-data/product/"+id+"/status").cookie(cookie).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("status","ENABLED")))).andReturn().getResponse().getStatus()).isEqualTo(400);
  var migration=new com.appnexus.masterdata.api.product.ProductStructureData.Migration(2L,0L,config.id(),category.id(),config.id(),"SERVICE",null,List.of(),List.of(),"验证宿主持久签名迁移",null);
  var preview=products.previewStructure(admin,id,migration);
  String key=UUID.randomUUID().toString();
  var command=new com.appnexus.masterdata.api.product.ProductStructureData.MigrationCommand(migration,(String)preview.get("previewToken"));
  assertThat(products.migrateStructure(admin,id,command,key).get("replayed")).isEqualTo(false);
  assertThat(products.migrateStructure(admin,id,command,key).get("replayed")).isEqualTo(true);
  jdbc.update("DELETE FROM access_member_role WHERE tenant_id=UUID_TO_BIN(?) AND member_id=UUID_TO_BIN(?)",tenant.toString(),member.toString());
  assertThatThrownBy(()->products.read(admin,id)).hasMessage("MASTER_DATA_DENIED");
  assertThatThrownBy(()->catalog.units(admin,"",1,20)).hasMessage("MASTER_DATA_DENIED");
  assertThatThrownBy(()->products.migrateStructure(admin,id,command,key)).hasMessage("MASTER_DATA_DENIED");
 }

 @Test void installedProductOperationsReferenceRealPermissionsAndPages(){
  var types=Set.of("ProductUnit","ProductCategory","ProductAttribute","ProductTemplate","Product");
  var operations=registry.all().stream().filter(o->types.contains(o.objectType())).toList();
  assertThat(operations).isNotEmpty();
  assertThat(operations.stream().map(o->o.id()).distinct().count()).isEqualTo(operations.size());
  for(var op:operations){
   for(String expression:op.permissions())for(String permission:expression.split("&"))assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM access_permission WHERE code=? AND scope_type='TENANT'",Integer.class,permission)).isEqualTo(1);
   for(String page:op.pages())assertThat(com.appnexus.platform.configuration.NavigationPages.find(page)).isNotNull();
  }
 }
}
