package com.appnexus.bulkwarehousing.application;

import static com.appnexus.bulkwarehousing.application.OutboundStockService.*;

import com.appnexus.bulkwarehousing.api.CoilOperations;
import com.appnexus.bulkwarehousing.api.InboundReceiptReferences.Position;
import com.appnexus.bulkwarehousing.api.WarehouseBalances;
import com.appnexus.contracts.businessobject.BusinessObjects.DataAccessExecutor;
import com.appnexus.contracts.operation.ReferenceNames;
import static com.appnexus.bulkwarehousing.application.InventoryOverviewReferences.*;
import com.appnexus.bulkwarehousing.application.WarehouseBalanceStore.OverviewSlice;
import com.appnexus.platform.authorization.AccessAuthorization.Subject;
import com.appnexus.platform.authorization.AccessPlanning;
import com.appnexus.platform.authorization.AccessPlan;
import com.appnexus.platform.authorization.ScopedWriteGate;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/** 按权属库存计算可提量，独立读取授权余额，并按原权限查询仓储单据。 */
public final class WarehouseBalanceService implements WarehouseBalances,DataAccessExecutor {
  private final WarehouseBalanceStore balances;
  private final OutboundStockStore stock;
  private final InboundReferenceSource masterData;
  private final InventoryOverviewReferences overviewReferences;
  private final AccessPlanning access;
  private final ScopedWriteGate operations;

  /** @param balances 定向余额投影 @param stock 原库存事务和并发保护
   * @param masterData 现有主数据权限 @param overviewReferences 库存展示的授权引用 @param access 统一权限计划 @param operations 查询事务内的受控操作许可 */
  public WarehouseBalanceService(WarehouseBalanceStore balances,OutboundStockStore stock,
      InboundReferenceSource masterData,InventoryOverviewReferences overviewReferences,
      AccessPlanning access,ScopedWriteGate operations) {
    this.balances=balances;
    this.stock=stock;
    this.masterData=masterData;
    this.overviewReferences=overviewReferences;
    this.access=access;
    this.operations=operations;
  }

  /** {@inheritDoc} */
  @Override public Set<String> enforcedResources() { return Set.of(RESOURCE); }

  /** {@inheritDoc} */
  @Override public OverviewPage overview(VerifiedTenantContext c,OverviewFilter filter,String afterId,int limit) {
    var plan=requireAccess(c,OVERVIEW_PERMISSION,RESOURCE);
    validatePage(afterId,limit);
    if (filter.warehouseId()!=null) uuid(filter.warehouseId());
    if (filter.ownerId()!=null) uuid(filter.ownerId());
    if (filter.productId()!=null) uuid(filter.productId());
    var rows=new java.util.ArrayList<OverviewRow>(limit);
    var names=new HashMap<ReferenceNames.Key,String>();
    String cursor=afterId;
    // 每批只加载固定条数；只从可见记录发布游标，避免通过翻页泄露不可见实收。
    int scanSize=100;
    while (true) {
      String start=cursor;
      var slices=authorized(plan,()->balances.overview(c,filter,start,scanSize));
      ReferenceNames.resolve(overviewReferences,c,slices,OVERVIEW_NAMES,names);
      for (var slice:slices) {
        String warehouse=overviewName(names,WAREHOUSE,slice.warehouseId());
        String owner=overviewName(names,PARTNER,slice.ownerId());
        String product=overviewName(names,PRODUCT,slice.productId());
        if(warehouse==null||owner==null||product==null)continue;
        if(rows.size()==limit)return new OverviewPage(List.copyOf(rows),rows.getLast().receiptId());
        String specification=switch(slice.identityKind()){
          case "SPECIFICATION" -> overviewName(names,SPECIFICATION,slice.identityId());
          case "SKU" -> overviewName(names,SKU_SPECIFICATION,slice.identityId());
          default -> null;
        };
        String skuCode="SKU".equals(slice.identityKind())?overviewName(names,SKU,slice.identityId()):null;
        rows.add(new OverviewRow(slice.receiptId(),slice.serial(),warehouse,owner,product,
            specification,skuCode,slice.receiptBatchNo(),overviewName(names,PRODUCT_UNIT,slice.unitId()),
            slice.onHand(),slice.pending(),slice.audit(),slice.warehouseId(),slice.ownerId(),slice.productId(),
            slice.identityId(),slice.unitId()));
      }
      if (slices.size()<scanSize) return new OverviewPage(List.copyOf(rows),null);
      cursor=slices.getLast().receiptId();
    }
  }

