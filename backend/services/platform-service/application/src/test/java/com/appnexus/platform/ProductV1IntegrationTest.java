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
  var draft=products.create(admin,new ProductV1Service.Input("SERVICE","无规格服务",null,category.id(),config.id(),"SERVICE",null,null,null,null,null));
  String id=(String)draft.get("id");
  assertThat(draft.get("status")).isEqualTo("DRAFT");
  assertThat(products.updateStatus(admin,id,new ProductV1Service.Status("ENABLED",0)).get("status")).isEqualTo("ENABLED");
  assertThatThrownBy(()->products.read(ordinary,id)).hasMessage("MASTER_DATA_DENIED");
  assertThatThrownBy(()->catalog.createCategory(ordinary,new CatalogData.CategoryInput("NO","拒绝",null,0))).hasMessage("MASTER_DATA_DENIED");
  assertThatThrownBy(()->products.create(ordinary,new ProductV1Service.Input("NO","拒绝",null,null,null,null,null,null,null,null,null))).hasMessage("MASTER_DATA_DENIED");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM md_product_v1_change WHERE tenant_id=UUID_TO_BIN(?) AND product_id=UUID_TO_BIN(?)",Integer.class,tenant.toString(),id)).isEqualTo(2);
  var json=new tools.jackson.databind.ObjectMapper();
  var selected=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/bff/tenants/current")
   .with(authenticated(account.toString(),0)).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
   .contentType("application/json").content(json.writeValueAsString(Map.of("tenantId",tenant.toString())))).andReturn().getResponse();
  assertThat(selected.getStatus()).isEqualTo(200);
  var cookie=selected.getCookie("JSESSIONID");
  var detail=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/master-data/product-v1/"+id).cookie(cookie)).andReturn().getResponse();
  assertThat(detail.getStatus()).isEqualTo(200);assertThat(detail.getHeader("Cache-Control")).contains("no-store");
  assertThat(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/master-data/product-v1/"+id)).andReturn().getResponse().getStatus()).isEqualTo(401);
  assertThat(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/master-data/product-v1/"+id+"/status").cookie(cookie).contentType("application/json").content(json.writeValueAsString(Map.of("status","DISABLED","version",1)))).andReturn().getResponse().getStatus()).isEqualTo(403);
  assertThat(products.read(admin,id).get("status")).isEqualTo("ENABLED");
  var ordinarySession=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/bff/tenants/current")
   .with(authenticated(ordinaryAccount.toString(),0)).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
   .contentType("application/json").content(json.writeValueAsString(Map.of("tenantId",tenant.toString())))).andReturn().getResponse();
  assertThat(ordinarySession.getStatus()).isEqualTo(200);
  assertThat(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/master-data/product-v1/"+id).cookie(ordinarySession.getCookie("JSESSIONID"))).andReturn().getResponse().getStatus()).isEqualTo(403);
  products.updateStatus(admin,id,new ProductV1Service.Status("DISABLED",1));
  var migration=new ProductV1Service.Migration(2L,0L,config.id(),category.id(),config.id(),"SERVICE",null,List.of(),List.of(),"验证宿主持久签名迁移",null);
  var preview=products.previewStructure(admin,id,migration);
  String key=UUID.randomUUID().toString();
  var command=new ProductV1Service.MigrationCommand(migration,(String)preview.get("previewToken"));
  assertThat(products.migrateStructure(admin,id,command,key).get("replayed")).isEqualTo(false);
  assertThat(products.migrateStructure(admin,id,command,key).get("replayed")).isEqualTo(true);
  jdbc.update("DELETE FROM access_member_role WHERE tenant_id=UUID_TO_BIN(?) AND member_id=UUID_TO_BIN(?)",tenant.toString(),member.toString());
  assertThatThrownBy(()->products.read(admin,id)).hasMessage("MASTER_DATA_DENIED");
  assertThatThrownBy(()->catalog.units(admin,"",1,20)).hasMessage("MASTER_DATA_DENIED");
  assertThatThrownBy(()->products.migrateStructure(admin,id,command,key)).hasMessage("MASTER_DATA_DENIED");
 }

 @Test void installedProductOperationsReferenceRealPermissionsAndPages(){
  var types=Set.of("ProductUnit","ProductCategory","ProductAttribute","ProductTemplate","ProductV1");
  var operations=registry.all().stream().filter(o->types.contains(o.objectType())).toList();
  assertThat(operations).isNotEmpty();
  assertThat(operations.stream().map(o->o.id()).distinct().count()).isEqualTo(operations.size());
  for(var op:operations){
   for(String expression:op.permissions())for(String permission:expression.split("&"))assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM access_permission WHERE code=? AND scope_type='TENANT'",Integer.class,permission)).isEqualTo(1);
   for(String page:op.pages())assertThat(com.appnexus.platform.configuration.NavigationPages.find(page)).isNotNull();
  }
 }
}
