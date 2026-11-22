package com.appnexus.platform;
import com.appnexus.bulkwarehousing.api.InboundReceiptReferences;
import com.appnexus.bulkwarehousing.api.InboundReceiving;
import com.appnexus.bulkwarehousing.api.InboundDisposition;
import com.appnexus.bulkwarehousing.api.OutboundStock;
import com.appnexus.bulkwarehousing.api.CoilOutbound;
import com.appnexus.bulkwarehousing.api.CoilTitleTransfer;
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
 @Autowired ProductService products;
 @Autowired OperationRegistry registry;
 @Autowired com.appnexus.platform.persistence.organization.OrganizationAdministratorBootstrap bootstrap;

 @Autowired com.appnexus.masterdata.api.product.ProductStructureManagement structure;
 @Autowired com.appnexus.masterdata.api.product.UnitConversions conversions;
 @Autowired com.appnexus.masterdata.adapter.persistence.UnitConversionMapper conversionMapper;
 @Autowired MasterDataManagement masterData;
 @Autowired InboundReceiptReferences inboundReferences;
 @Autowired InboundReceiving inboundReceiving;
 @Autowired OutboundStock outboundStock;
 @Autowired CoilOutbound coilOutbound;
 @Autowired CoilTitleTransfer coilTitleTransfer;
 @Autowired WarehouseBalances warehouseBalances;
 @Autowired com.appnexus.platform.authorization.RolePermissionManagement rolePermissions;
 @Autowired InboundDisposition inboundDisposition;

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

  var coalReceipts=receiveCoalInBatches(context,position,item,owner,target.id());
  var inventoryFilter=new WarehouseBalances.OverviewFilter(position.warehouseId(),owner,product);
  var inventory=warehouseBalances.overview(context,inventoryFilter,null,20);
  assertThat(inventory.rows()).filteredOn(row->row.receiptId().equals(coalReceipts.id()))
      .singleElement().satisfies(row->{
       assertThat(row.specification()).isEqualTo("仓储规格");
       assertThat(row.skuCode()).isEqualTo("REF-SKU");
       assertThat(row.receiptBatchNo()).isEqualTo("收货批次-01");
      });
  assertThat(inventory.rows()).anySatisfy(row->assertThat(row.receiptBatchNo()).isNull());
  verifyCoilOutbound(context,position,item,owner,target.id(),target.code());
  verifyCoilReweigh(context,position,item,owner,target.id(),target.code());
  verifyCrossWarehouseReturn(context,position,item,owner,target.id(),target.code());
  verifyCoilReturnTransit(context,position,item,owner,target.id(),target.code());
  verifyCoilTitleTransfer(context,item,owner,target.id(),target.code());
  verifyPartialCoilReturn(context,position,item,owner,target.id(),target.code());
  verifyMultilineInbound(context,item,owner,target.id(),spec);

  UUID unprivilegedAccount=UUID.randomUUID(), unprivilegedMember=UUID.randomUUID();
  jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'无仓储引用权限','unused')",
      unprivilegedAccount.toString(),"no-warehouse-ref-"+unprivilegedAccount);
  jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",
      unprivilegedMember.toString(),tenant.toString(),unprivilegedAccount.toString());
  var unprivileged=new VerifiedTenantContext(unprivilegedAccount,tenant,unprivilegedMember,1);
  verifyAllWarehouseWritesDenyUnprivilegedMember(unprivileged);
  bindReferenceContext(unprivileged);
  var restrictedBalance=new WarehouseBalances.Scope(owner,position.warehouseId(),item.productId(),item.identityId());
  assertThatThrownBy(()->warehouseBalances.physical(unprivileged,restrictedBalance)).isInstanceOf(SecurityException.class);
  assertThatThrownBy(()->warehouseBalances.authorizations(unprivileged,restrictedBalance,null,50))
      .isInstanceOf(SecurityException.class);
  assertThatThrownBy(()->warehouseBalances.document(unprivileged,
      WarehouseBalances.DocumentKind.INBOUND_RECEIPT,coalReceipts.id())).isInstanceOf(SecurityException.class);
  assertThatThrownBy(()->warehouseBalances.trace(unprivileged,coalReceipts.id()))
      .isInstanceOf(SecurityException.class);
  assertThatThrownBy(()->outboundStock.check(unprivileged,coalReceipts.id())).isInstanceOf(SecurityException.class);
  assertThatThrownBy(()->inboundReceiving.receive(unprivileged,coalReceipts)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->inboundReferences.resolve(unprivileged,request)).hasMessage("MASTER_DATA_DENIED");
  assertThatThrownBy(()->structure.resolveIdentity(unprivileged,product,kind,sku,0)).hasMessage("MASTER_DATA_DENIED");
  assertThatThrownBy(()->conversions.evidence(unprivileged,rule,0,"1250")).hasMessage("MASTER_DATA_DENIED");

  bindReferenceContext(outside);
  assertThatThrownBy(()->warehouseBalances.document(outside,WarehouseBalances.DocumentKind.INBOUND_RECEIPT,coalReceipts.id()))
      .hasMessage("OUTBOUND_ACCESS_DENIED");
  assertThatThrownBy(()->outboundStock.check(outside,coalReceipts.id())).hasMessage("OUTBOUND_ACCESS_DENIED");
  assertThatThrownBy(()->inboundReceiving.receive(outside,coalReceipts)).isInstanceOf(RuntimeException.class);
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

 /** 两个独立商品身份及同一行并发实收，均通过真实应用事务和 MySQL。 */
 private void verifyMultilineInbound(VerifiedTenantContext c,
     InboundReceiptReferences.Item item,String owner,String unit,String spec) throws Exception {
  bindReferenceContext(c);
  String warehouse=masterData.create(c,MasterData.Kind.WAREHOUSE,
      new MasterData.Input("MULTILINE-W","多行入仓验收仓",Map.of(),0)).get("id");
  var position=returnPosition(c,warehouse,"MULTILINE-W","MULTILINE-POS");
  String secondSku=newId();
  jdbc.update("""
     INSERT INTO md_product_sku(id,tenant_id,product_id,specification_id,code)
     VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),'REF-SKU-MULTILINE')
     """,secondSku,c.tenantId().toString(),item.productId(),spec);
  var secondItem=new InboundReceiptReferences.Item(item.productId(),InboundReceiptReferences.IdentityKind.SKU,secondSku,item.productVersion());
  String notice=newId(),firstLine=newId(),secondLine=newId(),number="MULTI-"+newId();
  inboundReceiving.issue(c,new InboundReceiving.Notice(notice,position.warehouseId(),owner,null,null,null,
      number,null,null,List.of(new InboundReceiving.NoticeLine(firstLine,item,unit,new BigDecimal("100"),null,null),
          new InboundReceiving.NoticeLine(secondLine,secondItem,unit,new BigDecimal("80"),null,null))));
  var found=warehouseBalances.document(c,WarehouseBalances.DocumentKind.INBOUND_NOTICE,number);
  assertThat(found.header().noticeId()).isEqualTo(notice);
  assertThat(found.lines()).extracting(WarehouseBalances.NoticeLineView::identityId).containsExactly(item.identityId(),secondSku);
  assertThatThrownBy(()->inboundReceiving.takeCustody(c,new InboundReceiving.Custody(newId(),notice,position,newId())))
      .hasMessage("INBOUND_ACCESS_DENIED");
  var first=new InboundReceiving.Custody(newId(),notice,position,newId(),firstLine,"TRUCK-01");
  var competing=new InboundReceiving.Custody(newId(),notice,position,newId(),firstLine,"TRUCK-02");
  var second=new InboundReceiving.Custody(newId(),notice,position,newId(),secondLine,"TRUCK-01");
  for(var custody:List.of(first,competing,second)) inboundReceiving.takeCustody(c,custody);
  var firstReceipt=new InboundReceiving.Receipt(newId(),first.id(),newId(),
      new InboundReceiptReferences.Request(position,item,owner,unit,"60",new InboundReceiptReferences.SameUnit()));
  var over=new InboundReceiving.Receipt(newId(),competing.id(),newId(),
      new InboundReceiptReferences.Request(position,item,owner,unit,"60",new InboundReceiptReferences.SameUnit()));
  assertThatThrownBy(()->commitWhileCompeting(c,()->inboundReceiving.receive(c,firstReceipt),
      ()->inboundReceiving.receive(c,over))).hasRootCauseMessage("INBOUND_LIMIT_EXCEEDED");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bw_stock_receipt WHERE tenant_id=UUID_TO_BIN(?) AND receipt_id=UUID_TO_BIN(?)",
      Integer.class,c.tenantId().toString(),over.id())).isZero();
  var finalFirst=new InboundReceiving.Receipt(newId(),competing.id(),newId(),
      new InboundReceiptReferences.Request(position,item,owner,unit,"40",new InboundReceiptReferences.SameUnit()));
  var finalSecond=new InboundReceiving.Receipt(newId(),second.id(),newId(),
      new InboundReceiptReferences.Request(position,secondItem,owner,unit,"80",new InboundReceiptReferences.SameUnit()));
  inboundReceiving.receive(c,finalFirst);
  assertThat(inboundReceiving.receive(c,finalSecond)).isEqualTo(inboundReceiving.receive(c,finalSecond));
  var complete=warehouseBalances.document(c,WarehouseBalances.DocumentKind.INBOUND_NOTICE,notice);
  assertThat(complete.header().status()).isEqualTo("COMPLETED");
  assertThat(complete.lines()).extracting(WarehouseBalances.NoticeLineView::receivedQuantity)
      .usingElementComparator(BigDecimal::compareTo).containsExactly(new BigDecimal("100"),new BigDecimal("80"));
  assertThat(complete.custodies()).allSatisfy(custody->assertThat(custody.receiptId()).isNotNull());
  assertThat(warehouseBalances.trace(c,finalSecond.id()).stock().identityId()).isEqualTo(secondSku);
  verifyEntryAndReceiptDocuments(c,position,item,secondItem,owner,unit);

 }

 /** 同车两个品规，分两单实收；任一行越限时整张确认单回滚。 */
 private void verifyEntryAndReceiptDocuments(VerifiedTenantContext c,InboundReceiptReferences.Position position,
     InboundReceiptReferences.Item firstItem,InboundReceiptReferences.Item secondItem,String owner,String unit) {
  String notice=newId(),firstLine=newId(),secondLine=newId(),entryId=newId(),firstCustody=newId(),secondCustody=newId();
  inboundReceiving.issue(c,new InboundReceiving.Notice(notice,position.warehouseId(),owner,null,null,null,
      "ENTRY-"+newId(),null,null,List.of(
      new InboundReceiving.NoticeLine(firstLine,firstItem,unit,new BigDecimal("100"),null,null),
      new InboundReceiving.NoticeLine(secondLine,secondItem,unit,new BigDecimal("80"),null,null))));
  var entry=new InboundReceiving.Entry(entryId,notice,"QA-TRUCK","验收承运方",java.time.Instant.now(),"测试同车多行",List.of(
      new InboundReceiving.EntryLine(firstCustody,firstLine,position,newId(),new BigDecimal("100")),
      new InboundReceiving.EntryLine(secondCustody,secondLine,position,newId(),new BigDecimal("80"))));
  inboundReceiving.recordEntry(c,entry);
  inboundReceiving.recordEntry(c,entry);
  var before=warehouseBalances.inboundEntry(c,entryId);
  assertThat(before.lines()).extracting(WarehouseBalances.BatchLine::identityId)
      .containsExactly(firstItem.identityId(),secondItem.identityId());
  assertThat(before.lines()).allSatisfy(row->assertThat(row.processed()).isFalse());
  assertThat(before.vehicleNumber()).isEqualTo("QA-TRUCK");

  var accepted=new InboundReceiving.ReceiptLine(newId(),firstCustody,newId(),new BigDecimal("60"),new BigDecimal("40"),"外包装破损");
  var exceeds=new InboundReceiving.ReceiptLine(newId(),secondCustody,newId(),new BigDecimal("90"),BigDecimal.ZERO,null);
  var invalid=new InboundReceiving.ReceiptDocument(newId(),entryId,"WB-BATCH-01",null,List.of(accepted,exceeds));
  assertThatThrownBy(()->inboundReceiving.confirmReceiptDocument(c,invalid)).hasMessage("INBOUND_LIMIT_EXCEEDED");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bw_stock_receipt WHERE tenant_id=UUID_TO_BIN(?) AND receipt_id=UUID_TO_BIN(?)",
      Integer.class,c.tenantId().toString(),accepted.id())).isZero();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bw_inbound_receipt_document WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",
      Integer.class,c.tenantId().toString(),invalid.id())).isZero();

  var first=new InboundReceiving.ReceiptDocument(newId(),entryId,"WB-BATCH-01",null,List.of(accepted));
  inboundReceiving.confirmReceiptDocument(c,first);
  inboundReceiving.confirmReceiptDocument(c,first);
  var posted=warehouseBalances.receiptDocument(c,first.id());
  assertThat(posted.lines()).singleElement().satisfies(row->{
    assertThat(row.receivedQuantity()).isEqualByComparingTo("60");
    assertThat(row.rejectionQuantity()).isEqualByComparingTo("40");
    assertThat(row.stockStatus()).isEqualTo("PENDING_QUALITY");
  });
  assertThat(warehouseBalances.inboundEntry(c,entryId).lines()).extracting(WarehouseBalances.BatchLine::processed)
      .containsExactly(true,false);
  var rejected=new InboundReceiving.ReceiptLine(newId(),secondCustody,null,BigDecimal.ZERO,new BigDecimal("80"),"标识不一致");
  var second=new InboundReceiving.ReceiptDocument(newId(),entryId,"WB-BATCH-02",null,List.of(rejected));
  inboundReceiving.confirmReceiptDocument(c,second);
  assertThat(warehouseBalances.receiptDocument(c,second.id()).lines()).singleElement().satisfies(row->{
    assertThat(row.receiptId()).isNull();
    assertThat(row.stockStatus()).isEqualTo("REJECTED");
  });
  assertThat(warehouseBalances.inboundEntry(c,entryId).lines()).allSatisfy(row->assertThat(row.processed()).isTrue());
  var current=warehouseBalances.document(c,WarehouseBalances.DocumentKind.INBOUND_NOTICE,notice);
  assertThat(current.lines()).extracting(WarehouseBalances.NoticeLineView::receivedQuantity)
      .usingElementComparator(BigDecimal::compareTo).containsExactly(new BigDecimal("60"),BigDecimal.ZERO);
 }

 /** 实际调用仓储装配入口，验证煤炭车次独立接管、过账和故障回滚。 */
 private InboundReceiving.Receipt receiveCoalInBatches(VerifiedTenantContext context,
     InboundReceiptReferences.Position position, InboundReceiptReferences.Item item, String owner, String unit) throws Exception {
  String noticeId=UUID.randomUUID().toString();
  var inboundNotice=new InboundReceiving.Notice(noticeId,position.warehouseId(),owner,item,unit,new BigDecimal("120000"));
  verifyRejectedStockWrite("bw_inbound_notice","id",noticeId,"FALSE",()->inboundReceiving.issue(context,inboundNotice));
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bw_inbound_notice WHERE id=UUID_TO_BIN(?)",Integer.class,noticeId))
      .isZero();
  inboundReceiving.issue(context,inboundNotice);
  var unreceived=warehouseBalances.document(context,WarehouseBalances.DocumentKind.INBOUND_NOTICE,noticeId);
  assertThat(unreceived.header().completedQuantity()).isEqualByComparingTo("0");
  assertThat(unreceived.related()).isEmpty();
  assertThatThrownBy(()->warehouseBalances.document(context,WarehouseBalances.DocumentKind.OUTBOUND_NOTICE,noticeId))
      .hasMessage("OUTBOUND_ACCESS_DENIED");

  var failedCustody=new InboundReceiving.Custody(newId(),noticeId,position,newId());
  verifyRejectedStockWrite("bw_inbound_custody","id",failedCustody.id(),"FALSE",
      ()->inboundReceiving.takeCustody(context,failedCustody));
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bw_inbound_custody WHERE id=UUID_TO_BIN(?)",Integer.class,failedCustody.id()))
      .isZero();
  assertThat(jdbc.queryForObject("SELECT flow_version FROM bw_inbound_notice WHERE id=UUID_TO_BIN(?)",
      Integer.class,noticeId)).isEqualTo(2);
  jdbc.update("UPDATE bw_inbound_notice SET flow_version=1 WHERE id=UUID_TO_BIN(?)",noticeId);
  var firstInput=coalReceipt(context,noticeId,position,item,owner,unit,"39000");
  var first=new InboundReceiving.Receipt(firstInput.id(),firstInput.custodyId(),firstInput.measurementId(),
      firstInput.references(),"收货批次-01");
  verifyExcludedWarehouseOperations(context,Map.of(
      "BulkInboundReceipt.issue",()->inboundReceiving.issue(context,inboundNotice),
      "BulkInboundReceipt.take_custody",()->inboundReceiving.takeCustody(context,failedCustody),
      "BulkInboundReceipt.receive",()->inboundReceiving.receive(context,first)));
  var second=coalReceipt(context,noticeId,position,item,owner,unit,"39000");
  var over=coalReceipt(context,noticeId,position,item,owner,unit,"43000");
  var failed=coalReceipt(context,noticeId,position,item,owner,unit,"42000");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bw_inbound_receipt WHERE tenant_id=UUID_TO_BIN(?)",
      Integer.class,context.tenantId().toString())).isZero();
  var firstPosting=inboundReceiving.receive(context,first);
  assertThat(firstPosting.quantity()).isEqualByComparingTo("39000");
  assertThat(inboundReceiving.receive(context,first)).isEqualTo(firstPosting);
  assertThat(warehouseBalances.trace(context,first.id()).stock().receiptBatchNo()).isEqualTo("收货批次-01");
  var directReceipt=warehouseBalances.document(context,WarehouseBalances.DocumentKind.INBOUND_RECEIPT,first.id());
  assertThat(directReceipt.header().parentId()).isEqualTo(noticeId);
  assertThat(directReceipt.header().quantity()).isEqualByComparingTo("39000");
  assertThat(warehouseBalances.document(context,WarehouseBalances.DocumentKind.INBOUND_NOTICE,noticeId).related())
      .anyMatch(line->line.kind().equals("RECEIPT")&&line.id().equals(first.id()));

  var changedBatch=new InboundReceiving.Receipt(first.id(),first.custodyId(),first.measurementId(),
      first.references(),"收货批次-02");
  assertThatThrownBy(()->inboundReceiving.receive(context,changedBatch)).hasMessage("INBOUND_REQUEST_CONFLICT");
  var changed=new InboundReceiving.Receipt(first.id(),first.custodyId(),UUID.randomUUID().toString(),first.references());
  assertThatThrownBy(()->inboundReceiving.receive(context,changed)).hasMessage("INBOUND_REQUEST_CONFLICT");
  assertThat(inboundReceiving.receive(context,second).quantity()).isEqualByComparingTo("39000");
  assertThatThrownBy(()->inboundReceiving.receive(context,over)).hasMessage("INBOUND_LIMIT_EXCEEDED");
  // 仅在隔离测试数据库对指定确认注入故障，确保确认 INSERT 后出现真实库存 SQL 失败。
  jdbc.execute("ALTER TABLE bw_stock_receipt ADD CONSTRAINT bw_test_stock_failure CHECK(receipt_id<>X'"
      +failed.id().replace("-","")+"')");
  try {
   assertThatThrownBy(()->inboundReceiving.receive(context,failed)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  } finally {
   jdbc.execute("ALTER TABLE bw_stock_receipt DROP CHECK bw_test_stock_failure");
  }
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bw_inbound_receipt WHERE id=UUID_TO_BIN(?)",
      Integer.class,failed.id())).isZero();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bw_inbound_custody WHERE id=UUID_TO_BIN(?)",
      Integer.class,failed.custodyId())).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT SUM(pending_quantity) FROM bw_stock_receipt WHERE tenant_id=UUID_TO_BIN(?)",
      BigDecimal.class,context.tenantId().toString())).isEqualByComparingTo("78000");
  assertThat(jdbc.queryForObject("SELECT received_quantity FROM bw_inbound_notice WHERE id=UUID_TO_BIN(?)",
      BigDecimal.class,noticeId)).isEqualByComparingTo("78000");
  assertThat(inboundReceiving.receive(context,failed).quantity()).isEqualByComparingTo("42000");
  var noticePlan=jdbc.queryForMap("""
      EXPLAIN SELECT received_quantity FROM bw_inbound_notice
      WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) FOR UPDATE
      """,context.tenantId().toString(),noticeId);
  assertThat(noticePlan.get("key")).isEqualTo("PRIMARY");
  var receiptPlan=jdbc.queryForMap("""
      EXPLAIN SELECT id,quantity FROM bw_inbound_receipt
      WHERE tenant_id=UUID_TO_BIN(?) AND custody_id=UUID_TO_BIN(?) FOR UPDATE
      """,context.tenantId().toString(),first.custodyId());
  assertThat(receiptPlan.get("key")).isNotNull();
  assertThat(((Number) receiptPlan.get("rows")).longValue()).isLessThanOrEqualTo(1L);
  assertThat(jdbc.queryForObject("SELECT SUM(pending_quantity) FROM bw_stock_receipt WHERE tenant_id=UUID_TO_BIN(?)",
      BigDecimal.class,context.tenantId().toString())).isEqualByComparingTo("120000");
  assertBlockedStock(context,failed.id(),OutboundStock.Gate.PENDING_QUALITY);
  verifyTitleAndQuality(context,first,second);
  verifyOutboundStockGate(context,first.id());
  return first;
 }

 /** 权利与质量独立，合格先于权利登记或权利先于合格均保持在库总量。 */
 private void verifyTitleAndQuality(VerifiedTenantContext context,InboundReceiving.Receipt first,
     InboundReceiving.Receipt second) throws Exception {
  var quality=new InboundDisposition.Decision(first.id(),UUID.randomUUID().toString());
  var json=new tools.jackson.databind.ObjectMapper();
  var session=mvc.perform(post("/bff/tenants/current").with(authenticated(context.accountId().toString(),0))
      .with(csrf()).contentType("application/json")
      .content(json.writeValueAsString(Map.of("tenantId",context.tenantId().toString())))).andReturn().getResponse();
  assertThat(session.getStatus()).isEqualTo(200);
  var cookie=session.getCookie("JSESSIONID");
  // 首次放行先保存凭证再更新库存，此处在隔离库中阻断库存步骤。
  bindReferenceContext(context);
  jdbc.execute("ALTER TABLE bw_stock_receipt ADD CONSTRAINT bw_test_release_failure CHECK(receipt_id<>X'"
      +first.id().replace("-","")+"' OR pending_quantity>0)");
  try {
   assertThatThrownBy(()->inboundDisposition.releaseQuality(context,quality))
       .isInstanceOf(org.springframework.dao.DataAccessException.class);
  } finally {
   jdbc.execute("ALTER TABLE bw_stock_receipt DROP CHECK bw_test_release_failure");
  }
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bw_inbound_quality_release WHERE receipt_id=UUID_TO_BIN(?)",
      Integer.class,first.id())).isZero();
  assertThat(jdbc.queryForObject("SELECT pending_quantity FROM bw_stock_receipt WHERE receipt_id=UUID_TO_BIN(?)",
      BigDecimal.class,first.id())).isEqualByComparingTo("39000");
  String forbiddenActor=UUID.randomUUID().toString();
  var injected=new LinkedHashMap<String,Object>();
  injected.put("receiptId",quality.receiptId());
  injected.put("evidenceId",quality.evidenceId());
  injected.put("createdBy",forbiddenActor);
  injected.put("updatedBy",forbiddenActor);
  injected.put("createdAt","2001-01-01T00:00:00Z");
  injected.put("updatedAt","2001-01-01T00:00:00Z");
  var release=mvc.perform(post("/api/bulk-inbound-quality-releases").cookie(cookie)
      .with(csrf()).contentType("application/json").content(json.writeValueAsString(injected))).andReturn().getResponse();
  if (release.getStatus()==400) {
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bw_inbound_quality_release WHERE receipt_id=UUID_TO_BIN(?)",
       Integer.class,first.id())).isZero();
   release=mvc.perform(post("/api/bulk-inbound-quality-releases").cookie(cookie)
       .with(csrf()).contentType("application/json").content(json.writeValueAsString(quality))).andReturn().getResponse();
  }
  var audit=jdbc.queryForMap("""
      SELECT BIN_TO_UUID(created_by) createdBy,BIN_TO_UUID(updated_by) updatedBy, CAST(YEAR(created_at) AS UNSIGNED) createdYear
      FROM bw_inbound_quality_release WHERE tenant_id=UUID_TO_BIN(?) AND receipt_id=UUID_TO_BIN(?)
      """,context.tenantId().toString(),first.id());
  assertThat(audit.get("createdBy")).isEqualTo(context.accountId().toString());
  assertThat(audit.get("updatedBy")).isEqualTo(context.accountId().toString());
  assertThat(((Number)audit.get("createdYear")).intValue()).isGreaterThan(2001);
  assertThat(release.getStatus()).as(release.getContentAsString()).isEqualTo(200);
  var body=json.readTree(release.getContentAsString());
  assertThat(body.path("pendingQuantity").decimalValue()).isEqualByComparingTo("0");
  assertThat(body.path("registeredQuantity").decimalValue()).isEqualByComparingTo("0");
  bindReferenceContext(context);
  assertBlockedStock(context,first.id(),OutboundStock.Gate.TITLE_UNREGISTERED);
  var effective=java.time.Instant.parse("2026-11-20T09:00:00Z");
  var titleFirst=new InboundDisposition.TitleDecision(first.id(),UUID.randomUUID().toString(),effective);
  var titleSecond=new InboundDisposition.TitleDecision(second.id(),titleFirst.evidenceId(),effective);
  verifyExcludedWarehouseOperations(context,Map.of(
      "BulkInboundTitleRegistration.register",()->inboundDisposition.registerTitle(context,titleFirst),
      "BulkInboundQualityRelease.release",()->inboundDisposition.releaseQuality(context,quality)));
  verifyRejectedStockWrite("bw_inbound_title","receipt_id",first.id(),"FALSE",
      ()->inboundDisposition.registerTitle(context,titleFirst));
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bw_inbound_title WHERE receipt_id=UUID_TO_BIN(?)",Integer.class,first.id()))
      .isZero();
  assertThat(inboundDisposition.registerTitle(context,titleFirst).registeredQuantity()).isEqualByComparingTo("39000");
  var registered=inboundDisposition.registerTitle(context,titleSecond);
  assertThat(registered.pendingQuantity()).isEqualByComparingTo("39000");
  assertThat(inboundDisposition.registerTitle(context,titleSecond)).isEqualTo(registered);
  var releaseSecond=new InboundDisposition.Decision(second.id(),quality.evidenceId());
  assertThat(inboundDisposition.releaseQuality(context,releaseSecond).pendingQuantity()).isEqualByComparingTo("0");
  assertThat(inboundDisposition.releaseQuality(context,releaseSecond).registeredQuantity()).isEqualByComparingTo("39000");
  assertThat(warehouseBalances.document(context,WarehouseBalances.DocumentKind.INBOUND_TITLE,second.id())
      .header().quantity()).isEqualByComparingTo("39000");
  assertThat(warehouseBalances.document(context,WarehouseBalances.DocumentKind.INBOUND_QUALITY,second.id())
      .header().evidenceId()).isEqualTo(releaseSecond.evidenceId());

  assertThatThrownBy(()->inboundDisposition.releaseQuality(context,
      new InboundDisposition.Decision(second.id(),UUID.randomUUID().toString()))).hasMessage("INBOUND_QUALITY_CONFLICT");
  assertThat(jdbc.queryForObject("SELECT SUM(inbound_quantity) FROM bw_stock_receipt WHERE tenant_id=UUID_TO_BIN(?)",
      BigDecimal.class,context.tenantId().toString())).isEqualByComparingTo("120000");
  assertThat(jdbc.queryForObject("SELECT SUM(pending_quantity) FROM bw_stock_receipt WHERE tenant_id=UUID_TO_BIN(?)",
      BigDecimal.class,context.tenantId().toString())).isEqualByComparingTo("42000");
  assertThat(jdbc.queryForObject("SELECT SUM(quantity) FROM bw_inbound_title WHERE tenant_id=UUID_TO_BIN(?)",
      BigDecimal.class,context.tenantId().toString())).isEqualByComparingTo("78000");
 }

 /** 通过真实库存用例验证四种拒配状态，并检查重放不会再占一笔。 */
 private void verifyOutboundStockGate(VerifiedTenantContext context,String receiptId) {
  assertThat(outboundStock.check(context,receiptId)).isEqualTo(OutboundStock.Gate.AVAILABLE);
  var restriction=new OutboundStock.Freeze(UUID.randomUUID().toString(),receiptId,UUID.randomUUID().toString());
  verifyRestrictedStockOperations(context,receiptId,restriction);
  verifyRejectedStockWrite("bw_stock_restriction","id",restriction.id(),"FALSE",
      ()->outboundStock.freeze(context,restriction));
  assertThat(outboundStock.restrictionHistory(context,receiptId))
      .noneMatch(history->history.id().equals(restriction.id()));
  outboundStock.freeze(context,restriction);
  assertBlockedStock(context,receiptId,OutboundStock.Gate.FROZEN);
  var second=new OutboundStock.Freeze(newId(),receiptId,newId());
  outboundStock.freeze(context,second);
  var lift=new OutboundStock.Freeze(restriction.id(),receiptId,newId());
  verifyRejectedStockWrite("bw_stock_restriction","id",lift.id(),"release_evidence_id IS NULL",
      ()->outboundStock.lift(context,lift));
  assertThat(outboundStock.restrictionHistory(context,receiptId)).filteredOn(history->history.id().equals(lift.id()))
      .allSatisfy(history->assertThat(history.releaseEvidenceId()).isNull()).hasSize(1);
  outboundStock.lift(context,lift);
  assertThat(outboundStock.restrictionDocument(context,restriction.id()).releaseEvidenceId()).isEqualTo(lift.evidenceId());

  outboundStock.lift(context,lift);
  assertBlockedStock(context,receiptId,OutboundStock.Gate.FROZEN);
  assertThat(outboundStock.restrictionHistory(context,receiptId))
      .anySatisfy(row -> {
        assertThat(row.id()).isEqualTo(restriction.id());
        assertThat(row.evidenceId()).isEqualTo(restriction.evidenceId());
        assertThat(row.releaseEvidenceId()).isEqualTo(lift.evidenceId());
        assertThat(row.createdBy()).isEqualTo(context.accountId().toString());
        assertThat(java.time.Instant.parse(row.updatedAt())).isAfterOrEqualTo(java.time.Instant.parse(row.createdAt()));
      })
      .anySatisfy(row -> {
        assertThat(row.id()).isEqualTo(second.id());
        assertThat(row.releaseEvidenceId()).isNull();
      });
  outboundStock.lift(context,new OutboundStock.Freeze(second.id(),receiptId,newId()));
  var hold=new OutboundStock.Hold(UUID.randomUUID().toString(),receiptId);
  verifyRejectedStockWrite("bw_stock_occupation","id",hold.id(),"FALSE",()->outboundStock.reserve(context,hold));
  assertThat(outboundStock.check(context,receiptId)).isEqualTo(OutboundStock.Gate.AVAILABLE);
  var posted=outboundStock.reserve(context,hold);
  assertThat(outboundStock.reserve(context,hold)).isEqualTo(posted);
  assertBlockedStock(context,receiptId,OutboundStock.Gate.OCCUPIED);
  verifyRejectedStockWrite("bw_stock_occupation","id",hold.id(),"released_at IS NULL",()->outboundStock.release(context,hold));
  assertThat(outboundStock.check(context,receiptId)).isEqualTo(OutboundStock.Gate.OCCUPIED);
  outboundStock.release(context,hold);
  assertThat(outboundStock.check(context,receiptId)).isEqualTo(OutboundStock.Gate.AVAILABLE);
 }

 /**
  * @param table 隔离夹具中受故障注入的固定业务表
  * @param keyColumn 命令写入行的固定键列
  * @param id 唯一夹具记录标识
  * @param allowed 为该记录保留原值的固定 SQL 条件
  * @param operation 经过正式装配的仓储写入
  */
 private void verifyRejectedStockWrite(String table,String keyColumn,String id,String allowed,Runnable operation) {
  String ddl=table+" ADD CONSTRAINT bw_test_write_failure CHECK("+keyColumn+"<>X'"
      +UUID.fromString(id).toString().replace("-","")+"' OR "+allowed+")";
  jdbc.execute("ALTER TABLE "+ddl);
  try {
   assertThatThrownBy(operation::run).isInstanceOf(org.springframework.dao.DataAccessException.class);
  } finally {
   jdbc.execute("ALTER TABLE "+table+" DROP CHECK bw_test_write_failure");
  }
 }

 /**
  * @param context 有仓储功能权限的租户管理员
  * @param receiptId 位于被排除仓库的有效库存
  * @param proposed 用于首次冻结的合法业务凭证
  */
 private void verifyRestrictedStockOperations(VerifiedTenantContext context,String receiptId,OutboundStock.Freeze proposed) {
  String allowedWarehouse=masterData.create(context,MasterData.Kind.WAREHOUSE,
      new MasterData.Input("TRACE-SCOPE-W","行范围验收仓",Map.of(),0)).get("id");
  var admin=new com.appnexus.platform.authorization.RoleManagement.Context(
      context.accountId(),context.tenantId(),context.memberId());
  UUID role=UUID.fromString(jdbc.queryForObject("""
      SELECT BIN_TO_UUID(id) FROM access_role WHERE tenant_id=UUID_TO_BIN(?) AND system_key LIKE 'TENANT_ADMIN:%'
      """,String.class,context.tenantId().toString()));
  UUID permission=UUID.fromString(jdbc.queryForObject(
      "SELECT BIN_TO_UUID(id) FROM access_permission WHERE code='masterdata.warehouse:read'",String.class));
  var original=rolePermissions.getAuthorization(admin,role);
  var initial=outboundStock.restrictionHistory(context,receiptId);
  var hold=new OutboundStock.Hold(newId(),receiptId);
  var activeRestriction=new OutboundStock.Freeze(newId(),receiptId,newId());
  outboundStock.reserve(context,hold);
  outboundStock.freeze(context,activeRestriction);
  try {
   scopeCrossReturnWarehouses(admin,role,permission,Set.of(UUID.fromString(allowedWarehouse)));
   assertThatThrownBy(()->outboundStock.freeze(context,proposed)).hasMessage("MASTER_DATA_INPUT_INVALID");
   assertThatThrownBy(()->outboundStock.lift(context,new OutboundStock.Freeze(activeRestriction.id(),receiptId,newId())))
       .hasMessage("MASTER_DATA_INPUT_INVALID");
   assertThatThrownBy(()->outboundStock.reserve(context,new OutboundStock.Hold(newId(),receiptId)))
       .hasMessage("MASTER_DATA_INPUT_INVALID");
   assertThatThrownBy(()->outboundStock.release(context,hold)).hasMessage("MASTER_DATA_INPUT_INVALID");
  } finally {
   var current=rolePermissions.getAuthorization(admin,role);
   rolePermissions.replaceAuthorization(admin,role,current.roleVersion(),original.permissionIds(),original.dataPolicies(),
       "恢复库存监管与占用验收权限");
  }
  assertThat(outboundStock.restrictionHistory(context,receiptId)).hasSize(initial.size()+1);
  outboundStock.lift(context,new OutboundStock.Freeze(activeRestriction.id(),receiptId,newId()));
  outboundStock.release(context,hold);
 }

 /**
  * @param context 持有原操作功能权限的真实主体
  * @param commands 使用合法仓储夹具、须读取被排除仓库的操作
  */
 private void verifyExcludedWarehouseOperations(VerifiedTenantContext context,Map<String,Runnable> commands) {
  String allowedWarehouse=masterData.create(context,MasterData.Kind.WAREHOUSE,
      new MasterData.Input("SCOPE-"+newId().substring(0,8),"范围负向验收仓",Map.of(),0)).get("id");
  var admin=new com.appnexus.platform.authorization.RoleManagement.Context(
      context.accountId(),context.tenantId(),context.memberId());
  UUID role=UUID.fromString(jdbc.queryForObject("""
      SELECT BIN_TO_UUID(id) FROM access_role WHERE tenant_id=UUID_TO_BIN(?) AND system_key LIKE 'TENANT_ADMIN:%'
      """,String.class,context.tenantId().toString()));
  UUID permission=UUID.fromString(jdbc.queryForObject(
      "SELECT BIN_TO_UUID(id) FROM access_permission WHERE code='masterdata.warehouse:read'",String.class));
  var original=rolePermissions.getAuthorization(admin,role);
  try {
   scopeCrossReturnWarehouses(admin,role,permission,Set.of(UUID.fromString(allowedWarehouse)));
   for (var entry:commands.entrySet()) {
    assertThatThrownBy(entry.getValue()::run).as("仓库读取范围拒绝 %s",entry.getKey())
        .satisfies(error->assertThat(error.getMessage()).isIn("MASTER_DATA_INPUT_INVALID","INBOUND_ACCESS_DENIED"));
   }
  } finally {
   var current=rolePermissions.getAuthorization(admin,role);
   var restored=rolePermissions.replaceAuthorization(admin,role,current.roleVersion(),original.permissionIds(),original.dataPolicies(),
       "恢复仓储逐操作范围验收策略");
   assertThat(restored.dataPolicies()).isEqualTo(original.dataPolicies());
  }
 }

 /** 从已过账实物形成独立的 51、49、3 吨卷，验证重放与对三层额度的原子调整。 */
 private void verifyCoilOutbound(VerifiedTenantContext c,InboundReceiptReferences.Position position,
     InboundReceiptReferences.Item item,String owner,String unit,String unitCode) throws Exception {
  String inbound=UUID.randomUUID().toString();
  inboundReceiving.issue(c,new InboundReceiving.Notice(inbound,position.warehouseId(),owner,item,unit,new BigDecimal("103000")));
  var first=coalReceipt(c,inbound,position,item,owner,unit,"51000");
  var second=coalReceipt(c,inbound,position,item,owner,unit,"49000");
  var third=coalReceipt(c,inbound,position,item,owner,unit,"3000");
  String title=UUID.randomUUID().toString(),quality=UUID.randomUUID().toString();
  for (var receipt:List.of(first,second,third)) {
   inboundReceiving.receive(c,receipt);
   inboundDisposition.registerTitle(c,new InboundDisposition.TitleDecision(receipt.id(),title,
       java.time.Instant.parse("2026-11-20T09:00:00Z")));
   inboundDisposition.releaseQuality(c,new InboundDisposition.Decision(receipt.id(),quality));
  }
  var firstIdentity=new CoilOutbound.Coil(first.id(),"COIL-51",unitCode);
  verifyRejectedStockWrite("bw_stock_coil","receipt_id",first.id(),"FALSE",()->coilOutbound.identify(c,firstIdentity));
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bw_stock_coil WHERE receipt_id=UUID_TO_BIN(?)",Integer.class,first.id()))
      .isZero();
  coilOutbound.identify(c,firstIdentity);
  assertThatThrownBy(()->coilOutbound.identify(c,new CoilOutbound.Coil(second.id(),"COIL-51",unitCode)))
      .hasMessage("OUTBOUND_REQUEST_CONFLICT");
  coilOutbound.identify(c,new CoilOutbound.Coil(second.id(),"COIL-49",unitCode));
  coilOutbound.identify(c,new CoilOutbound.Coil(third.id(),"COIL-03",unitCode));
  assertThatThrownBy(()->outboundStock.reserve(c,new OutboundStock.Hold(UUID.randomUUID().toString(),first.id())))
      .hasMessage("OUTBOUND_COIL_QUOTA_REQUIRED");

  String notice=UUID.randomUUID().toString(),auth=UUID.randomUUID().toString();
  var expiry=java.time.Instant.now().plusSeconds(3600);
  var outboundNotice=new CoilOutbound.Notice(notice,position.warehouseId(),owner,item,unit,unitCode,new BigDecimal("100000"));
  verifyRejectedStockWrite("bw_outbound_notice","id",notice,"FALSE",()->coilOutbound.issue(c,outboundNotice));
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bw_outbound_notice WHERE id=UUID_TO_BIN(?)",Integer.class,notice))
      .isZero();
  coilOutbound.issue(c,outboundNotice);
  var unallocated=warehouseBalances.document(c,WarehouseBalances.DocumentKind.OUTBOUND_NOTICE,notice);
  assertThat(unallocated.header().reserved()).isEqualByComparingTo("0");
  assertThat(unallocated.header().quantity()).isEqualByComparingTo("100000");

  var authorization=new CoilOutbound.Authorization(auth,notice,owner,expiry,new BigDecimal("100000"));
  verifyRejectedStockWrite("bw_pickup_authorization","id",auth,"FALSE",()->coilOutbound.authorize(c,authorization));
  assertThat(jdbc.queryForObject("SELECT committed FROM bw_outbound_notice WHERE id=UUID_TO_BIN(?)",BigDecimal.class,notice))
      .isEqualByComparingTo("0");
  coilOutbound.authorize(c,authorization);
  assertThat(warehouseBalances.authorization(c,auth).noticeId()).isEqualTo(notice);

  assertThatThrownBy(()->coilOutbound.authorize(c,new CoilOutbound.Authorization(
      UUID.randomUUID().toString(),notice,owner,expiry,BigDecimal.ONE))).hasMessage("OUTBOUND_NOTICE_COMMITMENT_LIMIT");
  String small=UUID.randomUUID().toString(), v1=UUID.randomUUID().toString(), v2=UUID.randomUUID().toString();
  coilOutbound.plan(c,new CoilOutbound.Trip(small,auth,"TRUCK-SMALL",new BigDecimal("50000")));
  var planOne=new CoilOutbound.Trip(v1,auth,"TRUCK-1",new BigDecimal("55000"));
  verifyRejectedStockWrite("bw_outbound_trip","id",v1,"FALSE",()->coilOutbound.plan(c,planOne));
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bw_outbound_trip WHERE id=UUID_TO_BIN(?)",Integer.class,v1))
      .isZero();
  coilOutbound.plan(c,planOne);
  var planned=warehouseBalances.document(c,WarehouseBalances.DocumentKind.OUTBOUND_TRIP,v1);
  assertThat(planned.header().vehicleNumber()).isEqualTo("TRUCK-1");
  assertThat(planned.related()).isEmpty();

  coilOutbound.plan(c,new CoilOutbound.Trip(v2,auth,"TRUCK-2",new BigDecimal("55000")));
  var preReserve=new CoilOutbound.PreReserve(auth,BigDecimal.ZERO,new BigDecimal("100000"));
  verifyRejectedStockWrite("bw_pickup_authorization","id",auth,"reserved=0",()->coilOutbound.preReserve(c,preReserve));
  assertThat(jdbc.queryForObject("SELECT reserved FROM bw_outbound_notice WHERE id=UUID_TO_BIN(?)",BigDecimal.class,notice))
      .isEqualByComparingTo("0");
  coilOutbound.preReserve(c,preReserve);
  assertThatThrownBy(()->coilOutbound.preReserve(c,new CoilOutbound.PreReserve(auth,BigDecimal.ZERO,BigDecimal.ONE)))
      .hasMessage("OUTBOUND_REQUEST_CONFLICT");
  assertThatThrownBy(()->coilOutbound.allocate(c,new CoilOutbound.Allocate(UUID.randomUUID().toString(),small,first.id())))
      .hasMessage("OUTBOUND_TRIP_LIMIT");
  assertThat(outboundStock.check(c,first.id())).isEqualTo(OutboundStock.Gate.AVAILABLE);

  var one=new CoilOutbound.Allocate(UUID.randomUUID().toString(),v1,first.id());
  var posted=coilOutbound.allocate(c,one);
  verifyExcludedWarehouseOperations(c,Map.of(
      "BulkCoilOutbound.identify",()->coilOutbound.identify(c,firstIdentity),
      "BulkCoilOutbound.issue",()->coilOutbound.issue(c,outboundNotice),
      "BulkCoilOutbound.authorize",()->coilOutbound.authorize(c,authorization),
      "BulkCoilOutbound.plan",()->coilOutbound.plan(c,planOne),
      "BulkCoilOutbound.pre_reserve",()->coilOutbound.preReserve(c,preReserve),
      "BulkCoilOutbound.close",()->coilOutbound.close(c,new CoilOutbound.CloseAuthorization(auth)),
      "BulkCoilOutbound.allocate",()->coilOutbound.allocate(c,one),
      "BulkCoilOutbound.cancel",()->coilOutbound.cancel(c,one)));
  assertThat(posted.serial()).isEqualTo("COIL-51");
  assertThat(posted.notice().reserved()).isEqualByComparingTo("100000");
  assertThat(posted.authorization().reserved()).isEqualByComparingTo("100000");
  assertThat(posted.trip().reserved()).isEqualByComparingTo("51000");
  assertThat(coilOutbound.allocate(c,one)).isEqualTo(posted);
  assertThatThrownBy(()->coilOutbound.allocate(c,new CoilOutbound.Allocate(UUID.randomUUID().toString(),v2,first.id())))
      .hasMessage("OUTBOUND_OCCUPIED");
  assertThatThrownBy(()->outboundStock.release(c,new OutboundStock.Hold(one.id(),first.id())))
      .hasMessage("OUTBOUND_COIL_QUOTA_REQUIRED");

  var two=new CoilOutbound.Allocate(UUID.randomUUID().toString(),v2,second.id());
  var json=new tools.jackson.databind.ObjectMapper();
  var session=mvc.perform(post("/bff/tenants/current").with(authenticated(c.accountId().toString(),0))
      .with(csrf()).contentType("application/json")
      .content(json.writeValueAsString(Map.of("tenantId",c.tenantId().toString())))).andReturn().getResponse();
  assertThat(session.getStatus()).isEqualTo(200);
  var response=mvc.perform(post("/api/bulk-coil-allocations").cookie(session.getCookie("JSESSIONID"))
      .with(csrf()).contentType("application/json").content(json.writeValueAsString(two))).andReturn().getResponse();
  assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
  assertThat(json.readTree(response.getContentAsString()).path("trip").path("reserved").decimalValue())
      .isEqualByComparingTo("49000");
  bindReferenceContext(c);
  var extra=new CoilOutbound.Allocate(UUID.randomUUID().toString(),v2,third.id());
  assertThatThrownBy(()->coilOutbound.allocate(c,extra)).hasMessage("OUTBOUND_NOTICE_LIMIT");
  assertThat(outboundStock.check(c,third.id())).isEqualTo(OutboundStock.Gate.AVAILABLE);

  var frozen=new OutboundStock.Freeze(UUID.randomUUID().toString(),first.id(),UUID.randomUUID().toString());
  outboundStock.freeze(c,frozen);
  assertThat(outboundStock.check(c,first.id())).isEqualTo(OutboundStock.Gate.FROZEN);
  verifyRejectedStockWrite("bw_outbound_trip","id",v1,"reserved=51000",()->coilOutbound.cancel(c,one));
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bw_stock_occupation WHERE id=UUID_TO_BIN(?) AND released_at IS NULL",
      Integer.class,one.id())).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT reserved FROM bw_outbound_notice WHERE id=UUID_TO_BIN(?)",BigDecimal.class,notice))
      .isEqualByComparingTo("100000");
  coilOutbound.cancel(c,one);
  coilOutbound.cancel(c,one);
  assertThat(outboundStock.check(c,first.id())).isEqualTo(OutboundStock.Gate.FROZEN);
  assertThatThrownBy(()->coilOutbound.allocate(c,new CoilOutbound.Allocate(UUID.randomUUID().toString(),v1,first.id())))
      .hasMessage("OUTBOUND_FROZEN");
  outboundStock.lift(c,new OutboundStock.Freeze(frozen.id(),first.id(),UUID.randomUUID().toString()));
  assertThat(outboundStock.check(c,first.id())).isEqualTo(OutboundStock.Gate.AVAILABLE);
  jdbc.execute("ALTER TABLE bw_coil_allocation ADD CONSTRAINT bw_test_coil_failure CHECK(id<>X'"
      +extra.id().replace("-","")+"')");
  try {
   assertThatThrownBy(()->coilOutbound.allocate(c,extra)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  } finally {
   jdbc.execute("ALTER TABLE bw_coil_allocation DROP CHECK bw_test_coil_failure");
  }
  assertThat(outboundStock.check(c,third.id())).isEqualTo(OutboundStock.Gate.AVAILABLE);
  assertThat(jdbc.queryForObject("SELECT reserved FROM bw_outbound_notice WHERE id=UUID_TO_BIN(?)",
      BigDecimal.class,notice)).isEqualByComparingTo("49000");
  var replacement=coilOutbound.allocate(c,extra);
  assertThat(replacement.notice().reserved()).isEqualByComparingTo("52000");
  assertThat(replacement.authorization().reserved()).isEqualByComparingTo("52000");
  assertThat(replacement.trip().reserved()).isEqualByComparingTo("52000");
  var close=new CoilOutbound.CloseAuthorization(auth);
  assertThatThrownBy(()->coilOutbound.close(c,close)).hasMessage("OUTBOUND_ACTIVE_TRIP_OCCUPATION");
  coilOutbound.cancel(c,extra);
  verifyConcurrentCoilAllocation(c,v1,v2,third.id());
  var index=jdbc.queryForMap("""
      EXPLAIN SELECT id FROM bw_stock_occupation WHERE tenant_id=UUID_TO_BIN(?)
      AND active_receipt_id=UUID_TO_BIN(?)
      """,c.tenantId().toString(),second.id());
  assertThat(index.get("key")).isNotNull();
  assertThat(((Number)index.get("rows")).longValue()).isLessThanOrEqualTo(1L);
  coilOutbound.cancel(c,two);
  coilOutbound.preReserve(c,new CoilOutbound.PreReserve(auth,BigDecimal.ZERO,new BigDecimal("10000")));
  verifyRejectedStockWrite("bw_pickup_authorization","id",auth,"closed_at IS NULL",()->coilOutbound.close(c,close));
  assertThat(jdbc.queryForObject("SELECT unassigned FROM bw_pickup_authorization WHERE id=UUID_TO_BIN(?)",BigDecimal.class,auth))
      .isEqualByComparingTo("10000");
  assertThat(jdbc.queryForObject("SELECT reserved FROM bw_outbound_notice WHERE id=UUID_TO_BIN(?)",BigDecimal.class,notice))
      .isEqualByComparingTo("10000");
  coilOutbound.close(c,close);
  coilOutbound.close(c,close);
  assertThatThrownBy(()->coilOutbound.allocate(c,new CoilOutbound.Allocate(UUID.randomUUID().toString(),v1,third.id())))
      .hasMessage("OUTBOUND_AUTHORIZATION_EXPIRED");
  assertThat(jdbc.queryForObject("SELECT reserved FROM bw_outbound_notice WHERE id=UUID_TO_BIN(?)",
      BigDecimal.class,notice)).isEqualByComparingTo("0");
  coilOutbound.authorize(c,new CoilOutbound.Authorization(UUID.randomUUID().toString(),notice,owner,expiry,
      new BigDecimal("100000")));

 }

 /**
  * @param context 两个事务的可信测试主体
  * @param first 先写入并持锁的操作
  * @param competing 在先行事务未提交时启动的独立操作
  * @return 先行事务提交后所得的竞争结果
  */
 private <T> T commitWhileCompeting(VerifiedTenantContext context,Runnable first,
     java.util.concurrent.Callable<T> competing) throws Exception {
  var ready=new java.util.concurrent.CountDownLatch(1);
  var worker=new java.util.concurrent.FutureTask<T>(()->{
   bindReferenceContext(context);
   try {
    ready.countDown();
    return competing.call();
   } finally {
    org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
   }
  });
  var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
  try {
   new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status->{
    first.run();
    executor.execute(worker);
    try {
     assertThat(ready.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
     assertThatThrownBy(()->worker.get(200,java.util.concurrent.TimeUnit.MILLISECONDS))
         .isInstanceOf(java.util.concurrent.TimeoutException.class);
    } catch (InterruptedException interrupted) {
     Thread.currentThread().interrupt();
     throw new IllegalStateException(interrupted);
    }
   });
   return worker.get(15,java.util.concurrent.TimeUnit.SECONDS);
  } finally {
   executor.shutdownNow();
  }
 }

 /** 对正式控制器的每个写入口验证有租户身份但无业务权限时拒绝。 */
 private void verifyAllWarehouseWritesDenyUnprivilegedMember(VerifiedTenantContext context) throws Exception {
  var session=mvc.perform(post("/bff/tenants/current").with(authenticated(context.accountId().toString(),0))
      .with(csrf()).contentType("application/json")
      .content(new tools.jackson.databind.ObjectMapper().writeValueAsString(Map.of("tenantId",context.tenantId().toString()))))
      .andReturn().getResponse();
  assertThat(session.getStatus()).isEqualTo(200);
  var routes=new java.util.TreeMap<String,Class<?>>();
  for (Class<?> controller : List.of(com.appnexus.bulkwarehousing.adapter.web.InboundReceivingController.class,
      com.appnexus.bulkwarehousing.adapter.web.OutboundStockController.class)) {
   for (var method:controller.getDeclaredMethods()) {
    var mapping=method.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class);
    if (mapping==null) continue;
    var requestType=java.util.Arrays.stream(method.getParameters())
        .filter(parameter->parameter.isAnnotationPresent(org.springframework.web.bind.annotation.RequestBody.class))
        .findFirst().orElseThrow().getType();
    for (var path:mapping.value()) assertThat(routes.put(path,requestType)).isNull();
   }
  }
  assertThat(routes).hasSize(31).containsKeys("/api/bulk-inbound-entries","/api/bulk-inbound-receipt-documents");
  var json=new tools.jackson.databind.ObjectMapper();
  for (var entry:routes.entrySet()) {
   String route=entry.getKey();
   var requestType=entry.getValue();
   String body="/api/bulk-coil-title-transfers".equals(route) ? "{\"expectedTitleRevision\":0}" : "{}";
   var baseline=json.readValue(body,requestType);
   String fields=java.util.Arrays.stream(requestType.getRecordComponents()).map(component->component.getName())
       .collect(java.util.stream.Collectors.joining(","));
   assertThat(fields).as("仓储请求禁止绑定信任字段 %s",route)
       .doesNotContain("tenantId","accountId","memberId","createdBy","createdAt","updatedBy","updatedAt");
   var payload=json.createObjectNode();
   if ("/api/bulk-coil-title-transfers".equals(route)) payload.put("expectedTitleRevision",0);
   for (String protectedField:List.of("tenantId","accountId","memberId","createdBy","createdAt","updatedBy","updatedAt")) {
    payload.put(protectedField,"untrusted");
   }
   try {
    assertThat(json.readValue(payload.toString(),requestType)).as("伪造信任字段不得改变请求 %s",route).isEqualTo(baseline);
   } catch (tools.jackson.databind.exc.UnrecognizedPropertyException denied) {
    assertThat(denied.getPropertyName()).isIn("tenantId","accountId","memberId","createdBy","createdAt","updatedBy","updatedAt");
   }
   var response=mvc.perform(post(route).cookie(session.getCookie("JSESSIONID")).with(csrf())
       .contentType("application/json").content(body)).andReturn().getResponse();
   assertThat(response.getStatus()).as("无仓储写权限的入口 %s，%s",route,response.getContentAsString()).isEqualTo(403);
  }
 }

 /** 两个独立数据库事务争抢同一卷，只有一个事务可以完成。 */
 private void verifyConcurrentCoilAllocation(VerifiedTenantContext c,String v1,String v2,String receiptId)
     throws Exception {
  var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
  var start=new java.util.concurrent.CountDownLatch(1);
  var ready=new java.util.concurrent.CountDownLatch(2);
  var commands=List.of(new CoilOutbound.Allocate(UUID.randomUUID().toString(),v1,receiptId),
      new CoilOutbound.Allocate(UUID.randomUUID().toString(),v2,receiptId));
  try {
   var futures=commands.stream().map(command -> pool.submit(() -> {
    bindReferenceContext(c);
    try {
     ready.countDown();
     if (!start.await(10,java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("test gate timeout");
     coilOutbound.allocate(c,command);
     return command;
    } finally {
     org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
    }
   })).toList();
   assertThat(ready.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
   start.countDown();
   var successful=new ArrayList<CoilOutbound.Allocate>();
   int rejected=0;
   for (var future:futures) {
    try {
     successful.add(future.get(15,java.util.concurrent.TimeUnit.SECONDS));
    } catch (java.util.concurrent.ExecutionException error) {
     assertThat(error.getCause()).isInstanceOf(com.appnexus.contracts.businessobject.BusinessObjects.Rejection.class)
         .hasMessage("OUTBOUND_OCCUPIED");
     rejected++;
    }
   }
   assertThat(rejected).isEqualTo(1);
   assertThat(successful).hasSize(1);
   coilOutbound.cancel(c,successful.getFirst());
  } finally {
   pool.shutdownNow();
  }
 }

 /** A 车复磅后父级超额，B 车换卷后重新采用有效分摊。 */
 private void verifyCoilReweigh(VerifiedTenantContext c,InboundReceiptReferences.Position position,
     InboundReceiptReferences.Item item,String owner,String unit,String unitCode) throws Exception {
  String inbound=UUID.randomUUID().toString();
  inboundReceiving.issue(c,new InboundReceiving.Notice(inbound,position.warehouseId(),owner,item,unit,new BigDecimal("147000")));
  var a=receiveWeighFixture(c,inbound,position,item,owner,unit,unitCode,"51000","REWEIGH-51");
  var b=receiveWeighFixture(c,inbound,position,item,owner,unit,unitCode,"49000","REWEIGH-49");
  var substitute=receiveWeighFixture(c,inbound,position,item,owner,unit,unitCode,"47000","REWEIGH-47");
  String notice=UUID.randomUUID().toString(), auth=UUID.randomUUID().toString();
  String v1=UUID.randomUUID().toString(), v2=UUID.randomUUID().toString();
  coilOutbound.issue(c,new CoilOutbound.Notice(notice,position.warehouseId(),owner,item,unit,unitCode,new BigDecimal("100000")));
  coilOutbound.authorize(c,new CoilOutbound.Authorization(auth,notice,owner,java.time.Instant.now().plusSeconds(3600),
      new BigDecimal("100000")));
  coilOutbound.plan(c,new CoilOutbound.Trip(v1,auth,"REWEIGH-A",new BigDecimal("55000")));
  coilOutbound.plan(c,new CoilOutbound.Trip(v2,auth,"REWEIGH-B",new BigDecimal("55000")));
  var assignA=new CoilOutbound.Allocate(UUID.randomUUID().toString(),v1,a.id());
  var assignB=new CoilOutbound.Allocate(UUID.randomUUID().toString(),v2,b.id());
  coilOutbound.allocate(c,assignA);
  coilOutbound.allocate(c,assignB);
  String sameVehicle=UUID.randomUUID().toString();
  coilOutbound.plan(c,new CoilOutbound.Trip(sameVehicle,auth,"REWEIGH-A",new BigDecimal("55000")));
  assertThatThrownBy(()->coilOutbound.allocate(c,new CoilOutbound.Allocate(
      UUID.randomUUID().toString(),sameVehicle,substitute.id()))).hasMessage("OUTBOUND_VEHICLE_MULTIPLE_LOADS");

  var firstLimits=limits(v1,"REWEIGH-A",unit,"50000","70000");
  var unreviewed=weigh(assignA,"REWEIGH-A",unit,"51000");
  verifyExcludedWarehouseOperations(c,Map.of(
      "BulkCoilOutbound.vehicle_limits",()->coilOutbound.verifyVehicleLimits(c,firstLimits),
      "BulkCoilOutbound.review_weighing",()->coilOutbound.reviewWeighing(c,unreviewed)));
  verifyRejectedStockWrite("bw_outbound_trip","id",v1,"payload_limit IS NULL",
      ()->coilOutbound.verifyVehicleLimits(c,firstLimits));
  assertThat(jdbc.queryForObject("SELECT payload_limit FROM bw_outbound_trip WHERE id=UUID_TO_BIN(?)",BigDecimal.class,v1))
      .isNull();
  coilOutbound.verifyVehicleLimits(c,firstLimits);
  var vehicleHeld=coilOutbound.reviewWeighing(c,weigh(assignA,"REWEIGH-A",unit,"51000"));
  assertThat(vehicleHeld.state()).isEqualTo(CoilOutbound.ReviewState.HELD);
  assertThat(vehicleHeld.reasons()).containsExactly("OUTBOUND_VEHICLE_PAYLOAD_LIMIT","OUTBOUND_VEHICLE_GROSS_LIMIT");

  coilOutbound.verifyVehicleLimits(c,limits(v1,"REWEIGH-A",unit,"54000","70000"));
  assertThat(coilOutbound.reviewWeighing(c,weigh(assignA,"REWEIGH-A",unit,"51000")).reasons())
      .containsExactly("OUTBOUND_VEHICLE_GROSS_LIMIT");
  coilOutbound.verifyVehicleLimits(c,limits(v1,"REWEIGH-A",unit,"50000","74000"));
  assertThat(coilOutbound.reviewWeighing(c,weigh(assignA,"REWEIGH-A",unit,"51000")).reasons())
      .containsExactly("OUTBOUND_VEHICLE_PAYLOAD_LIMIT");

  coilOutbound.verifyVehicleLimits(c,limits(v1,"REWEIGH-A",unit,"54000","74000"));
  coilOutbound.verifyVehicleLimits(c,limits(v2,"REWEIGH-B",unit,"54000","74000"));
  var input=weigh(assignA,"REWEIGH-A",unit,"53000");
  var blocked=coilOutbound.reviewWeighing(c,input);
  assertThat(blocked.state()).isEqualTo(CoilOutbound.ReviewState.HELD);
  assertThat(blocked.reasons()).containsExactly("OUTBOUND_NOTICE_LIMIT","OUTBOUND_AUTHORIZATION_LIMIT");
  assertThat(coilOutbound.reviewWeighing(c,input)).isEqualTo(blocked);
  assertThatThrownBy(()->coilOutbound.handover(c,new CoilOutbound.Handover(UUID.randomUUID().toString(),v1,
      blocked.id(),UUID.randomUUID().toString(),"REWEIGH-A",owner,List.of(a.id()))))
      .hasMessage("OUTBOUND_REVIEW_REQUIRED");
  assertThat(outboundStock.check(c,a.id())).isEqualTo(OutboundStock.Gate.OCCUPIED);
  assertThat(outboundStock.check(c,b.id())).isEqualTo(OutboundStock.Gate.OCCUPIED);
  assertThat(coilOutbound.allocate(c,assignA).trip().reserved()).isEqualByComparingTo("51000");
  assertThat(coilOutbound.allocate(c,assignB).notice().reserved()).isEqualByComparingTo("100000");

  coilOutbound.cancel(c,assignB);
  var swap=new CoilOutbound.Allocate(UUID.randomUUID().toString(),v2,substitute.id());
  coilOutbound.allocate(c,swap);
  assertThatThrownBy(()->coilOutbound.reviewWeighing(c,weigh(assignB,"REWEIGH-B",unit,"49000")))
      .hasMessage("OUTBOUND_LOAD_CHANGED");
  var weighedB=coilOutbound.reviewWeighing(c,weigh(swap,"REWEIGH-B",unit,"47000"));
  assertThat(weighedB.state()).isEqualTo(CoilOutbound.ReviewState.APPROVED);
  var approvedInput=weigh(assignA,"REWEIGH-A",unit,"53000");
  var oldReservation=jdbc.queryForObject("SELECT reserved FROM bw_outbound_trip WHERE id=UUID_TO_BIN(?)",BigDecimal.class,v1);
  verifyRejectedStockWrite("bw_coil_weigh_review","id",approvedInput.id(),"FALSE",
      ()->coilOutbound.reviewWeighing(c,approvedInput));
  assertThat(jdbc.queryForObject("SELECT reserved FROM bw_outbound_trip WHERE id=UUID_TO_BIN(?)",BigDecimal.class,v1))
      .isEqualByComparingTo(oldReservation);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bw_coil_weigh_review WHERE id=UUID_TO_BIN(?)",Integer.class,approvedInput.id()))
      .isZero();
  var weighedA=coilOutbound.reviewWeighing(c,approvedInput);
  assertThat(weighedA.state()).isEqualTo(CoilOutbound.ReviewState.APPROVED);
  assertThat(coilOutbound.allocate(c,assignA).trip().reserved()).isEqualByComparingTo("53000");
  assertThat(coilOutbound.allocate(c,swap).trip().reserved()).isEqualByComparingTo("47000");
  assertThat(coilOutbound.allocate(c,assignA).notice().reserved()).isEqualByComparingTo("100000");
  assertThat(outboundStock.check(c,b.id())).isEqualTo(OutboundStock.Gate.AVAILABLE);
  verifyCoilHandover(c,notice,auth,a.id(),substitute.id(),weighedA,weighedB,owner,assignA,position.locationId());
 }

 /** 门岗再次读取实物状态，冻结拒绝不写账，成功后两种重量分别核销。 */
 private void verifyCoilHandover(VerifiedTenantContext c,String notice,String authorization,String a,String b,
     CoilOutbound.Review weighedA,CoilOutbound.Review weighedB,String pickup,CoilOutbound.Allocate originalAllocation,
     String location) throws Exception {
  var first=new CoilOutbound.Handover(UUID.randomUUID().toString(),weighedA.tripId(),weighedA.id(),
      UUID.randomUUID().toString(),"REWEIGH-A",pickup,List.of(a));
  var otherGoods=new CoilOutbound.Handover(first.id(),first.tripId(),first.reviewId(),first.evidenceId(),
      first.vehicleNumber(),pickup,List.of(b));
  assertThatThrownBy(()->coilOutbound.handover(c,otherGoods)).hasMessage("OUTBOUND_LOAD_CHANGED");
  var wrongPickup=new CoilOutbound.Handover(first.id(),first.tripId(),first.reviewId(),first.evidenceId(),
      first.vehicleNumber(),UUID.randomUUID().toString(),first.receiptIds());
  assertThatThrownBy(()->coilOutbound.handover(c,wrongPickup)).hasMessage("OUTBOUND_ACCESS_DENIED");
  var wrongVehicle=new CoilOutbound.Handover(first.id(),first.tripId(),first.reviewId(),first.evidenceId(),
      "WRONG-VEHICLE",pickup,first.receiptIds());
  assertThatThrownBy(()->coilOutbound.handover(c,wrongVehicle)).hasMessage("OUTBOUND_INPUT_INVALID");

  assertThat(jdbc.queryForObject("SELECT flow_version FROM bw_outbound_notice WHERE id=UUID_TO_BIN(?)",
      Integer.class,notice)).isEqualTo(2);
  // 模拟迁移前签发、迁移后仍有待交接车次的 V1 通知。
  jdbc.update("UPDATE bw_outbound_notice SET flow_version=1 WHERE id=UUID_TO_BIN(?)",notice);
  verifyExcludedWarehouseOperations(c,Map.of(
      "BulkCoilOutbound.handover",()->coilOutbound.handover(c,first)));
  var json=new tools.jackson.databind.ObjectMapper();
  var session=mvc.perform(post("/bff/tenants/current").with(authenticated(c.accountId().toString(),0))
      .with(csrf()).contentType("application/json")
      .content(json.writeValueAsString(Map.of("tenantId",c.tenantId().toString())))).andReturn().getResponse();
  assertThat(session.getStatus()).isEqualTo(200);
  var cookie=session.getCookie("JSESSIONID");
  bindReferenceContext(c);
  var freeze=new OutboundStock.Freeze(UUID.randomUUID().toString(),a,UUID.randomUUID().toString());
  var blocked=commitWhileCompeting(c,()->outboundStock.freeze(c,freeze), () ->
      mvc.perform(post("/api/bulk-outbound-trips:handover").cookie(cookie).with(csrf())
          .contentType("application/json").content(json.writeValueAsString(first))).andReturn().getResponse());
  assertThat(blocked.getStatus()).isEqualTo(409);
  assertThat(json.readTree(blocked.getContentAsString()).path("code").asText()).isEqualTo("OUTBOUND_FROZEN");
  bindReferenceContext(c);
  assertUnpostedHandover(first,a,notice);

  outboundStock.lift(c,new OutboundStock.Freeze(freeze.id(),a,UUID.randomUUID().toString()));
  assertThat(outboundStock.check(c,a)).isEqualTo(OutboundStock.Gate.OCCUPIED);
  // 冻结解除后，注入真实数据库约束错误，验证出库流水、库存和货权共同回滚。
  jdbc.execute("ALTER TABLE bw_inbound_title ADD CONSTRAINT bw_test_handover_failure CHECK(receipt_id<>X'"
      +a.replace("-","")+"' OR issued_quantity=0)");
  try {
   assertThatThrownBy(()->coilOutbound.handover(c,first)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  } finally {
   jdbc.execute("ALTER TABLE bw_inbound_title DROP CHECK bw_test_handover_failure");
  }
  assertUnpostedHandover(first,a,notice);

  var traceWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
  var traceReady=new java.util.concurrent.CountDownLatch(1);
  org.springframework.mock.web.MockHttpServletResponse released;
  try {
   var traceResult=traceWorker.submit(() -> {
    bindReferenceContext(c);
    try {
     traceReady.countDown();
     return warehouseBalances.trace(c,a);
    } finally {
     org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
    }
   });
   assertThat(traceReady.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
   released=mvc.perform(post("/api/bulk-outbound-trips:handover").cookie(cookie).with(csrf())
       .contentType("application/json").content(json.writeValueAsString(first))).andReturn().getResponse();
   var snapshot=traceResult.get(15,java.util.concurrent.TimeUnit.SECONDS);
   boolean posted=snapshot.documents().stream().anyMatch(row -> "HANDOVER".equals(row.kind()) && first.id().equals(row.id()));
   assertThat(snapshot.stock().onHand()).isEqualByComparingTo(posted ? "0" : "51000");
  } finally {
   traceWorker.shutdownNow();
  }
  assertThat(released.getStatus()).as(released.getContentAsString()).isEqualTo(200);
  var body=json.readTree(released.getContentAsString());
  assertThat(body.path("issuedQuantity").decimalValue()).isEqualByComparingTo("53000");
  assertThat(body.path("stockDeduction").decimalValue()).isEqualByComparingTo("51000");
  bindReferenceContext(c);
  assertThat(coilOutbound.handover(c,first).issuedQuantity()).isEqualByComparingTo("53000");
  assertThatThrownBy(()->coilOutbound.handover(c,new CoilOutbound.Handover(
      UUID.randomUUID().toString(),first.tripId(),first.reviewId(),UUID.randomUUID().toString(),
      first.vehicleNumber(),pickup,List.of(a)))).hasMessage("OUTBOUND_TRIP_ALREADY_ISSUED");
  var index=jdbc.queryForMap("""
      EXPLAIN SELECT issued_quantity FROM bw_coil_handover_line
      WHERE tenant_id=UUID_TO_BIN(?) AND receipt_id=UUID_TO_BIN(?)
      """,c.tenantId().toString(),a);
  assertThat(index.get("key")).isNotNull();
  assertThat(((Number)index.get("rows")).longValue()).isLessThanOrEqualTo(1L);
  assertThat(outboundStock.check(c,a)).isEqualTo(OutboundStock.Gate.DISPATCHED);
  assertThatThrownBy(()->outboundStock.reserve(c,new OutboundStock.Hold(UUID.randomUUID().toString(),a)))
      .hasMessage("OUTBOUND_COIL_QUOTA_REQUIRED");

  var second=new CoilOutbound.Handover(UUID.randomUUID().toString(),weighedB.tripId(),weighedB.id(),
      UUID.randomUUID().toString(),"REWEIGH-B",pickup,List.of(b));
  assertThat(coilOutbound.handover(c,second).issuedQuantity()).isEqualByComparingTo("47000");
  assertThat(jdbc.queryForObject("SELECT issued FROM bw_outbound_notice WHERE id=UUID_TO_BIN(?)",
      BigDecimal.class,notice)).isEqualByComparingTo("100000");
  assertThat(jdbc.queryForObject("SELECT issued FROM bw_pickup_authorization WHERE id=UUID_TO_BIN(?)",
      BigDecimal.class,authorization)).isEqualByComparingTo("100000");
  assertThat(jdbc.queryForObject("SELECT issued FROM bw_outbound_trip WHERE id=UUID_TO_BIN(?)",
      BigDecimal.class,first.tripId())).isEqualByComparingTo("53000");
  assertThat(jdbc.queryForObject("SELECT reserved FROM bw_outbound_notice WHERE id=UUID_TO_BIN(?)",
      BigDecimal.class,notice)).isEqualByComparingTo("0");
  assertThat(jdbc.queryForObject("SELECT SUM(shipped_quantity) FROM bw_stock_receipt WHERE receipt_id IN (UUID_TO_BIN(?),UUID_TO_BIN(?))",
      BigDecimal.class,a,b)).isEqualByComparingTo("98000");
  assertThat(jdbc.queryForObject("SELECT SUM(issued_quantity) FROM bw_inbound_title WHERE receipt_id IN (UUID_TO_BIN(?),UUID_TO_BIN(?))",
      BigDecimal.class,a,b)).isEqualByComparingTo("98000");
  assertThat(jdbc.queryForObject("""
      SELECT COUNT(*) FROM bw_stock_occupation
      WHERE receipt_id IN (UUID_TO_BIN(?),UUID_TO_BIN(?)) AND released_at IS NULL
      """,Integer.class,a,b)).isZero();
  verifyCoilReversal(c,notice,authorization,first,originalAllocation,location,cookie);
 }

 /** 两次 53 t 实发、51 t 账面交接，分别按误确认与真实退回补偿。 */
 private void verifyCoilReversal(VerifiedTenantContext c,String notice,String authorization,
     CoilOutbound.Handover first,CoilOutbound.Allocate allocation,String location,
     jakarta.servlet.http.Cookie cookie) throws Exception {
  var receipt=allocation.receiptId();
  String unit=jdbc.queryForObject("SELECT BIN_TO_UUID(unit_id) FROM bw_coil_handover WHERE id=UUID_TO_BIN(?)",
      String.class,first.id());
  var freeze=new OutboundStock.Freeze(UUID.randomUUID().toString(),receipt,UUID.randomUUID().toString());
  outboundStock.freeze(c,freeze);
  var mistaken=new CoilOutbound.ReverseHandover(UUID.randomUUID().toString(),first.id(),
      CoilOutbound.ReversalKind.MISTAKEN_HANDOVER,UUID.randomUUID().toString(),null);

  // 在末尾恢复卷锁时失败，验证此前库存、货权、额度和冲销记录共同回滚。
  jdbc.execute("ALTER TABLE bw_stock_occupation ADD CONSTRAINT bw_test_reversal_failure CHECK(id<>X'"
      +allocation.id().replace("-","")+"' OR released_at IS NOT NULL)");
  try {
   assertThatThrownBy(()->coilOutbound.reverseHandover(c,mistaken)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  } finally {
   jdbc.execute("ALTER TABLE bw_stock_occupation DROP CHECK bw_test_reversal_failure");
  }
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bw_coil_handover_reversal WHERE id=UUID_TO_BIN(?)",
      Integer.class,mistaken.id())).isZero();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bw_coil_handover WHERE id=UUID_TO_BIN(?) AND reversed_at IS NULL",
      Integer.class,first.id())).isEqualTo(1);
  assertThat(outboundStock.check(c,receipt)).isEqualTo(OutboundStock.Gate.DISPATCHED);
  verifyReversalBalances(notice,authorization,first.tripId(),receipt,"100000","0","53000","0","51000");

  var json=new tools.jackson.databind.ObjectMapper();
  var reversed=mvc.perform(post("/api/bulk-coil-handover-reversals").cookie(cookie).with(csrf())
      .contentType("application/json").content(json.writeValueAsString(mistaken))).andReturn().getResponse();
  assertThat(reversed.getStatus()).as(reversed.getContentAsString()).isEqualTo(200);
  var response=json.readTree(reversed.getContentAsString());
  assertThat(response.path("restoredQuota").decimalValue()).isEqualByComparingTo("53000");
  assertThat(response.path("restoredStock").decimalValue()).isEqualByComparingTo("51000");
  assertThat(response.path("restoredReservation").decimalValue()).isEqualByComparingTo("53000");
  bindReferenceContext(c);
  assertThat(coilOutbound.reverseHandover(c,mistaken).id()).isEqualTo(mistaken.id());
  assertThat(warehouseBalances.document(c,WarehouseBalances.DocumentKind.HANDOVER_REVERSAL,mistaken.id())
      .header().parentId()).isEqualTo(first.id());
  assertThat(warehouseBalances.document(c,WarehouseBalances.DocumentKind.OUTBOUND_CONFIRMATION,first.id())
      .header().quantity()).isEqualByComparingTo("53000");
  assertThat(warehouseBalances.document(c,WarehouseBalances.DocumentKind.OUTBOUND_TRIP,first.tripId()).related())
      .extracting(WarehouseBalances.DocumentRelation::kind).contains("ALLOCATION","WEIGHING");

  assertThat(warehouseBalances.trace(c,first.receiptIds().getFirst()).documents()).anySatisfy(event -> {
   assertThat(event.kind()).isEqualTo("REVERSAL");
   assertThat(event.id()).isEqualTo(mistaken.id());
   assertThat(event.parentId()).isEqualTo(first.id());
   assertThat(event.quantity()).isEqualByComparingTo("51000");
  });
  assertThatThrownBy(()->coilOutbound.reverseHandover(c,new CoilOutbound.ReverseHandover(
      mistaken.id(),first.id(),mistaken.kind(),UUID.randomUUID().toString(),null)))
      .hasMessage("OUTBOUND_REQUEST_CONFLICT");
  var index=jdbc.queryForMap("""
      EXPLAIN SELECT id FROM bw_coil_handover_reversal
      WHERE tenant_id=UUID_TO_BIN(?) AND handover_id=UUID_TO_BIN(?)
      """,c.tenantId().toString(),first.id());
  assertThat(index.get("key")).isNotNull();
  assertThat(((Number)index.get("rows")).longValue()).isLessThanOrEqualTo(1L);
  assertThatThrownBy(()->coilOutbound.reverseHandover(c,new CoilOutbound.ReverseHandover(
      UUID.randomUUID().toString(),first.id(),mistaken.kind(),mistaken.evidenceId(),null)))
      .hasMessage("OUTBOUND_HANDOVER_REVERSED");
  assertThatThrownBy(()->coilOutbound.handover(c,first)).hasMessage("OUTBOUND_HANDOVER_REVERSED");
  verifyReversalBalances(notice,authorization,first.tripId(),receipt,"47000","53000","0","53000","0");
  assertThat(outboundStock.check(c,receipt)).isEqualTo(OutboundStock.Gate.FROZEN);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bw_stock_occupation WHERE id=UUID_TO_BIN(?) AND released_at IS NULL",
      Integer.class,allocation.id())).isEqualTo(1);

  var stale=new CoilOutbound.Handover(UUID.randomUUID().toString(),first.tripId(),first.reviewId(),
      UUID.randomUUID().toString(),first.vehicleNumber(),first.pickupPartnerId(),first.receiptIds());
  assertThatThrownBy(()->coilOutbound.handover(c,stale)).hasMessage("OUTBOUND_REVIEW_REQUIRED");
  assertThat(coilOutbound.reviewWeighing(c,weigh(allocation,first.vehicleNumber(),unit,"53000")).reasons())
      .contains("OUTBOUND_FROZEN");
  outboundStock.lift(c,new OutboundStock.Freeze(freeze.id(),receipt,UUID.randomUUID().toString()));
  var newReview=coilOutbound.reviewWeighing(c,weigh(allocation,first.vehicleNumber(),unit,"53000"));
  var second=new CoilOutbound.Handover(UUID.randomUUID().toString(),first.tripId(),newReview.id(),
      UUID.randomUUID().toString(),first.vehicleNumber(),first.pickupPartnerId(),first.receiptIds());
  assertThat(coilOutbound.handover(c,second).issuedQuantity()).isEqualByComparingTo("53000");

  var returned=new CoilOutbound.ReverseHandover(UUID.randomUUID().toString(),second.id(),
      CoilOutbound.ReversalKind.ACTUAL_RETURN,UUID.randomUUID().toString(),UUID.randomUUID().toString());
  assertThatThrownBy(()->coilOutbound.reverseHandover(c,returned)).hasMessage("OUTBOUND_RETURN_ACCEPTANCE_REQUIRED");
  var acceptance=new CoilOutbound.AcceptedReturn(returned.returnId(),second.id(),UUID.randomUUID().toString(),
      UUID.randomUUID().toString(),UUID.randomUUID().toString(),List.of(new CoilOutbound.ReturnedCoil(receipt,location)));
  var wrongLocation=new CoilOutbound.AcceptedReturn(acceptance.id(),second.id(),acceptance.custodyEvidenceId(),
      acceptance.titleEvidenceId(),acceptance.qualityEvidenceId(),
      List.of(new CoilOutbound.ReturnedCoil(receipt,UUID.randomUUID().toString())));
  assertThatThrownBy(()->coilOutbound.acceptReturn(c,wrongLocation)).hasMessage("OUTBOUND_RETURN_LOCATION_MISMATCH");
  verifyExcludedWarehouseOperations(c,Map.of(
      "BulkCoilOutbound.accept_return",()->coilOutbound.acceptReturn(c,acceptance),
      "BulkCoilOutbound.reverse_handover",()->coilOutbound.reverseHandover(c,returned)));
  verifyRejectedStockWrite("bw_coil_return_line","return_id",acceptance.id(),"FALSE",
      ()->coilOutbound.acceptReturn(c,acceptance));
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bw_coil_return_acceptance WHERE id=UUID_TO_BIN(?)",
      Integer.class,acceptance.id())).isZero();
  coilOutbound.acceptReturn(c,acceptance);
  coilOutbound.acceptReturn(c,acceptance);
  assertThat(outboundStock.check(c,receipt)).isEqualTo(OutboundStock.Gate.DISPATCHED);
  assertThatThrownBy(()->coilOutbound.reverseHandover(c,new CoilOutbound.ReverseHandover(
      UUID.randomUUID().toString(),second.id(),mistaken.kind(),UUID.randomUUID().toString(),null)))
      .hasMessage("OUTBOUND_ACTUAL_RETURN_REQUIRED");
  var returnFreeze=new OutboundStock.Freeze(UUID.randomUUID().toString(),receipt,UUID.randomUUID().toString());
  outboundStock.freeze(c,returnFreeze);
  var credit=coilOutbound.reverseHandover(c,returned);
  assertThat(credit.restoredStock()).isEqualByComparingTo("51000");
  assertThat(credit.restoredQuota()).isEqualByComparingTo("53000");
  assertThat(credit.restoredReservation()).isEqualByComparingTo("0");
  assertThat(coilOutbound.reverseHandover(c,returned)).isEqualTo(credit);
  assertThat(coilOutbound.returnDocument(c,returned.returnId()).header().restoredStock())
      .isEqualByComparingTo("51000");

  verifyReversalBalances(notice,authorization,second.tripId(),receipt,"47000","0","0","0","0");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bw_stock_occupation WHERE receipt_id=UUID_TO_BIN(?) AND released_at IS NULL",
      Integer.class,receipt)).isZero();
  assertThat(outboundStock.check(c,receipt)).isEqualTo(OutboundStock.Gate.FROZEN);
  String nextTrip=UUID.randomUUID().toString();
  coilOutbound.plan(c,new CoilOutbound.Trip(nextTrip,authorization,second.vehicleNumber(),new BigDecimal("55000")));
  var nextAllocation=new CoilOutbound.Allocate(UUID.randomUUID().toString(),nextTrip,receipt);
  assertThatThrownBy(()->coilOutbound.allocate(c,nextAllocation)).hasMessage("OUTBOUND_FROZEN");
  outboundStock.lift(c,new OutboundStock.Freeze(returnFreeze.id(),receipt,UUID.randomUUID().toString()));
  assertThat(outboundStock.check(c,receipt)).isEqualTo(OutboundStock.Gate.AVAILABLE);
  assertThatThrownBy(()->coilOutbound.allocate(c,new CoilOutbound.Allocate(
      UUID.randomUUID().toString(),second.tripId(),receipt))).hasMessage("OUTBOUND_TRIP_ALREADY_ISSUED");
  assertThat(coilOutbound.allocate(c,nextAllocation).quantity()).isEqualByComparingTo("51000");
  coilOutbound.cancel(c,nextAllocation);
 }

 private void verifyReversalBalances(String notice,String authorization,String trip,String receipt,
     String parentIssued,String parentReserved,String tripIssued,String tripReserved,String book) {
  var n=jdbc.queryForMap("SELECT issued,reserved FROM bw_outbound_notice WHERE id=UUID_TO_BIN(?)",notice);
  var a=jdbc.queryForMap("SELECT issued,reserved FROM bw_pickup_authorization WHERE id=UUID_TO_BIN(?)",authorization);
  var v=jdbc.queryForMap("SELECT issued,reserved FROM bw_outbound_trip WHERE id=UUID_TO_BIN(?)",trip);
  for (var parent:List.of(n,a)) {
   assertThat((BigDecimal)parent.get("issued")).isEqualByComparingTo(parentIssued);
   assertThat((BigDecimal)parent.get("reserved")).isEqualByComparingTo(parentReserved);
  }
  assertThat((BigDecimal)v.get("issued")).isEqualByComparingTo(tripIssued);
  assertThat((BigDecimal)v.get("reserved")).isEqualByComparingTo(tripReserved);
  assertThat(jdbc.queryForObject("SELECT shipped_quantity FROM bw_stock_receipt WHERE receipt_id=UUID_TO_BIN(?)",
      BigDecimal.class,receipt)).isEqualByComparingTo(book);
  assertThat(jdbc.queryForObject("SELECT issued_quantity FROM bw_inbound_title WHERE receipt_id=UUID_TO_BIN(?)",
      BigDecimal.class,receipt)).isEqualByComparingTo(book);
 }

 private void assertUnpostedHandover(CoilOutbound.Handover request,String receipt,String notice) {
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bw_coil_handover WHERE id=UUID_TO_BIN(?)",
      Integer.class,request.id())).isZero();
  assertThat(jdbc.queryForObject("SELECT shipped_quantity FROM bw_stock_receipt WHERE receipt_id=UUID_TO_BIN(?)",
      BigDecimal.class,receipt)).isEqualByComparingTo("0");
  assertThat(jdbc.queryForObject("SELECT issued_quantity FROM bw_inbound_title WHERE receipt_id=UUID_TO_BIN(?)",
      BigDecimal.class,receipt)).isEqualByComparingTo("0");
  assertThat(jdbc.queryForObject("SELECT reserved FROM bw_outbound_notice WHERE id=UUID_TO_BIN(?)",
      BigDecimal.class,notice)).isEqualByComparingTo("100000");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bw_stock_occupation WHERE active_receipt_id=UUID_TO_BIN(?)",
      Integer.class,receipt)).isEqualTo(1);
 }

 /** 跨仓实际接管恢复 51 t 账面，原仓冲回 53 t 实测。 */
 private void verifyCrossWarehouseReturn(VerifiedTenantContext c,InboundReceiptReferences.Position source,
     InboundReceiptReferences.Item item,String owner,String unit,String unitCode) throws Exception {
  String inbound=newId(), notice=newId(), authorization=newId(), trip=newId();
  String receivingWarehouse=masterData.create(c,MasterData.Kind.WAREHOUSE,
      new MasterData.Input("CROSS-RETURN-W","实际接收仓库",Map.of(),0)).get("id");
  var destination=returnPosition(c,receivingWarehouse,"CROSS-RETURN-W","CROSS-RETURN-ISOLATION");
  inboundReceiving.issue(c,new InboundReceiving.Notice(inbound,source.warehouseId(),owner,item,unit,new BigDecimal("51000")));
  var roll=receiveWeighFixture(c,inbound,source,item,owner,unit,unitCode,"51000","CROSS-WAREHOUSE-51",
      "收货批次-跨仓");
  var expiry=java.time.Instant.now().plusSeconds(3600);
  coilOutbound.issue(c,new CoilOutbound.Notice(notice,source.warehouseId(),owner,item,unit,unitCode,new BigDecimal("53000")));
  coilOutbound.authorize(c,new CoilOutbound.Authorization(authorization,notice,owner,expiry,new BigDecimal("53000")));
  String vehicle="CROSS-RETURN-VEHICLE";
  coilOutbound.plan(c,new CoilOutbound.Trip(trip,authorization,vehicle,new BigDecimal("53000")));
  var allocation=new CoilOutbound.Allocate(newId(),trip,roll.id());
  coilOutbound.allocate(c,allocation);
  coilOutbound.verifyVehicleLimits(c,limits(trip,vehicle,unit,"53000","73000"));
  var review=coilOutbound.reviewWeighing(c,weigh(allocation,vehicle,unit,"53000"));
  var original=new CoilOutbound.Handover(newId(),trip,review.id(),newId(),vehicle,owner,List.of(roll.id()));
  coilOutbound.handover(c,original);
  var shipped=warehouseBalances.trace(c,roll.id());
  assertThat(shipped.stock().onHand()).isZero();
  assertThat(shipped.stock().receiptBatchNo()).isEqualTo("收货批次-跨仓");
  var request=returnTo(original.id(),roll.id(),destination);
  var freeze=new OutboundStock.Freeze(newId(),roll.id(),newId());
  outboundStock.freeze(c,freeze);

  // 同一有功能权限主体，仅调整主数据仓库读取的记录范围，分别证明原仓和接收仓都必需。
  var admin=new com.appnexus.platform.authorization.RoleManagement.Context(c.accountId(),c.tenantId(),c.memberId());
  UUID role=UUID.fromString(jdbc.queryForObject("""
      SELECT BIN_TO_UUID(id) FROM access_role WHERE tenant_id=UUID_TO_BIN(?) AND system_key LIKE 'TENANT_ADMIN:%'
      """,String.class,c.tenantId().toString()));
  var before=rolePermissions.getAuthorization(admin,role);
  var readPermission=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM access_permission WHERE code='masterdata.warehouse:read'",
      String.class));
  jdbc.update("""
      UPDATE access_member_authorization SET inherit_data_scope=TRUE
      WHERE tenant_id=UUID_TO_BIN(?) AND member_id=UUID_TO_BIN(?) AND role_id=UUID_TO_BIN(?)
      """,c.tenantId().toString(),c.memberId().toString(),role.toString());
  try {
   scopeCrossReturnWarehouses(admin,role,readPermission,Set.of(UUID.fromString(source.warehouseId())));
   assertThatThrownBy(()->coilOutbound.returnCoils(c,request)).hasMessage("INBOUND_ACCESS_DENIED");
   scopeCrossReturnWarehouses(admin,role,readPermission,Set.of(UUID.fromString(destination.warehouseId())));
   assertThatThrownBy(()->coilOutbound.returnCoils(c,request)).hasMessage("INBOUND_ACCESS_DENIED");
   scopeCrossReturnWarehouses(admin,role,readPermission,
       Set.of(UUID.fromString(source.warehouseId()),UUID.fromString(destination.warehouseId())));

   // 最后一级额度写入失败时，两仓位置、卷号仓属、库存和货权必须一并回滚。
   jdbc.execute("ALTER TABLE bw_outbound_trip ADD CONSTRAINT bw_test_cross_return_failure CHECK(id<>X'"
       +trip.replace("-","")+"' OR issued=53000)");
   try {
    assertThatThrownBy(()->coilOutbound.returnCoils(c,request)).isInstanceOf(org.springframework.dao.DataAccessException.class);
   } finally {
    jdbc.execute("ALTER TABLE bw_outbound_trip DROP CHECK bw_test_cross_return_failure");
   }
   verifyReversalBalances(notice,authorization,trip,roll.id(),"53000","0","53000","0","51000");
   assertThat(jdbc.queryForObject("SELECT BIN_TO_UUID(warehouse_id) FROM bw_stock_coil WHERE receipt_id=UUID_TO_BIN(?)",
       String.class,roll.id())).isEqualTo(source.warehouseId());
   assertThat(jdbc.queryForObject("SELECT BIN_TO_UUID(current_warehouse_id) FROM bw_stock_receipt WHERE receipt_id=UUID_TO_BIN(?)",
       String.class,roll.id())).isNull();
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bw_coil_partial_return WHERE id=UUID_TO_BIN(?)",
       Integer.class,request.id())).isZero();

   var json=new tools.jackson.databind.ObjectMapper();
   var session=mvc.perform(post("/bff/tenants/current").with(authenticated(c.accountId().toString(),0))
       .with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("tenantId",c.tenantId().toString()))))
       .andReturn().getResponse();
   assertThat(session.getStatus()).isEqualTo(200);
   var response=mvc.perform(post("/api/bulk-coil-returns:post-coils").cookie(session.getCookie("JSESSIONID"))
       .with(csrf()).contentType("application/json").content(json.writeValueAsString(request))).andReturn().getResponse();
   assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
   assertThat(json.readTree(response.getContentAsString()).path("restoredQuota").decimalValue()).isEqualByComparingTo("53000");
   bindReferenceContext(c);
   assertThat(coilOutbound.returnCoils(c,request).restoredStock()).isEqualByComparingTo("51000");
   assertThat(coilOutbound.returnDocument(c,request.id()).lines()).singleElement()
       .satisfies(line->assertThat(line.warehouseId()).isEqualTo(destination.warehouseId()));

   assertThatThrownBy(()->coilOutbound.returnCoils(c,returnTo(original.id(),roll.id(),destination)))
       .hasMessage("OUTBOUND_HANDOVER_REVERSED");
   verifyReversalBalances(notice,authorization,trip,roll.id(),"0","0","0","0","0");

   assertReturnPositions(original.id(),request.id(),roll.id(),source.locationId(),destination.locationId());
   assertThat(jdbc.queryForObject("SELECT BIN_TO_UUID(source_warehouse_id) FROM bw_coil_handover_line WHERE handover_id=UUID_TO_BIN(?)",
       String.class,original.id())).isEqualTo(source.warehouseId());
   assertThat(jdbc.queryForObject("SELECT BIN_TO_UUID(receiving_warehouse_id) FROM bw_coil_partial_return_line WHERE return_id=UUID_TO_BIN(?)",
       String.class,request.id())).isEqualTo(destination.warehouseId());
   assertThat(jdbc.queryForObject("SELECT BIN_TO_UUID(warehouse_id) FROM bw_stock_coil WHERE receipt_id=UUID_TO_BIN(?)",
       String.class,roll.id())).isEqualTo(destination.warehouseId());
   var recovered=jdbc.queryForMap("""
       SELECT s.inbound_quantity-s.shipped_quantity stock,t.quantity-t.issued_quantity title,s.pending_quantity pending
       FROM bw_stock_receipt s JOIN bw_inbound_title t ON t.tenant_id=s.tenant_id AND t.receipt_id=s.receipt_id
       WHERE s.receipt_id=UUID_TO_BIN(?) AND s.current_warehouse_id=UUID_TO_BIN(?)
       """,roll.id(),destination.warehouseId());
   for (var key:List.of("stock","title","pending")) assertThat((BigDecimal)recovered.get(key)).isEqualByComparingTo("51000");
   var plan=jdbc.queryForList("""
       EXPLAIN SELECT COALESCE(s.current_warehouse_id,c.warehouse_id)
       FROM bw_stock_receipt s JOIN bw_inbound_receipt r ON r.tenant_id=s.tenant_id AND r.id=s.receipt_id
       JOIN bw_inbound_custody c ON c.tenant_id=r.tenant_id AND c.id=r.custody_id
       WHERE s.tenant_id=UUID_TO_BIN(?) AND s.receipt_id=UUID_TO_BIN(?)
       """,c.tenantId().toString(),roll.id());
   assertThat(plan).hasSize(3).allSatisfy(row -> {
    assertThat(row.get("key")).isEqualTo("PRIMARY");
    assertThat(((Number)row.get("rows")).longValue()).isLessThanOrEqualTo(1L);
   });
   scopeCrossReturnWarehouses(admin,role,readPermission,Set.of(UUID.fromString(source.warehouseId())));
   assertThatThrownBy(()->outboundStock.check(c,roll.id()))
       .isInstanceOf(MasterData.Rejection.class).hasMessage("MASTER_DATA_INPUT_INVALID");
   scopeCrossReturnWarehouses(admin,role,readPermission,Set.of(UUID.fromString(destination.warehouseId())));
   assertThat(outboundStock.check(c,roll.id())).isEqualTo(OutboundStock.Gate.FROZEN);
   scopeCrossReturnWarehouses(admin,role,readPermission,
       Set.of(UUID.fromString(source.warehouseId()),UUID.fromString(destination.warehouseId())));


   var trace=warehouseBalances.trace(c,roll.id());
   assertThat(trace.stock().warehouseId()).isEqualTo(destination.warehouseId());
   assertThat(trace.stock().receiptBatchNo()).isEqualTo("收货批次-跨仓");
   assertThat(trace.documents()).extracting(WarehouseBalances.TraceDocument::kind)
       .contains("INBOUND","ALLOCATION","WEIGHING","HANDOVER","RETURN");
   scopeCrossReturnWarehouses(admin,role,readPermission,Set.of(UUID.fromString(destination.warehouseId())));
   assertThatThrownBy(()->warehouseBalances.trace(c,roll.id()))
       .isInstanceOf(com.appnexus.contracts.businessobject.BusinessObjects.Rejection.class)
       .hasMessage("INBOUND_ACCESS_DENIED");
   scopeCrossReturnWarehouses(admin,role,readPermission,
       Set.of(UUID.fromString(source.warehouseId()),UUID.fromString(destination.warehouseId())));

   // 质检合格仍无法解除原卷的冻结。在无冻结的阶段单独测试待检门禁。
   String newNotice=newId(),newAuth=newId(),newTrip=newId();
   coilOutbound.issue(c,new CoilOutbound.Notice(newNotice,destination.warehouseId(),owner,item,unit,unitCode,new BigDecimal("53000")));
   coilOutbound.authorize(c,new CoilOutbound.Authorization(newAuth,newNotice,owner,expiry,new BigDecimal("53000")));
   coilOutbound.plan(c,new CoilOutbound.Trip(newTrip,newAuth,vehicle,new BigDecimal("53000")));
   var next=new CoilOutbound.Allocate(newId(),newTrip,roll.id());
   assertThatThrownBy(()->coilOutbound.allocate(c,next)).hasMessage("OUTBOUND_FROZEN");
   outboundStock.lift(c,new OutboundStock.Freeze(freeze.id(),roll.id(),newId()));
   assertThatThrownBy(()->coilOutbound.allocate(c,next)).hasMessage("OUTBOUND_PENDING_QUALITY");
   var secondFreeze=new OutboundStock.Freeze(newId(),roll.id(),newId());
   outboundStock.freeze(c,secondFreeze);
   coilOutbound.releaseReturnedQuality(c,new CoilOutbound.ReturnedQuality(request.id(),roll.id(),newId()));
   assertThatThrownBy(()->coilOutbound.allocate(c,next)).hasMessage("OUTBOUND_FROZEN");
   outboundStock.lift(c,new OutboundStock.Freeze(secondFreeze.id(),roll.id(),newId()));
   assertThat(coilOutbound.allocate(c,next).quantity()).isEqualByComparingTo("51000");
   coilOutbound.cancel(c,next);
  } finally {
   var current=rolePermissions.getAuthorization(admin,role);
   rolePermissions.replaceAuthorization(admin,role,current.roleVersion(),before.permissionIds(),before.dataPolicies(),
       "恢复返仓用例租户范围");
  }
 }

 private void scopeCrossReturnWarehouses(com.appnexus.platform.authorization.RoleManagement.Context admin,UUID role,
     UUID readPermission,Set<UUID> warehouses) {
  var current=rolePermissions.getAuthorization(admin,role);
  var policies=new HashSet<>(current.dataPolicies());
  policies.removeIf(policy -> policy.permissionId().equals(readPermission));
  policies.add(new com.appnexus.platform.authorization.RolePermissionManagement.DataPolicy(readPermission,
      com.appnexus.platform.authorization.DataScopeAuthorization.ScopeType.RULES,"masterdata.warehouse",Set.of(),
      List.of(new com.appnexus.platform.authorization.DataGrantRules.Rule(
          com.appnexus.platform.authorization.DataGrantRules.SubjectType.ROLE_MEMBERS,Set.of(),warehouses,List.of(),false))));
  rolePermissions.replaceAuthorization(admin,role,current.roleVersion(),current.permissionIds(),policies,"跨仓返仓范围验证");
 }

 /** 两卷实际发运、一卷仍在原交接，验证收货批次与在途逐卷核销。 */
 private void verifyCoilReturnTransit(VerifiedTenantContext c,InboundReceiptReferences.Position source,
     InboundReceiptReferences.Item item,String owner,String unit,String unitCode) throws Exception {
  String inbound=newId(),notice=newId(),authorization=newId(),trip=newId(),vehicle="RETURN-TRANSIT-VEHICLE";
  String receivingWarehouse=masterData.create(c,MasterData.Kind.WAREHOUSE,
      new MasterData.Input("TRANSIT-RETURN-W","返仓在途接收仓",Map.of(),0)).get("id");
  var destination=returnPosition(c,receivingWarehouse,"TRANSIT-RETURN-W","TRANSIT-RETURN-ISOLATION");
  String newWarehouse=masterData.create(c,MasterData.Kind.WAREHOUSE,
      new MasterData.Input("TRANSIT-REROUTE-W","返仓改仓接收仓",Map.of(),0)).get("id");
  var newDestination=returnPosition(c,newWarehouse,"TRANSIT-REROUTE-W","TRANSIT-REROUTE-ISOLATION");
  inboundReceiving.issue(c,new InboundReceiving.Notice(inbound,source.warehouseId(),owner,item,unit,new BigDecimal("88000")));
  var first=receiveWeighFixture(c,inbound,source,item,owner,unit,unitCode,"51000","TRANSIT-51");
  var second=receiveWeighFixture(c,inbound,source,item,owner,unit,unitCode,"17000","TRANSIT-17");
  var other=receiveWeighFixture(c,inbound,source,item,owner,unit,unitCode,"20000","TRANSIT-OTHER");
  coilOutbound.issue(c,new CoilOutbound.Notice(notice,source.warehouseId(),owner,item,unit,unitCode,new BigDecimal("100000")));
  coilOutbound.authorize(c,new CoilOutbound.Authorization(authorization,notice,owner,java.time.Instant.now().plusSeconds(3600),
      new BigDecimal("100000")));
  coilOutbound.plan(c,new CoilOutbound.Trip(trip,authorization,vehicle,new BigDecimal("100000")));
  var allocations=List.of(new CoilOutbound.Allocate(newId(),trip,first.id()),new CoilOutbound.Allocate(newId(),trip,second.id()),
      new CoilOutbound.Allocate(newId(),trip,other.id()));
  allocations.forEach(allocation -> coilOutbound.allocate(c,allocation));
  coilOutbound.verifyVehicleLimits(c,limits(trip,vehicle,unit,"100000","120000"));
  var review=coilOutbound.reviewWeighing(c,new CoilOutbound.Weighing(newId(),trip,newId(),vehicle,unit,
      new BigDecimal("110000"),new BigDecimal("20000"),
      List.of(new CoilOutbound.WeighedCoil(allocations.get(0).id(),new BigDecimal("53000")),
          new CoilOutbound.WeighedCoil(allocations.get(1).id(),new BigDecimal("17000")),
          new CoilOutbound.WeighedCoil(allocations.get(2).id(),new BigDecimal("20000")))));
  var original=new CoilOutbound.Handover(newId(),trip,review.id(),newId(),vehicle,owner,List.of(first.id(),second.id(),other.id()));
  coilOutbound.handover(c,original);

  var dispatch=new CoilOutbound.ReturnDispatch(newId(),original.id(),receivingWarehouse,newId(),List.of(first.id(),second.id()));
  verifyExcludedWarehouseOperations(c,Map.of(
      "BulkCoilOutbound.dispatch_return",()->coilOutbound.dispatchReturn(c,dispatch)));
  verifyRejectedStockWrite("bw_coil_return_transit_line","transit_id",dispatch.id(),"FALSE",
      ()->coilOutbound.dispatchReturn(c,dispatch));
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bw_coil_return_transit WHERE id=UUID_TO_BIN(?)",
      Integer.class,dispatch.id())).isZero();
  var json=new tools.jackson.databind.ObjectMapper();
  var session=mvc.perform(post("/bff/tenants/current").with(authenticated(c.accountId().toString(),0))
      .with(csrf()).contentType("application/json")
      .content(json.writeValueAsString(Map.of("tenantId",c.tenantId().toString())))).andReturn().getResponse();
  assertThat(session.getStatus()).isEqualTo(200);
  var cookie=session.getCookie("JSESSIONID");
  var posted=mvc.perform(post("/api/bulk-coil-return-transits").cookie(cookie).with(csrf())
      .contentType("application/json").content(json.writeValueAsString(dispatch))).andReturn().getResponse();
  assertThat(posted.getStatus()).as(posted.getContentAsString()).isEqualTo(200);
  bindReferenceContext(c);
  var inTransit=coilOutbound.dispatchReturn(c,dispatch);
  assertThat(inTransit.coils()).hasSize(2).allSatisfy(coil -> assertThat(coil.receivedReturnId()).isNull());
  assertThatThrownBy(()->coilOutbound.dispatchReturn(c,new CoilOutbound.ReturnDispatch(
      dispatch.id(),original.id(),receivingWarehouse,newId(),dispatch.receiptIds())))
      .hasMessage("OUTBOUND_REQUEST_CONFLICT");
  var lookup=jdbc.queryForMap("""
      EXPLAIN SELECT received_return_id FROM bw_coil_return_transit_line
      WHERE tenant_id=UUID_TO_BIN(?) AND handover_id=UUID_TO_BIN(?) AND receipt_id=UUID_TO_BIN(?)
      """,c.tenantId().toString(),original.id(),first.id());
  assertThat(lookup.get("key")).isNotNull();
  assertThat(((Number)lookup.get("rows")).longValue()).isLessThanOrEqualTo(1L);
  verifyReversalBalances(notice,authorization,trip,first.id(),"90000","0","90000","0","51000");
  assertThatThrownBy(()->coilOutbound.dispatchReturn(c,new CoilOutbound.ReturnDispatch(
      newId(),original.id(),receivingWarehouse,newId(),List.of(second.id()))))
      .hasMessage("OUTBOUND_RETURN_ALREADY_IN_TRANSIT");
  assertThatThrownBy(()->coilOutbound.returnCoils(c,returnTo(original.id(),first.id(),destination)))
      .hasMessage("OUTBOUND_RETURN_TRANSIT_REQUIRED");
  assertThatThrownBy(()->coilOutbound.returnCoils(c,receiveTransitAt(dispatch,source,first.id())))
      .hasMessage("OUTBOUND_RETURN_TRANSIT_WAREHOUSE_MISMATCH");
  assertThatThrownBy(()->coilOutbound.returnCoils(c,receiveTransitAt(dispatch,destination,first.id(),other.id())))
      .hasMessage("OUTBOUND_RETURN_TRANSIT_COIL_MISMATCH");
  assertThat(coilOutbound.returnTransit(c,dispatch.id())).isEqualTo(inTransit);

  var batchA=receiveTransitAt(dispatch,destination,first.id());
  var firstResult=coilOutbound.returnCoils(c,batchA);
  assertThat(firstResult.restoredQuota()).isEqualByComparingTo("53000");
  assertThat(firstResult.restoredStock()).isEqualByComparingTo("51000");
  assertThat(coilOutbound.returnCoils(c,batchA)).isEqualTo(firstResult);
  assertThat(outboundStock.check(c,first.id())).isEqualTo(OutboundStock.Gate.PENDING_QUALITY);
  verifyReversalBalances(notice,authorization,trip,second.id(),"37000","0","37000","0","17000");
  var read=mvc.perform(get("/api/bulk-coil-return-transits/"+dispatch.id()).cookie(cookie)).andReturn().getResponse();
  assertThat(read.getStatus()).as(read.getContentAsString()).isEqualTo(200);
  bindReferenceContext(c);
  var partial=coilOutbound.returnTransit(c,dispatch.id());
  assertThat(partial.coils()).filteredOn(row -> row.receivedReturnId()==null)
      .extracting(CoilOutbound.TransitCoil::receiptId).containsExactly(second.id());
  assertThat(partial.coils()).filteredOn(row -> row.receiptId().equals(first.id()))
      .extracting(CoilOutbound.TransitCoil::receivedReturnId).containsExactly(batchA.id());
  assertThatThrownBy(()->coilOutbound.returnCoils(c,receiveTransitAt(dispatch,destination,first.id())))
      .hasMessage("OUTBOUND_RETURN_SCOPE_EXHAUSTED");

  var batchB=receiveTransitAt(dispatch,destination,second.id());
  // 在接收过账最后一步核销在途时注入故障，验证账务及在途状态全部回滚。
  jdbc.execute("ALTER TABLE bw_coil_return_transit_line ADD CONSTRAINT bw_test_receive_transit_failure CHECK(receipt_id<>X'"
      +second.id().replace("-","")+"' OR received_return_id IS NULL)");
  try {
   assertThatThrownBy(()->coilOutbound.returnCoils(c,batchB)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  } finally {
   jdbc.execute("ALTER TABLE bw_coil_return_transit_line DROP CHECK bw_test_receive_transit_failure");
  }
  assertThat(coilOutbound.returnTransit(c,dispatch.id())).isEqualTo(partial);
  verifyReversalBalances(notice,authorization,trip,second.id(),"37000","0","37000","0","17000");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bw_coil_partial_return WHERE id=UUID_TO_BIN(?)",
      Integer.class,batchB.id())).isZero();
  assertThat(jdbc.queryForObject("SELECT BIN_TO_UUID(warehouse_id) FROM bw_stock_coil WHERE receipt_id=UUID_TO_BIN(?)",
      String.class,second.id())).isEqualTo(source.warehouseId());
  var reroute=new CoilOutbound.RerouteTransitCoil(newId(),dispatch.id(),second.id(),receivingWarehouse,
      newWarehouse,newId(),"复核批准改派接收仓");
  verifyShortLossAndReopen(c,cookie,dispatch,batchB,second.id(),notice,authorization,trip,owner,partial,reroute);
  verifyTransitReroute(c,cookie,dispatch,batchB,reroute,first.id(),source.warehouseId(),notice,authorization,trip,partial);
  var arrival=receiveTransitAt(dispatch,newDestination,second.id());
  var receivedB=coilOutbound.returnCoils(c,arrival);
  assertThat(receivedB.restoredStock()).isEqualByComparingTo("17000");
  assertThat(coilOutbound.returnCoils(c,arrival)).isEqualTo(receivedB);
  assertThat(coilOutbound.rerouteTransitCoil(c,reroute)).isEqualTo(reroute);
  assertThat(jdbc.queryForObject("SELECT BIN_TO_UUID(current_warehouse_id) FROM bw_stock_receipt WHERE receipt_id=UUID_TO_BIN(?)",
      String.class,second.id())).isEqualTo(newWarehouse);
  assertReturnPositions(original.id(),arrival.id(),second.id(),source.locationId(),newDestination.locationId());
  verifyTraceCapacity(c,second.id(),first.id(),trip);
  verifyConcurrentHighHistoryTrace(c,second.id(),trip);
  assertThat(warehouseBalances.trace(c,second.id()).documents())
      .extracting(WarehouseBalances.TraceDocument::kind)
      .contains("RETURN_DISPATCH","RETURN_REROUTE","RETURN_LOSS","RETURN_LOSS_REOPENED","RETURN");
  assertThat(warehouseBalances.trace(c,second.id()).documents()).anySatisfy(event -> {
   assertThat(event.kind()).isEqualTo("RETURN_REROUTE");
   assertThat(event.id()).isEqualTo(reroute.id());
   assertThat(event.parentId()).isEqualTo(dispatch.id());
   assertThat(event.warehouseId()).isEqualTo(newWarehouse);
  });
  assertThat(coilOutbound.returnTransit(c,dispatch.id()).coils()).allSatisfy(coil -> assertThat(coil.receivedReturnId()).isNotNull());
  verifyReversalBalances(notice,authorization,trip,other.id(),"20000","0","20000","0","20000");
  assertThat(outboundStock.check(c,second.id())).isEqualTo(OutboundStock.Gate.PENDING_QUALITY);
  assertThat(outboundStock.check(c,other.id())).isEqualTo(OutboundStock.Gate.DISPATCHED);
  assertThatThrownBy(()->coilOutbound.settleTransitLoss(c,new CoilOutbound.SettleTransitLoss(
      newId(),dispatch.id(),second.id(),owner,newId(),"已接管后不得结清为短损")))
      .hasMessage("OUTBOUND_RETURN_TRANSIT_COIL_MISMATCH");

  // 返仓质检凭证属于实际接管批次中的原卷，其他接管卷不得继承本次凭证。
  var inspection=new CoilOutbound.ReturnedQuality(arrival.id(),second.id(),newId());
  verifyExcludedWarehouseOperations(c,Map.of(
      "BulkCoilOutbound.release_returned_quality",()->coilOutbound.releaseReturnedQuality(c,inspection)));
  verifyRejectedStockWrite("bw_stock_receipt","receipt_id",second.id(),"pending_quantity>0",
      ()->coilOutbound.releaseReturnedQuality(c,inspection));
  assertThat(jdbc.queryForObject("""
      SELECT COUNT(*) FROM bw_coil_partial_return_line
      WHERE receipt_id=UUID_TO_BIN(?) AND return_id=UUID_TO_BIN(?) AND quality_evidence_id IS NOT NULL
      """,Integer.class,second.id(),inspection.returnId())).isZero();
  coilOutbound.releaseReturnedQuality(c,inspection);
  coilOutbound.releaseReturnedQuality(c,inspection);
  var evidence=coilOutbound.returnTransit(c,dispatch.id()).history();
  assertThat(evidence.stream().filter(row -> row.kind().equals("RECEIVE") && row.receiptId().equals(second.id())))
      .singleElement().satisfies(row -> {
        assertThat(row.id()).isEqualTo(arrival.id());
        assertThat(row.evidenceId()).isEqualTo(arrival.custodyEvidenceId());
        assertThat(row.qualityEvidenceId()).isEqualTo(inspection.evidenceId());
      });
  assertThat(evidence.stream().filter(row -> row.kind().equals("RECEIVE") && row.receiptId().equals(first.id())))
      .singleElement().satisfies(row -> assertThat(row.qualityEvidenceId()).isNull());
  verifyReversalBalances(notice,authorization,trip,other.id(),"20000","0","20000","0","20000");
 }

 /** 第二卷暂未到货，批准结清短损后凭独立补到复核重开。 */
 private void verifyShortLossAndReopen(VerifiedTenantContext c,jakarta.servlet.http.Cookie cookie,
     CoilOutbound.ReturnDispatch dispatch,CoilOutbound.PartialReturn arrival,String receiptId,String notice,
     String authorization,String trip,String partner,CoilOutbound.ReturnTransit partial,
     CoilOutbound.RerouteTransitCoil reroute) throws Exception {
  assertThat(partial.coils()).filteredOn(coil -> coil.receiptId().equals(receiptId))
      .extracting(CoilOutbound.TransitCoil::state).containsExactly(CoilOutbound.TransitState.IN_TRANSIT);
  var approval=new CoilOutbound.SettleTransitLoss(newId(),dispatch.id(),receiptId,partner,newId(),"现场复核确认短损");
  // 审批记录插入完成后使在途状态更新失败，真实数据库须一并撤回审批。
  jdbc.execute("ALTER TABLE bw_coil_return_transit_line ADD CONSTRAINT bw_test_loss_failure CHECK(receipt_id<>X'"
      +receiptId.replace("-","")+"' OR active_loss_id IS NULL)");
  try {
   assertThatThrownBy(()->coilOutbound.settleTransitLoss(c,approval)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  } finally {
   jdbc.execute("ALTER TABLE bw_coil_return_transit_line DROP CHECK bw_test_loss_failure");
  }
  assertThat(coilOutbound.returnTransit(c,dispatch.id())).isEqualTo(partial);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bw_coil_return_transit_loss WHERE id=UUID_TO_BIN(?)",
      Integer.class,approval.id())).isZero();
  verifyReversalBalances(notice,authorization,trip,receiptId,"37000","0","37000","0","17000");

  var json=new tools.jackson.databind.ObjectMapper();
  var closed=mvc.perform(post("/api/bulk-coil-return-transits:settle-loss").cookie(cookie)
      .with(csrf()).contentType("application/json").content(json.writeValueAsString(approval))).andReturn().getResponse();
  assertThat(closed.getStatus()).as(closed.getContentAsString()).isEqualTo(200);
  var body=json.readTree(closed.getContentAsString());
  assertThat(body.path("responsiblePartnerId").asText()).isEqualTo(partner);
  assertThat(body.path("evidenceId").asText()).isEqualTo(approval.evidenceId());
  bindReferenceContext(c);
  assertThat(coilOutbound.settleTransitLoss(c,approval).bookQuantity()).isEqualByComparingTo("17000");
  assertThatThrownBy(()->coilOutbound.settleTransitLoss(c,new CoilOutbound.SettleTransitLoss(
      approval.id(),dispatch.id(),receiptId,partner,newId(),approval.reason()))).hasMessage("OUTBOUND_REQUEST_CONFLICT");
  assertThatThrownBy(()->coilOutbound.settleTransitLoss(c,new CoilOutbound.SettleTransitLoss(
      newId(),dispatch.id(),receiptId,partner,approval.evidenceId(),approval.reason())))
      .hasMessage("OUTBOUND_RETURN_LOSS_SETTLED");
  var status=coilOutbound.returnTransit(c,dispatch.id());
  assertThat(status.coils()).extracting(CoilOutbound.TransitCoil::state)
      .containsExactlyInAnyOrder(CoilOutbound.TransitState.RECEIVED,CoilOutbound.TransitState.LOSS_SETTLED);
  assertThat(status.coils()).filteredOn(coil -> coil.receiptId().equals(receiptId))
      .extracting(CoilOutbound.TransitCoil::lossId).containsExactly(approval.id());
  verifyReversalBalances(notice,authorization,trip,receiptId,"37000","0","37000","0","17000");
  assertThat(outboundStock.check(c,receiptId)).isEqualTo(OutboundStock.Gate.DISPATCHED);
  assertThatThrownBy(()->coilOutbound.returnCoils(c,arrival)).hasMessage("OUTBOUND_RETURN_LOSS_SETTLED");
  assertThatThrownBy(()->coilOutbound.rerouteTransitCoil(c,reroute)).hasMessage("OUTBOUND_RETURN_LOSS_SETTLED");
  verifyReversalBalances(notice,authorization,trip,receiptId,"37000","0","37000","0","17000");

  var plan=jdbc.queryForMap("EXPLAIN SELECT reason FROM bw_coil_return_transit_loss WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",
      c.tenantId().toString(),approval.id());
  assertThat(plan.get("key")).isEqualTo("PRIMARY");
  assertThat(((Number)plan.get("rows")).longValue()).isLessThanOrEqualTo(1L);
  var reopen=new CoilOutbound.ReopenTransitLoss(newId(),dispatch.id(),approval.id(),newId());
  verifyExcludedWarehouseOperations(c,Map.of(
      "BulkCoilOutbound.settle_transit_loss",()->coilOutbound.settleTransitLoss(c,approval),
      "BulkCoilOutbound.reopen_transit_loss",()->coilOutbound.reopenTransitLoss(c,reopen)));
  assertThatThrownBy(()->coilOutbound.reopenTransitLoss(c,new CoilOutbound.ReopenTransitLoss(
      newId(),dispatch.id(),approval.id(),approval.evidenceId()))).hasMessage("OUTBOUND_INPUT_INVALID");
  verifyRejectedStockWrite("bw_coil_return_transit_line","receipt_id",receiptId,"active_loss_id IS NOT NULL",
      ()->coilOutbound.reopenTransitLoss(c,reopen));
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bw_coil_return_loss_reopening WHERE loss_id=UUID_TO_BIN(?)",
      Integer.class,approval.id())).isZero();
  var reopened=mvc.perform(post("/api/bulk-coil-return-transits:reopen-loss").cookie(cookie)
      .with(csrf()).contentType("application/json").content(json.writeValueAsString(reopen))).andReturn().getResponse();
  assertThat(reopened.getStatus()).as(reopened.getContentAsString()).isEqualTo(204);
  bindReferenceContext(c);
  coilOutbound.reopenTransitLoss(c,reopen);
  assertThat(coilOutbound.returnTransit(c,dispatch.id()).coils()).isEqualTo(partial.coils());
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bw_coil_return_transit_loss WHERE id=UUID_TO_BIN(?)",
      Integer.class,approval.id())).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bw_coil_return_loss_reopening WHERE loss_id=UUID_TO_BIN(?)",
      Integer.class,approval.id())).isEqualTo(1);
  assertThat(coilOutbound.settleTransitLoss(c,approval).id()).isEqualTo(approval.id());
  assertThat(coilOutbound.returnTransit(c,dispatch.id()).coils()).isEqualTo(partial.coils());
  verifyReversalBalances(notice,authorization,trip,receiptId,"37000","0","37000","0","17000");
 }

 /**
  * @param context 真实组合应用可信租户
  * @param receiptId 带跨仓、短损与改仓记录的目标钢卷
  * @param unrelatedReceiptId 同租户其他已过账卷
  * @param tripId 同租户的有效历史车次
  */
 private void verifyTraceCapacity(VerifiedTenantContext context,String receiptId,String unrelatedReceiptId,String tripId)
     throws Exception {
  String tenant=context.tenantId().toString();
  String sql=com.appnexus.bulkwarehousing.adapter.persistence.WarehouseBalanceMapper.class
      .getMethod("traceDocuments",byte[].class,String.class).getAnnotation(org.apache.ibatis.annotations.Select.class).value()[0];
  var parameters=new ArrayList<String>();
  var matcher=java.util.regex.Pattern.compile("#\\{(__verifiedTenant|id)}").matcher(sql);
  while (matcher.find()) parameters.add(matcher.group(1).equals("__verifiedTenant") ? tenant : receiptId);
  var statement=sql.replace("#{__verifiedTenant}","UUID_TO_BIN(?)").replace("#{id}","?");
  var baseline=warehouseBalances.trace(context,receiptId);
  new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status->{
   try {
    int inserted=jdbc.update("""
        INSERT INTO bw_stock_occupation(tenant_id,id,receipt_id,released_at)
        WITH RECURSIVE digit AS (SELECT 0 AS n UNION ALL SELECT n+1 FROM digit WHERE n<9)
        SELECT UUID_TO_BIN(?),UUID_TO_BIN(UUID()),UUID_TO_BIN(?),CURRENT_TIMESTAMP(6)
        FROM digit a CROSS JOIN digit b CROSS JOIN digit c CROSS JOIN digit d
        """,tenant,unrelatedReceiptId);
    assertThat(inserted).isEqualTo(10000);
    int allocations=jdbc.update("""
        INSERT INTO bw_coil_allocation(tenant_id,id,trip_id,receipt_id,serial,quantity)
        SELECT o.tenant_id,o.id,UUID_TO_BIN(?),o.receipt_id,'TRACE-CAPACITY',1
        FROM bw_stock_occupation o LEFT JOIN bw_coil_allocation a ON a.tenant_id=o.tenant_id AND a.id=o.id
        WHERE o.tenant_id=UUID_TO_BIN(?) AND o.receipt_id=UUID_TO_BIN(?) AND a.id IS NULL
        """,tripId,tenant,unrelatedReceiptId);
    assertThat(allocations).isEqualTo(10000);
    var plan=jdbc.queryForObject("EXPLAIN ANALYZE "+statement,String.class,parameters.toArray());
    assertThat(plan).contains("actual time=").contains("Index lookup");
    assertThat(plan).doesNotContain("Table scan on l");
    assertThat(warehouseBalances.trace(context,receiptId)).isEqualTo(baseline);
    System.out.println("TRACE_CAPACITY: unrelatedAllocations=10000, events="+baseline.documents().size()+"\n"+plan);
   } finally {
    status.setRollbackOnly();
   }
  });
 }

 /**
  * @param context 可信租户
  * @param receiptId 已含跨仓和短损历史的目标卷
  * @param tripId 同租户车次，用于合成已经释放的历史配货
  */
 private void verifyConcurrentHighHistoryTrace(VerifiedTenantContext context,String receiptId,String tripId)
     throws Exception {
  String tenant=context.tenantId().toString();
  String prefix=UUID.randomUUID().toString().replace("-","").substring(0,20).toUpperCase(java.util.Locale.ROOT);
  var baseline=warehouseBalances.trace(context,receiptId);
  var transaction=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
  var pool=java.util.concurrent.Executors.newFixedThreadPool(4);
  try {
   transaction.executeWithoutResult(status->{
    assertThat(jdbc.update("""
        INSERT INTO bw_stock_occupation(tenant_id,id,receipt_id,released_at)
        WITH RECURSIVE sequence AS (SELECT 1 AS n UNION ALL SELECT n+1 FROM sequence WHERE n<1000)
        SELECT UUID_TO_BIN(?),UNHEX(CONCAT(?,LPAD(HEX(n),12,'0'))),UUID_TO_BIN(?),CURRENT_TIMESTAMP(6)
        FROM sequence
        """,tenant,prefix,receiptId)).isEqualTo(1000);
    assertThat(jdbc.update("""
        INSERT INTO bw_coil_allocation(tenant_id,id,trip_id,receipt_id,serial,quantity)
        SELECT tenant_id,id,UUID_TO_BIN(?),receipt_id,'TRACE-HIGH-HISTORY',1 FROM bw_stock_occupation
        WHERE tenant_id=UUID_TO_BIN(?) AND receipt_id=UUID_TO_BIN(?) AND LEFT(HEX(id),20)=?
        """,tripId,tenant,receiptId,prefix)).isEqualTo(1000);
   });
   var expected=warehouseBalances.trace(context,receiptId);
   assertThat(expected.stock()).isEqualTo(baseline.stock());
   assertThat(expected.documents()).hasSize(baseline.documents().size()+2000);
   var ready=new java.util.concurrent.CountDownLatch(4);
   var start=new java.util.concurrent.CountDownLatch(1);
   var futures=new ArrayList<java.util.concurrent.Future<List<Long>>>();
   for (int worker=0;worker<4;worker++) {
    futures.add(pool.submit(()->{
     bindReferenceContext(context);
     try {
      ready.countDown();
      assertThat(start.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      var durations=new ArrayList<Long>();
      for (int repetition=0;repetition<4;repetition++) {
       long began=System.nanoTime();
       var trace=warehouseBalances.trace(context,receiptId);
       durations.add(java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-began));
       assertThat(trace).isEqualTo(expected);
      }
      return durations;
     } finally {
      org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
     }
    }));
   }
   assertThat(ready.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
   start.countDown();
   var timings=new ArrayList<Long>();
   for (var future:futures) timings.addAll(future.get(30,java.util.concurrent.TimeUnit.SECONDS));
   assertThat(timings).hasSize(16);
   System.out.printf("TRACE_HIGH_HISTORY: events=%d, concurrency=4, reads=16, maxMillis=%d%n",
       expected.documents().size(),java.util.Collections.max(timings));
  } finally {
   pool.shutdownNow();
   transaction.executeWithoutResult(status->{
    jdbc.update("DELETE FROM bw_coil_allocation WHERE tenant_id=UUID_TO_BIN(?) AND receipt_id=UUID_TO_BIN(?) AND LEFT(HEX(id),20)=?",
        tenant,receiptId,prefix);
    jdbc.update("DELETE FROM bw_stock_occupation WHERE tenant_id=UUID_TO_BIN(?) AND receipt_id=UUID_TO_BIN(?) AND LEFT(HEX(id),20)=?",
        tenant,receiptId,prefix);
   });
  }
 }

 /** 一卷已经接管，另一卷改仓；分解三仓权限并使活动路由写入失败后重试。 */
 private void verifyTransitReroute(VerifiedTenantContext c,jakarta.servlet.http.Cookie cookie,
     CoilOutbound.ReturnDispatch dispatch,CoilOutbound.PartialReturn oldArrival,CoilOutbound.RerouteTransitCoil reroute,
     String alreadyReceived,String sourceWarehouse,String notice,String authorization,String trip,
     CoilOutbound.ReturnTransit beforeRoute) throws Exception {
  var admin=new com.appnexus.platform.authorization.RoleManagement.Context(c.accountId(),c.tenantId(),c.memberId());
  UUID role=UUID.fromString(jdbc.queryForObject("""
      SELECT BIN_TO_UUID(id) FROM access_role WHERE tenant_id=UUID_TO_BIN(?) AND system_key LIKE 'TENANT_ADMIN:%'
      """,String.class,c.tenantId().toString()));
  var before=rolePermissions.getAuthorization(admin,role);
  UUID readPermission=UUID.fromString(jdbc.queryForObject("""
      SELECT BIN_TO_UUID(id) FROM access_permission WHERE code='masterdata.warehouse:read'
      """,String.class));
  jdbc.update("""
      UPDATE access_member_authorization SET inherit_data_scope=TRUE
      WHERE tenant_id=UUID_TO_BIN(?) AND member_id=UUID_TO_BIN(?) AND role_id=UUID_TO_BIN(?)
      """,c.tenantId().toString(),c.memberId().toString(),role.toString());
  var warehouses=Set.of(UUID.fromString(sourceWarehouse),UUID.fromString(dispatch.receivingWarehouseId()),
      UUID.fromString(reroute.newReceivingWarehouseId()));
  try {
   for (var denied:warehouses) {
    var allowed=new HashSet<>(warehouses);
    allowed.remove(denied);
    scopeCrossReturnWarehouses(admin,role,readPermission,allowed);
    assertThatThrownBy(()->coilOutbound.rerouteTransitCoil(c,reroute)).hasMessage("INBOUND_ACCESS_DENIED");
   }
   scopeCrossReturnWarehouses(admin,role,readPermission,warehouses);
   assertThatThrownBy(()->coilOutbound.rerouteTransitCoil(c,new CoilOutbound.RerouteTransitCoil(
       newId(),dispatch.id(),alreadyReceived,dispatch.receivingWarehouseId(),reroute.newReceivingWarehouseId(),
       newId(),reroute.reason()))).hasMessage("OUTBOUND_RETURN_TRANSIT_COIL_MISMATCH");

   // 追加批准记录后使活动路由写入失败，整笔改单应撤回。
   jdbc.execute("ALTER TABLE bw_coil_return_transit_line ADD CONSTRAINT bw_test_reroute_failure CHECK(receipt_id<>X'"
       +reroute.receiptId().replace("-","")+"' OR active_reroute_id IS NULL)");
   try {
    assertThatThrownBy(()->coilOutbound.rerouteTransitCoil(c,reroute)).isInstanceOf(org.springframework.dao.DataAccessException.class);
   } finally {
    jdbc.execute("ALTER TABLE bw_coil_return_transit_line DROP CHECK bw_test_reroute_failure");
   }
   assertThat(coilOutbound.returnTransit(c,dispatch.id()).coils()).isEqualTo(beforeRoute.coils());
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bw_coil_return_transit_reroute WHERE id=UUID_TO_BIN(?)",
       Integer.class,reroute.id())).isZero();
   verifyReversalBalances(notice,authorization,trip,reroute.receiptId(),"37000","0","37000","0","17000");

   var json=new tools.jackson.databind.ObjectMapper();
   assertThatThrownBy(()->commitWhileCompeting(c,()->coilOutbound.rerouteTransitCoil(c,reroute),
       ()->coilOutbound.returnCoils(c,oldArrival)))
       .isInstanceOf(java.util.concurrent.ExecutionException.class)
       .hasCauseInstanceOf(com.appnexus.contracts.businessobject.BusinessObjects.Rejection.class)
       .hasRootCauseMessage("OUTBOUND_RETURN_TRANSIT_WAREHOUSE_MISMATCH");
   var response=mvc.perform(post("/api/bulk-coil-return-transits:reroute-coil").cookie(cookie)
       .with(csrf()).contentType("application/json").content(json.writeValueAsString(reroute))).andReturn().getResponse();
   assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
   var changed=json.readTree(response.getContentAsString());
   assertThat(changed.path("evidenceId").asText()).isEqualTo(reroute.evidenceId());
   assertThat(changed.path("reason").asText()).isEqualTo(reroute.reason());
   bindReferenceContext(c);
   assertThat(coilOutbound.rerouteTransitCoil(c,reroute)).isEqualTo(reroute);
   assertThatThrownBy(()->coilOutbound.rerouteTransitCoil(c,new CoilOutbound.RerouteTransitCoil(
       reroute.id(),dispatch.id(),reroute.receiptId(),reroute.expectedReceivingWarehouseId(),
       reroute.newReceivingWarehouseId(),newId(),reroute.reason()))).hasMessage("OUTBOUND_REQUEST_CONFLICT");
   assertThatThrownBy(()->coilOutbound.rerouteTransitCoil(c,new CoilOutbound.RerouteTransitCoil(
       newId(),dispatch.id(),reroute.receiptId(),reroute.expectedReceivingWarehouseId(),
       reroute.newReceivingWarehouseId(),newId(),reroute.reason()))).hasMessage("OUTBOUND_RETURN_TRANSIT_ROUTE_CHANGED");
   assertThat(coilOutbound.returnTransit(c,dispatch.id()).receivingWarehouseId()).isEqualTo(dispatch.receivingWarehouseId());
   assertThat(coilOutbound.returnTransit(c,dispatch.id()).coils()).filteredOn(row -> row.receiptId().equals(alreadyReceived))
       .extracting(CoilOutbound.TransitCoil::receivingWarehouseId).containsExactly(dispatch.receivingWarehouseId());
   assertThat(coilOutbound.returnTransit(c,dispatch.id()).coils()).filteredOn(row -> row.receiptId().equals(reroute.receiptId()))
       .extracting(CoilOutbound.TransitCoil::receivingWarehouseId,CoilOutbound.TransitCoil::rerouteId)
       .containsExactly(tuple(reroute.newReceivingWarehouseId(),reroute.id()));
   assertThatThrownBy(()->coilOutbound.returnCoils(c,oldArrival)).hasMessage("OUTBOUND_RETURN_TRANSIT_WAREHOUSE_MISMATCH");
   for (String required : List.of(reroute.expectedReceivingWarehouseId(), reroute.newReceivingWarehouseId())) {
    var allowed = new HashSet<>(warehouses);
    allowed.remove(UUID.fromString(required));
    scopeCrossReturnWarehouses(admin,role,readPermission,allowed);
    assertThatThrownBy(()->warehouseBalances.trace(c,reroute.receiptId()))
        .isInstanceOf(com.appnexus.contracts.businessobject.BusinessObjects.Rejection.class)
        .hasMessage("INBOUND_ACCESS_DENIED");
   }
   scopeCrossReturnWarehouses(admin,role,readPermission,warehouses);
   verifyReversalBalances(notice,authorization,trip,reroute.receiptId(),"37000","0","37000","0","17000");
   var plan=jdbc.queryForMap("""
       EXPLAIN SELECT receiving_warehouse_id FROM bw_coil_return_transit_reroute
       WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)
       """,c.tenantId().toString(),reroute.id());
   assertThat(plan.get("key")).isEqualTo("PRIMARY");
   assertThat(((Number)plan.get("rows")).longValue()).isLessThanOrEqualTo(1L);
  } finally {
   var current=rolePermissions.getAuthorization(admin,role);
   rolePermissions.replaceAuthorization(admin,role,current.roleVersion(),before.permissionIds(),before.dataPolicies(),
       "恢复在途改单用例权限");
  }
 }

 private static CoilOutbound.PartialReturn receiveTransitAt(CoilOutbound.ReturnDispatch dispatch,
     InboundReceiptReferences.Position destination,String... ids) {
  return new CoilOutbound.PartialReturn(newId(),dispatch.handoverId(),newId(),newId(),
      java.util.Arrays.stream(ids).map(id -> new CoilOutbound.ReturnedCoil(id,destination.locationId(),destination)).toList(),
      dispatch.id());
 }

 /** 入仓整卷 A→B，冻结及占用时拒绝，证明旧提货授权无法覆盖新取得货权。 */
 private void verifyCoilTitleTransfer(VerifiedTenantContext c,InboundReceiptReferences.Item item,
     String ownerA,String unit,String unitCode) throws Exception {
  String warehouse=masterData.create(c,MasterData.Kind.WAREHOUSE,
      new MasterData.Input("TITLE-TRANSFER-W","不动货过户测试仓",Map.of(),0)).get("id");
  var position=returnPosition(c,warehouse,"TITLE-TRANSFER-W","TITLE-TRANSFER-POS");
  String ownerB=newId(),inbound=newId(),vehicle="TITLE-TRANSFER-VEHICLE";
  jdbc.update("""
      INSERT INTO md_partner(id,tenant_id,code,name,partner_type,organization_id)
      SELECT UUID_TO_BIN(?),tenant_id,'TITLE-OWNER-B','目标货主','CUSTOMER',organization_id
      FROM md_partner WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)
      """,ownerB,c.tenantId().toString(),ownerA);
  inboundReceiving.issue(c,new InboundReceiving.Notice(inbound,warehouse,ownerA,item,unit,new BigDecimal("51000")));
  var coil=receiveWeighFixture(c,inbound,position,item,ownerA,unit,unitCode,"51000","TITLE-TRANSFER-51");
  String tripA=titleTransferTrip(c,warehouse,ownerA,item,unit,unitCode,vehicle);
  String staleTripB=titleTransferTrip(c,warehouse,ownerB,item,unit,unitCode,vehicle);
  var command=new CoilTitleTransfer.Command(newId(),coil.id(),warehouse,ownerA,ownerB,0,newId());
  verifyExcludedWarehouseOperations(c,Map.of(
      "BulkCoilTitleTransfer.transfer",()->coilTitleTransfer.transfer(c,command)));
  var freeze=new OutboundStock.Freeze(newId(),coil.id(),newId());
  outboundStock.freeze(c,freeze);
  assertThatThrownBy(()->coilTitleTransfer.transfer(c,command)).hasMessage("OUTBOUND_FROZEN");
  outboundStock.lift(c,new OutboundStock.Freeze(freeze.id(),coil.id(),newId()));
  var allocation=new CoilOutbound.Allocate(newId(),tripA,coil.id());
  coilOutbound.allocate(c,allocation);
  coilOutbound.verifyVehicleLimits(c,limits(tripA,vehicle,unit,"51000","71000"));
  var review=coilOutbound.reviewWeighing(c,weigh(allocation,vehicle,unit,"51000"));
  assertThat(review.state()).isEqualTo(CoilOutbound.ReviewState.APPROVED);
  assertThatThrownBy(()->coilTitleTransfer.transfer(c,command)).hasMessage("OUTBOUND_OCCUPIED");
  coilOutbound.cancel(c,allocation);

  // 转出分录已执行，转入失败时两边及活动货权仍须完整回滚。
  jdbc.execute("ALTER TABLE bw_coil_title_entry ADD CONSTRAINT bw_test_title_entry_failure CHECK(transfer_id<>X'"
      +command.id().replace("-","")+"' OR direction='OUT')");
  try {
   assertThatThrownBy(()->coilTitleTransfer.transfer(c,command)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  } finally {
   jdbc.execute("ALTER TABLE bw_coil_title_entry DROP CHECK bw_test_title_entry_failure");
  }
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bw_coil_title_entry WHERE transfer_id=UUID_TO_BIN(?)",
      Integer.class,command.id())).isZero();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bw_coil_title_transfer WHERE id=UUID_TO_BIN(?)",
      Integer.class,command.id())).isZero();
  assertThat(jdbc.queryForObject("SELECT BIN_TO_UUID(owner_id) FROM bw_inbound_title WHERE receipt_id=UUID_TO_BIN(?)",
      String.class,coil.id())).isEqualTo(ownerA);

  var json=new tools.jackson.databind.ObjectMapper();
  var session=mvc.perform(post("/bff/tenants/current").with(authenticated(c.accountId().toString(),0))
      .with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("tenantId",c.tenantId().toString()))))
      .andReturn().getResponse();
  assertThat(session.getStatus()).isEqualTo(200);
  var transferred=mvc.perform(post("/api/bulk-coil-title-transfers").cookie(session.getCookie("JSESSIONID"))
      .with(csrf()).contentType("application/json").content(json.writeValueAsString(command))).andReturn().getResponse();
  assertThat(transferred.getStatus()).as(transferred.getContentAsString()).isEqualTo(200);
  bindReferenceContext(c);
  var result=coilTitleTransfer.transfer(c,command);
  assertThat(warehouseBalances.document(c,WarehouseBalances.DocumentKind.TITLE_TRANSFER,command.id())
      .header().otherOwnerId()).isEqualTo(ownerB);

  assertThat(result.quantity()).isEqualByComparingTo("51000");
  assertThat(result.locationId()).isEqualTo(position.locationId());
  assertThat(result.titleRevision()).isPositive();
  assertThat(jdbc.queryForObject("SELECT BIN_TO_UUID(owner_id) FROM bw_inbound_title WHERE receipt_id=UUID_TO_BIN(?)",
      String.class,coil.id())).isEqualTo(ownerB);
  var entries=jdbc.queryForMap("""
      SELECT COUNT(*) entries,SUM(signed_quantity) balance,
        SUM(CASE WHEN direction='OUT' THEN signed_quantity ELSE 0 END) debit,
        SUM(CASE WHEN direction='IN' THEN signed_quantity ELSE 0 END) credit
      FROM bw_coil_title_entry WHERE transfer_id=UUID_TO_BIN(?)
      """,command.id());
  assertThat(((Number)entries.get("entries")).intValue()).isEqualTo(2);
  assertThat((BigDecimal)entries.get("balance")).isEqualByComparingTo("0");
  assertThat((BigDecimal)entries.get("debit")).isEqualByComparingTo("-51000");
  assertThat((BigDecimal)entries.get("credit")).isEqualByComparingTo("51000");
  assertThat(jdbc.queryForObject("SELECT inbound_quantity-shipped_quantity FROM bw_stock_receipt WHERE receipt_id=UUID_TO_BIN(?)",
      BigDecimal.class,coil.id())).isEqualByComparingTo("51000");
  assertThatThrownBy(()->coilOutbound.allocate(c,new CoilOutbound.Allocate(newId(),tripA,coil.id())))
      .hasMessage("OUTBOUND_ACCESS_DENIED");
  assertThatThrownBy(()->coilOutbound.handover(c,new CoilOutbound.Handover(newId(),tripA,review.id(),newId(),vehicle,
      ownerA,List.of(coil.id())))).hasMessage("OUTBOUND_REVIEW_REQUIRED");
  assertThatThrownBy(()->coilOutbound.allocate(c,new CoilOutbound.Allocate(newId(),staleTripB,coil.id())))
      .hasMessage("OUTBOUND_AUTHORIZATION_TITLE_CHANGED");
  String newTripB=titleTransferTrip(c,warehouse,ownerB,item,unit,unitCode,vehicle);
  var scopeA=new WarehouseBalances.Scope(ownerA,warehouse,item.productId(),item.identityId());
  var scopeB=new WarehouseBalances.Scope(ownerB,warehouse,item.productId(),item.identityId());
  var cookie=session.getCookie("JSESSIONID");
  var physical=mvc.perform(get("/api/bulk-warehouse-stock-balances").cookie(cookie)
      .param("ownerId",ownerB).param("warehouseId",warehouse).param("productId",item.productId())
      .param("identityId",item.identityId())).andReturn().getResponse();
  assertThat(physical.getStatus()).as(physical.getContentAsString()).isEqualTo(200);
  var stockRow=json.readTree(physical.getContentAsString()).get(0);
  assertThat(stockRow.path("onHandTitle").decimalValue()).isEqualByComparingTo("51000");
  assertThat(stockRow.path("pickable").decimalValue()).isEqualByComparingTo("51000");
  var authorizations=mvc.perform(get("/api/bulk-pickup-authorization-balances").cookie(cookie)
      .param("ownerId",ownerA).param("warehouseId",warehouse).param("productId",item.productId())
      .param("identityId",item.identityId())).andReturn().getResponse();
  assertThat(authorizations.getStatus()).as(authorizations.getContentAsString()).isEqualTo(200);
  assertThat(json.readTree(authorizations.getContentAsString()).path("rows").get(0).path("remaining").decimalValue())
      .isEqualByComparingTo("53000");
  bindReferenceContext(c);
  assertThat(warehouseBalances.physical(c,scopeA)).isEmpty();
  assertThat(warehouseBalances.physical(c,new WarehouseBalances.Scope(ownerB,warehouse,item.productId(),newId()))).isEmpty();
  assertThat(warehouseBalances.coils(c,scopeA,null,50).rows()).isEmpty();
  var visible=warehouseBalances.coils(c,scopeB,null,50).rows().getFirst();
  assertThat(visible.receiptId()).isEqualTo(coil.id());
  assertThat(visible.titleRevision()).isEqualTo(result.titleRevision());
  assertThat(visible.transferable()).isTrue();
  var coilPlan=jdbc.queryForList("""
      EXPLAIN SELECT s.receipt_id,k.serial,t.title_revision
      FROM bw_inbound_title t
      JOIN bw_stock_receipt s ON s.tenant_id=t.tenant_id AND s.receipt_id=t.receipt_id
      JOIN bw_stock_coil k ON k.tenant_id=s.tenant_id AND k.receipt_id=s.receipt_id
      JOIN bw_inbound_receipt r ON r.tenant_id=s.tenant_id AND r.id=s.receipt_id
      JOIN bw_inbound_custody c ON c.tenant_id=r.tenant_id AND c.id=r.custody_id
      JOIN bw_inbound_notice n ON n.tenant_id=c.tenant_id AND n.id=c.notice_id
      WHERE t.tenant_id=UUID_TO_BIN(?) AND t.owner_id=UUID_TO_BIN(?)
        AND COALESCE(s.current_warehouse_id,c.warehouse_id)=UUID_TO_BIN(?)
        AND n.product_id=UUID_TO_BIN(?) AND n.identity_id=UUID_TO_BIN(?)
        AND s.shipped_quantity=0 AND t.issued_quantity=0 AND t.quantity=s.inbound_quantity
        AND t.effective_at<=CURRENT_TIMESTAMP(6)
      ORDER BY s.receipt_id LIMIT 51
      """,c.tenantId().toString(),ownerB,warehouse,item.productId(),item.identityId());
  assertThat(coilPlan).filteredOn(row -> "t".equals(row.get("table"))).singleElement().satisfies(row -> {
   assertThat(row.get("key")).isNotNull();
   assertThat(((Number)row.get("rows")).longValue()).isLessThanOrEqualTo(1L);
  });
  var firstPage=warehouseBalances.authorizations(c,scopeB,null,1);
  assertThat(firstPage.rows()).hasSize(1);
  assertThat(firstPage.nextAfterId()).isEqualTo(firstPage.rows().getFirst().authorizationId());
  assertThat(warehouseBalances.authorizations(c,scopeB,firstPage.nextAfterId(),1).rows())
      .extracting(WarehouseBalances.AuthorizationBalance::authorizationId)
      .doesNotContain(firstPage.nextAfterId()).hasSize(1);

  var next=new CoilOutbound.Allocate(newId(),newTripB,coil.id());
  assertThat(coilOutbound.allocate(c,next).quantity()).isEqualByComparingTo("51000");
  var occupied=warehouseBalances.physical(c,scopeB).getFirst();
  assertThat(occupied.onHandTitle()).isEqualByComparingTo("51000");
  assertThat(occupied.pickable()).isEqualByComparingTo("0");
  assertThat(warehouseBalances.coils(c,scopeB,null,50).rows().getFirst().status()).isEqualTo("OCCUPIED");
  assertThat(warehouseBalances.authorizations(c,scopeB,null,50).rows())
      .anySatisfy(row -> assertThat(row.remaining()).isEqualByComparingTo("2000"));
  coilOutbound.cancel(c,next);
  var freezeB=new OutboundStock.Freeze(newId(),coil.id(),newId());
  outboundStock.freeze(c,freezeB);
  assertThat(warehouseBalances.physical(c,scopeB).getFirst().pickable()).isEqualByComparingTo("0");
  assertThat(warehouseBalances.coils(c,scopeB,null,50).rows().getFirst().status()).isEqualTo("FROZEN");
  outboundStock.lift(c,new OutboundStock.Freeze(freezeB.id(),coil.id(),newId()));
  assertThat(warehouseBalances.physical(c,scopeB).getFirst().pickable()).isEqualByComparingTo("51000");

  String pendingNotice=newId();
  inboundReceiving.issue(c,new InboundReceiving.Notice(pendingNotice,warehouse,ownerB,item,unit,new BigDecimal("3000")));
  var pending=coalReceipt(c,pendingNotice,position,item,ownerB,unit,"3000");
  inboundReceiving.receive(c,pending);
  inboundDisposition.registerTitle(c,new InboundDisposition.TitleDecision(pending.id(),newId(),
      java.time.Instant.parse("2026-11-20T09:00:00Z")));
  var withPending=warehouseBalances.physical(c,scopeB).getFirst();
  assertThat(withPending.onHandTitle()).isEqualByComparingTo("54000");
  assertThat(withPending.pickable()).isEqualByComparingTo("51000");
  var stockIndex=jdbc.queryForMap("""
      EXPLAIN SELECT receipt_id FROM bw_inbound_title WHERE tenant_id=UUID_TO_BIN(?) AND owner_id=UUID_TO_BIN(?)
      """,c.tenantId().toString(),ownerB);
  assertThat(stockIndex.get("key")).isEqualTo("idx_bw_title_owner_receipt");
  var noticeIndex=jdbc.queryForMap("""
      EXPLAIN SELECT id FROM bw_outbound_notice WHERE tenant_id=UUID_TO_BIN(?) AND warehouse_id=UUID_TO_BIN(?)
        AND owner_id=UUID_TO_BIN(?) AND product_id=UUID_TO_BIN(?)
      """,c.tenantId().toString(),warehouse,ownerB,item.productId());
  assertThat(noticeIndex.get("key")).isEqualTo("idx_bw_notice_owner_product");

  var finalAllocation=new CoilOutbound.Allocate(newId(),newTripB,coil.id());
  coilOutbound.allocate(c,finalAllocation);
  coilOutbound.verifyVehicleLimits(c,limits(newTripB,vehicle,unit,"51000","71000"));
  var bReview=coilOutbound.reviewWeighing(c,weigh(finalAllocation,vehicle,unit,"51000"));
  var finalHandover=new CoilOutbound.Handover(newId(),newTripB,bReview.id(),newId(),vehicle,ownerB,List.of(coil.id()));
  var concurrentTitle=new CoilTitleTransfer.Command(newId(),coil.id(),warehouse,ownerB,ownerA,visible.titleRevision(),newId());
  assertThatThrownBy(()->commitWhileCompeting(c,()->coilOutbound.handover(c,finalHandover),
      ()->coilTitleTransfer.transfer(c,concurrentTitle)))
      .isInstanceOf(java.util.concurrent.ExecutionException.class)
      .hasCauseInstanceOf(com.appnexus.contracts.businessobject.BusinessObjects.Rejection.class)
      .hasRootCauseMessage("WAREHOUSING_TITLE_NOT_TRANSFERABLE");
  var trace=warehouseBalances.trace(c,coil.id());
  assertThat(trace.stock().receiptId()).isEqualTo(coil.id());
  assertThat(trace.stock().ownerId()).isEqualTo(ownerB);
  assertThat(trace.documents()).extracting(WarehouseBalances.TraceDocument::kind)
      .contains("INBOUND","TRANSFER","OUTBOUND","ALLOCATION","WEIGHING","HANDOVER");
  assertThat(trace.documents()).anySatisfy(event -> {
   assertThat(event.kind()).isEqualTo("TRANSFER");
   assertThat(event.ownerId()).isEqualTo(ownerA);
   assertThat(event.otherOwnerId()).isEqualTo(ownerB);
  });
  verifyTraceHistoricalPartnerDenied(c,coil.id(),ownerB);
  var remainder=warehouseBalances.physical(c,scopeB).getFirst();
  assertThat(remainder.onHandTitle()).isEqualByComparingTo("3000");
  assertThat(remainder.pickable()).isEqualByComparingTo("0");
  assertThat(warehouseBalances.authorizations(c,scopeB,null,50).rows()).anySatisfy(row -> {
   assertThat(row.issued()).isEqualByComparingTo("51000");
   assertThat(row.remaining()).isEqualByComparingTo("2000");
  });

 }

 /**
  * @param context 保有追溯功能权限的租户主体
  * @param receiptId 已过户的库存身份
  * @param currentOwner 唯一仍有主数据读取权限的当前货主
  */
 private void verifyTraceHistoricalPartnerDenied(VerifiedTenantContext context,String receiptId,String currentOwner) {
  var admin=new com.appnexus.platform.authorization.RoleManagement.Context(
      context.accountId(),context.tenantId(),context.memberId());
  UUID role=UUID.fromString(jdbc.queryForObject("""
      SELECT BIN_TO_UUID(id) FROM access_role WHERE tenant_id=UUID_TO_BIN(?) AND system_key LIKE 'TENANT_ADMIN:%'
      """,String.class,context.tenantId().toString()));
  UUID permission=UUID.fromString(jdbc.queryForObject("""
      SELECT BIN_TO_UUID(id) FROM access_permission WHERE code='masterdata.partner:read'
      """,String.class));
  var original=rolePermissions.getAuthorization(admin,role);
  var policies=new HashSet<>(original.dataPolicies());
  policies.removeIf(policy->policy.permissionId().equals(permission));
  policies.add(new com.appnexus.platform.authorization.RolePermissionManagement.DataPolicy(permission,
      com.appnexus.platform.authorization.DataScopeAuthorization.ScopeType.RULES,"masterdata.partner",Set.of(),
      List.of(new com.appnexus.platform.authorization.DataGrantRules.Rule(
          com.appnexus.platform.authorization.DataGrantRules.SubjectType.ROLE_MEMBERS,Set.of(),
          Set.of(UUID.fromString(currentOwner)),List.of(),false))));
  try {
   rolePermissions.replaceAuthorization(admin,role,original.roleVersion(),original.permissionIds(),policies,"限制历史追溯货主");
   assertThatThrownBy(()->warehouseBalances.trace(context,receiptId))
       .isInstanceOf(com.appnexus.contracts.businessobject.BusinessObjects.Rejection.class)
       .hasMessage("OUTBOUND_ACCESS_DENIED");
  } finally {
   var current=rolePermissions.getAuthorization(admin,role);
   rolePermissions.replaceAuthorization(admin,role,current.roleVersion(),original.permissionIds(),original.dataPolicies(),
       "恢复单卷追溯货主权限");
  }
 }

 private String titleTransferTrip(VerifiedTenantContext c,String warehouse,String owner,InboundReceiptReferences.Item item,
     String unit,String unitCode,String vehicle) {
  String notice=newId(),authorization=newId(),trip=newId();
  var quantity=new BigDecimal("53000");
  coilOutbound.issue(c,new CoilOutbound.Notice(notice,warehouse,owner,item,unit,unitCode,quantity));
  coilOutbound.authorize(c,new CoilOutbound.Authorization(authorization,notice,owner,
      java.time.Instant.now().plusSeconds(3600),quantity));
  coilOutbound.plan(c,new CoilOutbound.Trip(trip,authorization,vehicle,quantity));
  return trip;
 }

 /** 同车三卷，分批返回两卷；保留第三卷已出，并验证待检与冻结独立关卡。 */
 private void verifyPartialCoilReturn(VerifiedTenantContext c,InboundReceiptReferences.Position position,
     InboundReceiptReferences.Item item,String owner,String unit,String unitCode) throws Exception {
  String inbound=newId(), notice=newId(), auth=newId(), trip=newId(), nextTrip=newId();
  String vehicle="PARTIAL-RETURN-VEHICLE";
  inboundReceiving.issue(c,new InboundReceiving.Notice(inbound,position.warehouseId(),owner,item,unit,new BigDecimal("88000")));
  var a=receiveWeighFixture(c,inbound,position,item,owner,unit,unitCode,"51000","PARTIAL-51");
  var b=receiveWeighFixture(c,inbound,position,item,owner,unit,unitCode,"17000","PARTIAL-17");
  var other=receiveWeighFixture(c,inbound,position,item,owner,unit,unitCode,"20000","PARTIAL-20");
  coilOutbound.issue(c,new CoilOutbound.Notice(notice,position.warehouseId(),owner,item,unit,unitCode,new BigDecimal("100000")));
  coilOutbound.authorize(c,new CoilOutbound.Authorization(auth,notice,owner,java.time.Instant.now().plusSeconds(3600),
      new BigDecimal("100000")));
  coilOutbound.plan(c,new CoilOutbound.Trip(trip,auth,vehicle,new BigDecimal("100000")));
  var allocations=List.of(new CoilOutbound.Allocate(newId(),trip,a.id()),new CoilOutbound.Allocate(newId(),trip,b.id()),
      new CoilOutbound.Allocate(newId(),trip,other.id()));
  allocations.forEach(allocation -> coilOutbound.allocate(c,allocation));
  coilOutbound.verifyVehicleLimits(c,limits(trip,vehicle,unit,"100000","120000"));
  var review=coilOutbound.reviewWeighing(c,new CoilOutbound.Weighing(newId(),trip,newId(),vehicle,unit,
      new BigDecimal("110000"),new BigDecimal("20000"),
      List.of(new CoilOutbound.WeighedCoil(allocations.get(0).id(),new BigDecimal("53000")),
          new CoilOutbound.WeighedCoil(allocations.get(1).id(),new BigDecimal("17000")),
          new CoilOutbound.WeighedCoil(allocations.get(2).id(),new BigDecimal("20000")))));
  var shipment=new CoilOutbound.Handover(newId(),trip,review.id(),newId(),vehicle,owner,List.of(a.id(),b.id(),other.id()));
  assertThat(coilOutbound.handover(c,shipment).issuedQuantity()).isEqualByComparingTo("90000");
  coilOutbound.plan(c,new CoilOutbound.Trip(nextTrip,auth,vehicle,new BigDecimal("100000")));
  var isolationA=returnPosition(c,position.warehouseId(),"REF-W","RETURN-QUARANTINE-A");
  var isolationB=returnPosition(c,position.warehouseId(),"REF-W","RETURN-QUARANTINE-B");
  var disabled=returnPosition(c,position.warehouseId(),"REF-W","RETURN-CLOSED");
  masterData.setStatus(c,MasterData.Kind.STORAGE_LOCATION,disabled.locationId(),new MasterData.Status(false,0));
  var stopped=new InboundReceiptReferences.Position(disabled.warehouseId(),disabled.areaId(),disabled.locationId(),0,0,1);
  String outsideWarehouse=masterData.create(c,MasterData.Kind.WAREHOUSE,
      new MasterData.Input("RETURN-OTHER-W","不同仓库",Map.of(),0)).get("id");
  var outside=returnPosition(c,outsideWarehouse,"RETURN-OTHER-W","RETURN-OTHER");
  var unrelated=new InboundReceiptReferences.Position(position.warehouseId(),outside.areaId(),outside.locationId(),0,0,0);
  assertThatThrownBy(()->coilOutbound.returnCoils(c,returnTo(shipment.id(),a.id(),unrelated)))
      .isInstanceOf(MasterData.Rejection.class).hasMessage("MASTER_DATA_INPUT_INVALID");
  assertThatThrownBy(()->coilOutbound.returnCoils(c,returnTo(shipment.id(),a.id(),stopped)))
      .isInstanceOf(MasterData.Rejection.class).hasMessage("MASTER_DATA_INPUT_INVALID");
  var stalePosition=new InboundReceiptReferences.Position(isolationA.warehouseId(),isolationA.areaId(),
      isolationA.locationId(),0,0,1);
  assertThatThrownBy(()->coilOutbound.returnCoils(c,returnTo(shipment.id(),a.id(),stalePosition)))
      .hasMessage("MASTER_DATA_CHANGED");
  verifyReversalBalances(notice,auth,trip,a.id(),"90000","0","90000","0","51000");
  var returnedA=new CoilOutbound.ReturnedCoil(a.id(),isolationA.locationId(),isolationA);
  var returnedB=new CoilOutbound.ReturnedCoil(b.id(),isolationB.locationId(),isolationB);
  var batchA=new CoilOutbound.PartialReturn(newId(),shipment.id(),newId(),newId(),List.of(returnedA));
  var json=new tools.jackson.databind.ObjectMapper();
  var session=mvc.perform(post("/bff/tenants/current").with(authenticated(c.accountId().toString(),0))
      .with(csrf()).contentType("application/json")
      .content(json.writeValueAsString(Map.of("tenantId",c.tenantId().toString())))).andReturn().getResponse();
  assertThat(session.getStatus()).isEqualTo(200);
  var response=mvc.perform(post("/api/bulk-coil-returns:post-coils").cookie(session.getCookie("JSESSIONID"))
      .with(csrf()).contentType("application/json").content(json.writeValueAsString(batchA))).andReturn().getResponse();
  assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
  assertThat(json.readTree(response.getContentAsString()).path("restoredQuota").decimalValue()).isEqualByComparingTo("53000");
  assertThat(json.readTree(response.getContentAsString()).path("restoredStock").decimalValue()).isEqualByComparingTo("51000");
  bindReferenceContext(c);
  var replay=coilOutbound.returnCoils(c,batchA);
  assertThat(replay.restoredQuota()).isEqualByComparingTo("53000");
  assertThatThrownBy(()->coilOutbound.returnCoils(c,new CoilOutbound.PartialReturn(batchA.id(),shipment.id(),newId(),
      batchA.titleEvidenceId(),List.of(returnedA)))).hasMessage("OUTBOUND_REQUEST_CONFLICT");
  assertThatThrownBy(()->coilOutbound.returnCoils(c,new CoilOutbound.PartialReturn(newId(),shipment.id(),newId(),
      newId(),List.of(returnedA,returnedB)))).hasMessage("OUTBOUND_RETURN_SCOPE_EXHAUSTED");
  assertThatThrownBy(()->coilOutbound.reverseHandover(c,new CoilOutbound.ReverseHandover(
      newId(),shipment.id(),CoilOutbound.ReversalKind.MISTAKEN_HANDOVER,newId(),null)))
      .hasMessage("OUTBOUND_PARTIAL_RETURN_EXISTS");
  verifyReversalBalances(notice,auth,trip,a.id(),"37000","0","37000","0","0");
  assertThat(outboundStock.check(c,b.id())).isEqualTo(OutboundStock.Gate.DISPATCHED);
  assertReturnPositions(shipment.id(),batchA.id(),a.id(),position.locationId(),isolationA.locationId());
  assertThatThrownBy(()->coilOutbound.returnCoils(c,new CoilOutbound.PartialReturn(batchA.id(),shipment.id(),
      batchA.custodyEvidenceId(),batchA.titleEvidenceId(),
      List.of(new CoilOutbound.ReturnedCoil(a.id(),isolationB.locationId(),isolationB)))))
      .hasMessage("OUTBOUND_REQUEST_CONFLICT");
  assertThat(outboundStock.check(c,a.id())).isEqualTo(OutboundStock.Gate.PENDING_QUALITY);
  var reuseA=new CoilOutbound.Allocate(newId(),nextTrip,a.id());
  assertThatThrownBy(()->coilOutbound.allocate(c,reuseA)).hasMessage("OUTBOUND_PENDING_QUALITY");
  String priorQuality=jdbc.queryForObject("SELECT BIN_TO_UUID(evidence_id) FROM bw_inbound_quality_release WHERE receipt_id=UUID_TO_BIN(?)",
      String.class,a.id());
  assertThatThrownBy(()->inboundDisposition.releaseQuality(c,new InboundDisposition.Decision(a.id(),priorQuality)))
      .hasMessage("INBOUND_RETURN_QUALITY_REQUIRED");
  assertThatThrownBy(()->coilOutbound.releaseReturnedQuality(c,new CoilOutbound.ReturnedQuality(batchA.id(),a.id(),priorQuality)))
      .hasMessage("OUTBOUND_RETURN_QUALITY_EVIDENCE_REUSED");

  var freeze=new OutboundStock.Freeze(newId(),b.id(),newId());
  outboundStock.freeze(c,freeze);
  var batchB=new CoilOutbound.PartialReturn(newId(),shipment.id(),newId(),newId(),List.of(returnedB));
  // 在三层额度的最后一步注入故障，校验库存、权利、前两级额度与明细的共同回滚。
  jdbc.execute("ALTER TABLE bw_outbound_trip ADD CONSTRAINT bw_test_partial_return_failure CHECK(id<>X'"
      +trip.replace("-","")+"' OR issued>=37000)");
  try {
   assertThatThrownBy(()->coilOutbound.returnCoils(c,batchB)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  } finally {
   jdbc.execute("ALTER TABLE bw_outbound_trip DROP CHECK bw_test_partial_return_failure");
  }
  verifyReversalBalances(notice,auth,trip,b.id(),"37000","0","37000","0","17000");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bw_coil_partial_return WHERE id=UUID_TO_BIN(?)",
      Integer.class,batchB.id())).isZero();
  assertThat(jdbc.queryForObject("""
      SELECT COUNT(*) FROM bw_coil_handover_line WHERE handover_id=UUID_TO_BIN(?)
      AND receipt_id=UUID_TO_BIN(?) AND reversed_at IS NULL
      """,Integer.class,shipment.id(),b.id())).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT BIN_TO_UUID(current_location_id) FROM bw_stock_receipt WHERE receipt_id=UUID_TO_BIN(?)",
      String.class,b.id())).isNull();
  assertThat(coilOutbound.returnCoils(c,batchB).restoredQuota()).isEqualByComparingTo("17000");
  assertReturnPositions(shipment.id(),batchB.id(),b.id(),position.locationId(),isolationB.locationId());
  verifyReversalBalances(notice,auth,trip,b.id(),"20000","0","20000","0","0");
  assertThat(jdbc.queryForObject("SELECT pending_quantity FROM bw_stock_receipt WHERE receipt_id=UUID_TO_BIN(?)",
      BigDecimal.class,b.id())).isEqualByComparingTo("17000");
  assertThat(jdbc.queryForObject("""
      SELECT BIN_TO_UUID(receipt_id) FROM bw_coil_handover_line
      WHERE handover_id=UUID_TO_BIN(?) AND reversed_at IS NULL
      """,String.class,shipment.id())).isEqualTo(other.id());
  verifyReversalBalances(notice,auth,trip,other.id(),"20000","0","20000","0","20000");

  var qualifyB=new CoilOutbound.ReturnedQuality(batchB.id(),b.id(),newId());
  coilOutbound.releaseReturnedQuality(c,qualifyB);
  coilOutbound.releaseReturnedQuality(c,qualifyB);
  var reuseB=new CoilOutbound.Allocate(newId(),nextTrip,b.id());
  assertThatThrownBy(()->coilOutbound.allocate(c,reuseB)).hasMessage("OUTBOUND_FROZEN");
  outboundStock.lift(c,new OutboundStock.Freeze(freeze.id(),b.id(),newId()));
  assertThat(outboundStock.check(c,b.id())).isEqualTo(OutboundStock.Gate.AVAILABLE);
  coilOutbound.releaseReturnedQuality(c,new CoilOutbound.ReturnedQuality(batchA.id(),a.id(),newId()));
  assertThat(outboundStock.check(c,a.id())).isEqualTo(OutboundStock.Gate.AVAILABLE);
  assertThat(outboundStock.check(c,other.id())).isEqualTo(OutboundStock.Gate.DISPATCHED);

  var finalBatch=new CoilOutbound.PartialReturn(newId(),shipment.id(),newId(),newId(),
      List.of(new CoilOutbound.ReturnedCoil(other.id(),position.locationId())));
  assertThat(coilOutbound.returnCoils(c,finalBatch).restoredQuota()).isEqualByComparingTo("20000");
  verifyReversalBalances(notice,auth,trip,other.id(),"0","0","0","0","0");
  assertThat(jdbc.queryForObject("SELECT SUM(restored_quota) FROM bw_coil_partial_return WHERE handover_id=UUID_TO_BIN(?)",
      BigDecimal.class,shipment.id())).isEqualByComparingTo("90000");
  assertThat(jdbc.queryForObject("SELECT SUM(restored_stock) FROM bw_coil_partial_return WHERE handover_id=UUID_TO_BIN(?)",
      BigDecimal.class,shipment.id())).isEqualByComparingTo("88000");
  assertThatThrownBy(()->coilOutbound.returnCoils(c,new CoilOutbound.PartialReturn(newId(),shipment.id(),newId(),newId(),
      List.of(returnedA)))).hasMessage("OUTBOUND_HANDOVER_REVERSED");
  var plan=jdbc.queryForMap("""
      EXPLAIN SELECT restored_quota FROM bw_coil_partial_return_line
      WHERE tenant_id=UUID_TO_BIN(?) AND handover_id=UUID_TO_BIN(?) AND allocation_id=UUID_TO_BIN(?)
      """,c.tenantId().toString(),shipment.id(),allocations.get(0).id());
  assertThat(plan.get("key")).isNotNull();
  assertThat(((Number)plan.get("rows")).longValue()).isLessThanOrEqualTo(1L);
  assertThat(coilOutbound.returnCoils(c,batchA)).isEqualTo(replay);
  // 停用历史出仓库位不会让已返到隔离位的卷回退到旧位置校验。
  masterData.setStatus(c,MasterData.Kind.STORAGE_LOCATION,position.locationId(),new MasterData.Status(false,0));
  assertThat(outboundStock.check(c,a.id())).isEqualTo(OutboundStock.Gate.AVAILABLE);
  var newAllocation=new CoilOutbound.Allocate(newId(),nextTrip,a.id());
  coilOutbound.allocate(c,newAllocation);
  coilOutbound.verifyVehicleLimits(c,limits(nextTrip,vehicle,unit,"100000","120000"));
  var newReview=coilOutbound.reviewWeighing(c,weigh(newAllocation,vehicle,unit,"53000"));
  var reissue=new CoilOutbound.Handover(newId(),nextTrip,newReview.id(),newId(),vehicle,owner,List.of(a.id()));
  coilOutbound.handover(c,reissue);
  assertThat(jdbc.queryForObject("""
      SELECT BIN_TO_UUID(source_location_id) FROM bw_coil_handover_line WHERE handover_id=UUID_TO_BIN(?)
      """,String.class,reissue.id())).isEqualTo(isolationA.locationId());
  assertReturnPositions(shipment.id(),batchA.id(),a.id(),position.locationId(),isolationA.locationId());
 }

 private InboundReceiptReferences.Position returnPosition(VerifiedTenantContext c,String warehouseId,
     String warehouseCode,String code) {
  String area=masterData.create(c,MasterData.Kind.STORAGE_AREA,
      new MasterData.Input(code,"返仓隔离区",Map.of("parentCode",warehouseCode,"areaType","WORK"),0)).get("id");
  String location=masterData.create(c,MasterData.Kind.STORAGE_LOCATION,
      new MasterData.Input(code+"-L","实际返仓隔离位",Map.of("parentCode",code),0)).get("id");
  return new InboundReceiptReferences.Position(warehouseId,area,location,0,0,0);
 }

 private static CoilOutbound.PartialReturn returnTo(String handoverId,String receiptId,
     InboundReceiptReferences.Position position) {
  return new CoilOutbound.PartialReturn(newId(),handoverId,newId(),newId(),
      List.of(new CoilOutbound.ReturnedCoil(receiptId,position.locationId(),position)));
 }

 private void assertReturnPositions(String handoverId,String batchId,String receiptId,String original,String actual) {
  assertThat(jdbc.queryForObject("""
      SELECT BIN_TO_UUID(source_location_id) FROM bw_coil_handover_line
      WHERE handover_id=UUID_TO_BIN(?) AND receipt_id=UUID_TO_BIN(?)
      """,String.class,handoverId,receiptId)).isEqualTo(original);
  assertThat(jdbc.queryForObject("""
      SELECT BIN_TO_UUID(location_id) FROM bw_coil_partial_return_line
      WHERE return_id=UUID_TO_BIN(?) AND receipt_id=UUID_TO_BIN(?)
      """,String.class,batchId,receiptId)).isEqualTo(actual);
  assertThat(jdbc.queryForObject("SELECT BIN_TO_UUID(current_location_id) FROM bw_stock_receipt WHERE receipt_id=UUID_TO_BIN(?)",
      String.class,receiptId)).isEqualTo(actual);
 }

 private static String newId() {
  return UUID.randomUUID().toString();
 }

 private InboundReceiving.Receipt receiveWeighFixture(VerifiedTenantContext c,String inbound,
     InboundReceiptReferences.Position position,InboundReceiptReferences.Item item,
     String owner,String unit,String unitCode,String quantity,String serial) {
  return receiveWeighFixture(c,inbound,position,item,owner,unit,unitCode,quantity,serial,null);
 }

 private InboundReceiving.Receipt receiveWeighFixture(VerifiedTenantContext c,String inbound,
     InboundReceiptReferences.Position position,InboundReceiptReferences.Item item,
     String owner,String unit,String unitCode,String quantity,String serial,String receiptBatchNo) {
  var input=coalReceipt(c,inbound,position,item,owner,unit,quantity);
  var receipt=new InboundReceiving.Receipt(input.id(),input.custodyId(),input.measurementId(),
      input.references(),receiptBatchNo);
  inboundReceiving.receive(c,receipt);
  inboundDisposition.registerTitle(c,new InboundDisposition.TitleDecision(receipt.id(),UUID.randomUUID().toString(),
      java.time.Instant.parse("2026-11-20T09:00:00Z")));
  inboundDisposition.releaseQuality(c,new InboundDisposition.Decision(receipt.id(),UUID.randomUUID().toString()));
  coilOutbound.identify(c,new CoilOutbound.Coil(receipt.id(),serial,unitCode));
  return receipt;
 }

 private CoilOutbound.VehicleLimits limits(String tripId,String vehicle,String unit,String payload,String gross) {
  return new CoilOutbound.VehicleLimits(tripId,vehicle,unit,UUID.randomUUID().toString(),
      new BigDecimal(payload),new BigDecimal(gross));
 }

 private CoilOutbound.Weighing weigh(CoilOutbound.Allocate allocation,String vehicle,String unit,String amount) {
  var actual=new BigDecimal(amount);
  var tare=new BigDecimal("20000");
  return new CoilOutbound.Weighing(UUID.randomUUID().toString(),allocation.tripId(),UUID.randomUUID().toString(),
      vehicle,unit,actual.add(tare),tare,List.of(new CoilOutbound.WeighedCoil(allocation.id(),actual)));
 }

 private void assertBlockedStock(VerifiedTenantContext context,String id,OutboundStock.Gate expected) {
  assertThat(outboundStock.check(context,id)).isEqualTo(expected);
  assertThatThrownBy(()->outboundStock.reserve(context,new OutboundStock.Hold(UUID.randomUUID().toString(),id)))
      .hasMessage("OUTBOUND_"+expected.name());
 }

 private InboundReceiving.Receipt coalReceipt(VerifiedTenantContext context,String noticeId,
     InboundReceiptReferences.Position position,InboundReceiptReferences.Item item,String owner,String unit,String quantity) {
  String custody=UUID.randomUUID().toString();
  inboundReceiving.takeCustody(context,new InboundReceiving.Custody(custody,noticeId,position,UUID.randomUUID().toString()));
  return new InboundReceiving.Receipt(UUID.randomUUID().toString(),custody,UUID.randomUUID().toString(),
      new InboundReceiptReferences.Request(position,item,owner,unit,quantity,new InboundReceiptReferences.SameUnit()));
 }

 private InboundReceiptReferences.Position warehousePosition(VerifiedTenantContext context) {
  String warehouse=masterData.create(context,MasterData.Kind.WAREHOUSE,
      new MasterData.Input("REF-W","进仓仓库",Map.of(),0)).get("id");
  String area=masterData.create(context,MasterData.Kind.STORAGE_AREA,
      new MasterData.Input("REF-A","进仓库区",Map.of("parentCode","REF-W"),0)).get("id");
  String location=masterData.create(context,MasterData.Kind.STORAGE_LOCATION,
      new MasterData.Input("REF-L","进仓位置",Map.of("parentCode","REF-A"),0)).get("id");
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