  /** 库存响应的名称契约；只有规格和 SKU 对应身份才声明子结构引用。 */
  private static final List<ReferenceNames.Field<OverviewSlice>> OVERVIEW_NAMES=List.of(
      new ReferenceNames.Field<>(WAREHOUSE,OverviewSlice::warehouseId),
      new ReferenceNames.Field<>(PARTNER,OverviewSlice::ownerId),
      new ReferenceNames.Field<>(PRODUCT,OverviewSlice::productId),
      new ReferenceNames.Field<>(PRODUCT_UNIT,OverviewSlice::unitId),
      new ReferenceNames.Field<>(SPECIFICATION,row->"SPECIFICATION".equals(row.identityKind())?row.identityId():null),
      new ReferenceNames.Field<>(SKU,row->"SKU".equals(row.identityKind())?row.identityId():null),
      new ReferenceNames.Field<>(SKU_SPECIFICATION,row->"SKU".equals(row.identityKind())?row.identityId():null));

  private static String overviewName(java.util.Map<ReferenceNames.Key,String> names,String type,String value){
    return names.get(new ReferenceNames.Key(type,value));
  }

  /** {@inheritDoc} */
  @Override public List<Physical> physical(VerifiedTenantContext c,Scope scope) {
    var plan=require(c,READ_PERMISSION,RESOURCE,scope);
    var groups=new LinkedHashMap<IdentityUnit,Physical>();
    var positions=new HashMap<Position,Boolean>();
    for (var row:authorized(plan,()->balances.physical(c,scope))) {
      var key=new IdentityUnit(row.identityId(),row.unitId());
      var existing=groups.getOrDefault(key,new Physical(row.identityId(),row.unitId(),BigDecimal.ZERO,BigDecimal.ZERO));
      var eligible=row.eligible().signum()>0 && positions.computeIfAbsent(row.position(),pos -> masterData.usablePosition(c,pos))
          ? row.eligible() : BigDecimal.ZERO;
      groups.put(key,new Physical(key.identity(),key.unit(),existing.onHandTitle().add(row.title()).stripTrailingZeros(),
          existing.pickable().add(eligible).stripTrailingZeros()));
    }
    return List.copyOf(groups.values());
  }

  /** {@inheritDoc} */
  @Override public CoilPage coils(VerifiedTenantContext c,Scope scope,String afterId,int limit) {
    var plan=require(c,READ_PERMISSION,RESOURCE,scope);
    validatePage(afterId,limit);
    var rows=authorized(plan,()->balances.coils(c,scope,afterId,limit+1));
    var positions=new HashMap<Position,Boolean>();
    var page=rows.stream().limit(limit).map(row -> {
      boolean validLocation=positions.computeIfAbsent(row.position(),pos -> masterData.usablePosition(c,pos));
      boolean transferable=validLocation && ("AVAILABLE".equals(row.status()) || "PENDING_QUALITY".equals(row.status()));
      return new Coil(row.receiptId(),row.serial(),row.identityId(),row.unitId(),row.quantity(),row.titleRevision(),
          validLocation ? row.status() : "LOCATION_UNAVAILABLE",transferable);
    }).toList();
    return new CoilPage(page,rows.size()>limit ? page.getLast().receiptId() : null);
  }

  /** {@inheritDoc} */
  @Override public AuthorizationPage authorizations(VerifiedTenantContext c,Scope scope,String afterId,int limit) {
    var plan=require(c,CoilOperations.READ_AUTHORIZATION_BALANCES.permission(),CoilOperations.RESOURCE,scope);
    validatePage(afterId,limit);
    var rows=authorized(plan,()->balances.authorizations(c,scope,afterId,limit+1));
    if (rows.size()<=limit) return new AuthorizationPage(rows,null);
    var page=List.copyOf(rows.subList(0,limit));
    return new AuthorizationPage(page,page.getLast().authorizationId());
  }

