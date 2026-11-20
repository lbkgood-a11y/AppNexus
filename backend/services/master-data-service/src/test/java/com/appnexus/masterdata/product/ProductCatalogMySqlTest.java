package com.appnexus.masterdata.product;
import com.appnexus.masterdata.application.product.*;
import com.appnexus.masterdata.adapter.persistence.*;
import com.appnexus.masterdata.adapter.transaction.*;
import com.appnexus.masterdata.adapter.codec.*;
import com.appnexus.masterdata.adapter.integration.product.ProductApprovalDeclaration;
import com.appnexus.masterdata.application.product.ProductStructureService;
import com.appnexus.masterdata.application.product.ProductCatalogService;
import com.appnexus.masterdata.application.product.ProductAttributeService;
import com.appnexus.masterdata.application.product.ProductImageService;
import com.appnexus.masterdata.application.product.ProductTemplateService;
import com.appnexus.masterdata.application.product.ProductService;
import com.appnexus.masterdata.bootstrap.MasterDataConfiguration;

import static org.assertj.core.api.Assertions.*;
import com.appnexus.masterdata.api.masterdata.MasterData.Rejection;
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
import static com.appnexus.masterdata.api.product.ProductCatalogData.*;

/** 隔离MySQL验证商品DDL与真实领域事务；不连接开发库，不以mock代替数据库约束。 */
@Tag("container")
class ProductCatalogMySqlTest {
 @Test void schemaAndDomainProtectTenancyVersionsAndPublishedConfiguration() throws Exception {
  try(var mysql=new MySQLContainer("mysql:8.4")){
   mysql.start();
   var source=new DriverManagerDataSource(mysql.getJdbcUrl()+"?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true",mysql.getUsername(),mysql.getPassword());
   var jdbc=new JdbcTemplate(source);
   for(String name:List.of("V97__product_catalog_foundation.sql","V98__product_attribute_templates.sql","V99__product_attribute_templates.sql","V100__product_category_template_binding.sql","V101__product_v1_master.sql","V103__product_value_integrity.sql","V106__product_structure_requests.sql","V108__product_legacy_preservation.sql","V110__product_custom_choices.sql","V112__product_integrity_hardening.sql","V113__product_unit_conversion.sql","V114__product_images.sql","V116__product_specifications_components.sql","V117__product_approval.sql","V119__product_custom_specification_values.sql","V132__product_organization_ownership.sql")){
    // 权限/菜单真实迁移由组合应用验证；此测试只执行同文件完整DDL，不伪造业务约束。
    String sql=new String(new ClassPathResource("db/migration/"+name).getInputStream().readAllBytes(),StandardCharsets.UTF_8).split("INSERT INTO access_permission")[0];
    try(var conn=source.getConnection()){ScriptUtils.executeSqlScript(conn,new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));}
   }
   try(var migrationConnection=source.getConnection()){new db.migration.V115__product_extended_modes().migrate(new org.flywaydb.core.api.migration.Context(){public org.flywaydb.core.api.configuration.Configuration getConfiguration(){return null;}public java.sql.Connection getConnection(){return migrationConnection;}});}
   // 复用历史DDL中md_product及审计补列的原始语句，不伪造旧字段类型。
   String oldSql=new String(new ClassPathResource("db/migration/V75__master_data.sql").getInputStream().readAllBytes(),StandardCharsets.UTF_8);
   oldSql=oldSql.substring(oldSql.indexOf("CREATE TABLE md_product ("));oldSql=oldSql.substring(0,oldSql.indexOf(";")+1);
   String oldAudit=new String(new ClassPathResource("db/migration/V83__global_record_audit.sql").getInputStream().readAllBytes(),StandardCharsets.UTF_8);
   String quote=String.valueOf((char)96);
   oldAudit=oldAudit.substring(oldAudit.indexOf("ALTER TABLE "+quote+"md_product"+quote));oldAudit=oldAudit.substring(0,oldAudit.indexOf(";")+1);
   try(var conn=source.getConnection()){ScriptUtils.executeSqlScript(conn,new ByteArrayResource((oldSql+oldAudit).getBytes(StandardCharsets.UTF_8)));}
   var c=new VerifiedTenantContext(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1);
   var other=new VerifiedTenantContext(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1);
   AccessAuthorization allow=(subject,permission)->true;
   var manager=new DataSourceTransactionManager(source);
   var mapperFactory=new org.mybatis.spring.SqlSessionFactoryBean();
   mapperFactory.setDataSource(source);
   var mapperConfig=new org.apache.ibatis.session.Configuration();
      mapperConfig.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);
      mapperConfig.setCacheEnabled(false);
      mapperConfig.setCallSettersOnNulls(true);
   mapperConfig.addMapper(com.appnexus.masterdata.adapter.persistence.UnitConversionMapper.class);
   mapperConfig.addMapper(com.appnexus.masterdata.adapter.persistence.ProductRecordMapper.class);
   mapperConfig.addMapper(com.appnexus.masterdata.adapter.persistence.ProductCompletenessMapper.class);
   mapperConfig.addMapper(com.appnexus.masterdata.adapter.persistence.ProductSnapshotMapper.class);
   mapperConfig.addMapper(com.appnexus.masterdata.adapter.persistence.ProductHistoryMapper.class);
   mapperConfig.addMapper(com.appnexus.masterdata.adapter.persistence.ProductListMapper.class);
   mapperConfig.addMapper(com.appnexus.masterdata.adapter.persistence.ProductCatalogReadMapper.class);
   for(Class<?> type:List.of(ProductCatalogMapper.class,ProductAttributeMapper.class,ProductTemplateMapper.class,ProductImageMapper.class,ProductStructureMapper.class,ProductMainMapper.class,ProductAttributeValueMapper.class,ProductAccessMapper.class,ProductApprovalMapper.class,com.appnexus.platform.persistence.businessobject.ObjectRecordMapper.class))mapperConfig.addMapper(type);
   mapperFactory.setConfiguration(mapperConfig);
   mapperFactory.setMapperLocations(new org.springframework.core.io.support.PathMatchingResourcePatternResolver().getResources("classpath*:com/appnexus/masterdata/adapter/persistence/*Mapper.xml"));
   var sql=new org.mybatis.spring.SqlSessionTemplate(mapperFactory.getObject());
   var catalogRepository=new MyBatisProductCatalogRepository(sql.getMapper(ProductCatalogMapper.class));

