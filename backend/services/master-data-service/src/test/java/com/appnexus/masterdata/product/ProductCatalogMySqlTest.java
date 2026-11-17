package com.appnexus.masterdata.product;

import static org.assertj.core.api.Assertions.*;
import com.appnexus.masterdata.MasterData.Rejection;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.core.io.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.mysql.MySQLContainer;
import static com.appnexus.masterdata.product.CatalogData.*;

/** 隔离MySQL验证商品DDL与真实领域事务；不连接开发库，不以mock代替数据库约束。 */
@Tag("container")
class ProductCatalogMySqlTest {
 @Test void schemaAndDomainProtectTenancyVersionsAndPublishedConfiguration() throws Exception {
  try(var mysql=new MySQLContainer("mysql:8.4")){
   mysql.start();
   var source=new DriverManagerDataSource(mysql.getJdbcUrl()+"?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true",mysql.getUsername(),mysql.getPassword());
   var jdbc=new JdbcTemplate(source);
   for(String name:List.of("V97__product_catalog_foundation.sql","V98__product_attribute_templates.sql","V99__product_attribute_templates.sql","V100__product_category_template_binding.sql","V101__product_v1_master.sql","V103__product_value_integrity.sql","V106__product_structure_requests.sql")){
    // 权限/菜单真实迁移由组合应用验证；此测试只执行同文件完整DDL，不伪造业务约束。
    String sql=new String(new ClassPathResource("db/migration/"+name).getInputStream().readAllBytes(),StandardCharsets.UTF_8).split("INSERT INTO access_permission")[0];
    try(var conn=source.getConnection()){ScriptUtils.executeSqlScript(conn,new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));}
   }
   var c=new VerifiedTenantContext(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1);
   var other=new VerifiedTenantContext(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1);
   AccessAuthorization allow=(subject,permission)->true;
   var manager=new DataSourceTransactionManager(source);
   var service=new ProductCatalogService(jdbc,allow,manager);
   var unit=service.createUnit(c,new UnitInput("KG","千克","MASS",3,0));
   assertThat(unit.audit().createdBy()).isEqualTo(c.accountId().toString());
   assertThatThrownBy(()->service.updateUnit(other,unit.id(),new UnitInput("KG","伪造","MASS",3,0))).isInstanceOf(Rejection.class);
   assertThatThrownBy(()->jdbc.update("UPDATE md_product_unit SET precision_scale=13 WHERE tenant_id=UUID_TO_BIN(?)",c.tenantId().toString())).isInstanceOf(org.springframework.dao.DataAccessException.class);
   var parent=service.createCategory(c,new CategoryInput("ROOT","根",null,0));
   var child=service.createCategory(c,new CategoryInput("CHILD","子",parent.id(),0));
   assertThatThrownBy(()->service.updateCategory(c,parent.id(),new CategoryInput("ROOT","循环",child.id(),0))).isInstanceOf(Rejection.class);
   assertThatThrownBy(()->service.statusCategory(c,parent.id(),new Status(false,0))).isInstanceOf(Rejection.class);
   var slots=new LinkedHashMap<String,String>();for(String key:List.of("BASIC","SPEC","QUALITY","EXTENSION"))slots.put(key,null);
   var config=service.publish(c,parent.id(),new Publish(slots,false,0));
   assertThat(config.revision()).isEqualTo(1);
   assertThat(config.templates()).containsOnlyKeys("BASIC","SPEC","QUALITY","EXTENSION");
   assertThatThrownBy(()->service.publish(c,parent.id(),new Publish(slots,false,0))).isInstanceOf(Rejection.class);
   assertThatThrownBy(()->service.configuration(other,parent.id(),config.id())).isInstanceOf(Rejection.class);
   var renamed=service.updateCategory(c,parent.id(),new CategoryInput("ROOT","新根",null,1));
   assertThat(service.configuration(c,parent.id(),config.id()).path()).isEqualTo("根");
   assertThat(renamed.audit().createdAt()).isEqualTo(parent.audit().createdAt());
   var denied=new ProductCatalogService(jdbc,(subject,permission)->false,manager);
   assertThatThrownBy(()->denied.createCategory(c,new CategoryInput("DENIED","拒绝",null,0))).hasMessage("MASTER_DATA_DENIED");
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM md_product_category WHERE code='DENIED'",Integer.class)).isZero();
   var attributes=new ProductAttributeService(jdbc,allow,manager);
   var attributeId=attributes.createAttribute(c,new ProductAttributeService.AttributeInput("material",null,0)).get("id");
   var revisionId=attributes.createRevision(c,attributeId,new ProductAttributeService.RevisionInput("材质","SINGLE_SELECT",null,null,"{}",0)).get("id");
   attributes.createOption(c,revisionId,new ProductAttributeService.OptionInput("STEEL","钢",0,1));
   attributes.publishRevision(c,revisionId,2);
   assertThatThrownBy(()->attributes.createOption(c,revisionId,new ProductAttributeService.OptionInput("WOOD","木",1,3))).isInstanceOf(Rejection.class);
   assertThatThrownBy(()->attributes.createAttribute(c,new ProductAttributeService.AttributeInput("brand",null,0))).isInstanceOf(Rejection.class);
   var templates=new ProductTemplateService(jdbc,allow,manager);
   var templateId=templates.create(c,new ProductTemplateService.TemplateInput("BASIC","基础","BASIC",0)).get("id");
   var templateVersionId=templates.createVersion(c,templateId,new ProductTemplateService.VersionInput("",List.of(new ProductTemplateService.ItemInput(attributeId,revisionId,null,null,true,false,false,0)),0)).get("id");
   templates.publish(c,templateVersionId,1);
   assertThatThrownBy(()->templates.publish(c,templateVersionId,2)).isInstanceOf(Rejection.class);
   slots.put("BASIC",templateVersionId);
   var bound=service.publish(c,parent.id(),new Publish(slots,false,2));
   assertThat(bound.templates().get("BASIC")).isEqualTo(templateVersionId);
   assertThat(service.configuration(c,parent.id(),config.id()).templates().get("BASIC")).isNull();
   slots.put("BASIC",null);slots.put("QUALITY",templateVersionId);
   assertThatThrownBy(()->service.publish(c,parent.id(),new Publish(slots,false,3))).isInstanceOf(Rejection.class);
   var tokens=new HashMap<String,String>();
   var validProof=new java.util.concurrent.atomic.AtomicBoolean(true);
   var proofs=new com.appnexus.contracts.operation.OperationPreviewProof(){
    public Proof issue(String binding){String token=UUID.randomUUID().toString();tokens.put(token,binding);return new Proof(token,java.time.Instant.now().plusSeconds(900));}
    public boolean verify(String token,String binding){return validProof.get()&&Objects.equals(tokens.get(token),binding);}
   };
   var products=new ProductV1Service(jdbc,allow,manager,proofs);
   var draft=products.create(c,new ProductV1Service.Input("P1","草稿",null,null,null,null,null,null,null,null,null));
   assertThat(draft.get("structureState")).isEqualTo("INCOMPLETE");
   assertThat(draft.get("createdBy")).isEqualTo(c.accountId().toString());
   assertThat(draft.get("updatedBy")).isEqualTo(c.accountId().toString());
   String draftId=(String)draft.get("id");
   assertThatThrownBy(()->products.updateStatus(c,draftId,new ProductV1Service.Status("ENABLED",0))).hasMessage("PRODUCT_COMPLETION_REQUIRED");
   products.archive(c,draftId,new ProductV1Service.Version(0),true);
   assertThatThrownBy(()->products.updateStatus(c,draftId,new ProductV1Service.Status("ENABLED",1))).isInstanceOf(Rejection.class);
   products.archive(c,draftId,new ProductV1Service.Version(1),false);
   assertThatThrownBy(()->products.read(other,draftId)).isInstanceOf(Rejection.class);
   String itemId=jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM md_product_template_item WHERE template_version_id=UUID_TO_BIN(?)",String.class,templateVersionId);
   String optionId=jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM md_product_attribute_option WHERE revision_id=UUID_TO_BIN(?)",String.class,revisionId);
   var complete=products.create(c,new ProductV1Service.Input("P2","服务",null,parent.id(),bound.id(),"SERVICE",null,null,null,null,List.of(new ProductV1Service.Value(itemId,null,List.of(optionId)))));
   assertThat(complete.get("structureState")).isEqualTo("COMPLETE");
   String productId=(String)complete.get("id");products.updateStatus(c,productId,new ProductV1Service.Status("ENABLED",0));
   var previous=products.read(c,productId);
   assertThatThrownBy(()->products.update(c,productId,new ProductV1Service.Update("不完整",null,null,null,null,List.of(),1))).hasMessage("PRODUCT_COMPLETION_REQUIRED");
   assertThat(products.read(c,productId)).isEqualTo(previous);
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM md_product_v1_change WHERE product_id=UUID_TO_BIN(?)",Integer.class,productId)).isEqualTo(2);
   assertThatThrownBy(()->products.updateStatus(c,productId,new ProductV1Service.Status("DISABLED",0))).isInstanceOf(Rejection.class);
   var valueAudit=jdbc.queryForMap("SELECT BIN_TO_UUID(id) id,created_by,created_at FROM md_product_v1_attribute_value WHERE product_id=UUID_TO_BIN(?)",productId);
   products.update(c,productId,new ProductV1Service.Update("服务修正",null,null,null,null,List.of(new ProductV1Service.Value(itemId,null,List.of(optionId))),1));
   assertThat(jdbc.queryForMap("SELECT BIN_TO_UUID(id) id,created_by,created_at FROM md_product_v1_attribute_value WHERE product_id=UUID_TO_BIN(?)",productId)).usingRecursiveComparison().isEqualTo(valueAudit);
   // 同一父version并发普通写只能成功一次；历史唯一版本和CAS不得被重放绕过。
   try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
    java.util.concurrent.Callable<Boolean> update=()->{try{products.update(c,productId,new ProductV1Service.Update("并发",null,null,null,null,null,2));return true;}catch(Rejection e){return false;}};
    var results=executor.invokeAll(List.of(update,update));
    assertThat(results.stream().filter(f->{try{return f.get();}catch(Exception e){throw new RuntimeException(e);}}).count()).isEqualTo(1);
   }
   assertThat(((Number)products.read(c,productId).get("version")).longValue()).isEqualTo(3);
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM md_product_v1_change WHERE product_id=UUID_TO_BIN(?)",Integer.class,productId)).isEqualTo(4);
   assertThatThrownBy(()->jdbc.update("UPDATE md_product_v1_attribute_value SET integer_value=1 WHERE product_id=UUID_TO_BIN(?)",productId)).isInstanceOf(org.springframework.dao.DataAccessException.class);
   assertThatThrownBy(()->jdbc.update("UPDATE md_product_template_item SET attribute_id=UUID_TO_BIN(?) WHERE id=UUID_TO_BIN(?)",UUID.randomUUID().toString(),itemId)).isInstanceOf(org.springframework.dao.DataAccessException.class);

