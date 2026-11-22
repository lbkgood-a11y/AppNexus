package com.appnexus.bulkwarehousing.application;

import com.appnexus.bulkwarehousing.domain.title.TitleTransferRules;
import com.appnexus.bulkwarehousing.domain.stock.StockAvailability;
import com.appnexus.bulkwarehousing.api.OutboundOperations;
import com.appnexus.bulkwarehousing.api.OutboundStock;
import com.appnexus.contracts.businessobject.BusinessObjects.DataAccessExecutor;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.authorization.AccessAuthorization.Subject;
import com.appnexus.platform.authorization.AccessPlanning;
import com.appnexus.platform.authorization.AccessPlan;
import com.appnexus.platform.authorization.ScopedWriteGate;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 统一库存门禁。配货与冻结、解除、释放使用相同库存行锁，检查结果不能作为放行凭证。 */
public final class OutboundStockService implements OutboundStock, DataAccessExecutor {
  private final OutboundStockStore store;
  private final InboundReferenceSource masterData;
  private final AccessPlanning access;
  private final ScopedWriteGate writes;

  /** @param store 同库受控存储 @param masterData 原主数据授权入口 @param access 统一权限计划 */
  public OutboundStockService(OutboundStockStore store, InboundReferenceSource masterData, AccessPlanning access, ScopedWriteGate writes) {
    this.store=store;
    this.masterData=masterData;
    this.access=access;
    this.writes=writes;
  }

  /** {@inheritDoc} */
  @Override public Set<String> enforcedResources() {
    return Set.of(OutboundOperations.CHECK.resource(),OutboundOperations.FREEZE.resource());
  }

  /** {@inheritDoc} */
  @Override public Gate check(VerifiedTenantContext context,String receiptId) {
    var plan=require(context,OutboundOperations.CHECK);
    return store.transaction(() -> {
      writes.authorizeOperation(plan);
      return gate(authorizedStock(context,receiptId));
    });
  }

  /** {@inheritDoc} */
  @Override public Reservation reserve(VerifiedTenantContext context,Hold hold) {
    var plan=require(context,OutboundOperations.RESERVE);
    validate(hold);
    return store.transaction(() -> {
      writes.authorizeOperation(plan);
      var stock=authorizedStock(context,hold.receiptId());
      if (!StockAvailability.supportsStandaloneReservation(stock.coil())) {
        throw new Rejection(409, "OUTBOUND_COIL_QUOTA_REQUIRED");
      }
      var original=store.occupation(context,hold.id());
      var change=StockAvailability.reserve(original==null ? null : original.receiptId(),
          hold.receiptId(),original!=null && original.released());
      if (change==StockAvailability.OccupationChange.CONFLICT) throw conflict();
      if (change==StockAvailability.OccupationChange.APPLY) {
        requireAvailable(stock);
        store.reserve(context,hold.id(),hold.receiptId());
      }
      return reservation(hold.id(),stock);
    });
  }

  /** {@inheritDoc} */
  @Override public void release(VerifiedTenantContext context,Hold hold) {
    var plan=require(context,OutboundOperations.RELEASE);
    validate(hold);
    store.transaction(() -> {
      writes.authorizeOperation(plan);
      authorizedStock(context,hold.receiptId());
      var original=store.occupation(context,hold.id());
      switch (StockAvailability.release(original==null ? null : original.receiptId(),hold.receiptId(),
          original!=null && original.quotaManaged(),original!=null && original.released())) {
        case CONFLICT -> throw conflict();
        case QUOTA_REQUIRED -> throw new Rejection(409,"OUTBOUND_COIL_QUOTA_REQUIRED");
        case APPLY -> store.release(context,hold.id());
        case REPLAY -> { }
      }
      return null;
    });
  }

  /** {@inheritDoc} */
  @Override public List<RestrictionEvidence> restrictionHistory(VerifiedTenantContext context,String receiptId) {
    var plan=require(context,OutboundOperations.READ_RESTRICTIONS);
    return store.transaction(() -> {
      writes.authorizeOperation(plan);
      authorizedStock(context,receiptId);
      return store.restrictionHistory(context,receiptId);
    });
  }

  /** {@inheritDoc} */
  @Override public RestrictionEvidence restrictionDocument(VerifiedTenantContext context,String id) {
    var plan=require(context,OutboundOperations.READ_RESTRICTIONS);
    uuid(id);
    return store.transaction(()->{
      writes.authorizeOperation(plan);
      var record=store.restriction(context,id);
      if (record==null) throw denied();
      authorizedStock(context,record.receiptId());
      return store.restrictionHistory(context,record.receiptId()).stream()
          .filter(row->id.equals(row.id())).findFirst().orElseThrow(OutboundStockService::denied);
    });
  }