   var service=new ProductCatalogService(allow,new SpringProductCatalogTransaction(manager),catalogRepository);
   var unit=service.createUnit(c,new UnitInput("KG","千克","MASS",3,0));
   assertThat(unit.audit().createdBy()).isEqualTo(c.accountId().toString());
   assertThatThrownBy(()->service.updateUnit(other,unit.id(),new UnitInput("KG","伪造","MASS",3,0))).isInstanceOf(Rejection.class);
   assertThatThrownBy(()->jdbc.update("UPDATE md_product_unit SET precision_scale=13 WHERE tenant_id=UUID_TO_BIN(?)",c.tenantId().toString())).isInstanceOf(org.springframework.dao.DataAccessException.class);
   // 精确直连换算必须真实落库：跨租户、跨维度、重复关系、反向系数和CAS反向验收。
   var gram=service.createUnit(c,new UnitInput("G","克","MASS",3,0));
   var kilogram=unit;
   var hour=service.createUnit(c,new UnitInput("HOUR","小时","TIME",2,0));
   var conversionMapper=new org.mybatis.spring.SqlSessionTemplate(mapperFactory.getObject()).getMapper(com.appnexus.masterdata.adapter.persistence.UnitConversionMapper.class);
   var conversion=com.appnexus.masterdata.bootstrap.MasterDataConfiguration.conversions(conversionMapper,allow,new DataSourceTransactionManager(source));
   @SuppressWarnings("unchecked") var converted=(Map<String,String>)conversion.create(c,new com.appnexus.masterdata.api.product.UnitConversions.Input(gram.id(),kilogram.id(),"0.001"));
   assertThat(conversion.calculate(c,converted.get("id"),"1234.5").toString()).contains("1.2345","1.235");
   assertThatThrownBy(()->conversion.create(c,new com.appnexus.masterdata.api.product.UnitConversions.Input(gram.id(),hour.id(),"1"))).isInstanceOf(Rejection.class);
   assertThatThrownBy(()->conversion.create(c,new com.appnexus.masterdata.api.product.UnitConversions.Input(gram.id(),kilogram.id(),"2"))).isInstanceOf(Rejection.class);
   assertThatThrownBy(()->conversion.create(c,new com.appnexus.masterdata.api.product.UnitConversions.Input(kilogram.id(),gram.id(),"999"))).isInstanceOf(Rejection.class);
   assertThatThrownBy(()->conversion.calculate(other,converted.get("id"),"1")).isInstanceOf(Rejection.class);
   assertThatThrownBy(()->conversion.create(c,new com.appnexus.masterdata.api.product.UnitConversions.Input(gram.id(),kilogram.id(),"0.0000000000001"))).isInstanceOf(Rejection.class);
   conversion.status(c,converted.get("id"),new com.appnexus.masterdata.api.product.UnitConversions.Status(false,0L));
   var conversionAudit=jdbc.queryForMap("SELECT BIN_TO_UUID(created_by) createdBy,created_at,updated_at,version,enabled FROM md_product_unit_conversion WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",c.tenantId().toString(),converted.get("id"));
   assertThat(conversionAudit.get("createdBy").toString()).isEqualToIgnoringCase(c.accountId().toString());
   assertThatThrownBy(()->conversion.calculate(c,converted.get("id"),"1")).isInstanceOf(Rejection.class);
   assertThatThrownBy(()->conversion.status(c,converted.get("id"),new com.appnexus.masterdata.api.product.UnitConversions.Status(true,0L))).isInstanceOf(Rejection.class);
   // 乐观锁失败必须回滚状态、版本及修改时间，不能因已取得单位锁而推进记录审计。
   assertThat(jdbc.queryForMap("SELECT BIN_TO_UUID(created_by) createdBy,created_at,updated_at,version,enabled FROM md_product_unit_conversion WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",c.tenantId().toString(),converted.get("id"))).isEqualTo(conversionAudit);
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
   var denied=new ProductCatalogService((subject,permission)->false,new SpringProductCatalogTransaction(manager),catalogRepository);
   assertThatThrownBy(()->denied.createCategory(c,new CategoryInput("DENIED","拒绝",null,0))).hasMessage("MASTER_DATA_DENIED");
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM md_product_category WHERE code='DENIED'",Integer.class)).isZero();
   var attributes=new ProductAttributeService(allow,new SpringProductAttributeTransaction(manager),new MyBatisProductAttributeRepository(sql.getMapper(ProductAttributeMapper.class)),new JacksonAttributeRuleContent());
   var attributeId=attributes.createAttribute(c,new com.appnexus.masterdata.api.product.ProductAttributeData.AttributeInput("material",null,0)).get("id");
   var revisionId=attributes.createRevision(c,attributeId,new com.appnexus.masterdata.api.product.ProductAttributeData.RevisionInput("材质","SINGLE_SELECT",null,null,"{}",0)).get("id");
   attributes.createOption(c,revisionId,new com.appnexus.masterdata.api.product.ProductAttributeData.OptionInput("STEEL","钢",0,1));
   attributes.publishRevision(c,revisionId,2);
   assertThatThrownBy(()->attributes.createOption(c,revisionId,new com.appnexus.masterdata.api.product.ProductAttributeData.OptionInput("WOOD","木",1,3))).isInstanceOf(Rejection.class);
   assertThatThrownBy(()->attributes.createAttribute(c,new com.appnexus.masterdata.api.product.ProductAttributeData.AttributeInput("brand",null,0))).isInstanceOf(Rejection.class);
   var templates=new ProductTemplateService(allow,new com.appnexus.masterdata.adapter.transaction.SpringProductTemplateTransaction(manager),new com.appnexus.masterdata.adapter.persistence.MyBatisProductTemplateRepository(new org.mybatis.spring.SqlSessionTemplate(mapperFactory.getObject()).getMapper(com.appnexus.masterdata.adapter.persistence.ProductTemplateMapper.class)));
   var templateId=templates.create(c,new com.appnexus.masterdata.api.product.ProductTemplateData.TemplateInput("BASIC","基础","BASIC",0)).get("id");
   var templateVersionId=templates.createVersion(c,templateId,new com.appnexus.masterdata.api.product.ProductTemplateData.VersionInput("",List.of(new com.appnexus.masterdata.api.product.ProductTemplateData.ItemInput(attributeId,revisionId,null,null,true,true,true,0)),0)).get("id");
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
   UUID organization=UUID.randomUUID();
   var products=productService(sql,manager,allow,proofs,organization);
   // 图片真实解码重编码、父CAS和历史同事务；跨租户、非法内容和旧版本均拒绝。
   var imageProduct=products.create(c,new com.appnexus.masterdata.api.product.ProductMainData.Input("IMAGE","图片测试",null,null,null,null,null,null,null,null,null,"",organization.toString()));
   String imageProductId=(String)imageProduct.get("id");
   var images=new ProductImageService(allow,new com.appnexus.masterdata.adapter.transaction.SpringRecordTransaction(manager),products,new com.appnexus.masterdata.adapter.persistence.MyBatisProductImageRepository(new org.mybatis.spring.SqlSessionTemplate(mapperFactory.getObject()).getMapper(com.appnexus.masterdata.adapter.persistence.ProductImageMapper.class)),new com.appnexus.masterdata.adapter.media.ImageIoProductImageContent());
   var png=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",png);
   @SuppressWarnings("unchecked") var uploaded=(Map<String,Object>)images.upload(c,imageProductId,0,png.toByteArray());
   assertThat(images.content(c,imageProductId,(String)uploaded.get("id")).bytes()).isNotEmpty();
   assertThatThrownBy(()->images.content(other,imageProductId,(String)uploaded.get("id"))).isInstanceOf(Rejection.class);
   assertThatThrownBy(()->images.upload(c,imageProductId,1,"not an image".getBytes(StandardCharsets.UTF_8))).isInstanceOf(Rejection.class);
   assertThatThrownBy(()->images.detach(c,imageProductId,(String)uploaded.get("id"),0)).isInstanceOf(Rejection.class);
   images.detach(c,imageProductId,(String)uploaded.get("id"),1);
   assertThatThrownBy(()->images.content(c,imageProductId,(String)uploaded.get("id"))).isInstanceOf(Rejection.class);
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM md_product_v1_change WHERE tenant_id=UUID_TO_BIN(?) AND product_id=UUID_TO_BIN(?)",Integer.class,c.tenantId().toString(),imageProductId)).isEqualTo(3);
   // 自定义规格不会自动生成SKU；SKU归属不可改且退出规格保留身份。
   var structural=products.create(c,new com.appnexus.masterdata.api.product.ProductMainData.Input("STRUCTURE","结构测试",null,parent.id(),config.id(),"SERVICE",null,null,null,null,null,"",organization.toString()));String structuralId=(String)structural.get("id");
   var move=new com.appnexus.masterdata.api.product.ProductStructureData.Migration(0L,0L,config.id(),parent.id(),config.id(),"SERVICE",null,List.of(),List.of(),"采用自定义规格",null,"CUSTOM",List.of());
   var movePreview=products.previewStructure(c,structuralId,move);products.migrateStructure(c,structuralId,new com.appnexus.masterdata.api.product.ProductStructureData.MigrationCommand(move,(String)movePreview.get("previewToken")),UUID.randomUUID().toString());
   var structures=new ProductStructureService(allow,new SpringProductStructureTransaction(manager),products,new MyBatisProductStructureRepository(sql.getMapper(ProductStructureMapper.class)),new JacksonAttributeRuleContent());
   @SuppressWarnings("unchecked") var spec=(Map<String,String>)structures.createSpecification(c,structuralId,new com.appnexus.masterdata.api.product.ProductStructureCommands.Specification("按图加工","图纸A-01",List.of(),1L));
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM md_product_sku WHERE tenant_id=UUID_TO_BIN(?) AND product_id=UUID_TO_BIN(?)",Integer.class,c.tenantId().toString(),structuralId)).isZero();
   structures.createSku(c,structuralId,new com.appnexus.masterdata.api.product.ProductStructureCommands.Sku(spec.get("id"),"STRUCTURE-SKU",2L));
   assertThatThrownBy(()->structures.createSku(c,structuralId,new com.appnexus.masterdata.api.product.ProductStructureCommands.Sku(spec.get("id"),"STRUCTURE-SKU",3L))).isInstanceOf(Rejection.class);
   assertThatThrownBy(()->structures.read(other,structuralId)).isInstanceOf(Rejection.class);
   var retire=new com.appnexus.masterdata.api.product.ProductStructureData.Migration(3L,1L,config.id(),parent.id(),config.id(),"SERVICE",null,List.of(),List.of(),"显式退出规格",null,"NONE",List.of(spec.get("id")));
   var retirePreview=products.previewStructure(c,structuralId,retire);products.migrateStructure(c,structuralId,new com.appnexus.masterdata.api.product.ProductStructureData.MigrationCommand(retire,(String)retirePreview.get("previewToken")),UUID.randomUUID().toString());
   assertThat(jdbc.queryForObject("SELECT retired_at IS NOT NULL FROM md_product_specification WHERE id=UUID_TO_BIN(?)",Boolean.class,spec.get("id"))).isTrue();
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM md_product_sku WHERE code='STRUCTURE-SKU'",Integer.class)).isEqualTo(1);
   // 组合数量、父子归属和自引用保护；组成不触发自动库存拆装。
   var componentProduct=products.create(c,new com.appnexus.masterdata.api.product.ProductMainData.Input("PART","组成品",null,parent.id(),config.id(),"PHYSICAL",unit.id(),null,null,null,null,"",organization.toString()));String componentId=(String)componentProduct.get("id");products.updateStatus(c,componentId,new com.appnexus.masterdata.api.product.ProductCommandData.Status("ENABLED",0));
   var composite=products.create(c,new com.appnexus.masterdata.api.product.ProductMainData.Input("KIT","组合品",null,parent.id(),config.id(),"COMPOSITE",unit.id(),null,null,null,null,"",organization.toString()));String compositeId=(String)composite.get("id");
   assertThatThrownBy(()->products.updateStatus(c,compositeId,new com.appnexus.masterdata.api.product.ProductCommandData.Status("ENABLED",0))).hasMessage("PRODUCT_COMPLETION_REQUIRED");
   structures.addComponent(c,compositeId,new com.appnexus.masterdata.api.product.ProductStructureCommands.Component(componentId,"2.000",1L,0L));
   assertThatThrownBy(()->structures.addComponent(c,compositeId,new com.appnexus.masterdata.api.product.ProductStructureCommands.Component(compositeId,"1",1L,1L))).isInstanceOf(Rejection.class);
   products.updateStatus(c,compositeId,new com.appnexus.masterdata.api.product.ProductCommandData.Status("ENABLED",1));
   // 审批适配器契约：用测试端口模拟可靠终态，数据库、快照冻结和幂等仍为真实MySQL事务；不冒充真实引擎全流程。
   var approvalProduct=products.create(c,new com.appnexus.masterdata.api.product.ProductMainData.Input("APPROVAL","待审核",null,parent.id(),config.id(),"SERVICE",null,null,null,null,null,"",organization.toString()));String approvalProductId=(String)approvalProduct.get("id"),approvalOrg=UUID.randomUUID().toString();
   var workflowPlatform=org.mockito.Mockito.mock(com.appnexus.contracts.workflow.WorkflowPlatform.class);org.mockito.Mockito.when(workflowPlatform.active(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenReturn(true);org.mockito.Mockito.when(workflowPlatform.allowed(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString())).thenReturn(true);org.mockito.Mockito.when(workflowPlatform.documentOrganizations(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(new com.appnexus.contracts.workflow.WorkflowPlatform.Option(approvalOrg,"测试组织")));
   org.mockito.Mockito.when(workflowPlatform.projectData(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyMap())).thenAnswer(call->call.getArgument(2));
   var starterPort=org.mockito.Mockito.mock(com.appnexus.contracts.workflow.WorkflowStarter.class);org.mockito.Mockito.when(starterPort.startBound(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenReturn("test-instance");
   var taskPort=org.mockito.Mockito.mock(com.appnexus.contracts.workflow.WorkflowTaskAccess.class);
   var bindingPort=org.mockito.Mockito.mock(com.appnexus.contracts.businessobject.BusinessObjectBindings.class);org.mockito.Mockito.when(bindingPort.resolveOperation(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyMap())).thenAnswer(invocation->new com.appnexus.contracts.businessobject.BusinessObjects.Resolution("test-resolution",c.tenantId().toString(),"ProductApproval",invocation.getArgument(3),invocation.getArgument(4),1,"test-definition",1,invocation.getArgument(5),"submit","WORKFLOW","","product.approval"));
   var executionFactory=new org.springframework.beans.factory.support.StaticListableBeanFactory();var taskFactory=new org.springframework.beans.factory.support.StaticListableBeanFactory();taskFactory.addBean("tasks",taskPort);
   var approvalDeclaration=new ProductApprovalDeclaration();
   approvalDeclaration.runtimeFactory(new com.appnexus.platform.persistence.businessobject.MappedObjectRuntimeFactory(sql.getMapper(com.appnexus.platform.persistence.businessobject.ObjectRecordMapper.class)));
   var approvals=new ProductApprovalService(new SpringRecordTransaction(manager),products,products,workflowPlatform,()->executionFactory.getBean(com.appnexus.contracts.businessobject.BusinessObjectExecution.class),()->taskPort,new MyBatisProductApprovalRepository(sql.getMapper(ProductApprovalMapper.class)),new JacksonProductContent());

   executionFactory.addBean("execution",com.appnexus.platform.businessobject.BusinessObjectUseCases.execution(List.of(approvalDeclaration),bindingPort,new com.appnexus.platform.persistence.businessobject.configuration.SpringConfigurationTransaction(manager),new com.appnexus.contracts.businessobject.BusinessObjectPlatform(){
     public boolean platformAdministrator(String actor){return false;}
     public boolean tenantAdministrator(com.appnexus.contracts.businessobject.BusinessObjects.Context context){return false;}
     public boolean tenantExists(String tenant){return true;}
     public boolean active(com.appnexus.contracts.businessobject.BusinessObjects.Context context){return workflowPlatform.active(context.tenantId(),context.actorId());}
     public boolean operationAllowed(com.appnexus.contracts.businessobject.BusinessObjects.Context context,String operation,String permission){return workflowPlatform.allowed(new com.appnexus.contracts.workflow.WorkflowContext(context.tenantId(),context.actorId(),context.requestId()),permission);}
     public com.appnexus.contracts.businessobject.BusinessObjectRuntime.Snapshot authorizeRecord(com.appnexus.contracts.businessobject.BusinessObjects.Context context,com.appnexus.contracts.businessobject.BusinessObjects.Descriptor descriptor,String operation,com.appnexus.contracts.businessobject.BusinessObjectRuntime.Snapshot snapshot){return snapshot;}
   },()->starterPort,()->null));
   var approvalBridge=new com.appnexus.workflow.adapter.flowable.ObjectWorkflowAdapter(approvalDeclaration,approvalDeclaration.descriptor().operations().getFirst(),workflowPlatform,()->taskPort,manager);
   var workflowContext=new com.appnexus.contracts.workflow.WorkflowContext(c.tenantId().toString(),c.accountId().toString(),UUID.randomUUID().toString());
   @SuppressWarnings("unchecked") var submitted=(Map<String,Object>)approvals.submit(c,workflowContext,approvalProductId,0,approvalOrg);String requestId=(String)submitted.get("id");
   assertThat(approvals.submit(c,workflowContext,approvalProductId,0,approvalOrg)).isEqualTo(submitted);
   assertThatThrownBy(()->products.update(c,approvalProductId,new com.appnexus.masterdata.api.product.ProductMainData.Update("修改冻结正文",null,null,null,null,List.of(),1))).hasMessage("PRODUCT_APPROVAL_IN_PROGRESS");
   var badResult=new com.appnexus.contracts.workflow.WorkflowBusiness.Invocation("delivery",workflowContext,"ProductApproval",requestId,approvalOrg,1,"EXECUTE","delivery",new com.appnexus.contracts.workflow.WorkflowBusiness.Reference("wrong-instance","test-definition",1,null,"APPROVAL","APPROVED"),null,Map.of());
   assertThatThrownBy(()->approvalBridge.execute(badResult)).isInstanceOf(SecurityException.class);
   var goodResult=new com.appnexus.contracts.workflow.WorkflowBusiness.Invocation("delivery",workflowContext,"ProductApproval",requestId,approvalOrg,1,"EXECUTE","delivery",new com.appnexus.contracts.workflow.WorkflowBusiness.Reference("test-instance","test-definition",1,null,"APPROVAL","APPROVED"),null,Map.of());
   approvalBridge.execute(goodResult);approvalBridge.execute(goodResult);
   assertThat(products.read(c,approvalProductId).get("status")).isEqualTo("DRAFT");
   products.updateStatus(c,approvalProductId,new com.appnexus.masterdata.api.product.ProductCommandData.Status("ENABLED",1));
   // CUSTOM按固定SPEC修订保存七类型及自定义枚举；缺必填、异修订、类型冲突和精度失败整单回滚。
   var customCategory=service.createCategory(c,new CategoryInput("CUSTOM-STRUCT","自定义结构",null,0));
   var customItems=new ArrayList<com.appnexus.masterdata.api.product.ProductTemplateData.ItemInput>();var customAttributes=new LinkedHashMap<String,String>();
   for(String type:List.of("TEXT","INTEGER","DECIMAL","BOOLEAN","DATE","SINGLE_SELECT","MULTI_SELECT")){
    String aid=attributes.createAttribute(c,new com.appnexus.masterdata.api.product.ProductAttributeData.AttributeInput("custom-"+type,null,0)).get("id");
    String rid=attributes.createRevision(c,aid,new com.appnexus.masterdata.api.product.ProductAttributeData.RevisionInput(type,type,type.equals("DECIMAL")?unit.id():null,type.equals("INTEGER")?Integer.valueOf(0):type.equals("DECIMAL")?Integer.valueOf(2):null,type.equals("TEXT")?"{\"maxLength\":20}":"{}",0,type.endsWith("SELECT")?List.of(new com.appnexus.masterdata.api.product.ProductAttributeData.OptionSeed("A","选项A"),new com.appnexus.masterdata.api.product.ProductAttributeData.OptionSeed("B","选项B")):List.of())).get("id");attributes.publishRevision(c,rid,1);customAttributes.put(type,rid);customItems.add(new com.appnexus.masterdata.api.product.ProductTemplateData.ItemInput(aid,rid,null,null,true,false,type.endsWith("SELECT"),customItems.size()));
   }
   String customTemplate=templates.create(c,new com.appnexus.masterdata.api.product.ProductTemplateData.TemplateInput("CUSTOM-STRUCT","自定义结构模板","SPEC",0)).get("id");String customTemplateVersion=templates.createVersion(c,customTemplate,new com.appnexus.masterdata.api.product.ProductTemplateData.VersionInput("七类型",customItems,0)).get("id");templates.publish(c,customTemplateVersion,1);
   var customSlots=new LinkedHashMap<String,String>();for(String purpose:List.of("BASIC","SPEC","QUALITY","EXTENSION"))customSlots.put(purpose,null);customSlots.put("SPEC",customTemplateVersion);var customConfig=service.publish(c,customCategory.id(),new Publish(customSlots,false,0));
   String customStructuredId=(String)products.create(c,new com.appnexus.masterdata.api.product.ProductMainData.Input("CUSTOM-STRUCT","结构化非标品",null,customCategory.id(),customConfig.id(),"SERVICE",null,null,null,null,null,"",organization.toString())).get("id");
   var customMove=new com.appnexus.masterdata.api.product.ProductStructureData.Migration(0L,0L,customConfig.id(),customCategory.id(),customConfig.id(),"SERVICE",null,List.of(),List.of(),"自定义结构化规格",null,"CUSTOM",List.of());var customPreview=products.previewStructure(c,customStructuredId,customMove);products.migrateStructure(c,customStructuredId,new com.appnexus.masterdata.api.product.ProductStructureData.MigrationCommand(customMove,(String)customPreview.get("previewToken")),UUID.randomUUID().toString());
   var customValues=new ArrayList<com.appnexus.masterdata.api.product.ProductStructureCommands.SpecValue>();
   for(var entry:customAttributes.entrySet()){String type=entry.getKey(),rid=entry.getValue(),item=jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM md_product_template_item WHERE template_version_id=UUID_TO_BIN(?) AND attribute_revision_id=UUID_TO_BIN(?)",String.class,customTemplateVersion,rid);String raw=switch(type){case "TEXT"->"按图加工";case "INTEGER"->"0";case "DECIMAL"->"12.30";case "BOOLEAN"->"false";case "DATE"->"2026-11-18";default->null;};List<String> optionIds=type.endsWith("SELECT")?List.of(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM md_product_attribute_option WHERE revision_id=UUID_TO_BIN(?) AND option_code='A'",String.class,rid)):List.of();customValues.add(new com.appnexus.masterdata.api.product.ProductStructureCommands.SpecValue(item,raw,optionIds,type.equals("MULTI_SELECT")?List.of("客户定制"):List.of()));}
   assertThatThrownBy(()->structures.createSpecification(c,customStructuredId,new com.appnexus.masterdata.api.product.ProductStructureCommands.Specification("缺必填","说明",customValues.subList(1,customValues.size()),1L))).isInstanceOf(Rejection.class);
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM md_product_specification WHERE product_id=UUID_TO_BIN(?)",Integer.class,customStructuredId)).isZero();
   @SuppressWarnings("unchecked") var structured=(Map<String,String>)structures.createSpecification(c,customStructuredId,new com.appnexus.masterdata.api.product.ProductStructureCommands.Specification("七类型非标规格",null,customValues,1L));
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM md_product_spec_value WHERE specification_id=UUID_TO_BIN(?)",Integer.class,structured.get("id"))).isEqualTo(7);
   assertThat(structures.read(c,customStructuredId).toString()).contains("按图加工","客户定制","2026-11-18","选项A");
   assertThat(products.read(c,customStructuredId).toString()).contains("specificationValues","客户定制");
   var mixed=new ArrayList<>(customValues);var numeric=customValues.get(2);mixed.set(2,new com.appnexus.masterdata.api.product.ProductStructureCommands.SpecValue(numeric.templateItemId(),"12.345"));
   assertThatThrownBy(()->structures.createSpecification(c,customStructuredId,new com.appnexus.masterdata.api.product.ProductStructureCommands.Specification("超精度",null,mixed,2L))).isInstanceOf(Rejection.class);
   var wrongOption=new ArrayList<>(customValues);var single=customValues.get(5);wrongOption.set(5,new com.appnexus.masterdata.api.product.ProductStructureCommands.SpecValue(single.templateItemId(),null,customValues.get(6).optionIds(),List.of()));assertThatThrownBy(()->structures.createSpecification(c,customStructuredId,new com.appnexus.masterdata.api.product.ProductStructureCommands.Specification("异修订选项",null,wrongOption,2L))).isInstanceOf(Rejection.class);
   assertThatThrownBy(()->jdbc.update("UPDATE md_product_spec_value SET text_value='冲突' WHERE specification_id=UUID_TO_BIN(?) AND value_type='BOOLEAN'",structured.get("id"))).isInstanceOf(org.springframework.dao.DataAccessException.class);
   assertThat(((Number)products.read(c,customStructuredId).get("version")).longValue()).isEqualTo(2);
   // 标准SPEC模板固定修订，标准组合重复拒绝且不自动建立SKU。
   var specAttribute=attributes.createAttribute(c,new com.appnexus.masterdata.api.product.ProductAttributeData.AttributeInput("diameter",null,0)).get("id");
   var specRevision=attributes.createRevision(c,specAttribute,new com.appnexus.masterdata.api.product.ProductAttributeData.RevisionInput("直径","DECIMAL",unit.id(),2,"{}",0)).get("id");attributes.publishRevision(c,specRevision,1);
   var specTemplate=templates.create(c,new com.appnexus.masterdata.api.product.ProductTemplateData.TemplateInput("SPEC","规格","SPEC",0)).get("id");
   var specVersion=templates.createVersion(c,specTemplate,new com.appnexus.masterdata.api.product.ProductTemplateData.VersionInput("规格1",List.of(new com.appnexus.masterdata.api.product.ProductTemplateData.ItemInput(specAttribute,specRevision,null,null,true,false,false,0)),0)).get("id");templates.publish(c,specVersion,1);
   var specSlots=new LinkedHashMap<String,String>();for(String purpose:List.of("BASIC","SPEC","QUALITY","EXTENSION"))specSlots.put(purpose,null);specSlots.put("SPEC",specVersion);
   var specConfig=service.publish(c,parent.id(),new Publish(specSlots,false,3));
   String specItem=jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM md_product_template_item WHERE template_version_id=UUID_TO_BIN(?)",String.class,specVersion);
   var standard=products.create(c,new com.appnexus.masterdata.api.product.ProductMainData.Input("STANDARD","标准品",null,parent.id(),specConfig.id(),"SERVICE",null,null,null,null,null,"",organization.toString()));String standardId=(String)standard.get("id");
   var standardMove=new com.appnexus.masterdata.api.product.ProductStructureData.Migration(0L,0L,specConfig.id(),parent.id(),specConfig.id(),"SERVICE",null,List.of(),List.of(),"开启标准规格",null,"STANDARD",List.of());var standardPreview=products.previewStructure(c,standardId,standardMove);products.migrateStructure(c,standardId,new com.appnexus.masterdata.api.product.ProductStructureData.MigrationCommand(standardMove,(String)standardPreview.get("previewToken")),UUID.randomUUID().toString());
   structures.createSpecification(c,standardId,new com.appnexus.masterdata.api.product.ProductStructureCommands.Specification("直径12","",List.of(new com.appnexus.masterdata.api.product.ProductStructureCommands.SpecValue(specItem,"12.00")),1L));
   assertThatThrownBy(()->structures.createSpecification(c,standardId,new com.appnexus.masterdata.api.product.ProductStructureCommands.Specification("重复","",List.of(new com.appnexus.masterdata.api.product.ProductStructureCommands.SpecValue(specItem,"12")),2L))).isInstanceOf(Rejection.class);
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM md_product_sku WHERE product_id=UUID_TO_BIN(?)",Integer.class,standardId)).isZero();
   var draft=products.create(c,new com.appnexus.masterdata.api.product.ProductMainData.Input("P1","草稿",null,null,null,null,null,null,null,null,null,"",organization.toString()));
   assertThat(draft.get("structureState")).isEqualTo("INCOMPLETE");
   assertThat(draft.get("createdBy")).isEqualTo(c.accountId().toString());
   assertThat(draft.get("updatedBy")).isEqualTo(c.accountId().toString());
   String draftId=(String)draft.get("id");
   assertThatThrownBy(()->products.updateStatus(c,draftId,new com.appnexus.masterdata.api.product.ProductCommandData.Status("ENABLED",(Long)null))).isInstanceOf(Rejection.class);
   assertThatThrownBy(()->products.archive(c,draftId,new com.appnexus.masterdata.api.product.ProductCommandData.Version((Long)null),true)).isInstanceOf(Rejection.class);
   assertThatThrownBy(()->products.updateStatus(c,draftId,new com.appnexus.masterdata.api.product.ProductCommandData.Status("ENABLED",0))).hasMessage("PRODUCT_COMPLETION_REQUIRED");
   products.archive(c,draftId,new com.appnexus.masterdata.api.product.ProductCommandData.Version(0),true);
   assertThatThrownBy(()->products.updateStatus(c,draftId,new com.appnexus.masterdata.api.product.ProductCommandData.Status("ENABLED",1))).isInstanceOf(Rejection.class);
   products.archive(c,draftId,new com.appnexus.masterdata.api.product.ProductCommandData.Version(1),false);
   assertThatThrownBy(()->products.read(other,draftId)).isInstanceOf(Rejection.class);
   String itemId=jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM md_product_template_item WHERE template_version_id=UUID_TO_BIN(?)",String.class,templateVersionId);
   String optionId=jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM md_product_attribute_option WHERE revision_id=UUID_TO_BIN(?)",String.class,revisionId);
   var complete=products.create(c,new com.appnexus.masterdata.api.product.ProductMainData.Input("P2","服务",null,parent.id(),bound.id(),"SERVICE",null,null,null,null,List.of(new com.appnexus.masterdata.api.product.ProductMainData.Value(itemId,null,List.of(optionId))),"",organization.toString()));
   assertThat(complete.get("structureState")).isEqualTo("COMPLETE");
   String productId=(String)complete.get("id");products.updateStatus(c,productId,new com.appnexus.masterdata.api.product.ProductCommandData.Status("ENABLED",0));
   var jsonFilters=new tools.jackson.databind.ObjectMapper();
   String filter=jsonFilters.writeValueAsString(List.of(new com.appnexus.masterdata.api.product.ProductListFilter.AttributeFilter(itemId,"eq",List.of(optionId))));
   var filtered=(Map<?,?>)products.list(c,"","",null,null,null,false,1,20,bound.id(),filter);
   assertThat(((Number)filtered.get("total")).longValue()).isEqualTo(1);
   assertThatThrownBy(()->products.list(c,"","",null,null,null,false,1,20,config.id(),filter)).isInstanceOf(Rejection.class);
   assertThatThrownBy(()->products.list(c,"","",null,null,null,false,1,20,null,filter)).isInstanceOf(Rejection.class);
   String tooMany=jsonFilters.writeValueAsString(java.util.Collections.nCopies(4,new com.appnexus.masterdata.api.product.ProductListFilter.AttributeFilter(itemId,"eq",List.of(optionId))));
   assertThatThrownBy(()->products.list(c,"","",null,null,null,false,1,20,bound.id(),tooMany)).isInstanceOf(Rejection.class);
   var customProduct=products.create(c,new com.appnexus.masterdata.api.product.ProductMainData.Input("CUSTOM","自定义值",null,parent.id(),bound.id(),"SERVICE",null,null,null,null,List.of(new com.appnexus.masterdata.api.product.ProductMainData.Value(itemId,null,List.of(),List.of("特殊材质"))),"",organization.toString()));
   assertThat(customProduct.get("choices").toString()).contains("特殊材质");
   var previous=products.read(c,productId);
   assertThatThrownBy(()->products.update(c,productId,new com.appnexus.masterdata.api.product.ProductMainData.Update("不完整",null,null,null,null,List.of(),1))).hasMessage("PRODUCT_COMPLETION_REQUIRED");
   assertThat(products.read(c,productId)).isEqualTo(previous);
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM md_product_v1_change WHERE product_id=UUID_TO_BIN(?)",Integer.class,productId)).isEqualTo(2);
   assertThatThrownBy(()->products.updateStatus(c,productId,new com.appnexus.masterdata.api.product.ProductCommandData.Status("DISABLED",0))).isInstanceOf(Rejection.class);
   var valueAudit=jdbc.queryForMap("SELECT BIN_TO_UUID(id) id,created_by,created_at FROM md_product_v1_attribute_value WHERE product_id=UUID_TO_BIN(?)",productId);
   products.update(c,productId,new com.appnexus.masterdata.api.product.ProductMainData.Update("服务修正",null,null,null,null,List.of(new com.appnexus.masterdata.api.product.ProductMainData.Value(itemId,null,List.of(optionId))),1));
   assertThat(jdbc.queryForMap("SELECT BIN_TO_UUID(id) id,created_by,created_at FROM md_product_v1_attribute_value WHERE product_id=UUID_TO_BIN(?)",productId)).usingRecursiveComparison().isEqualTo(valueAudit);
   // 同一父version并发普通写只能成功一次；历史唯一版本和CAS不得被重放绕过。
   try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
    java.util.concurrent.Callable<Boolean> update=()->{try{products.update(c,productId,new com.appnexus.masterdata.api.product.ProductMainData.Update("并发",null,null,null,null,null,2));return true;}catch(Rejection e){return false;}};
    var results=executor.invokeAll(List.of(update,update));
    assertThat(results.stream().filter(f->{try{return f.get();}catch(Exception e){throw new RuntimeException(e);}}).count()).isEqualTo(1);
   }
   assertThat(((Number)products.read(c,productId).get("version")).longValue()).isEqualTo(3);
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM md_product_v1_change WHERE product_id=UUID_TO_BIN(?)",Integer.class,productId)).isEqualTo(4);
   assertThatThrownBy(()->jdbc.update("UPDATE md_product_v1_attribute_value SET integer_value=1 WHERE product_id=UUID_TO_BIN(?)",productId)).isInstanceOf(org.springframework.dao.DataAccessException.class);
   assertThatThrownBy(()->jdbc.update("UPDATE md_product_template_item SET attribute_id=UUID_TO_BIN(?) WHERE id=UUID_TO_BIN(?)",UUID.randomUUID().toString(),itemId)).isInstanceOf(org.springframework.dao.DataAccessException.class);

   products.updateStatus(c,productId,new com.appnexus.masterdata.api.product.ProductCommandData.Status("DISABLED",3));
   var pageResult=(Map<?,?>)products.list(c,"并发","P2",null,"SERVICE","DISABLED",false,1,1);
   assertThat(((Number)pageResult.get("total")).longValue()).isEqualTo(1);
   assertThat((List<?>)pageResult.get("rows")).hasSize(1);
   assertThat(products.configuration(c,bound.id()).get("templates")).isInstanceOf(Map.class);
   var migration=new com.appnexus.masterdata.api.product.ProductStructureData.Migration(4L,0L,bound.id(),parent.id(),config.id(),"SERVICE",null,List.of(),List.of(itemId),"显式退出原属性",null);
   validProof.set(false);
   assertThatThrownBy(()->products.migrateStructure(c,productId,new com.appnexus.masterdata.api.product.ProductStructureData.MigrationCommand(migration,"tampered"),UUID.randomUUID().toString())).hasMessage("PRODUCT_PREVIEW_CHANGED_OR_EXPIRED");
   assertThat(((Number)products.read(c,productId).get("version")).longValue()).isEqualTo(4);
   validProof.set(true);
   // 只读预检不得推进父审计、属性或追加历史；不仅检查没有幂等回执。
   var beforePreview=jdbc.queryForMap("SELECT version,structure_revision,updated_at FROM md_product_v1 WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",c.tenantId().toString(),productId);
   int historyBeforePreview=jdbc.queryForObject("SELECT COUNT(*) FROM md_product_v1_change WHERE tenant_id=UUID_TO_BIN(?) AND product_id=UUID_TO_BIN(?)",Integer.class,c.tenantId().toString(),productId);
   var preview=products.previewStructure(c,productId,migration);
   assertThat(jdbc.queryForMap("SELECT version,structure_revision,updated_at FROM md_product_v1 WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",c.tenantId().toString(),productId)).isEqualTo(beforePreview);
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM md_product_v1_change WHERE tenant_id=UUID_TO_BIN(?) AND product_id=UUID_TO_BIN(?)",Integer.class,c.tenantId().toString(),productId)).isEqualTo(historyBeforePreview);
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM md_product_structure_request WHERE product_id=UUID_TO_BIN(?)",Integer.class,productId)).isZero();
   String token=(String)preview.get("previewToken"),key=UUID.randomUUID().toString();
   var command=new com.appnexus.masterdata.api.product.ProductStructureData.MigrationCommand(migration,token);
   try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
    java.util.concurrent.Callable<Map<String,Object>> migrate=()->products.migrateStructure(c,productId,command,key);
    var results=executor.invokeAll(List.of(migrate,migrate));
    assertThat(results.stream().filter(f->{try{return Boolean.TRUE.equals(f.get().get("replayed"));}catch(Exception e){throw new RuntimeException(e);}}).count()).isEqualTo(1);
   }
   assertThat(((Number)products.read(c,productId).get("version")).longValue()).isEqualTo(5);
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM md_product_structure_request WHERE product_id=UUID_TO_BIN(?)",Integer.class,productId)).isEqualTo(1);
   validProof.set(false);
   assertThat(products.migrateStructure(c,productId,command,key).get("replayed")).isEqualTo(true);
   validProof.set(true);
   var changedPayload=new com.appnexus.masterdata.api.product.ProductStructureData.Migration(4L,0L,bound.id(),parent.id(),config.id(),"SERVICE",null,List.of(),List.of(itemId),"不同载荷",null);
   assertThatThrownBy(()->products.migrateStructure(c,productId,new com.appnexus.masterdata.api.product.ProductStructureData.MigrationCommand(changedPayload,token),key)).hasMessage("PRODUCT_IDEMPOTENCY_KEY_REUSED");
   assertThatThrownBy(()->products.previewStructure(c,productId,migration)).hasMessage("PRODUCT_SOURCE_VERSION_CHANGED");
   var next=new com.appnexus.masterdata.api.product.ProductStructureData.Migration(5L,1L,config.id(),parent.id(),bound.id(),"SERVICE",null,List.of(new com.appnexus.masterdata.api.product.ProductMainData.Value(itemId,null,List.of(optionId))),List.of(),"恢复质量属性",null);
   var nextPreview=products.previewStructure(c,productId,next);
   String nextKey=UUID.randomUUID().toString();
   var nextCommand=new com.appnexus.masterdata.api.product.ProductStructureData.MigrationCommand(next,(String)nextPreview.get("previewToken"));
   var beforeFailure=products.read(c,productId);
   jdbc.execute("ALTER TABLE md_product_v1_change ADD CONSTRAINT ck_test_history_failure CHECK(to_version<>6 OR event_type<>'STRUCTURE')");
   assertThatThrownBy(()->products.migrateStructure(c,productId,nextCommand,nextKey)).isInstanceOf(RuntimeException.class);
   assertThat(products.read(c,productId)).isEqualTo(beforeFailure);
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM md_product_structure_request WHERE product_id=UUID_TO_BIN(?)",Integer.class,productId)).isEqualTo(1);
   jdbc.execute("ALTER TABLE md_product_v1_change DROP CHECK ck_test_history_failure");
   products.migrateStructure(c,productId,nextCommand,nextKey);
   assertThat(((Number)products.read(c,productId).get("version")).longValue()).isEqualTo(6);
   jdbc.update("UPDATE md_product_structure_request SET expires_at=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP(6)) WHERE tenant_id=UUID_TO_BIN(?) AND request_key=UUID_TO_BIN(?)",c.tenantId().toString(),nextKey);
   assertThatThrownBy(()->products.migrateStructure(c,productId,nextCommand,nextKey)).hasMessage("PRODUCT_IDEMPOTENCY_WINDOW_EXPIRED");
   var noPermission=productService(sql,manager,(subject,permission)->false,proofs,organization);
   assertThatThrownBy(()->noPermission.migrateStructure(c,productId,command,key)).hasMessage("MASTER_DATA_DENIED");

   String legacyId=UUID.randomUUID().toString(),original="  旧规格\n直径 00.50  ";
   jdbc.update("INSERT INTO md_product(id,tenant_id,code,name,specification,unit,barcode,enabled,version) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'LEGACY','旧商品',?,'旧单位','0000123',TRUE,7)",legacyId,c.tenantId().toString(),original);
   var legacy=products.stageLegacy(c,legacyId);
   assertThat(legacy.get("id")).isEqualTo(legacyId);assertThat(legacy.get("legacySpecificationOriginal")).isEqualTo(original);
   assertThat(legacy.get("barcode")).isEqualTo("0000123");assertThat(legacy.get("structureState")).isEqualTo("LEGACY_PENDING");
   assertThat(legacy.get("createdAt")).isNull();assertThat(legacy.get("updatedAt")).isNull();
   assertThatThrownBy(()->products.stageLegacy(c,legacyId)).hasMessage("PRODUCT_LEGACY_ALREADY_STAGED");
   assertThatThrownBy(()->products.stageLegacy(other,legacyId)).isInstanceOf(Rejection.class);
   products.reviewLegacySpecification(c,legacyId,new com.appnexus.masterdata.api.product.ProductCommandData.LegacyReview("DEFERRED_SPEC","需要真实规格",7L));
   assertThat(products.read(c,legacyId).get("structureState")).isEqualTo("LEGACY_PENDING");
   assertThatThrownBy(()->products.reviewLegacySpecification(c,legacyId,new com.appnexus.masterdata.api.product.ProductCommandData.LegacyReview("CONFIRMED_NONE","旧版本确认",7L))).isInstanceOf(Rejection.class);
   products.updateStatus(c,legacyId,new com.appnexus.masterdata.api.product.ProductCommandData.Status("DISABLED",8));
   var blockedLegacyChange=new com.appnexus.masterdata.api.product.ProductStructureData.Migration(9L,0L,null,parent.id(),config.id(),"SERVICE",null,List.of(),List.of(),"迁移旧档","CONFIRMED_NONE");
   assertThatThrownBy(()->products.previewStructure(c,legacyId,blockedLegacyChange)).hasMessage("PRODUCT_LEGACY_SPEC_REQUIRED");
   products.reviewLegacySpecification(c,legacyId,new com.appnexus.masterdata.api.product.ProductCommandData.LegacyReview("CONFIRMED_NONE","人工核实无需规格",9L));
   var legacyChange=new com.appnexus.masterdata.api.product.ProductStructureData.Migration(10L,0L,null,parent.id(),config.id(),"SERVICE",null,List.of(),List.of(),"迁移旧档","CONFIRMED_NONE");
   var legacyPreview=products.previewStructure(c,legacyId,legacyChange);
   products.migrateStructure(c,legacyId,new com.appnexus.masterdata.api.product.ProductStructureData.MigrationCommand(legacyChange,(String)legacyPreview.get("previewToken")),UUID.randomUUID().toString());
   assertThat(products.read(c,legacyId).get("legacySpecificationOriginal")).isEqualTo(original);
   assertThat(products.read(c,legacyId).get("status")).isEqualTo("DISABLED");assertThat(products.read(c,legacyId).get("structureState")).isEqualTo("COMPLETE");
   var reconciliation=(Map<?,?>)products.reconcileLegacy(c,1,20);
   assertThat(((List<Map<String,Object>>)reconciliation.get("rows")).getFirst().get("result")).isEqualTo("MATCHED");
   jdbc.update("UPDATE md_product SET name='旧入口再次修改',version=version+1 WHERE id=UUID_TO_BIN(?)",legacyId);
   reconciliation=(Map<?,?>)products.reconcileLegacy(c,1,20);
   assertThat(((List<Map<String,Object>>)reconciliation.get("rows")).getFirst().get("result")).isEqualTo("SOURCE_CHANGED");
   // 配置草稿修改/退役不改旧商品解释，分组名称由服务端固定快照。
   var groupId=attributes.createGroup(c,new com.appnexus.masterdata.api.product.ProductAttributeData.GroupInput("G","原分组",0)).get("id");
   attributes.updateGroup(c,groupId,new com.appnexus.masterdata.api.product.ProductAttributeData.GroupInput("G","新分组",0));
   attributes.setStatus(c,groupId,new Status(false,1),true);
   var decimalAttribute=attributes.createAttribute(c,new com.appnexus.masterdata.api.product.ProductAttributeData.AttributeInput("heat",null,0)).get("id");
   var decimalRevision=attributes.createRevision(c,decimalAttribute,new com.appnexus.masterdata.api.product.ProductAttributeData.RevisionInput("热值","DECIMAL",unit.id(),2,"{\"min\":\"1.00\",\"max\":\"9.99\"}",0)).get("id");
   attributes.updateRevision(c,decimalRevision,new com.appnexus.masterdata.api.product.ProductAttributeData.RevisionInput("热值","DECIMAL",unit.id(),2,"{\"min\":\"1.00\",\"max\":\"9.99\"}",1));
   attributes.publishRevision(c,decimalRevision,2);
   assertThatThrownBy(()->attributes.updateRevision(c,decimalRevision,new com.appnexus.masterdata.api.product.ProductAttributeData.RevisionInput("错误修改","TEXT",null,null,"{}",3))).isInstanceOf(Rejection.class);
   var multi=templates.create(c,new com.appnexus.masterdata.api.product.ProductTemplateData.TemplateInput("MULTI","多属性","QUALITY",0)).get("id");
   var multiVersion=templates.createVersion(c,multi,new com.appnexus.masterdata.api.product.ProductTemplateData.VersionInput("两项",List.of(new com.appnexus.masterdata.api.product.ProductTemplateData.ItemInput(attributeId,revisionId,null,null,true,false,true,0),new com.appnexus.masterdata.api.product.ProductTemplateData.ItemInput(decimalAttribute,decimalRevision,null,null,true,true,false,1)),0)).get("id");
   templates.updateVersion(c,multiVersion,new com.appnexus.masterdata.api.product.ProductTemplateData.VersionInput("两项确认",List.of(new com.appnexus.masterdata.api.product.ProductTemplateData.ItemInput(attributeId,revisionId,null,null,true,false,true,0),new com.appnexus.masterdata.api.product.ProductTemplateData.ItemInput(decimalAttribute,decimalRevision,null,null,true,true,false,1)),1));
   templates.publish(c,multiVersion,2);
   assertThatThrownBy(()->templates.updateVersion(c,multiVersion,new com.appnexus.masterdata.api.product.ProductTemplateData.VersionInput("不能改",List.of(),3))).isInstanceOf(Rejection.class);
   templates.retire(c,multiVersion,3);
   assertThat(templates.versions(c,multi).toString()).contains("RETIRED");
   templates.status(c,multi,new Status(false,4));
   attributes.retireRevision(c,decimalRevision,3);
   assertThat(attributes.revisions(c,decimalAttribute).toString()).contains("RETIRED");
   assertThat(products.read(c,productId).get("values").toString()).contains("材质");

   var cutover=new db.migration.V111__product_single_writer_cutover();
   var flywayContext=org.mockito.Mockito.mock(org.flywaydb.core.api.migration.Context.class);
   try(var conn=source.getConnection()){
    org.mockito.Mockito.when(flywayContext.getConnection()).thenReturn(conn);
    assertThatThrownBy(()->cutover.migrate(flywayContext)).hasMessage("PRODUCT_CUTOVER_SOURCE_CHANGED_OR_UNRECONCILED");
   }
   assertThat(products.read(c,legacyId).get("legacySpecificationOriginal")).isEqualTo(original);
   jdbc.update("UPDATE md_product SET name='旧商品',version=7 WHERE id=UUID_TO_BIN(?)",legacyId);
   try(var conn=source.getConnection()){org.mockito.Mockito.when(flywayContext.getConnection()).thenReturn(conn);cutover.migrate(flywayContext);}
   assertThat(products.read(c,legacyId).get("legacySpecificationOriginal")).isEqualTo(original);

   assertThat(jdbc.queryForList("SELECT t.table_name FROM information_schema.tables t LEFT JOIN information_schema.columns c ON c.table_schema=t.table_schema AND c.table_name=t.table_name AND c.column_name IN ('created_by','created_at','updated_by','updated_at') WHERE t.table_schema=DATABASE() AND t.table_type='BASE TABLE' GROUP BY t.table_name HAVING COUNT(c.column_name)<>4",String.class)).isEmpty();
  }
 }

 /** 真实商品存储与统一范围执行夹具；仅替换权威策略事实来源，不替换行范围或业务授权执行。 */
 private static ProductService productService(org.mybatis.spring.SqlSessionTemplate sql,org.springframework.transaction.PlatformTransactionManager manager,AccessAuthorization access,com.appnexus.contracts.operation.OperationPreviewProof proof,UUID organization){
  com.appnexus.platform.authorization.AccessPlanning planning=(subject,permission,resource)->{if(!access.hasPermission(subject,permission))throw new SecurityException("FIXTURE_PERMISSION_DENIED");return new com.appnexus.platform.authorization.AccessPlan(subject,permission,resource,new com.appnexus.platform.authorization.DataScopeAuthorization.DataScope(com.appnexus.platform.authorization.DataScopeAuthorization.ScopeType.ALL,Set.of()),Map.of(),java.time.Instant.now());};
  com.appnexus.platform.authorization.DataAccessReferences references=(subject,permission)->List.of(new com.appnexus.platform.authorization.DataAccessReferences.Organization(organization,"测试归属组织"));
  var authorization=new ProductAccess(planning,new com.appnexus.platform.persistence.platformscope.MySqlRowAccessControl(),references,sql.getMapper(ProductAccessMapper.class));
  var records=new MyBatisProductRepository(sql.getMapper(ProductRecordMapper.class));
  var facts=new MyBatisProductCompletenessFacts(sql.getMapper(ProductCompletenessMapper.class));
  var aggregates=new MyBatisProductAggregateRepository(sql.getMapper(ProductSnapshotMapper.class),sql.getMapper(ProductHistoryMapper.class),sql.getMapper(ProductAttributeValueMapper.class),records);
  return new ProductService(access,proof,records,new MyBatisProductCommandRepository(sql.getMapper(ProductMainMapper.class)),aggregates,new MyBatisProductListQueries(sql.getMapper(ProductListMapper.class),new tools.jackson.databind.ObjectMapper(),authorization),new MyBatisProductCatalogQueries(sql.getMapper(ProductCatalogReadMapper.class)),authorization,new SpringProductExecutionTransaction(manager),new JacksonProductContent(),new JacksonAttributeRuleContent(),new ProductCompletenessService(facts,records));
 }
}
