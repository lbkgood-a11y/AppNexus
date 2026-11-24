package com.appnexus.platform;
import com.appnexus.bulkwarehousing.api.InboundReceiptReferences;
import com.appnexus.bulkwarehousing.api.InboundReceiving;
import com.appnexus.bulkwarehousing.api.WarehouseBalances;
import java.math.BigDecimal;
import com.appnexus.masterdata.api.masterdata.MasterData;
import com.appnexus.masterdata.api.masterdata.MasterDataManagement;
import com.appnexus.masterdata.api.product.ProductCatalogData;
import com.appnexus.masterdata.application.product.ProductService;
import com.appnexus.platform.security.operation.OperationRegistry;

import static org.assertj.core.api.Assertions.*;
import com.appnexus.masterdata.application.product.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** 完整组合应用及真实RBAC验证商品配置/草稿；不修改共享库或管理员凭据。 */
@SpringBootTest
@ActiveProfiles("local")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class ProductV1IntegrationTest extends DatabaseTestSupport {
 /** 防止直接用例测试的可信请求属性泄漏到下一测试。 */
 @org.junit.jupiter.api.AfterEach void clearDirectContext(){org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();}
 @Autowired JdbcTemplate jdbc;
 @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
 @Autowired org.springframework.test.web.servlet.MockMvc mvc;
 @Autowired ProductCatalogService catalog;
 @Autowired com.appnexus.masterdata.api.product.ProductAttributeManagement batchAttributes;
 @Autowired com.appnexus.masterdata.api.product.ProductTemplateManagement batchTemplates;
 @Autowired ProductService products;
 @Autowired OperationRegistry registry;
 @Autowired com.appnexus.platform.persistence.organization.OrganizationAdministratorBootstrap bootstrap;

 @Autowired com.appnexus.masterdata.api.product.ProductStructureManagement structure;
 @Autowired com.appnexus.masterdata.api.product.UnitConversions conversions;
 @Autowired com.appnexus.masterdata.adapter.persistence.UnitConversionMapper conversionMapper;
 @Autowired MasterDataManagement masterData;
 @Autowired InboundReceiptReferences inboundReferences;
 @Autowired InboundReceiving inboundReceiving;
 @Autowired WarehouseBalances warehouseBalances;
 @Autowired com.appnexus.platform.authorization.RolePermissionManagement rolePermissions;

 /** 通过组合应用的公开契约验证规格归属、停用、可信租户和换算版本。 */
 @Test void warehouseReferencesUseExistingProductAndConversionReadBoundary() throws Exception {
  UUID tenant=UUID.randomUUID(), account=UUID.randomUUID(), member=UUID.randomUUID();
  UUID foreign=UUID.randomUUID(), foreignMember=UUID.randomUUID();
  for(var pair:Map.of(tenant,member,foreign,foreignMember).entrySet()){
   jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'仓储引用测试租户')",pair.getKey().toString());
  }
  jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'仓储引用测试','unused')",
      account.toString(),"warehouse-ref-"+account);
  for(var pair:Map.of(tenant,member,foreign,foreignMember).entrySet()){
   jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",
       pair.getValue().toString(),pair.getKey().toString(),account.toString());
   bootstrap.initialize(pair.getKey(),pair.getValue());
  }
  var context=new VerifiedTenantContext(account,tenant,member,1);
  var outside=new VerifiedTenantContext(account,foreign,foreignMember,1);
  bindReferenceContext(context);
  String organization=ProductOrganizationFixture.create(jdbc,tenant);
  var source=catalog.createUnit(context,new ProductCatalogData.UnitInput("REF-G","克","MASS",3,0));
  var target=catalog.createUnit(context,new ProductCatalogData.UnitInput("REF-KG","千克","MASS",3,0));
  var category=catalog.createCategory(context,new ProductCatalogData.CategoryInput("REF","仓储测试分类",null,0));
  var slots=new LinkedHashMap<String,String>();
  for(String purpose:List.of("BASIC","SPEC","QUALITY","EXTENSION")) slots.put(purpose,null);
  var config=catalog.publish(context,category.id(),new ProductCatalogData.Publish(slots,false,0));
  String product=UUID.randomUUID().toString(), otherProduct=UUID.randomUUID().toString();
  for(String id:List.of(product,otherProduct)){
   jdbc.update("""
       INSERT INTO md_product_v1(id,tenant_id,code,name,product_form,base_unit_id,category_id,category_config_id,
           specification_mode,status,structure_state,organization_id)
       VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,'仓储商品','PHYSICAL',UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),
           'CUSTOM','ENABLED','COMPLETE',UUID_TO_BIN(?))
       """,id,tenant.toString(),id,target.id(),category.id(),config.id(),organization);
  }
  jdbc.update("""
      UPDATE md_product_v1 SET measurement_config=JSON_OBJECT('units',JSON_ARRAY(),'packaging',
          JSON_ARRAY(JSON_OBJECT('code','PACK','unitId',?,'quantity','24'))) WHERE id=UUID_TO_BIN(?)
      """,source.id(),product);
  var packageEvidence=structure.packagingEvidence(context,product,"PACK",0,"10");
  assertThat(packageEvidence.convertedQuantity()).isEqualByComparingTo("240");
  assertThatThrownBy(()->structure.packagingEvidence(context,product,"PACK",1,"10")).hasMessage("MASTER_DATA_CHANGED");
  String spec=UUID.randomUUID().toString(), sku=UUID.randomUUID().toString();
  jdbc.update("""
      INSERT INTO md_product_specification(id,tenant_id,product_id,config_id,mode,name)
      VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),'CUSTOM','仓储规格')
      """,spec,tenant.toString(),product,config.id());
  jdbc.update("""
      INSERT INTO md_product_sku(id,tenant_id,product_id,specification_id,code)
      VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),'REF-SKU')
      """,sku,tenant.toString(),product,spec);
  var kind=com.appnexus.masterdata.api.product.ProductStructureManagement.IdentityKind.SKU;
  assertThat(structure.resolveIdentity(context,product,kind,sku,0).specificationId()).isEqualTo(spec);
  assertThatThrownBy(()->structure.resolveIdentity(context,otherProduct,kind,sku,0)).hasMessage("PRODUCT_STRUCTURE_INVALID");
  assertThatThrownBy(()->structure.resolveIdentity(context,product,kind,sku,1)).hasMessage("MASTER_DATA_CHANGED");
  String rule=UUID.randomUUID().toString();
  jdbc.update("""
      INSERT INTO md_product_unit_conversion(id,tenant_id,source_unit_id,target_unit_id,factor)
      VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),0.001)
      """,rule,tenant.toString(),source.id(),target.id());
  String measuredSpec=newId();
  jdbc.update("""
      INSERT INTO md_product_specification(id,tenant_id,product_id,config_id,mode,name)
      VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),'CUSTOM','独立计量规格')
      """,measuredSpec,tenant.toString(),otherProduct,config.id());
  jdbc.update("UPDATE md_product_v1 SET status='DISABLED' WHERE id=UUID_TO_BIN(?)",otherProduct);
  var measurement=new com.appnexus.masterdata.api.product.ProductMeasurement.SkuConfiguration(
      target.id(),source.id(),List.of(
          new com.appnexus.masterdata.api.product.ProductMeasurement.Packaging("BOX","箱",source.id(),"24"),
          new com.appnexus.masterdata.api.product.ProductMeasurement.Packaging("PALLET","托",source.id(),"96")));
  var created=(Map<?,?>)structure.createSku(context,otherProduct,
      new com.appnexus.masterdata.api.product.ProductStructureCommands.Sku(measuredSpec,"REF-MEASURED",0L,measurement));
  String measuredSku=(String)created.get("id");
  jdbc.update("UPDATE md_product_v1 SET status='ENABLED' WHERE id=UUID_TO_BIN(?)",otherProduct);
  assertThat(structure.resolveIdentity(context,otherProduct,kind,measuredSku,1).baseUnitId()).isEqualTo(target.id());
  assertThat(structure.skuPackagingEvidence(context,otherProduct,measuredSku,"BOX",1,"2").convertedQuantity())
      .isEqualByComparingTo("48");
  assertThat(structure.skuPackagingEvidence(context,otherProduct,measuredSku,"PALLET",1,"2").convertedQuantity())
      .isEqualByComparingTo("192");
  assertThatThrownBy(()->structure.skuPackagingEvidence(context,product,measuredSku,"BOX",0,"2"))
      .hasMessage("PRODUCT_STRUCTURE_INVALID");
  jdbc.update("UPDATE md_product_v1 SET status='DISABLED' WHERE id=UUID_TO_BIN(?)",otherProduct);
  var revised=new com.appnexus.masterdata.api.product.ProductMeasurement.SkuConfiguration(
      target.id(),target.id(),List.of(
          new com.appnexus.masterdata.api.product.ProductMeasurement.Packaging("BOX","更新箱规",source.id(),"30")));
  var update=new com.appnexus.masterdata.api.product.ProductMeasurement.SkuChange(1L,0L,revised);
  structure.saveSkuMeasurement(context,otherProduct,measuredSku,update);
  assertThatThrownBy(()->structure.saveSkuMeasurement(context,otherProduct,measuredSku,update))
      .hasMessage("MASTER_DATA_CHANGED");
  var wrongStock=new com.appnexus.masterdata.api.product.ProductMeasurement.SkuConfiguration(
      source.id(),source.id(),List.of());
  assertThatThrownBy(()->structure.saveSkuMeasurement(context,otherProduct,measuredSku,
      new com.appnexus.masterdata.api.product.ProductMeasurement.SkuChange(2L,1L,wrongStock)))
      .hasMessage("PRODUCT_STRUCTURE_INVALID");
  jdbc.update("UPDATE md_product_v1 SET status='ENABLED' WHERE id=UUID_TO_BIN(?)",otherProduct);
  assertThat(structure.skuPackagingEvidence(context,otherProduct,measuredSku,"BOX",2,"2").convertedQuantity())
      .isEqualByComparingTo("60");
  var evidence=conversions.evidence(context,rule,0,"1250");
  assertThat(evidence.sourceUnitId()).isEqualTo(source.id());
  assertThat(evidence.targetUnitId()).isEqualTo(target.id());
  assertThat(evidence.convertedQuantity()).isEqualByComparingTo("1.25");
  assertThatThrownBy(()->conversions.evidence(context,rule,1,"1250")).hasMessage("MASTER_DATA_CHANGED");
  var position=warehousePosition(context);
  String owner=UUID.randomUUID().toString();
  jdbc.update("""
      INSERT INTO md_partner(id,tenant_id,code,name,partner_type,organization_id)
      VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'REF-OWNER','仓储货主','CUSTOMER',UUID_TO_BIN(?))
      """,owner,tenant.toString(),organization);
  var item=new InboundReceiptReferences.Item(product,InboundReceiptReferences.IdentityKind.SKU,sku,0);
  var request=new InboundReceiptReferences.Request(position,item,owner,source.id(),"1250",
      new InboundReceiptReferences.UnitRule(rule,0));
  var resolved=inboundReferences.resolve(context,request);
  assertThat(resolved.ownerPartnerId()).isEqualTo(owner);
  assertThat(resolved.position()).isEqualTo(position);
  assertThat(resolved.specificationId()).isEqualTo(spec);
  assertThat(resolved.quantity().baseQuantity()).isEqualByComparingTo("1.25");
  var packaged=inboundReferences.resolve(context,new InboundReceiptReferences.Request(
      position,item,owner,source.id(),"10",new InboundReceiptReferences.Packaging("PACK")));
  assertThat(packaged.quantity().baseQuantity()).isEqualByComparingTo("240");
  var direct=inboundReferences.resolve(context,new InboundReceiptReferences.Request(
      position,item,owner,target.id(),"1.25",new InboundReceiptReferences.SameUnit()));
  assertThat(direct.quantity().baseQuantity()).isEqualByComparingTo("1.25");
  assertThatThrownBy(()->inboundReferences.resolve(context,new InboundReceiptReferences.Request(
      position,item,owner,target.id(),"1250",new InboundReceiptReferences.UnitRule(rule,0))))
      .hasMessage("INBOUND_CONVERSION_UNIT_MISMATCH");

  String notice=verifyMultilineInbound(context,item,owner,target.id(),spec);

  UUID unprivilegedAccount=UUID.randomUUID(), unprivilegedMember=UUID.randomUUID();
  jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'无仓储引用权限','unused')",
      unprivilegedAccount.toString(),"no-warehouse-ref-"+unprivilegedAccount);
  jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",
      unprivilegedMember.toString(),tenant.toString(),unprivilegedAccount.toString());
  var unprivileged=new VerifiedTenantContext(unprivilegedAccount,tenant,unprivilegedMember,1);
  bindReferenceContext(unprivileged);
  assertThatThrownBy(()->inboundReceiving.update(unprivileged,new InboundReceiving.Update(null,0)))
      .isInstanceOf(SecurityException.class);
  assertThatThrownBy(()->warehouseBalances.document(unprivileged,WarehouseBalances.DocumentKind.INBOUND_NOTICE,notice))
      .isInstanceOf(SecurityException.class);
  assertThatThrownBy(()->inboundReferences.resolve(unprivileged,request)).hasMessage("MASTER_DATA_DENIED");
  assertThatThrownBy(()->structure.resolveIdentity(unprivileged,product,kind,sku,0)).hasMessage("MASTER_DATA_DENIED");
  assertThatThrownBy(()->conversions.evidence(unprivileged,rule,0,"1250")).hasMessage("MASTER_DATA_DENIED");

  bindReferenceContext(outside);
  assertThatThrownBy(()->warehouseBalances.document(outside,WarehouseBalances.DocumentKind.INBOUND_NOTICE,notice))
      .hasMessage("OUTBOUND_ACCESS_DENIED");
  assertThatThrownBy(()->inboundReferences.resolve(outside,request))
      .isInstanceOf(com.appnexus.masterdata.api.masterdata.MasterData.Rejection.class);
  assertThatThrownBy(()->structure.resolveIdentity(outside,product,kind,sku,0))
      .isInstanceOf(com.appnexus.masterdata.api.masterdata.MasterData.Rejection.class);
  assertThatThrownBy(()->conversions.evidence(outside,rule,0,"1250")).hasMessage("PRODUCT_CONVERSION_INVALID");
  bindReferenceContext(context);
  jdbc.update("UPDATE md_product_specification SET enabled=FALSE WHERE id=UUID_TO_BIN(?)",spec);
  assertThatThrownBy(()->structure.resolveIdentity(context,product,kind,sku,0)).hasMessage("PRODUCT_STRUCTURE_INVALID");
  byte[] tenantBytes=java.nio.ByteBuffer.allocate(16)
      .putLong(tenant.getMostSignificantBits()).putLong(tenant.getLeastSignificantBits()).array();
  assertThatThrownBy(()->conversionMapper.status(false,context.accountId().toString(),tenantBytes,rule,0L))
      .hasRootCauseMessage("SCOPED_WRITE_PLAN_REQUIRED");
  conversions.status(context,rule,new com.appnexus.masterdata.api.product.UnitConversions.Status(false,0L));
  assertThatThrownBy(()->conversions.evidence(context,rule,1,"1250")).hasMessage("PRODUCT_CONVERSION_INVALID");
 }

 /** 验证多行通知签发和授权后的明细读取。 */
 private String verifyMultilineInbound(VerifiedTenantContext c,
     InboundReceiptReferences.Item item,String owner,String unit,String spec) throws Exception {
  bindReferenceContext(c);
  String warehouse=masterData.create(c,MasterData.Kind.WAREHOUSE,
      new MasterData.Input("MULTILINE-W","多行入仓验收仓",Map.of(),0)).get("id");
  String secondSku=newId();
  jdbc.update("""
     INSERT INTO md_product_sku(id,tenant_id,product_id,specification_id,code)
     VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),'REF-SKU-MULTILINE')
     """,secondSku,c.tenantId().toString(),item.productId(),spec);
  var secondItem=new InboundReceiptReferences.Item(item.productId(),InboundReceiptReferences.IdentityKind.SKU,secondSku,item.productVersion());
  String notice=newId(),firstLine=newId(),secondLine=newId(),number="MULTI-"+newId();
  inboundReceiving.issue(c,new InboundReceiving.Notice(notice,warehouse,owner,null,null,null,
      number,null,null,List.of(new InboundReceiving.NoticeLine(firstLine,item,unit,new BigDecimal("100"),null,null,"CONTRACT-01"),
          new InboundReceiving.NoticeLine(secondLine,secondItem,unit,new BigDecimal("80"),null,null)),
      "APPOINTMENT-01","CONVEYANCE-01"));
  var found=warehouseBalances.document(c,WarehouseBalances.DocumentKind.INBOUND_NOTICE,number);
  assertThat(found.header().noticeId()).isEqualTo(notice);
  assertThat(found.header().appointmentNo()).isEqualTo("APPOINTMENT-01");
  assertThat(found.header().plannedConveyance()).isEqualTo("CONVEYANCE-01");
  assertThat(found.lines()).extracting(WarehouseBalances.NoticeLineView::id)
      .containsExactlyInAnyOrder(firstLine,secondLine);
  assertThat(found.lines()).filteredOn(line->line.id().equals(firstLine)).singleElement().satisfies(line->{
   assertThat(line.contractNo()).isEqualTo("CONTRACT-01");
   assertThat(line.identityId()).isEqualTo(item.identityId());
   assertThat(line.receivedQuantity()).isZero();
   assertThat(line.audit().createdAt()).isNotNull();
  });
  assertThat(found.lines()).filteredOn(line->line.id().equals(secondLine)).singleElement().satisfies(line->{
   assertThat(line.contractNo()).isNull();
   assertThat(line.identityId()).isEqualTo(secondSku);
   assertThat(line.receivedQuantity()).isZero();
  });
  String secondNotice=newId();
  inboundReceiving.issue(c,new InboundReceiving.Notice(secondNotice,warehouse,owner,item,unit,BigDecimal.TEN));
  // 历史通知若引用不可见货主，既不能占用页内位置，也不得计入总数。
  String hiddenNotice=newId();
  jdbc.update("""
      INSERT INTO bw_inbound_notice(tenant_id,id,notice_no,warehouse_id,owner_id,flow_version)
      VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,UUID_TO_BIN(?),UUID_TO_BIN(?),2)
      """,c.tenantId().toString(),hiddenNotice,hiddenNotice,warehouse,newId());
  jdbc.update("""
      INSERT INTO bw_inbound_notice_line(tenant_id,id,notice_id,product_id,identity_kind,identity_id,
          product_version,base_unit_id,approved_quantity,received_quantity)
      VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),'SKU',UUID_TO_BIN(?),?,UUID_TO_BIN(?),10,0)
      """,c.tenantId().toString(),newId(),hiddenNotice,item.productId(),item.identityId(),item.productVersion(),unit);
  var filter=new WarehouseBalances.NoticeFilter(null,warehouse,null,null);
  var firstPage=warehouseBalances.inboundNotices(c,filter,1,1);
  var secondPage=warehouseBalances.inboundNotices(c,filter,2,1);
  assertThat(firstPage.total()).isEqualTo(2);
  assertThat(secondPage.total()).isEqualTo(2);
  assertThat(List.of(firstPage.rows().getFirst().noticeId(),secondPage.rows().getFirst().noticeId()))
      .containsExactlyInAnyOrder(notice,secondNotice);
  assertThat(warehouseBalances.inboundNotices(c,filter,3,1).rows()).isEmpty();
  assertThat(warehouseBalances.inboundNotices(c,new WarehouseBalances.NoticeFilter(number,warehouse,null,null),1,20).total())
      .isEqualTo(1);
  var edit=new InboundReceiving.Notice(notice,warehouse,owner,null,null,null,number,null,"修改备注",
      List.of(new InboundReceiving.NoticeLine(firstLine,item,unit,new BigDecimal("120"),null,"修改明细","CONTRACT-EDIT"),
          new InboundReceiving.NoticeLine(secondLine,secondItem,unit,new BigDecimal("80"),null,null)),
      "APPOINTMENT-EDIT","CONVEYANCE-EDIT",new InboundReceiving.Driver("司机测试","555-0100","TEST-DRIVER-ID"));
  inboundReceiving.update(c,new InboundReceiving.Update(edit,found.header().version()));
  var modified=warehouseBalances.document(c,WarehouseBalances.DocumentKind.INBOUND_NOTICE,notice);
  assertThat(modified.header().remark()).isEqualTo("修改备注");
  assertThat(modified.header().version()).isEqualTo(found.header().version()+1);
  assertThat(modified.header().driver()).isEqualTo(new InboundReceiving.Driver("司机测试","555-0100","TEST-DRIVER-ID"));
  jdbc.update("""
      INSERT INTO access_role_field_security(role_id,resource_type,field_code,display_mode)
      SELECT id,'warehousing.inbound','driverIdNumber','HIDDEN'
      FROM access_role WHERE tenant_id=UUID_TO_BIN(?) AND code='sys_tenant_admin'
      """,c.tenantId().toString());
  var hidden=warehouseBalances.document(c,WarehouseBalances.DocumentKind.INBOUND_NOTICE,notice);
  assertThat(hidden.header().driver().driverIdNumber()).isNull();
  assertThat(hidden.driverRestrictions()).containsKey("driverIdNumber");
  var tampered=new InboundReceiving.Notice(notice,warehouse,owner,null,null,null,number,null,edit.remark(),
      edit.lines(),edit.appointmentNo(),edit.plannedConveyance(),new InboundReceiving.Driver("司机测试","555-0100","CHANGED-ID"));
  assertThatThrownBy(()->inboundReceiving.update(c,new InboundReceiving.Update(tampered,modified.header().version())))
      .hasMessage("INBOUND_FIELD_DENIED");
  assertThat(warehouseBalances.document(c,WarehouseBalances.DocumentKind.INBOUND_NOTICE,notice).header().version())
      .isEqualTo(modified.header().version());
  var preserved=new InboundReceiving.Notice(notice,warehouse,owner,null,null,null,number,null,"修改其他字段",
      edit.lines(),edit.appointmentNo(),edit.plannedConveyance(),new InboundReceiving.Driver("司机测试","555-0100",null));
  inboundReceiving.update(c,new InboundReceiving.Update(preserved,modified.header().version()));
  jdbc.update("DELETE f FROM access_role_field_security f JOIN access_role r ON r.id=f.role_id WHERE r.tenant_id=UUID_TO_BIN(?) AND f.resource_type='warehousing.inbound' AND f.field_code='driverIdNumber'",c.tenantId().toString());

  assertThat(warehouseBalances.document(c,WarehouseBalances.DocumentKind.INBOUND_NOTICE,notice)
      .header().driver().driverIdNumber()).isEqualTo("TEST-DRIVER-ID");
  assertThat(modified.header().audit().createdAt()).isEqualTo(found.header().audit().createdAt());
  assertThat(modified.lines()).filteredOn(line->line.id().equals(firstLine)).singleElement()
      .satisfies(line->assertThat(line.contractNo()).isEqualTo("CONTRACT-EDIT"));
  assertThatThrownBy(()->inboundReceiving.update(c,new InboundReceiving.Update(edit,found.header().version())))
      .hasMessage("INBOUND_VERSION_CONFLICT");
  return notice;
 }

 private static String newId() { return UUID.randomUUID().toString(); }

 private InboundReceiptReferences.Position warehousePosition(VerifiedTenantContext context) {
  return warehousePosition(context,"REF");
 }

 /** 每个验收用例使用独立位置，避免此前停用或调拨验收改变后续位置。 */
 private InboundReceiptReferences.Position warehousePosition(VerifiedTenantContext context,String codePrefix) {
  String warehouse=masterData.create(context,MasterData.Kind.WAREHOUSE,
      new MasterData.Input(codePrefix+"-W","进仓仓库",Map.of(),0)).get("id");
  String area=masterData.create(context,MasterData.Kind.STORAGE_AREA,
      new MasterData.Input(codePrefix+"-A","进仓库区",Map.of("parentCode",codePrefix+"-W"),0)).get("id");
  String location=masterData.create(context,MasterData.Kind.STORAGE_LOCATION,
      new MasterData.Input(codePrefix+"-L","进仓位置",Map.of("parentCode",codePrefix+"-A"),0)).get("id");
  return new InboundReceiptReferences.Position(warehouse,area,location,0,0,0);
 }

 private void bindReferenceContext(VerifiedTenantContext context) {
  var request=new org.springframework.mock.web.MockHttpServletRequest();
  request.setAttribute(VerifiedTenantContext.class.getName(),context);
  org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
      new org.springframework.web.context.request.ServletRequestAttributes(request));
 }

 @Test void realTenantRolesAndRevocationProtectCatalogDraftAndHistory() throws Exception {
  UUID tenant=UUID.randomUUID(),account=UUID.randomUUID(),member=UUID.randomUUID(),ordinaryAccount=UUID.randomUUID(),ordinaryMember=UUID.randomUUID();
  jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'商品V1隔离测试租户')",tenant.toString());
  for(var pair:List.of(Map.entry(account,member),Map.entry(ordinaryAccount,ordinaryMember))){
   jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'商品测试成员','unused')",pair.getKey().toString(),"product-"+pair.getKey());
   jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",pair.getValue().toString(),tenant.toString(),pair.getKey().toString());
  }
  bootstrap.initialize(tenant,member);
  String organization=ProductOrganizationFixture.create(jdbc,tenant);
  var admin=new VerifiedTenantContext(account,tenant,member,1);
  var ordinary=new VerifiedTenantContext(ordinaryAccount,tenant,ordinaryMember,1);
  // 正式Mapper执行可信请求门禁；直接用例回归也必须建立与真实已授权成员一致的服务端上下文。
  var directRequest=new org.springframework.mock.web.MockHttpServletRequest();
  directRequest.setAttribute(VerifiedTenantContext.class.getName(),admin);
  org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(directRequest));
  var category=catalog.createCategory(admin,new ProductCatalogData.CategoryInput("ROOT","商品分类",null,0));
  assertThat(category.audit().createdBy()).isEqualTo(account.toString());
  var slots=new LinkedHashMap<String,String>();for(String purpose:List.of("BASIC","SPEC","QUALITY","EXTENSION"))slots.put(purpose,null);
  var config=catalog.publish(admin,category.id(),new ProductCatalogData.Publish(slots,false,0));
  var draft=products.create(admin,new com.appnexus.masterdata.api.product.ProductMainData.Input("SERVICE","无规格服务",null,category.id(),config.id(),"SERVICE",null,null,null,null,null,null,organization));
  String id=(String)draft.get("id");
  assertThat(draft.get("status")).isEqualTo("DRAFT");
  assertThat(products.updateStatus(admin,id,new com.appnexus.masterdata.api.product.ProductCommandData.Status("ENABLED",0)).get("status")).isEqualTo("ENABLED");
  assertThatThrownBy(()->products.read(ordinary,id)).hasMessage("MASTER_DATA_DENIED");
  assertThatThrownBy(()->catalog.createCategory(ordinary,new ProductCatalogData.CategoryInput("NO","拒绝",null,0))).hasMessage("MASTER_DATA_DENIED");
  assertThatThrownBy(()->products.create(ordinary,new com.appnexus.masterdata.api.product.ProductMainData.Input("NO","拒绝",null,null,null,null,null,null,null,null,null))).hasMessage("MASTER_DATA_DENIED");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM md_product_v1_change WHERE tenant_id=UUID_TO_BIN(?) AND product_id=UUID_TO_BIN(?)",Integer.class,tenant.toString(),id)).isEqualTo(2);
  var json=new tools.jackson.databind.ObjectMapper();
  var selected=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/bff/tenants/current")
   .with(authenticated(account.toString(),0)).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
   .contentType("application/json").content(json.writeValueAsString(Map.of("tenantId",tenant.toString())))).andReturn().getResponse();
  assertThat(selected.getStatus()).isEqualTo(200);
  var cookie=selected.getCookie("JSESSIONID");
  verifyAttributeCreation(cookie,json,tenant);
  // 列表已授权行才投影记录审计，供公共ManagementTable显示审计查询，不增加自由表查询入口。
  var listing=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/master-data/product").cookie(cookie)).andReturn().getResponse();
  assertThat(listing.getStatus()).isEqualTo(200);
  assertThat(listing.getHeader("Cache-Control")).contains("no-store");
  var listed=json.readTree(listing.getContentAsString()).path("rows").get(0).path("audit");
  assertThat(listed.path("createdBy").asText()).isEqualTo(account.toString());
  assertThat(listed.path("createdAt").asText()).isNotBlank();
  // 真实HTTP图片链路：原始二进制、CSRF、受权读取和父CAS，不以模拟上传代替接口验收。
  String imageProduct=(String)products.create(admin,new com.appnexus.masterdata.api.product.ProductMainData.Input("HTTP-IMAGE","图片接口",null,null,null,null,null,null,null,null,null,null,organization)).get("id");
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
  assertThat(mvc.perform(get("/api/master-data/product-attribute").cookie(ordinarySession.getCookie("JSESSIONID")))
    .andReturn().getResponse().getStatus()).isEqualTo(403);
  assertThat(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/master-data/product/"+id).cookie(ordinarySession.getCookie("JSESSIONID"))).andReturn().getResponse().getStatus()).isEqualTo(403);
  String oldId=UUID.randomUUID().toString();
  String original="  legacy specification 0005  ";
  jdbc.update("INSERT INTO md_product(id,tenant_id,code,name,barcode,specification,unit,enabled,version,created_by,created_at,updated_by,updated_at) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'OLD-PRODUCT','旧商品','000099',?,'历史单位',TRUE,2,NULL,NULL,NULL,NULL)",oldId,tenant.toString(),original);
  var staged=products.stageLegacy(admin,oldId);
  assertThat(staged.get("legacySpecificationOriginal")).isEqualTo(original);
  assertThat(staged.get("createdAt")).isNull();assertThat(staged.get("updatedAt")).isNull();
  assertThatThrownBy(()->products.stageLegacy(ordinary,oldId)).hasMessage("MASTER_DATA_DENIED");
  products.reviewLegacySpecification(admin,oldId,new com.appnexus.masterdata.api.product.ProductCommandData.LegacyReview("DEFERRED_SPEC","保留真实规格",2L));
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

 /** 通过真实写入和查询端点覆盖首个草稿、选项与数据库回滚。 */
 private void verifyAttributeCreation(jakarta.servlet.http.Cookie cookie,tools.jackson.databind.ObjectMapper json,UUID tenant) throws Exception {
  String path="/api/master-data/product-attribute";
  var option=Map.of("code","black","label","黑色");
  var initial=Map.of("name","颜色","dataType","SINGLE_SELECT","validationJson","{}","options",List.of(option),"version",0);
  var rejected=new HashMap<String,Object>(initial);
  rejected.put("options",List.of(option,option));
  var failed=mvc.perform(post(path).cookie(cookie).with(csrf()).contentType("application/json")
    .content(json.writeValueAsString(Map.of("code","COLOR-REJECTED","version",0,"initialRevision",rejected)))).andReturn().getResponse();
  assertThat(failed.getStatus()).as(failed.getContentAsString()).isEqualTo(409);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM md_product_attribute WHERE tenant_id=UUID_TO_BIN(?) AND code='COLOR-REJECTED'",
    Integer.class,tenant.toString())).isZero();
  var created=mvc.perform(post(path).cookie(cookie).with(csrf()).contentType("application/json")
    .content(json.writeValueAsString(Map.of("code","COLOR","version",0,"initialRevision",initial)))).andReturn().getResponse();
  assertThat(created.getStatus()).as(created.getContentAsString()).isEqualTo(200);
  String id=json.readTree(created.getContentAsString()).path("id").asText();
  var list=mvc.perform(get(path).cookie(cookie)).andReturn().getResponse();
  assertThat(list.getStatus()).isEqualTo(200);
  var row=json.readTree(list.getContentAsString()).get(0);
  assertThat(row.path("id").asText()).isEqualToIgnoringCase(id);
  assertThat(row.path("name").asText()).isEqualTo("颜色");
  assertThat(row.path("dataType").asText()).isEqualTo("SINGLE_SELECT");
  var revisions=mvc.perform(get(path+"/"+id+"/revisions").cookie(cookie)).andReturn().getResponse();
  assertThat(revisions.getStatus()).isEqualTo(200);
  var revision=json.readTree(revisions.getContentAsString()).get(0);
  assertThat(revision.path("state").asText()).isEqualTo("DRAFT");
  var options=mvc.perform(get(path+"/revisions/"+revision.path("id").asText()+"/options").cookie(cookie)).andReturn().getResponse();
  assertThat(options.getStatus()).isEqualTo(200);
  assertThat(json.readTree(options.getContentAsString()).get(0).path("label").asText()).isEqualTo("黑色");
 }

 @Test void installedProductOperationsReferenceRealPermissionsAndPages(){
  // 明细页面沿用原权限，不创建平行资源；迁移必须生成且仅生成一个可解析菜单。
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_menu WHERE component_key='master-data/product-sku' AND permission_code='masterdata.product:read'",Integer.class)).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_menu_resource r JOIN platform_menu m ON m.id=r.menu_id WHERE m.component_key='master-data/product-sku' AND r.resource_code='masterdata.product'",Integer.class)).isEqualTo(1);
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