  /** {@inheritDoc} */
  @Override public AuthorizationBalance authorization(VerifiedTenantContext c,String id) {
    var permission=CoilOperations.READ_AUTHORIZATION_BALANCES.permission();
    var plan=requireAccess(c,permission,CoilOperations.RESOURCE);
    uuid(id);
    return authorized(plan,()->{
      var document=balances.authorization(c,id);
      if (document==null) throw denied();
      require(c,permission,CoilOperations.RESOURCE,document.scope());
      return document.balance();
    });
  }

  /** {@inheritDoc} */
  @Override public DocumentDetail document(VerifiedTenantContext c,DocumentKind kind,String documentNo) {
    var plan=requireAccess(c,READ_PERMISSION,RESOURCE);
    if (kind==null) throw invalid();
    if (kind==DocumentKind.INBOUND_NOTICE) {
      if (documentNo==null || documentNo.isBlank() || !documentNo.equals(documentNo.strip())
          || documentNo.codePointCount(0,documentNo.length())>64 || documentNo.codePoints().anyMatch(Character::isISOControl)) throw invalid();
    } else uuid(documentNo);
    return authorized(plan,()->{
      var header=balances.document(c,kind,documentNo);
      if (header==null) throw denied();
      String source=kind==DocumentKind.INBOUND_NOTICE ? header.noticeId() : documentNo;
      List<NoticeLineView> lines=kind==DocumentKind.INBOUND_NOTICE ? balances.inboundLines(c,source) : List.of();
      if(kind==DocumentKind.INBOUND_NOTICE&&lines.isEmpty())throw denied();
      var required=new java.util.ArrayList<ReferenceNames.Key>();
      required.add(new ReferenceNames.Key(ACTIVE_WAREHOUSE,header.warehouseId()));
      required.add(new ReferenceNames.Key(ACTIVE_PARTNER,header.ownerId()));
      if(header.otherOwnerId()!=null)required.add(new ReferenceNames.Key(ACTIVE_PARTNER,header.otherOwnerId()));
      if(lines.isEmpty())required.add(new ReferenceNames.Key(PRODUCT,header.productId()));
      else for(var line:lines)required.add(new ReferenceNames.Key(PRODUCT,line.productId()));
      requireReferences(c,required);
      var related=balances.documentRelations(c,kind,source);
      var custodies=kind==DocumentKind.INBOUND_NOTICE ? balances.inboundCustodies(c,source) : List.<NoticeCustodyView>of();
      return new DocumentDetail(header,related.stream().limit(100).toList(),
          related.size()>100 || custodies.size()>100,lines,custodies.stream().limit(100).toList());
    });
  }

  /** {@inheritDoc} */
  @Override public BatchDocument inboundEntry(VerifiedTenantContext context,String id) {
    return readBatch(context,id,false);
  }

  /** {@inheritDoc} */
  @Override public BatchDocument receiptDocument(VerifiedTenantContext context,String id) {
    return readBatch(context,id,true);
  }

  /** 遵守原单据读取门禁与主数据引用校验；跨租户与不存在同一结果。 */
  private BatchDocument readBatch(VerifiedTenantContext context,String id,boolean receipt) {
    var plan=requireAccess(context,READ_PERMISSION,RESOURCE);
    uuid(id);
    return authorized(plan,()->{
      var batch=balances.inboundBatch(context,id,receipt);
      if (batch==null) throw new com.appnexus.contracts.businessobject.BusinessObjects.Rejection(404,"INBOUND_DOCUMENT_NOT_FOUND");
      var required=new java.util.ArrayList<ReferenceNames.Key>();
      required.add(new ReferenceNames.Key(ACTIVE_WAREHOUSE,batch.warehouseId()));
      required.add(new ReferenceNames.Key(ACTIVE_PARTNER,batch.ownerId()));
      for(var line:batch.lines())required.add(new ReferenceNames.Key(PRODUCT,line.productId()));
      requireReferences(context,required);
      return batch;
    });
  }