  /** {@inheritDoc} */
  @Override public void freeze(VerifiedTenantContext context,Freeze freeze) {
    var plan=require(context,OutboundOperations.FREEZE);
    validate(freeze);
    store.transaction(() -> {
      // 已发批也可登记待返仓或待冲销的监管限制；冻结从不改变在库量。
      writes.authorizeOperation(plan);
      authorizedStock(context,freeze.receiptId());
      var original=store.restriction(context,freeze.id());
      var decision=TitleTransferRules.freeze(original!=null && original.receiptId().equals(freeze.receiptId()),
          original==null ? null : original.evidenceId(),freeze.evidenceId(),
          original!=null && original.releaseEvidence()!=null);
      switch (decision) {
        case APPLY -> {
          writes.authorize(plan,UUID.fromString(freeze.id()),UUID.fromString(freeze.receiptId()));
          store.freeze(context,freeze);
        }
        case CONFLICT -> throw conflict();
        case REPLAY -> { }
      }
      return null;
    });
  }

  /** {@inheritDoc} */
  @Override public void lift(VerifiedTenantContext context,Freeze freeze) {
    var plan=require(context,OutboundOperations.LIFT);
    validate(freeze);
    store.transaction(() -> {
      writes.authorizeOperation(plan);
      authorizedStock(context,freeze.receiptId());
      var original=store.restriction(context,freeze.id());
      var decision=TitleTransferRules.lift(original!=null && original.receiptId().equals(freeze.receiptId()),
          original==null ? null : original.releaseEvidence(),freeze.evidenceId());
      switch (decision) {
        case APPLY -> {
          writes.authorize(plan,UUID.fromString(freeze.id()),UUID.fromString(freeze.receiptId()));
          store.lift(context,freeze);
        }
        case CONFLICT -> throw conflict();
        case REPLAY -> { }
      }
      return null;
    });
  }

  // 读取库存范围后仍复核主数据的仓库、库位和货主权限，禁止从编号反推出越权记录。
  OutboundStockStore.Facts authorizedStock(VerifiedTenantContext context,String receiptId) {
    uuid(receiptId);
    var stock=store.lock(context,receiptId);
    return authorizeReferences(context,stock);
  }

  OutboundStockStore.Facts authorizedStock(VerifiedTenantContext context,String receiptId,String warehouseId) {
    uuid(receiptId);
    return authorizeReferences(context,store.lockInWarehouse(context,receiptId,warehouseId));
  }

  private OutboundStockStore.Facts authorizeReferences(VerifiedTenantContext context,OutboundStockStore.Facts stock) {
    if (stock==null) throw denied();
    masterData.position(context,stock.position());
    masterData.owner(context,stock.ownerId());
    return stock;
  }

  static void requireAvailable(OutboundStockStore.Facts stock) {
    var result=gate(stock);
    if (result!=Gate.AVAILABLE) throw new Rejection(409,"OUTBOUND_"+result.name());
  }

  private static Gate gate(OutboundStockStore.Facts stock) {
    return switch (StockAvailability.evaluate(stock.dispatched(), stock.frozen(),
        stock.qualityReady(), stock.titleReady(), stock.occupied())) {
      case DISPATCHED -> Gate.DISPATCHED;
      case FROZEN -> Gate.FROZEN;
      case PENDING_QUALITY -> Gate.PENDING_QUALITY;
      case TITLE_UNREGISTERED -> Gate.TITLE_UNREGISTERED;
      case OCCUPIED -> Gate.OCCUPIED;
      case AVAILABLE -> Gate.AVAILABLE;
    };
  }

  private static Reservation reservation(String id,OutboundStockStore.Facts stock) {
    return new Reservation(id,stock.receiptId(),stock.quantity().stripTrailingZeros(),stock.unitId());
  }

  private AccessPlan require(VerifiedTenantContext context,OutboundOperations operation) {
    if (context==null) throw denied();
    var plan=access.plan(Subject.tenant(context.accountId(),context.tenantId(),context.memberId()),
        operation.permission(),operation.resource());
    if (!plan.tenantWide()) throw denied();
    return plan;
  }

  private static void validate(Hold hold) {
    if (hold==null) throw invalid();
    uuid(hold.id());
    uuid(hold.receiptId());
  }

  private static void validate(Freeze freeze) {
    if (freeze==null) throw invalid();
    uuid(freeze.id());
    uuid(freeze.receiptId());
    uuid(freeze.evidenceId());
  }

  static void uuid(String value) {
    if (value==null) throw invalid();
    try {
      if (!UUID.fromString(value).toString().equals(value)) throw invalid();
    } catch (IllegalArgumentException error) {
      throw invalid();
    }
  }

  static Rejection invalid() { return new Rejection(400,"OUTBOUND_INPUT_INVALID"); }
  static Rejection denied() { return new Rejection(403,"OUTBOUND_ACCESS_DENIED"); }
  static Rejection conflict() { return new Rejection(409,"OUTBOUND_REQUEST_CONFLICT"); }
}
