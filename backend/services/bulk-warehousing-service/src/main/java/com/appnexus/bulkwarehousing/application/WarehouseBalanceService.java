package com.appnexus.bulkwarehousing.application;

import static com.appnexus.bulkwarehousing.application.OutboundStockService.*;

import com.appnexus.bulkwarehousing.api.CoilOperations;
import com.appnexus.bulkwarehousing.api.InboundReceiptReferences.Position;
import com.appnexus.bulkwarehousing.api.WarehouseBalances;
import com.appnexus.contracts.businessobject.BusinessObjects.DataAccessExecutor;
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

/** 按权属库存计算可提量，授权余额从原通知及额度独立读取。 */
public final class WarehouseBalanceService implements WarehouseBalances,DataAccessExecutor {
  private final WarehouseBalanceStore balances;
  private final OutboundStockStore stock;
  private final InboundReferenceSource masterData;
  private final AccessPlanning access;
  private final ScopedWriteGate operations;

  /** @param balances 定向余额投影 @param stock 原库存事务和并发保护
   * @param masterData 现有主数据权限 @param access 统一权限计划 @param operations 查询事务内的受控操作许可 */
  public WarehouseBalanceService(WarehouseBalanceStore balances,OutboundStockStore stock,
      InboundReferenceSource masterData,AccessPlanning access,ScopedWriteGate operations) {
    this.balances=balances;
    this.stock=stock;
    this.masterData=masterData;
    this.access=access;
    this.operations=operations;
  }

  /** {@inheritDoc} */
  @Override public Set<String> enforcedResources() { return Set.of(RESOURCE); }

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
    masterData.requireWarehouse(c,stock.warehouseId());
    requireTracePartner(c,stock.ownerId());
    masterData.requireProduct(c,stock.productId());
    var docs=balances.traceDocuments(c,receiptId);
    // 历史上曾涉及的仓库和货主均需当前可读，防止过户后通过身份反查其他主体单据。
    var warehouses=new java.util.HashSet<String>();
    var owners=new java.util.HashSet<String>();
    warehouses.add(stock.warehouseId());
    owners.add(stock.ownerId());
    for (var event:docs) {
      var doc = event.document();
      if (event.previousWarehouseId() != null && warehouses.add(event.previousWarehouseId())) {
        masterData.requireWarehouse(c,event.previousWarehouseId());
      }
      if (warehouses.add(doc.warehouseId())) masterData.requireWarehouse(c,doc.warehouseId());
      if (owners.add(doc.ownerId())) requireTracePartner(c,doc.ownerId());
      if (event.responsiblePartnerId() != null && owners.add(event.responsiblePartnerId())) {
        requireTracePartner(c,event.responsiblePartnerId());
      }
      if (doc.otherOwnerId()!=null && owners.add(doc.otherOwnerId())) requireTracePartner(c,doc.otherOwnerId());
    }
    return new CoilTrace(stock,docs.stream().map(WarehouseBalanceStore.TraceEvent::document).toList());
  }

  /** 历史主体不可见时拒绝整条追溯，响应不区分主体不存在、停用或不可读。 */
  private void requireTracePartner(VerifiedTenantContext context,String partnerId) {
    try {
      masterData.owner(context,partnerId);
    } catch (IllegalArgumentException unavailable) {
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