  /** {@inheritDoc} */
  @Override public CoilTrace trace(VerifiedTenantContext c,String receiptId) {
    var plan=requireAccess(c,READ_PERMISSION,RESOURCE);
    uuid(receiptId);
    return authorized(plan,()->traceLocked(c,receiptId));
  }

  private CoilTrace traceLocked(VerifiedTenantContext c,String receiptId) {
    // 相关业务提交均在同一库存锁保护；当前账面与单据不能跨越另一次过账读取。
    if (stock.lock(c,receiptId)==null) throw denied();
    var stock=balances.traceStock(c,receiptId);
    if (stock==null) throw denied();
    var docs=balances.traceDocuments(c,receiptId);
    // 原业务要求追溯涉及的全部仓库和货主仍有效且可见。
    var required=new java.util.ArrayList<ReferenceNames.Key>();
    required.add(new ReferenceNames.Key(ACTIVE_WAREHOUSE,stock.warehouseId()));
    required.add(new ReferenceNames.Key(ACTIVE_PARTNER,stock.ownerId()));
    required.add(new ReferenceNames.Key(PRODUCT,stock.productId()));
    for(var event:docs){
      var doc=event.document();
      required.add(new ReferenceNames.Key(ACTIVE_WAREHOUSE,doc.warehouseId()));
      required.add(new ReferenceNames.Key(ACTIVE_PARTNER,doc.ownerId()));
      if(event.previousWarehouseId()!=null)required.add(new ReferenceNames.Key(ACTIVE_WAREHOUSE,event.previousWarehouseId()));
      if(event.responsiblePartnerId()!=null)required.add(new ReferenceNames.Key(ACTIVE_PARTNER,event.responsiblePartnerId()));
      if(doc.otherOwnerId()!=null)required.add(new ReferenceNames.Key(ACTIVE_PARTNER,doc.otherOwnerId()));
    }
    requireReferences(c,required);
    return new CoilTrace(stock,docs.stream().map(WarehouseBalanceStore.TraceEvent::document).toList());
  }

  /** 任一用于业务准入的引用不可见时，拒绝整条单据或追溯。 */
  private void requireReferences(VerifiedTenantContext context,List<ReferenceNames.Key> required){
    if(required.stream().anyMatch(key->key.value()==null))throw denied();
    var resolved=ReferenceNames.resolveKeys(overviewReferences,context,required);
    for(var key:required){
      if(resolved.get(key)!=null)continue;
      if(ACTIVE_WAREHOUSE.equals(key.type())){
        throw new com.appnexus.contracts.businessobject.BusinessObjects.Rejection(403,"INBOUND_ACCESS_DENIED");
      }
      throw denied();
    }
  }

  private static void validatePage(String afterId,int limit) {
    if (limit<1 || limit>100) throw invalid();
    if (afterId!=null) uuid(afterId);
  }

  private AccessPlan require(VerifiedTenantContext c,String permission,String resource,Scope scope) {
    var plan=requireAccess(c,permission,resource);
    if (scope==null) throw invalid();
    uuid(scope.ownerId());
    uuid(scope.warehouseId());
    uuid(scope.productId());
    if (scope.identityId()!=null) uuid(scope.identityId());
    masterData.requireWarehouse(c,scope.warehouseId());
    masterData.owner(c,scope.ownerId());
    masterData.requireProduct(c,scope.productId());
    return plan;
  }

  private AccessPlan requireAccess(VerifiedTenantContext c,String permission,String resource) {
    if(c==null)throw denied();
    var plan=access.plan(Subject.tenant(c.accountId(),c.tenantId(),c.memberId()),permission,resource);
    if(!plan.tenantWide())throw denied();
    return plan;
  }

  /** 固定租户全部查询使用同一真实操作的事务凭据，仓库及其他引用继续由原用例复核。 */
  private <T>T authorized(AccessPlan plan,java.util.function.Supplier<T> query){
    return stock.transaction(()->{
      operations.authorizeOperation(plan);
      return query.get();
    });
  }

  private record IdentityUnit(String identity,String unit) {}
}