   products.updateStatus(c,productId,new ProductV1Service.Status("DISABLED",3));
   var pageResult=(Map<?,?>)products.list(c,"并发","P2",null,"SERVICE","DISABLED",false,1,1);
   assertThat(((Number)pageResult.get("total")).longValue()).isEqualTo(1);
   assertThat((List<?>)pageResult.get("rows")).hasSize(1);
   assertThat(products.configuration(c,bound.id()).get("templates")).isInstanceOf(Map.class);
   var migration=new ProductV1Service.Migration(4L,0L,bound.id(),parent.id(),config.id(),"SERVICE",null,List.of(),List.of(itemId),"显式退出原属性",null);
   validProof.set(false);
   assertThatThrownBy(()->products.migrateStructure(c,productId,new ProductV1Service.MigrationCommand(migration,"tampered"),UUID.randomUUID().toString())).hasMessage("PRODUCT_PREVIEW_CHANGED_OR_EXPIRED");
   assertThat(((Number)products.read(c,productId).get("version")).longValue()).isEqualTo(4);
   validProof.set(true);
   var preview=products.previewStructure(c,productId,migration);
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM md_product_structure_request",Integer.class)).isZero();
   String token=(String)preview.get("previewToken"),key=UUID.randomUUID().toString();
   var command=new ProductV1Service.MigrationCommand(migration,token);
   try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
    java.util.concurrent.Callable<Map<String,Object>> migrate=()->products.migrateStructure(c,productId,command,key);
    var results=executor.invokeAll(List.of(migrate,migrate));
    assertThat(results.stream().filter(f->{try{return Boolean.TRUE.equals(f.get().get("replayed"));}catch(Exception e){throw new RuntimeException(e);}}).count()).isEqualTo(1);
   }
   assertThat(((Number)products.read(c,productId).get("version")).longValue()).isEqualTo(5);
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM md_product_structure_request",Integer.class)).isEqualTo(1);
   validProof.set(false);
   assertThat(products.migrateStructure(c,productId,command,key).get("replayed")).isEqualTo(true);
   validProof.set(true);
   var changedPayload=new ProductV1Service.Migration(4L,0L,bound.id(),parent.id(),config.id(),"SERVICE",null,List.of(),List.of(itemId),"不同载荷",null);
   assertThatThrownBy(()->products.migrateStructure(c,productId,new ProductV1Service.MigrationCommand(changedPayload,token),key)).hasMessage("PRODUCT_IDEMPOTENCY_KEY_REUSED");
   assertThatThrownBy(()->products.previewStructure(c,productId,migration)).hasMessage("PRODUCT_SOURCE_VERSION_CHANGED");
   var next=new ProductV1Service.Migration(5L,1L,config.id(),parent.id(),bound.id(),"SERVICE",null,List.of(new ProductV1Service.Value(itemId,null,List.of(optionId))),List.of(),"恢复质量属性",null);
   var nextPreview=products.previewStructure(c,productId,next);
   String nextKey=UUID.randomUUID().toString();
   var nextCommand=new ProductV1Service.MigrationCommand(next,(String)nextPreview.get("previewToken"));
   var beforeFailure=products.read(c,productId);
   jdbc.execute("ALTER TABLE md_product_v1_change ADD CONSTRAINT ck_test_history_failure CHECK(to_version<>6 OR event_type<>'STRUCTURE')");
   assertThatThrownBy(()->products.migrateStructure(c,productId,nextCommand,nextKey)).isInstanceOf(RuntimeException.class);
   assertThat(products.read(c,productId)).isEqualTo(beforeFailure);
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM md_product_structure_request",Integer.class)).isEqualTo(1);
   jdbc.execute("ALTER TABLE md_product_v1_change DROP CHECK ck_test_history_failure");
   products.migrateStructure(c,productId,nextCommand,nextKey);
   assertThat(((Number)products.read(c,productId).get("version")).longValue()).isEqualTo(6);
   jdbc.update("UPDATE md_product_structure_request SET expires_at=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP(6)) WHERE tenant_id=UUID_TO_BIN(?) AND request_key=UUID_TO_BIN(?)",c.tenantId().toString(),nextKey);
   assertThatThrownBy(()->products.migrateStructure(c,productId,nextCommand,nextKey)).hasMessage("PRODUCT_IDEMPOTENCY_WINDOW_EXPIRED");
   var noPermission=new ProductV1Service(jdbc,(subject,permission)->false,manager,proofs);
   assertThatThrownBy(()->noPermission.migrateStructure(c,productId,command,key)).hasMessage("MASTER_DATA_DENIED");

   assertThat(jdbc.queryForList("SELECT t.table_name FROM information_schema.tables t LEFT JOIN information_schema.columns c ON c.table_schema=t.table_schema AND c.table_name=t.table_name AND c.column_name IN ('created_by','created_at','updated_by','updated_at') WHERE t.table_schema=DATABASE() AND t.table_type='BASE TABLE' GROUP BY t.table_name HAVING COUNT(c.column_name)<>4",String.class)).isEmpty();
  }
 }
}
