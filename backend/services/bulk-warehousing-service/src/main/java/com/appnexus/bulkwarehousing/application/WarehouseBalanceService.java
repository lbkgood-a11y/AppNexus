package com.appnexus.bulkwarehousing.application;

import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import java.util.UUID;

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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/** 按统一权限读取入仓通知和库存总览，并复核主数据引用可见性。 */
public final class WarehouseBalanceService implements WarehouseBalances,DataAccessExecutor {
  private final WarehouseBalanceStore balances;
  private final InventoryOverviewReferences overviewReferences;
  private final AccessPlanning access;
  private final ScopedWriteGate operations;
  private final com.appnexus.platform.authorization.FieldProtection fields;

  /** @param balances 定向余额投影
   * @param overviewReferences 库存展示的授权引用 @param access 统一权限计划 @param operations 查询事务内的受控操作许可 */
  public WarehouseBalanceService(WarehouseBalanceStore balances,
      InventoryOverviewReferences overviewReferences,
      AccessPlanning access,ScopedWriteGate operations,com.appnexus.platform.authorization.FieldProtection fields) {
    this.balances=balances;
    this.overviewReferences=overviewReferences;
    this.access=access;
    this.operations=operations;
    this.fields=fields;
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
  @Override public NoticePage inboundNotices(VerifiedTenantContext c,NoticeFilter filter,int page,int pageSize) {
    var plan=requireAccess(c,READ_PERMISSION,RESOURCE);
    if (page<1) throw invalid();
    validatePage(null,pageSize);
    if(filter.warehouseId()!=null)uuid(filter.warehouseId());
    if(filter.ownerId()!=null)uuid(filter.ownerId());
    validateNoticeFilter(filter.documentNo());
    validateNoticeFilter(filter.appointmentNo());
    return authorized(plan,()->readNoticePage(c,filter,(page-1L)*pageSize,pageSize));
  }

  /** 按可见记录计数和切页，仅在内存保留请求页与每批的授权数据。 */
  private NoticePage readNoticePage(VerifiedTenantContext c,NoticeFilter filter,long offset,int pageSize) {
    var rows=new java.util.ArrayList<DocumentHeader>(pageSize);
    long total=0;
    String cursor=null;
    final int scanSize=30;
    // 在同一读取事务中扫描候选；未授权的通知既不占页内位置，也不计入总数。
    while(true){
      var candidates=balances.inboundNotices(c,filter,cursor,scanSize);
      if(candidates.isEmpty())break;
      var products=balances.inboundNoticeProducts(c,candidates.stream().map(DocumentHeader::noticeId).toList());
      var required=new HashMap<String,List<ReferenceNames.Key>>();
      for(var row:candidates){
        var ids=products.get(row.noticeId());
        if(ids==null || ids.isEmpty())continue;
        var keys=new java.util.ArrayList<ReferenceNames.Key>(ids.size()+2);
        keys.add(new ReferenceNames.Key(ACTIVE_WAREHOUSE,row.warehouseId()));
        keys.add(new ReferenceNames.Key(ACTIVE_PARTNER,row.ownerId()));
        for(var id:ids)keys.add(new ReferenceNames.Key(PRODUCT,id));
        required.put(row.noticeId(),keys);
      }
      var visible=ReferenceNames.resolveKeys(overviewReferences,c,required.values().stream().flatMap(List::stream).distinct().toList());
      for(var row:candidates){
        var keys=required.get(row.noticeId());
        if(keys==null || keys.stream().anyMatch(key->visible.get(key)==null))continue;
        if(total>=offset && rows.size()<pageSize)rows.add(row);
        total++;
      }
      if(candidates.size()<scanSize)break;
      cursor=candidates.getLast().noticeId();
    }
    return new NoticePage(List.copyOf(rows),total);
  }

  /** 列表按业务编号精确匹配，拒绝首尾空白、控制字符和超长参数。 */
  private static void validateNoticeFilter(String value) {
    if(value!=null&&(value.isBlank()||!value.equals(value.strip())||value.codePointCount(0,value.length())>64
        ||value.codePoints().anyMatch(Character::isISOControl)))throw invalid();
  }

  /** {@inheritDoc} */
  @Override public DocumentDetail document(VerifiedTenantContext c,DocumentKind kind,String documentNo) {
    var plan=requireAccess(c,READ_PERMISSION,RESOURCE);
    if (kind!=DocumentKind.INBOUND_NOTICE) throw invalid();
    if (documentNo==null || documentNo.isBlank() || !documentNo.equals(documentNo.strip())
        || documentNo.codePointCount(0,documentNo.length())>64 || documentNo.codePoints().anyMatch(Character::isISOControl)) throw invalid();
    return authorized(plan,()->{
      var header=balances.document(c,documentNo);
      if (header==null) throw denied();
      var lines=balances.inboundLines(c,header.noticeId());
      if (lines.isEmpty()) throw denied();
      var required=new java.util.ArrayList<ReferenceNames.Key>();
      required.add(new ReferenceNames.Key(ACTIVE_WAREHOUSE,header.warehouseId()));
      required.add(new ReferenceNames.Key(ACTIVE_PARTNER,header.ownerId()));
      for(var line:lines)required.add(new ReferenceNames.Key(PRODUCT,line.productId()));
      requireReferences(c,required);
      return new DocumentDetail(protectDriver(c,header),lines,
          fields.restrictions(Subject.tenant(c.accountId(),c.tenantId(),c.memberId()),"warehousing.inbound"));
    });
  }

  /** 任一用于业务准入的引用不可见时，拒绝整张入仓通知。 */
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

  /** 司机原文只经过平台统一字段投影后进入详情，业务层不实现脱敏算法。 */
  private DocumentHeader protectDriver(VerifiedTenantContext context,DocumentHeader header) {
    var source=header.driver();
    if(source==null)return header;
    var values=new java.util.HashMap<String,Object>();
    values.put("driverName",source.driverName());
    values.put("driverPhone",source.driverPhone());
    values.put("driverIdNumber",source.driverIdNumber());
    var projected=fields.project(Subject.tenant(context.accountId(),context.tenantId(),context.memberId()),"warehousing.inbound",values);
    return header.withDriver(new com.appnexus.bulkwarehousing.api.InboundReceiving.Driver(
        (String)projected.get("driverName"),(String)projected.get("driverPhone"),(String)projected.get("driverIdNumber")));
  }

  private static void validatePage(String afterId,int limit) {
    if (limit<1 || limit>100) throw invalid();
    if (afterId!=null) uuid(afterId);
  }

  private AccessPlan requireAccess(VerifiedTenantContext c,String permission,String resource) {
    if(c==null)throw denied();
    var plan=access.plan(Subject.tenant(c.accountId(),c.tenantId(),c.memberId()),permission,resource);
    if(!plan.tenantWide())throw denied();
    return plan;
  }

  /** 固定租户全部查询使用同一真实操作的事务凭据，仓库及其他引用继续由原用例复核。 */
  private <T>T authorized(AccessPlan plan,java.util.function.Supplier<T> query){
    return balances.transaction(()->{
      operations.authorizeOperation(plan);
      return query.get();
    });
  }

  private static void uuid(String value) {
    try {
      if (value==null || !UUID.fromString(value).toString().equals(value)) throw invalid();
    } catch (IllegalArgumentException error) {
      throw invalid();
    }
  }

  private static Rejection invalid() { return new Rejection(400,"OUTBOUND_INPUT_INVALID"); }
  private static Rejection denied() { return new Rejection(403,"OUTBOUND_ACCESS_DENIED"); }
}
