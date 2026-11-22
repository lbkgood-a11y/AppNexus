package com.appnexus.bulkwarehousing.application;

import com.appnexus.bulkwarehousing.domain.title.TitleTransferRules;
import com.appnexus.bulkwarehousing.domain.stock.StockAvailability;
import com.appnexus.bulkwarehousing.domain.process.WarehouseFlowTemplate;
import com.appnexus.bulkwarehousing.domain.dispatch.DispatchQuota;
import com.appnexus.bulkwarehousing.domain.evidence.MeasurementAllocation;
import com.appnexus.bulkwarehousing.domain.returns.ReturnBalance;
import com.appnexus.bulkwarehousing.domain.process.CoilDispatchSteps;
import static com.appnexus.bulkwarehousing.application.OutboundStockService.*;

import com.appnexus.bulkwarehousing.api.CoilOperations;
import com.appnexus.bulkwarehousing.api.InboundOperations;
import com.appnexus.bulkwarehousing.api.CoilOutbound;
import com.appnexus.bulkwarehousing.api.InboundReceiptReferences.Position;
import com.appnexus.bulkwarehousing.application.CoilOutboundStore.*;
import com.appnexus.contracts.businessobject.BusinessObjects.DataAccessExecutor;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.authorization.AccessAuthorization.Subject;
import com.appnexus.platform.authorization.AccessPlanning;
import com.appnexus.platform.authorization.AccessPlan;
import com.appnexus.platform.authorization.ScopedWriteGate;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 三级额度与卷号排他占用的同库协调。所有额度变化从已处理的整卷明细推导。 */
public final class CoilOutboundService implements CoilOutbound,DataAccessExecutor {
  private static final BigDecimal ZERO=BigDecimal.ZERO;
  private final CoilOutboundStore quotas;
  private final OutboundStockStore stock;
  private final OutboundStockService gate;
  private final InboundReferenceSource masterData;
  private final AccessPlanning access;
  private final ScopedWriteGate writes;

  /** @param quotas 额度与卷号存储 @param stock 实物占用 @param gate 公共库存门禁
   * @param masterData 主数据原授权读取 @param access 统一权限 */
  public CoilOutboundService(CoilOutboundStore quotas,OutboundStockStore stock,OutboundStockService gate,
      InboundReferenceSource masterData,AccessPlanning access,ScopedWriteGate writes) {
    this.quotas=quotas;
    this.stock=stock;
    this.gate=gate;
    this.masterData=masterData;
    this.access=access;
    this.writes=writes;
  }

  /** {@inheritDoc} */
  @Override public Set<String> enforcedResources() { return Set.of(CoilOperations.RESOURCE); }

  /** {@inheritDoc} */
  @Override public void identify(VerifiedTenantContext c,Coil coil) {
    var plan=require(c,CoilOperations.IDENTIFY);
    if (coil==null || !StockAvailability.validCoilSerial(coil.serial())) throw invalid();
    write(plan,() -> {
      var facts=gate.authorizedStock(c,coil.receiptId());
      masterData.requireMassUnit(c,facts.unitId(),coil.unitCode());
      var existing=quotas.serial(c,coil.receiptId());
      switch (StockAvailability.identify(existing,coil.serial(),facts.occupied())) {
        case APPLY -> quotas.identify(c,facts.position().warehouseId(),coil);
        case REPLAY -> { }
        case OCCUPIED -> throw new Rejection(409,"OUTBOUND_OCCUPIED");
        case CONFLICT -> throw conflict();
      }
      return null;
    });
  }

  /** {@inheritDoc} */
  @Override public void issue(VerifiedTenantContext c,Notice notice) {
    var plan=require(c,CoilOperations.ISSUE);
    if (notice==null || notice.item()==null || notice.item().kind()==null) throw invalid();
    uuid(notice.id());
    uuid(notice.warehouseId());
    uuid(notice.ownerId());
    positive(notice.cap());
    var template = WarehouseFlowTemplate.STEEL_CATALOG.select("dispatch", "road-coil");
    if (!template.permits(WarehouseFlowTemplate.Step.NOTICE, Set.of())) throw invalid();
    write(plan,() -> {
      masterData.requireWarehouse(c,notice.warehouseId());
      masterData.owner(c,notice.ownerId());
      var item=masterData.identity(c,notice.item());
      if (!DispatchQuota.acceptsNoticeUnit(item.baseUnitId(), notice.unitId())) throw invalid();
      masterData.requireMassUnit(c,notice.unitId(),notice.unitCode());
      quotas.guard(c,notice.warehouseId());
      quotas.issue(c,notice,template.version());
      return null;
    });
  }

  /** {@inheritDoc} */
  @Override public void authorize(VerifiedTenantContext c,Authorization authorization) {
    var plan=require(c,CoilOperations.AUTHORIZE);
    if (authorization==null || authorization.expiresAt()==null) throw invalid();
    uuid(authorization.id());
    uuid(authorization.pickupPartnerId());
    positive(authorization.cap());
    if (!authorization.expiresAt().isAfter(Instant.now())
        || authorization.expiresAt().isAfter(Instant.ofEpochSecond(Integer.MAX_VALUE))) throw invalid();
    write(plan,() -> {
      var n=lockNotice(c,authorization.noticeId());
      masterData.owner(c,authorization.pickupPartnerId());
      if (!DispatchQuota.allowsCommitment(n.committed(), authorization.cap(), n.quota().cap())) throw limit("NOTICE_COMMITMENT");
      requireFlow(c,n.id(),WarehouseFlowTemplate.Step.AUTHORIZATION,WarehouseFlowTemplate.Step.NOTICE);
      quotas.authorize(c,authorization);
      return null;
    });
  }

  /** {@inheritDoc} */
  @Override public void plan(VerifiedTenantContext c,Trip trip) {
    var plan=require(c,CoilOperations.PLAN);
    if (trip==null || trip.vehicleNumber()==null || trip.vehicleNumber().isBlank()
        || trip.vehicleNumber().length()>32 || !trip.vehicleNumber().equals(trip.vehicleNumber().strip())) throw invalid();
    uuid(trip.id());
    positive(trip.cap());
    write(plan,() -> {
      var chain=lockChain(c,"authorization",trip.authorizationId());
      requireActive(c,chain.authorization());
      if (!DispatchQuota.allowsCommitment(ZERO, trip.cap(), chain.authorization().quota().cap())) throw limit("AUTHORIZATION");
      if (trip.recoveryReceiptId()!=null && reuseRecoveryTrip(c,chain,trip)) return null;
      requireFlow(c,chain.notice().id(),WarehouseFlowTemplate.Step.TRIP,WarehouseFlowTemplate.Step.AUTHORIZATION);
      quotas.plan(c,trip);
      return null;
    });
  }

  /** 判断与建车共用仓控制锁，防止同一授权和原卷重复建立续办车次。
   * @return 请求是已建立的同一车次时返回 true
   */
  private boolean reuseRecoveryTrip(VerifiedTenantContext c,Chain chain,Trip request) {
    var facts=gate.authorizedStock(c,request.recoveryReceiptId(),chain.notice().warehouseId());
    requireScope(chain.notice(),facts);
    requireTitleAuthorization(chain.authorization(),facts);
    var active=quotas.activeRecoveryTrip(c,request.authorizationId(),facts.receiptId());
    if (active!=null) {
      var existing=quotas.trip(c,active);
      if (active.equals(request.id()) && existing.vehicleNumber().equals(request.vehicleNumber())
          && existing.quota().cap().compareTo(request.cap())==0) return true;
      throw new Rejection(409,"OUTBOUND_RECOVERY_TRIP_EXISTS");
    }
    if (quotas.recoveryEvents(c,request.authorizationId(),facts.receiptId()).stream()
        .noneMatch(event -> event.kind().equals("CANCELLED"))) throw denied();
    requireAvailable(facts);
    return false;
  }

  /** {@inheritDoc} */
  @Override public void preReserve(VerifiedTenantContext c,PreReserve reservation) {
    var plan=require(c,CoilOperations.PRE_RESERVE);
    if (reservation==null) throw invalid();
    nonnegative(reservation.expected());
    nonnegative(reservation.quantity());
    write(plan,() -> {
      var chain=lockChain(c,"authorization",reservation.authorizationId());
      var a=chain.authorization();
      requireActive(c,a);
      var delta=DispatchQuota.reviseUnassigned(a.unassigned(),reservation.expected(),reservation.quantity())
          .orElseThrow(OutboundStockService::conflict);
      checkAmount(chain.notice().quota(),delta,"NOTICE");
      checkAmount(a.quota(),delta,"AUTHORIZATION");
      requireFlow(c,chain.notice().id(),WarehouseFlowTemplate.Step.PRE_RESERVE,WarehouseFlowTemplate.Step.AUTHORIZATION);
      quotas.adjust(c,chain.notice().id(),a.id(),null,delta,ZERO,delta);
      return null;
    });
  }

  /** {@inheritDoc} */
  @Override public void close(VerifiedTenantContext c,CloseAuthorization command) {
    var plan=require(c,CoilOperations.CLOSE);
    if (command==null) throw invalid();
    write(plan,() -> {
      var chain=lockChain(c,"authorization",command.authorizationId());
      var authorization=chain.authorization();
      if (authorization.closed()) return null;
      if (!DispatchQuota.canClose(authorization.quota().reserved(), authorization.unassigned())) {
        throw new Rejection(409,"OUTBOUND_ACTIVE_TRIP_OCCUPATION");
      }
      var release=authorization.unassigned().negate();
      quotas.adjust(c,chain.notice().id(),authorization.id(),null,release,ZERO,release);
      requireFlow(c,chain.notice().id(),WarehouseFlowTemplate.Step.CLOSE,WarehouseFlowTemplate.Step.AUTHORIZATION);
      quotas.close(c,authorization);
      return null;
    });
  }

  /** {@inheritDoc} */
  @Override public Allocation allocate(VerifiedTenantContext c,Allocate command) {
    var plan=require(c,CoilOperations.ALLOCATE);
    validate(command);
    return write(plan,() -> {
      var chain=lockChain(c,"trip",command.tripId());
      if (chain.trip().recoveryReceiptId()!=null && !chain.trip().recoveryReceiptId().equals(command.receiptId())) throw denied();
      var facts=gate.authorizedStock(c,command.receiptId(),chain.notice().warehouseId());
      requireScope(chain.notice(),facts);
      requireTitleAuthorization(chain.authorization(),facts);
      requireExecutionTrip(chain.trip());
      var existing=quotas.allocated(c,command.id());
      if (existing!=null) {
        requireSame(existing,command);
        if (existing.released()) throw conflict();
        return result(command,existing.serial(),existing.quantity(),chain,ZERO,ZERO);
      }
      requireActive(c,chain.authorization());
      requireWholeVehicle(c,chain);
      requireAvailable(facts);
      String serial=quotas.serial(c,facts.receiptId());
      if (serial==null) throw new Rejection(409,"OUTBOUND_COIL_IDENTITY_REQUIRED");
      BigDecimal quantity=facts.quantity();
      var change=DispatchQuota.assign(quantity, chain.authorization().unassigned());
      var extra=change.parentDelta();
      checkAmount(chain.notice().quota(),extra,"NOTICE");
      checkAmount(chain.authorization().quota(),extra,"AUTHORIZATION");
      checkAmount(chain.trip().quota(),quantity,"TRIP");
      requireFlow(c,chain.notice().id(),WarehouseFlowTemplate.Step.ALLOCATION,WarehouseFlowTemplate.Step.TRIP);
      stock.reserve(c,command.id(),facts.receiptId());
      quotas.allocate(c,command,serial,quantity);
      quotas.adjust(c,chain.notice().id(),chain.authorization().id(),chain.trip().id(),extra,change.tripDelta(),change.unassignedDelta());
      quotas.invalidateReview(c,chain.trip().id());
      return result(command,serial,quantity,chain,extra,quantity);
    });
  }

  /** {@inheritDoc} */
  @Override public void cancel(VerifiedTenantContext c,Allocate command) {
    var plan=require(c,CoilOperations.CANCEL);
    validate(command);
    write(plan,() -> {
      var chain=lockChain(c,"trip",command.tripId());
      requireOpenTrip(chain.trip());
      var facts=gate.authorizedStock(c,command.receiptId(),chain.notice().warehouseId());
      requireScope(chain.notice(),facts);
      var existing=quotas.allocated(c,command.id());
      requireSame(existing,command);
      if (!existing.released()) {
        var change=DispatchQuota.assign(existing.quantity().negate(), chain.authorization().unassigned());
        requireFlow(c,chain.notice().id(),WarehouseFlowTemplate.Step.CANCEL,WarehouseFlowTemplate.Step.ALLOCATION);
        stock.release(c,command.id());
        quotas.adjust(c,chain.notice().id(),chain.authorization().id(),chain.trip().id(),
            change.parentDelta(),change.tripDelta(),change.unassignedDelta());
        quotas.invalidateReview(c,chain.trip().id());
        if (chain.trip().recoveryReceiptId()!=null) quotas.closeRecoveryTrip(c,chain.trip().id());
      }
      return null;
    });
  }

  /** {@inheritDoc} */
  @Override public RecoveryPage recoveryEntries(VerifiedTenantContext c,String authorizationId,String afterId) {
    require(c,CoilOperations.READ_AUTHORIZATION_BALANCES);
    require(c,CoilOperations.READ_RETURN_TRANSIT);
    if (afterId!=null) uuid(afterId);
    var warehouse=stock.transaction(() -> lockChain(c,"authorization",authorizationId).notice().warehouseId());
    var rows=new ArrayList<RecoveryEntry>();
    var cursor=afterId;
    int pageSize=30;
    // 每个候选先复核原卷，再复用发运范围检查。分开事务以沿用各自仓锁顺序，游标仅返回有权库存身份。
    while (rows.size()<=pageSize) {
      var origins=quotas.recoveryOrigins(c,authorizationId,warehouse,cursor,pageSize+1);
      for (var origin:origins) {
        cursor=origin.receiptId();
        var entry=authorizedRecoveryEntry(c,authorizationId,origin);
        if (entry!=null) rows.add(entry);
        if (rows.size()>pageSize) return new RecoveryPage(List.copyOf(rows.subList(0,pageSize)),rows.get(pageSize-1).receiptId());
      }
      if (origins.size()<pageSize+1) break;
    }
    return new RecoveryPage(List.copyOf(rows),null);
  }

  /** 对候选执行实时原卷、授权及实际发运读取校验，不返回无权来源。 */
  private RecoveryEntry authorizedRecoveryEntry(VerifiedTenantContext c,String authorizationId,RecoveryOrigin origin) {
    try {
      var current=authorizationRecovery(c,authorizationId,origin.receiptId());
      var transit=returnTransit(c,origin.transitId());
      var received=transit.coils().stream().filter(coil -> coil.receiptId().equals(origin.receiptId())
          && coil.receivedReturnId()!=null).findFirst();
      if (received.isEmpty()) return null;
      var active=current.activeTrip();
      // 同一授权有多个车次时，入口的状态、车辆和数量必须引用同一个车次。
      var entryTrip=active==null ? current.events().getLast() : current.events().stream()
          .filter(event -> event.tripId().equals(active.tripId())).findFirst().orElseThrow(OutboundStockService::conflict);
      return new RecoveryEntry(origin.receiptId(),received.get().serial(),origin.transitId(),
          current.canPlan() || active!=null,entryTrip.tripId(),entryTrip.vehicleNumber(),entryTrip.reserved(),entryTrip.issued(),current.canPlan());
    } catch(Rejection rejection) {
      if (rejection.status()==403) return null;
      throw rejection;
    }
  }

  /** {@inheritDoc} */
  @Override public AuthorizationRecovery authorizationRecovery(VerifiedTenantContext c,String authorizationId,String receiptId) {
    require(c,CoilOperations.READ_AUTHORIZATION_BALANCES);
    return stock.transaction(() -> {
      var chain=lockChain(c,"authorization",authorizationId);
      var authorization=chain.authorization();
      masterData.owner(c,authorization.pickupPartnerId());
      var facts=gate.authorizedStock(c,receiptId,chain.notice().warehouseId());
      requireScope(chain.notice(),facts);
      var events=quotas.recoveryEvents(c,authorizationId,receiptId);
      if (events.isEmpty()) throw denied();
      var activeId=quotas.activeRecoveryTrip(c,authorizationId,receiptId);
      boolean eligible=authorization.valid() && facts.qualityReady() && facts.titleReady()
          && !facts.dispatched() && facts.titleRevision()<=authorization.titleWatermark();
      var active=eligible && activeId!=null ? recoveryStep(c,activeId,facts) : null;
      boolean canPlan=eligible && activeId==null && !facts.occupied()
          && events.stream().anyMatch(row -> row.kind().equals("CANCELLED"));
      return new AuthorizationRecovery(chain.notice().id(),authorizationId,authorization.pickupPartnerId(),canPlan,events,active);
    });
  }

  /** 当前装载及有效复磅决定下一步，历史复磅只用于追溯。 */
  private RecoveryTrip recoveryStep(VerifiedTenantContext c,String tripId,OutboundStockStore.Facts facts) {
    var trip=quotas.trip(c,tripId);
    var allocations=quotas.activeAllocations(c,tripId);
    if (allocations.isEmpty()) {
      return facts.occupied() ? null : new RecoveryTrip(tripId,trip.vehicleNumber(),null,null,RecoveryStep.ALLOCATE);
    }
    if (allocations.size()!=1 || !facts.occupied() || !allocations.getFirst().receiptId().equals(facts.receiptId())) {
      throw conflict();
    }
    var allocation=allocations.getFirst().id();
    if (trip.limitsEvidenceId()==null) return new RecoveryTrip(tripId,trip.vehicleNumber(),allocation,null,RecoveryStep.VEHICLE);
    if (trip.reviewId()!=null) {
      var review=quotas.review(c,trip.reviewId());
      if (review!=null && review.result().state()==ReviewState.APPROVED
          && review.result().tripId().equals(tripId) && review.result().loadVersion()==trip.loadVersion()
          && review.result().net().compareTo(trip.quota().reserved())==0) {
        return new RecoveryTrip(tripId,trip.vehicleNumber(),allocation,trip.reviewId(),RecoveryStep.HANDOVER);
      }
    }
    return new RecoveryTrip(tripId,trip.vehicleNumber(),allocation,null,RecoveryStep.WEIGH);
  }

  /** {@inheritDoc} */
  @Override public void verifyVehicleLimits(VerifiedTenantContext c,VehicleLimits limits) {
    var plan=require(c,CoilOperations.VEHICLE_LIMITS);
    if (limits==null) throw invalid();
    uuid(limits.evidenceId());
    positive(limits.payloadLimit());
    positive(limits.grossLimit());
    if (!MeasurementAllocation.validVehicleLimits(limits.payloadLimit(), limits.grossLimit())) throw invalid();
    write(plan,() -> {
      var chain=lockChain(c,"trip",limits.tripId());
      requireActive(c,chain.authorization());
      requireExecutionTrip(chain.trip());
      requireVehicle(chain,limits.vehicleNumber(),limits.unitId());
      if (limits.evidenceId().equals(chain.trip().limitsEvidenceId())) {
        if (!MeasurementAllocation.sameVehicleLimits(chain.trip().payloadLimit(),chain.trip().grossLimit(),
            limits.payloadLimit(),limits.grossLimit())) throw conflict();
        return null;
      }
      requireFlow(c,chain.notice().id(),WarehouseFlowTemplate.Step.VEHICLE_LIMITS,WarehouseFlowTemplate.Step.TRIP);
      quotas.vehicleLimits(c,limits);
      return null;
    });
  }

  /** {@inheritDoc} */
  @Override public Review reviewWeighing(VerifiedTenantContext c,Weighing input) {
    var plan=require(c,CoilOperations.REVIEW_WEIGHING);
    if (input==null || input.coils()==null || input.coils().isEmpty() || input.coils().size()>500) throw invalid();
    uuid(input.id());
    uuid(input.ticketId());
    positive(input.gross());
    nonnegative(input.tare());
    var net=input.gross().subtract(input.tare());
    positive(net);
    var measured=new HashMap<String,BigDecimal>();
    for (var coil:input.coils()) {
      if (coil==null) throw invalid();
      uuid(coil.allocationId());
      positive(coil.quantity());
      if (measured.putIfAbsent(coil.allocationId(),coil.quantity())!=null) throw invalid();
    }
    if (!MeasurementAllocation.balances(measured.values(), net)) throw invalid();
    var digest=weighingDigest(input);
    return write(plan,() -> {
      var chain=lockChain(c,"trip",input.tripId());
      requireVehicle(chain,input.vehicleNumber(),input.unitId());
      var prior=quotas.review(c,input.id());
      if (prior!=null) {
        if (!prior.result().tripId().equals(input.tripId()) || !prior.digest().equals(digest)) throw conflict();
        return prior.result();
      }
      requireActive(c,chain.authorization());
      requireExecutionTrip(chain.trip());
      requireWholeVehicle(c,chain);
      var active=matchedLoad(c,chain,measured.keySet());
      requireFlow(c,chain.notice().id(),WarehouseFlowTemplate.Step.WEIGHING,WarehouseFlowTemplate.Step.ALLOCATION);
      var reasons=new ArrayList<String>();
      checkVehicle(chain.trip(),net,input.gross(),reasons);
      for (var coil:active) {
        var facts=gate.authorizedStock(c,coil.receiptId(),chain.notice().warehouseId());
        requireScope(chain.notice(),facts);
        requireTitleAuthorization(chain.authorization(),facts);
        if (facts.frozen() && !reasons.contains("OUTBOUND_FROZEN")) reasons.add("OUTBOUND_FROZEN");
        if ((!facts.qualityReady() || !facts.titleReady() || !facts.occupied())
            && !reasons.contains("OUTBOUND_STOCK_NOT_READY")) reasons.add("OUTBOUND_STOCK_NOT_READY");
      }
      var tripDelta=net.subtract(chain.trip().quota().reserved());
      var change=DispatchQuota.assign(tripDelta, chain.authorization().unassigned());
      var extra=change.parentDelta();
      addExceeded(chain.notice().quota(),extra,"NOTICE",reasons);
      addExceeded(chain.authorization().quota(),extra,"AUTHORIZATION",reasons);
      addExceeded(chain.trip().quota(),tripDelta,"TRIP",reasons);
      var state=reasons.isEmpty() ? ReviewState.APPROVED : ReviewState.HELD;
      var result=new Review(input.id(),input.tripId(),state,net.stripTrailingZeros(),List.copyOf(reasons),
          chain.trip().loadVersion());
      if (state==ReviewState.APPROVED) {
        for (var coil:input.coils()) quotas.applyMeasured(c,coil);
        quotas.adjust(c,chain.notice().id(),chain.authorization().id(),chain.trip().id(),extra,change.tripDelta(),change.unassignedDelta());
      }
      quotas.review(c,input,result,digest);
      return result;
    });
  }

  /** {@inheritDoc} */
  @Override public Dispatch handover(VerifiedTenantContext c,Handover request) {
    var plan=require(c,CoilOperations.HANDOVER);
    if (request==null || request.receiptIds()==null || request.receiptIds().isEmpty()
        || request.receiptIds().size()>500) throw invalid();
    uuid(request.id());
    uuid(request.reviewId());
    uuid(request.evidenceId());
    uuid(request.pickupPartnerId());
    request.receiptIds().forEach(OutboundStockService::uuid);
    var actual=Set.copyOf(request.receiptIds());
    if (actual.size()!=request.receiptIds().size()) throw invalid();
    return write(plan,() -> {
      var chain=lockChain(c,"trip",request.tripId());
      requireVehicle(chain,request.vehicleNumber(),chain.notice().unitId());
      if (!chain.authorization().pickupPartnerId().equals(request.pickupPartnerId())) throw denied();
      var key=handoverDigest(request);
      var prior=quotas.dispatch(c,request.id());
      if (prior!=null) {
        if (!prior.result().tripId().equals(request.tripId()) || !prior.digest().equals(key)) throw conflict();
        if (prior.reversed()) throw new Rejection(409,"OUTBOUND_HANDOVER_REVERSED");
        if (chain.trip().retired()) throw new Rejection(409,"OUTBOUND_PARTIAL_RETURN_EXISTS");
        return prior.result();
      }
      requireActive(c,chain.authorization());
      requireExecutionTrip(chain.trip());
      requireWholeVehicle(c,chain);
      var approved=approvedReview(c,chain,request.reviewId());
      var load=quotas.activeAllocations(c,request.tripId());
      if (!DispatchQuota.matchesLoad(load.stream().map(Allocated::receiptId).toList(),actual)) {
        throw new Rejection(409,"OUTBOUND_LOAD_CHANGED");
      }
      var rows=new ArrayList<DispatchedCoil>();
      for (var coil:load) {
        var facts=gate.authorizedStock(c,coil.receiptId(),chain.notice().warehouseId());
        requireScope(chain.notice(),facts);
        requireTitleAuthorization(chain.authorization(),facts);
        if (facts.frozen()) throw new Rejection(409,"OUTBOUND_FROZEN");
        if (!StockAvailability.canHandover(facts.dispatched(),facts.qualityReady(),facts.titleReady(),facts.occupied())) {
          throw new Rejection(409,"OUTBOUND_STOCK_NOT_READY");
        }
        var occupation=stock.occupation(c,coil.id());
        if (occupation==null || occupation.released() || !occupation.quotaManaged()
            || !occupation.receiptId().equals(coil.receiptId())
            || !coil.serial().equals(quotas.serial(c,coil.receiptId()))) throw conflict();
        rows.add(new DispatchedCoil(coil,facts.quantity(),facts.position().warehouseId(),facts.position().locationId()));
      }
      var issued=load.stream().map(Allocated::quantity).reduce(ZERO,BigDecimal::add);
      if (!DispatchQuota.allowsIssue(issued, approved.result().net(), chain.trip().quota().reserved(),
          chain.authorization().quota().reserved().subtract(chain.authorization().unassigned()),
          chain.notice().quota().reserved())) throw conflict();
      // 库存身份复核耗时可能跨越有效期，落账前再核验数据库当前授权状态。
      requireActive(c,quotas.authorization(c,chain.authorization().id()));
      var deduction=rows.stream().map(DispatchedCoil::bookQuantity).reduce(ZERO,BigDecimal::add);
      var result=new Dispatch(request.id(),request.tripId(),issued.stripTrailingZeros(),
          deduction.stripTrailingZeros(),chain.notice().unitId());
      quotas.dispatch(c,request,result,key,chain.notice(),chain.authorization(),rows);
      for (var coil:load) stock.release(c,coil.id());
      return result;
    });
  }

  /** {@inheritDoc} */
  @Override public void acceptReturn(VerifiedTenantContext c,AcceptedReturn request) {
    var plan=require(c,CoilOperations.ACCEPT_RETURN);
    require(c,InboundOperations.RELEASE_QUALITY.permission(),InboundOperations.RELEASE_QUALITY.resource());
    require(c,InboundOperations.REGISTER_TITLE.permission(),InboundOperations.REGISTER_TITLE.resource());
    if (request==null) throw invalid();
    uuid(request.id());
    uuid(request.custodyEvidenceId());
    uuid(request.titleEvidenceId());
    uuid(request.qualityEvidenceId());
    var positions=returnCoilsById(request.coils());
    if (positions.values().stream().anyMatch(coil -> coil.position()!=null)) throw invalid();
    var key=returnDigest(request.handoverId(),request.custodyEvidenceId(),request.titleEvidenceId(),
        request.qualityEvidenceId(),request.coils());
    write(plan,() -> {
      var chain=lockOriginal(c,request.handoverId());
      var previous=quotas.acceptedReturn(c,request.id());
      if (previous!=null) {
        if (!previous.handoverId().equals(request.handoverId()) || !previous.digest().equals(key)) throw conflict();
        return null;
      }
      var original=requireCurrentDispatch(c,request.handoverId(),chain);
      var lines=originalLines(c,original.result());
      if (!ReturnBalance.coversOriginal(lines.stream().map(IssuedCoil::receiptId).toList(), positions.keySet())) {
        throw conflict();
      }
      for (var line:lines) {
        var coil=positions.get(line.receiptId());
        verifyReturnPosition(c,chain,line,coil.locationId());
      }
      requireFlow(c,chain.notice().id(),WarehouseFlowTemplate.Step.RETURN_ACCEPT,WarehouseFlowTemplate.Step.HANDOVER);
      quotas.acceptReturn(c,request,key);
      return null;
    });
  }

  /** {@inheritDoc} */
  @Override public Reversal reverseHandover(VerifiedTenantContext c,ReverseHandover request) {
    var plan=require(c,CoilOperations.REVERSE_HANDOVER);
    if (request==null || request.kind()==null) throw invalid();
    uuid(request.id());
    uuid(request.evidenceId());
    if (request.kind()==ReversalKind.ACTUAL_RETURN) uuid(request.returnId());
    else if (request.returnId()!=null) throw invalid();
    var key=digest(request.handoverId()+"|"+request.kind()+"|"+request.evidenceId()+"|"+request.returnId());
    return write(plan,() -> {
      var chain=lockOriginal(c,request.handoverId());
      var previous=quotas.reversal(c,request.id());
      if (previous!=null) {
        if (!previous.result().handoverId().equals(request.handoverId()) || !previous.digest().equals(key)) throw conflict();
        return previous.result();
      }
      var original=requireCurrentDispatch(c,request.handoverId(),chain);
      var acceptance=quotas.acceptedReturnFor(c,request.handoverId());
      if (request.kind()==ReversalKind.ACTUAL_RETURN && !ReturnBalance.matchesReversal(true, request.returnId(), acceptance)) {
        throw new Rejection(409,"OUTBOUND_RETURN_ACCEPTANCE_REQUIRED");
      }
      if (request.kind()==ReversalKind.MISTAKEN_HANDOVER && !ReturnBalance.matchesReversal(false, null, acceptance)) {
        throw new Rejection(409,"OUTBOUND_ACTUAL_RETURN_REQUIRED");
      }
      // 已关闭授权保持关闭；误确认恢复实物，但不重新派发已经撤回的授权。
      boolean restoreLock=ReturnBalance.restoresOccupation(request.kind()==ReversalKind.MISTAKEN_HANDOVER,chain.authorization().closed());
      if (restoreLock) requireWholeVehicle(c,chain);
      var lines=originalLines(c,original.result());
      for (var line:lines) dispatchedStock(c,chain,line);
      var dispatch=original.result();
      var result=new Reversal(request.id(),request.handoverId(),request.kind(),dispatch.issuedQuantity(),
          dispatch.stockDeduction(),restoreLock ? dispatch.issuedQuantity() : ZERO,dispatch.unitId());
      requireFlow(c,chain.notice().id(),WarehouseFlowTemplate.Step.REVERSAL,WarehouseFlowTemplate.Step.HANDOVER);
      quotas.reverse(c,request,result,key,chain.notice(),chain.authorization(),chain.trip(),lines);
      if (restoreLock) for (var line:lines) stock.restore(c,line.allocationId(),line.receiptId());
      return result;
    });
  }

  /** {@inheritDoc} */
  @Override public ReturnPreparation prepareReturn(VerifiedTenantContext c,String handoverId) {
    require(c,CoilOperations.DISPATCH_RETURN);
    return stock.transaction(() -> {
      var chain=lockOriginal(c,handoverId);
      var original=quotas.dispatch(c,handoverId);
      var warehouse=chain.notice().warehouseId();
      if (original.reversed() || quotas.acceptedReturnFor(c,handoverId)!=null) {
        return new ReturnPreparation(handoverId,warehouse,List.of());
      }
      var candidates=new ArrayList<ReturnCandidate>();
      for (var line:remainingReturnLines(c,chain,handoverId)) {
        if (quotas.transitLink(c,handoverId,line.receiptId())!=null) continue;
        var facts=stock.lockInWarehouse(c,line.receiptId(),warehouse);
        if (facts==null) throw denied();
        requireDispatchedScope(chain,line,facts);
        candidates.add(new ReturnCandidate(line.receiptId(),quotas.serial(c,line.receiptId()),
            line.book().stripTrailingZeros(),line.issued().stripTrailingZeros()));
      }
      return new ReturnPreparation(handoverId,warehouse,List.copyOf(candidates));
    });
  }

  /** {@inheritDoc} */
  @Override public ReturnTransit dispatchReturn(VerifiedTenantContext c,ReturnDispatch dispatch) {
    var plan=require(c,CoilOperations.DISPATCH_RETURN);
    if (dispatch==null || dispatch.receiptIds()==null || dispatch.receiptIds().isEmpty()
        || dispatch.receiptIds().size()>500) throw invalid();
    uuid(dispatch.id());
    uuid(dispatch.receivingWarehouseId());
    uuid(dispatch.evidenceId());
    dispatch.receiptIds().forEach(OutboundStockService::uuid);
    var ids=Set.copyOf(dispatch.receiptIds());
    if (ids.size()!=dispatch.receiptIds().size()) throw invalid();
    var key=digest(dispatch.handoverId()+"|"+dispatch.receivingWarehouseId()+"|"+dispatch.evidenceId()+"|"
        +String.join("|",dispatch.receiptIds().stream().sorted().toList()));
    return write(plan,() -> {
      var chain=lockOriginal(c,dispatch.handoverId(),Set.of(dispatch.receivingWarehouseId()));
      if (chain.notice().warehouseId().equals(dispatch.receivingWarehouseId())) throw invalid();
      var prior=quotas.transit(c,dispatch.id());
      if (prior!=null) {
        if (!prior.handoverId().equals(dispatch.handoverId()) || !prior.digest().equals(key)) throw conflict();
        return transitResult(c,chain,prior);
      }
      requireReturnOpen(c,dispatch.handoverId());
      var selected=remainingReturnLines(c,chain,dispatch.handoverId()).stream()
          .filter(line -> ids.contains(line.receiptId())).toList();
      if (selected.size()!=ids.size()) throw new Rejection(409,"OUTBOUND_RETURN_SCOPE_EXHAUSTED");
      for (var line:selected) {
        var facts=stock.lockInWarehouse(c,line.receiptId(),chain.notice().warehouseId());
        if (facts==null) throw denied();
        requireDispatchedScope(chain,line,facts);
        if (quotas.transitLink(c,dispatch.handoverId(),line.receiptId())!=null) {
          throw new Rejection(409,"OUTBOUND_RETURN_ALREADY_IN_TRANSIT");
        }
      }
      requireFlow(c,chain.notice().id(),WarehouseFlowTemplate.Step.RETURN_DISPATCH,WarehouseFlowTemplate.Step.HANDOVER);
      quotas.dispatchReturn(c,dispatch,key,selected);
      return transitResult(c,chain,new Transit(dispatch.id(),dispatch.handoverId(),dispatch.receivingWarehouseId(),dispatch.evidenceId(),key));
    });
  }

  /** {@inheritDoc} */
  @Override public ReturnTransit returnTransit(VerifiedTenantContext c,String id) {
    require(c,CoilOperations.READ_RETURN_TRANSIT);
    uuid(id);
    return stock.transaction(() -> {
      var transit=quotas.transit(c,id);
      if (transit==null) throw denied();
      var chain=lockOriginal(c,transit.handoverId(),Set.of(transit.warehouseId()));
      return transitResult(c,chain,transit);
    });
  }

  /** {@inheritDoc} */
  @Override public RerouteTransitCoil rerouteTransitCoil(VerifiedTenantContext c,RerouteTransitCoil command) {
    var plan=require(c,CoilOperations.REROUTE_TRANSIT_COIL);
    if (command==null || command.reason()==null || command.reason().isBlank() || command.reason().length()>256) {
      throw invalid();
    }
    uuid(command.id());
    uuid(command.receiptId());
    uuid(command.expectedReceivingWarehouseId());
    uuid(command.newReceivingWarehouseId());
    uuid(command.evidenceId());
    if (command.expectedReceivingWarehouseId().equals(command.newReceivingWarehouseId())) throw invalid();
    var key=digest(command.transitId()+"|"+command.receiptId()+"|"+command.expectedReceivingWarehouseId()+"|"
        +command.newReceivingWarehouseId()+"|"+command.evidenceId()+"|"+command.reason());
    return write(plan,() -> {
      var transit=requireTransit(c,command.transitId());
      var warehouses=new java.util.HashSet<>(List.of(transit.warehouseId(),command.expectedReceivingWarehouseId(),
          command.newReceivingWarehouseId()));
      var chain=lockOriginal(c,transit.handoverId(),warehouses);
      if (chain.notice().warehouseId().equals(command.newReceivingWarehouseId())) throw invalid();
      var prior=quotas.transitReroute(c,command.id());
      if (prior!=null) {
        if (!prior.result().transitId().equals(transit.id()) || !prior.digest().equals(key)) throw conflict();
        return prior.result();
      }
      var link=lossEligibleLink(c,transit,command.receiptId());
      switch (ReturnBalance.routeDecision(link.receivingWarehouseId(),command.expectedReceivingWarehouseId(),
          link.lossId()!=null,true)) {
        case LOSS_SETTLED -> throw new Rejection(409,"OUTBOUND_RETURN_LOSS_SETTLED");
        case ROUTE_CHANGED -> throw routeChanged();
        case ALLOWED -> { }
      }
      requireFlow(c,chain.notice().id(),WarehouseFlowTemplate.Step.RETURN_REROUTE,WarehouseFlowTemplate.Step.RETURN_DISPATCH);
      quotas.rerouteTransitCoil(c,command,key,link.rerouteId());
      return command;
    });
  }

  /** {@inheritDoc} */
  @Override public TransitLoss settleTransitLoss(VerifiedTenantContext c,SettleTransitLoss command) {
    var plan=require(c,CoilOperations.SETTLE_TRANSIT_LOSS);
    if (command==null || command.reason()==null || command.reason().isBlank() || command.reason().length()>256) {
      throw invalid();
    }
    uuid(command.id());
    uuid(command.receiptId());
    uuid(command.responsiblePartnerId());
    uuid(command.evidenceId());
    var key=digest(command.transitId()+"|"+command.receiptId()+"|"+command.responsiblePartnerId()
        +"|"+command.evidenceId()+"|"+command.reason());
    return write(plan,() -> {
      var transit=requireTransit(c,command.transitId());
      var chain=lockTransitCoil(c,transit,command.receiptId());
      var prior=quotas.transitLoss(c,command.id());
      if (prior!=null) {
        if (!prior.result().transitId().equals(transit.id()) || !prior.digest().equals(key)) throw conflict();
        return prior.result();
      }
      masterData.owner(c,command.responsiblePartnerId());
      var link=lossEligibleLink(c,transit,command.receiptId());
      if (link.lossId()!=null) throw new Rejection(409,"OUTBOUND_RETURN_LOSS_SETTLED");
      var issued=remainingReturnLines(c,chain,transit.handoverId()).stream()
          .filter(line -> line.receiptId().equals(command.receiptId())).findFirst().orElseThrow(OutboundStockService::conflict);
      var result=new TransitLoss(command.id(),transit.id(),command.receiptId(),command.responsiblePartnerId(),
          command.evidenceId(),command.reason(),issued.book().stripTrailingZeros(),issued.issued().stripTrailingZeros());
      requireFlow(c,chain.notice().id(),WarehouseFlowTemplate.Step.LOSS_SETTLE,WarehouseFlowTemplate.Step.RETURN_DISPATCH);
      quotas.settleTransitLoss(c,result,key);
      return result;
    });
  }

  /** {@inheritDoc} */
  @Override public void reopenTransitLoss(VerifiedTenantContext c,ReopenTransitLoss command) {
    var plan=require(c,CoilOperations.REOPEN_TRANSIT_LOSS);
    if (command==null) throw invalid();
    uuid(command.id());
    uuid(command.lossId());
    uuid(command.evidenceId());
    write(plan,() -> {
      var transit=requireTransit(c,command.transitId());
      var loss=quotas.transitLoss(c,command.lossId());
      if (loss==null || !loss.result().transitId().equals(transit.id())) throw denied();
      var receiptId=loss.result().receiptId();
      var chain=lockTransitCoil(c,transit,receiptId);
      var prior=quotas.transitReopening(c,command.id());
      if (prior!=null) {
        if (!prior.transitId().equals(transit.id()) || !prior.lossId().equals(command.lossId())
            || !prior.evidenceId().equals(command.evidenceId())) throw conflict();
        return null;
      }
      if (command.evidenceId().equals(loss.result().evidenceId())) throw invalid();
      var link=lossEligibleLink(c,transit,receiptId);
      if (!ReturnBalance.canReopenLoss(link.lossId(),command.lossId())) throw new Rejection(409,"OUTBOUND_RETURN_LOSS_NOT_ACTIVE");
      requireFlow(c,chain.notice().id(),WarehouseFlowTemplate.Step.LOSS_REOPEN,WarehouseFlowTemplate.Step.LOSS_SETTLE);
      quotas.reopenTransitLoss(c,command,receiptId);
      return null;
    });
  }

  private Transit requireTransit(VerifiedTenantContext c,String id) {
    uuid(id);
    var transit=quotas.transit(c,id);
    if (transit==null) throw denied();
    return transit;
  }

  private Chain lockTransitCoil(VerifiedTenantContext c,Transit transit,String receiptId) {
    var before=quotas.transitLink(c,transit.handoverId(),receiptId);
    if (before==null || !transit.id().equals(before.transitId())) throw denied();
    var warehouses=new java.util.HashSet<>(List.of(transit.warehouseId(),before.receivingWarehouseId()));
    var chain=lockOriginal(c,transit.handoverId(),warehouses);
    var current=quotas.transitLink(c,transit.handoverId(),receiptId);
    if (current==null || !before.receivingWarehouseId().equals(current.receivingWarehouseId())) throw routeChanged();
    return chain;
  }

  private static Rejection routeChanged() {
    return new Rejection(409,"OUTBOUND_RETURN_TRANSIT_ROUTE_CHANGED");
  }

  private TransitLink lossEligibleLink(VerifiedTenantContext c,Transit transit,String receiptId) {
    var link=quotas.transitLink(c,transit.handoverId(),receiptId);
    if (link == null || !ReturnBalance.isOpenTransit(link.transitId(), transit.id(), link.receivedReturnId() != null)) {
      throw new Rejection(409,"OUTBOUND_RETURN_TRANSIT_COIL_MISMATCH");
    }
    return link;
  }

  private ReturnTransit transitResult(VerifiedTenantContext c,Chain chain,Transit transit) {
    var coils=quotas.transitCoils(c,transit.id());
    // 同一发运可有多个当前接收仓，完整清单不得越过任何一卷的读取范围。
    coils.stream().map(TransitCoil::receivingWarehouseId).distinct()
        .forEach(warehouse -> masterData.requireWarehouse(c,warehouse));
    var history=quotas.transitEvidence(c,transit.id());
    var historicalWarehouses=new java.util.HashSet<String>();
    historicalWarehouses.add(transit.warehouseId());
    coils.forEach(coil -> historicalWarehouses.add(coil.receivingWarehouseId()));
    for (var fact:history) {
      if (fact.previousWarehouseId()!=null && historicalWarehouses.add(fact.previousWarehouseId())) {
        masterData.requireWarehouse(c,fact.previousWarehouseId());
      }
      if (fact.warehouseId()!=null && historicalWarehouses.add(fact.warehouseId())) {
        masterData.requireWarehouse(c,fact.warehouseId());
      }
    }
    boolean showQuota=canReadAuthorizationBalances(c);
    return new ReturnTransit(transit.id(),transit.handoverId(),chain.notice().warehouseId(),transit.warehouseId(),
        chain.notice().unitId(),coils,transit.evidenceId(),history,
        showQuota ? returnQuota(chain.notice().id(),chain.notice().quota()) : null,
        showQuota ? returnQuota(chain.authorization().id(),chain.authorization().quota()) : null,
        showQuota ? returnQuota(chain.trip().id(),chain.trip().quota()) : null);
  }

  private boolean canReadAuthorizationBalances(VerifiedTenantContext c) {
    try {
      require(c,CoilOperations.READ_AUTHORIZATION_BALANCES);
      return true;
    } catch (SecurityException denied) {
      return false;
    } catch (Rejection rejected) {
      if (rejected.status()==403) return false;
      throw rejected;
    }
  }

  private static ReturnQuota returnQuota(String id,Quota quota) {
    return new ReturnQuota(id,quota.cap().stripTrailingZeros(),quota.reserved().stripTrailingZeros(),
        quota.issued().stripTrailingZeros(),quota.remaining().stripTrailingZeros());
  }

  /** {@inheritDoc} */
  @Override public PartialReturnResult returnCoils(VerifiedTenantContext c,PartialReturn request) {
    var plan=require(c,CoilOperations.RETURN_COILS);
    require(c,InboundOperations.REGISTER_TITLE.permission(),InboundOperations.REGISTER_TITLE.resource());
    if (request==null) throw invalid();
    uuid(request.id());
    uuid(request.custodyEvidenceId());
    uuid(request.titleEvidenceId());
    var positions=returnCoilsById(request.coils());
    if (request.transitId()!=null) uuid(request.transitId());
    var returnKey=returnDigest(request.handoverId(),request.custodyEvidenceId(),request.titleEvidenceId(),"",request.coils());
    var key=request.transitId()==null ? returnKey : digest(returnKey+"|"+request.transitId());
    return write(plan,() -> {
      var destinations=positions.values().stream().map(ReturnedCoil::position)
          .filter(java.util.Objects::nonNull).map(Position::warehouseId).collect(java.util.stream.Collectors.toSet());
      var chain=lockOriginal(c,request.handoverId(),destinations);
      var previous=quotas.partialReturn(c,request.id());
      if (previous!=null) {
        if (!previous.result().handoverId().equals(request.handoverId()) || !previous.digest().equals(key)) throw conflict();
        return previous.result();
      }
      var original=requireReturnOpen(c,request.handoverId());
      var transit=validatedTransit(c,request);
      var remaining=remainingReturnLines(c,chain,request.handoverId());
      var selected=remaining.stream().filter(line -> positions.containsKey(line.receiptId())).toList();
      if (selected.size()!=positions.size()) throw new Rejection(409,"OUTBOUND_RETURN_SCOPE_EXHAUSTED");
      var rows=new ArrayList<ReturnedLine>();
      for (var line:selected) {
        var target=positions.get(line.receiptId());
        requireTransitLink(c,request.handoverId(),line,transit,target);
        rows.add(new ReturnedLine(line,resolveReturnPosition(c,chain,line,target)));
      }
      var net=selected.stream().map(IssuedCoil::issued).reduce(ZERO,BigDecimal::add);
      var book=selected.stream().map(IssuedCoil::book).reduce(ZERO,BigDecimal::add);
      var result=new PartialReturnResult(request.id(),request.handoverId(),net.stripTrailingZeros(),
          book.stripTrailingZeros(),original.result().unitId());
      requireFlow(c,chain.notice().id(),WarehouseFlowTemplate.Step.RETURN_RECEIVE,WarehouseFlowTemplate.Step.HANDOVER);
      quotas.returnCoils(c,request,result,key,chain.notice(),chain.authorization(),chain.trip(),rows);
      return result;
    });
  }

  private PostedDispatch requireReturnOpen(VerifiedTenantContext c,String handoverId) {
    var original=quotas.dispatch(c,handoverId);
    if (original==null || original.reversed()) throw new Rejection(409,"OUTBOUND_HANDOVER_REVERSED");
    if (quotas.acceptedReturnFor(c,handoverId)!=null) {
      throw new Rejection(409,"OUTBOUND_WHOLE_RETURN_ACCEPTANCE_EXISTS");
    }
    return original;
  }

  private List<IssuedCoil> remainingReturnLines(VerifiedTenantContext c,Chain chain,String handoverId) {
    var remaining=quotas.activeIssuedCoils(c,handoverId);
    var remainingNet=remaining.stream().map(IssuedCoil::issued).reduce(ZERO,BigDecimal::add);
    if (!ReturnBalance.reconciles(remainingNet, chain.trip().quota().issued(), chain.trip().quota().reserved())) {
      throw conflict();
    }
    return remaining;
  }

  private Transit validatedTransit(VerifiedTenantContext c,PartialReturn request) {
    if (request.transitId()==null) return null;
    var transit=quotas.transit(c,request.transitId());
    if (transit==null || !transit.handoverId().equals(request.handoverId())) {
      throw new Rejection(409,"OUTBOUND_RETURN_TRANSIT_MISMATCH");
    }
    return transit;
  }

  private void requireTransitLink(VerifiedTenantContext c,String handoverId,IssuedCoil line,Transit transit,
      ReturnedCoil target) {
    var link=quotas.transitLink(c,handoverId,line.receiptId());
    if (transit==null) {
      if (link!=null) throw new Rejection(409,"OUTBOUND_RETURN_TRANSIT_REQUIRED");
      return;
    }
    if (link == null || !ReturnBalance.isOpenTransit(link.transitId(), transit.id(), link.receivedReturnId() != null)) {
      throw new Rejection(409,"OUTBOUND_RETURN_TRANSIT_COIL_MISMATCH");
    }
    String targetWarehouse=target.position()==null ? null : target.position().warehouseId();
    switch (ReturnBalance.routeDecision(link.receivingWarehouseId(),targetWarehouse,link.lossId()!=null,false)) {
      case ROUTE_CHANGED -> throw new Rejection(409,"OUTBOUND_RETURN_TRANSIT_WAREHOUSE_MISMATCH");
      case LOSS_SETTLED -> throw new Rejection(409,"OUTBOUND_RETURN_LOSS_SETTLED");
      case ALLOWED -> { }
    }
  }

  /** {@inheritDoc} */
  @Override public void releaseReturnedQuality(VerifiedTenantContext c,ReturnedQuality request) {
    var plan=require(c,CoilOperations.RELEASE_RETURNED_QUALITY);
    require(c,InboundOperations.RELEASE_QUALITY.permission(),InboundOperations.RELEASE_QUALITY.resource());
    if (request==null) throw invalid();
    uuid(request.returnId());
    uuid(request.receiptId());
    uuid(request.evidenceId());
    write(plan,() -> {
      var batch=quotas.partialReturn(c,request.returnId());
      if (batch==null) throw denied();
      // 返仓行的目标仓及位置一经记账不可修改。先定位两仓，再按固定顺序获取控制锁。
      var line=quotas.returnedStock(c,request.returnId(),request.receiptId());
      if (line==null) throw denied();
      var chain=lockOriginal(c,batch.result().handoverId(),Set.of(line.warehouseId()));
      line=quotas.returnedStock(c,request.returnId(),request.receiptId());
      if (line.qualityEvidenceId()!=null) {
        if (!line.qualityEvidenceId().equals(request.evidenceId())) throw conflict();
        return null;
      }
      var facts=gate.authorizedStock(c,request.receiptId(),line.warehouseId());
      requireGoodsScope(chain.notice(),facts);
      if (!StockAvailability.canReleaseReturnedQuality(line.locationId(), facts.position().locationId(),
          facts.dispatched(), facts.occupied(), facts.qualityReady())) throw conflict();
      requireFlow(c,chain.notice().id(),WarehouseFlowTemplate.Step.RETURN_QUALITY,WarehouseFlowTemplate.Step.RETURN_RECEIVE);
      quotas.releaseReturnedQuality(c,request,line);
      return null;
    });
  }

  private static HashMap<String,ReturnedCoil> returnCoilsById(List<ReturnedCoil> coils) {
    if (coils==null || coils.isEmpty() || coils.size()>500) throw invalid();
    var positions=new HashMap<String,ReturnedCoil>();
    for (var coil:coils) {
      if (coil==null) throw invalid();
      uuid(coil.receiptId());
      uuid(coil.locationId());
      if (coil.position()!=null) {
        uuid(coil.position().warehouseId());
        uuid(coil.position().areaId());
        if (!coil.locationId().equals(coil.position().locationId())) throw invalid();
      }
      if (positions.putIfAbsent(coil.receiptId(),coil)!=null) throw invalid();
    }
    return positions;
  }

  private Position resolveReturnPosition(
      VerifiedTenantContext c,Chain chain,IssuedCoil line,ReturnedCoil target) {
    if (target.position()==null) {
      return verifyReturnPosition(c,chain,line,target.locationId()).position();
    }
    // 来源位置是历史快照，可已停用。原仓与实际接收仓均已通过范围校验并取得仓级锁。
    var facts=stock.lockInWarehouse(c,line.receiptId(),chain.notice().warehouseId());
    if (facts==null) throw denied();
    requireDispatchedScope(chain,line,facts);
    masterData.owner(c,facts.ownerId());
    return masterData.position(c,target.position());
  }

  private OutboundStockStore.Facts verifyReturnPosition(VerifiedTenantContext c,Chain chain,IssuedCoil line,String locationId) {
    var facts=dispatchedStock(c,chain,line);
    if (!facts.position().locationId().equals(locationId)) {
      throw new Rejection(409,"OUTBOUND_RETURN_LOCATION_MISMATCH");
    }
    return facts;
  }

  private Chain lockOriginal(VerifiedTenantContext c,String handoverId) {
    uuid(handoverId);
    var original=quotas.dispatch(c,handoverId);
    if (original==null) throw denied();
    return lockChain(c,"trip",original.result().tripId());
  }

  private Chain lockOriginal(VerifiedTenantContext c,String handoverId,Set<String> receivingWarehouses) {
    uuid(handoverId);
    var original=quotas.dispatch(c,handoverId);
    if (original==null) throw denied();
    var link=quotas.links(c,"trip",original.result().tripId());
    if (link==null) throw denied();
    var warehouses=new java.util.TreeSet<>(receivingWarehouses);
    warehouses.add(link.warehouseId());
    for (var warehouse:warehouses) masterData.requireWarehouse(c,warehouse);
    for (var warehouse:warehouses) quotas.guard(c,warehouse);
    // 原仓控制锁已持有；复用锁定原通知、授权及车次，并复核原货主。
    return lockChain(c,"trip",original.result().tripId());
  }

  private PostedDispatch requireCurrentDispatch(VerifiedTenantContext c,String handoverId,Chain chain) {
    var original=quotas.dispatch(c,handoverId);
    if (original==null || original.reversed()) throw new Rejection(409,"OUTBOUND_HANDOVER_REVERSED");
    if (quotas.hasTransit(c,handoverId)) throw new Rejection(409,"OUTBOUND_RETURN_TRANSIT_REQUIRED");
    if (chain.trip().retired()) throw new Rejection(409,"OUTBOUND_PARTIAL_RETURN_EXISTS");
    if (!ReturnBalance.reconciles(original.result().issuedQuantity(),chain.trip().quota().issued(),
        chain.trip().quota().reserved())) throw conflict();
    return original;
  }

  private List<IssuedCoil> originalLines(VerifiedTenantContext c,Dispatch dispatch) {
    var lines=quotas.issuedCoils(c,dispatch.id());
    if (lines.isEmpty() || lines.stream().map(IssuedCoil::issued).reduce(ZERO,BigDecimal::add)
        .compareTo(dispatch.issuedQuantity())!=0 || lines.stream().map(IssuedCoil::book).reduce(ZERO,BigDecimal::add)
        .compareTo(dispatch.stockDeduction())!=0) throw conflict();
    return lines;
  }

  private OutboundStockStore.Facts dispatchedStock(VerifiedTenantContext c,Chain chain,IssuedCoil line) {
    var facts=gate.authorizedStock(c,line.receiptId(),chain.notice().warehouseId());
    requireDispatchedScope(chain,line,facts);
    return facts;
  }

  private static void requireDispatchedScope(Chain chain,IssuedCoil line,OutboundStockStore.Facts facts) {
    requireScope(chain.notice(),facts);
    if (!StockAvailability.matchesDispatched(facts.dispatched(),facts.occupied(),facts.quantity(),line.book())) throw conflict();
  }

  private static String returnDigest(String handoverId,String custodyEvidenceId,String titleEvidenceId,
      String qualityEvidenceId,List<ReturnedCoil> coils) {
    var value=new StringBuilder().append(handoverId).append('|').append(custodyEvidenceId)
        .append('|').append(titleEvidenceId).append('|').append(qualityEvidenceId);
    for (var coil:coils.stream().sorted(Comparator.comparing(ReturnedCoil::receiptId)).toList()) {
      value.append('|').append(coil.receiptId()).append(':').append(coil.locationId());
      var position=coil.position();
      // 保留原库位格式的摘要，部署前后的历史重放保持兼容。
      if (position!=null) value.append(':').append(position.warehouseId()).append(':').append(position.areaId())
          .append(':').append(position.warehouseVersion()).append(':').append(position.areaVersion())
          .append(':').append(position.locationVersion());
    }
    return digest(value.toString());
  }

  private void requireFlow(VerifiedTenantContext c,String noticeId,WarehouseFlowTemplate.Step step,
      WarehouseFlowTemplate.Step confirmed) {
    var template=WarehouseFlowTemplate.STEEL_CATALOG.resume(
        WarehouseFlowTemplate.COIL_DISPATCH_V1.code(),quotas.flowVersion(c,noticeId));
    if (!template.permits(step,Set.of(confirmed))) throw new Rejection(409,"OUTBOUND_FLOW_STEP_NOT_ALLOWED");
  }

  private RecordedReview approvedReview(VerifiedTenantContext c,Chain chain,String reviewId) {
    if (!reviewId.equals(chain.trip().reviewId())) throw new Rejection(409,"OUTBOUND_REVIEW_REQUIRED");
    var review=quotas.review(c,reviewId);
    boolean validWeighing=review!=null && MeasurementAllocation.matchesHandover(
        review.result().state()==ReviewState.APPROVED,review.result().tripId(),chain.trip().id(),
        review.result().loadVersion(),chain.trip().loadVersion());
    var confirmed = validWeighing ? Set.of(WarehouseFlowTemplate.Step.WEIGHING, WarehouseFlowTemplate.Step.ALLOCATION)
        : Set.<WarehouseFlowTemplate.Step>of();
    if (!WarehouseFlowTemplate.STEEL_CATALOG.resume(
        WarehouseFlowTemplate.COIL_DISPATCH_V1.code(), quotas.flowVersion(c,chain.notice().id())).permits(WarehouseFlowTemplate.Step.HANDOVER, confirmed)) {
      throw new Rejection(409,"OUTBOUND_REVIEW_REQUIRED");
    }
    var reasons=new ArrayList<String>();
    checkVehicle(chain.trip(),review.result().net(),review.gross(),reasons);
    if (!reasons.isEmpty()) throw new Rejection(409,reasons.getFirst());
    return review;
  }

  private static String handoverDigest(Handover command) {
    // 车牌已与不可变车次比对，摘要只需要固定格式的凭证 UUID 和实物 UUID。
    return digest(command.tripId()+"|"+command.reviewId()+"|"+command.evidenceId()+"|"+command.pickupPartnerId()
        +"|"+String.join("|",command.receiptIds().stream().sorted().toList()));
  }

  private List<Allocated> matchedLoad(VerifiedTenantContext c,Chain chain,Set<String> allocationIds) {
    var active=quotas.activeAllocations(c,chain.trip().id());
    if (!DispatchQuota.matchesLoad(active.stream().map(Allocated::id).toList(),allocationIds)) {
      throw new Rejection(409,"OUTBOUND_LOAD_CHANGED");
    }
    if (active.stream().map(Allocated::quantity).reduce(ZERO,BigDecimal::add)
        .compareTo(chain.trip().quota().reserved())!=0) throw conflict();
    return active;
  }

  private static void checkVehicle(TripState trip,BigDecimal net,BigDecimal gross,List<String> reasons) {
    MeasurementAllocation.vehicleIssues(trip.payloadLimit(), trip.grossLimit(), net, gross)
        .forEach(issue -> reasons.add(switch (issue) {
          case LIMITS_REQUIRED -> "OUTBOUND_VEHICLE_LIMITS_REQUIRED";
          case PAYLOAD_LIMIT -> "OUTBOUND_VEHICLE_PAYLOAD_LIMIT";
          case GROSS_LIMIT -> "OUTBOUND_VEHICLE_GROSS_LIMIT";
        }));
  }

  private static void addExceeded(Quota quota,BigDecimal delta,String level,List<String> reasons) {
    if (!DispatchQuota.allowsChange(quota.reserved(), quota.remaining(), delta)) {
      reasons.add("OUTBOUND_"+level+"_LIMIT");
    }
  }

  private static void requireVehicle(Chain chain,String vehicleNumber,String unitId) {
    if (!chain.trip().vehicleNumber().equals(vehicleNumber) || !chain.notice().unitId().equals(unitId)) throw invalid();
  }

  private void requireWholeVehicle(VerifiedTenantContext c,Chain chain) {
    if (quotas.otherVehicleLoad(c,chain.notice().warehouseId(),chain.trip())) {
      throw new Rejection(409,"OUTBOUND_VEHICLE_MULTIPLE_LOADS");
    }
  }

  private static void requireExecutionTrip(TripState trip) {
    switch (CoilDispatchSteps.stage(trip.retired(), trip.quota().issued(), trip.recoveryClosed())) {
      case ALREADY_ISSUED -> throw new Rejection(409, "OUTBOUND_TRIP_ALREADY_ISSUED");
      case RECOVERY_CLOSED -> throw new Rejection(409, "OUTBOUND_RECOVERY_TRIP_CLOSED");
      case EXECUTABLE -> { }
    }
  }

  private static void requireOpenTrip(TripState trip) {
    if (CoilDispatchSteps.stage(trip.retired(), trip.quota().issued(), trip.recoveryClosed())
        == CoilDispatchSteps.Stage.ALREADY_ISSUED) throw new Rejection(409,"OUTBOUND_TRIP_ALREADY_ISSUED");
  }

  // 分摊顺序不是业务语义，摘要按配货编号排序，重量使用精确十进制文本。
  private static String weighingDigest(Weighing command) {
    var stable=new StringBuilder().append(command.ticketId()).append('|')
        .append(command.gross().stripTrailingZeros().toPlainString()).append('|')
        .append(command.tare().stripTrailingZeros().toPlainString());
    command.coils().stream().sorted(Comparator.comparing(WeighedCoil::allocationId)).forEach(coil ->
        stable.append('|').append(coil.allocationId()).append(':').append(coil.quantity().stripTrailingZeros().toPlainString()));
    return digest(stable.toString());
  }

  private static String digest(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private NoticeState lockNotice(VerifiedTenantContext c,String id) {
    uuid(id);
    var link=quotas.links(c,"notice",id);
    if (link==null) throw denied();
    masterData.requireWarehouse(c,link.warehouseId());
    quotas.guard(c,link.warehouseId());
    var notice=quotas.notice(c,id);
    if (notice==null) throw denied();
    masterData.owner(c,notice.ownerId());
    return notice;
  }

  private record Chain(NoticeState notice,AuthorizationState authorization,TripState trip) {}

  private Chain lockChain(VerifiedTenantContext c,String level,String id) {
    uuid(id);
    // 父引用不可修改，先定位再严格按父子顺序加锁。
    var link=quotas.links(c,level,id);
    if (link==null) throw denied();
    masterData.requireWarehouse(c,link.warehouseId());
    quotas.guard(c,link.warehouseId());
    var n=quotas.notice(c,link.noticeId());
    var a=quotas.authorization(c,link.authorizationId());
    var t="trip".equals(level) ? quotas.trip(c,id) : null;
    if (n==null || a==null || "trip".equals(level) && t==null) throw denied();
    masterData.owner(c,n.ownerId());
    return new Chain(n,a,t);
  }

  private void requireActive(VerifiedTenantContext c,AuthorizationState authorization) {
    if (!authorization.valid()) throw expired();
    masterData.owner(c,authorization.pickupPartnerId());
  }

  private static void requireScope(NoticeState n,OutboundStockStore.Facts stock) {
    if (!n.warehouseId().equals(stock.position().warehouseId())) throw denied();
    requireGoodsScope(n,stock);
  }

  private static void requireTitleAuthorization(AuthorizationState authorization,OutboundStockStore.Facts stock) {
    if (!TitleTransferRules.withinAuthorization(stock.titleRevision(), authorization.titleWatermark())) {
      throw new Rejection(409,"OUTBOUND_AUTHORIZATION_TITLE_CHANGED");
    }
  }

  private static void requireGoodsScope(NoticeState n,OutboundStockStore.Facts stock) {
    if (!n.ownerId().equals(stock.ownerId()) || !n.productId().equals(stock.productId())
        || !n.identityId().equals(stock.identityId())
        || !n.unitId().equals(stock.unitId())) throw denied();
  }

  private static Allocation result(Allocate command,String serial,BigDecimal quantity,Chain chain,
      BigDecimal parentDelta,BigDecimal tripDelta) {
    return new Allocation(command.id(),command.tripId(),command.receiptId(),serial,quantity.stripTrailingZeros(),
        chain.notice().quota().after(parentDelta),chain.authorization().quota().after(parentDelta),
        chain.trip().quota().after(tripDelta));
  }

  private static void requireSame(Allocated original,Allocate command) {
    if (original==null || !original.tripId().equals(command.tripId())
        || !original.receiptId().equals(command.receiptId())) throw conflict();
  }

  private static void validate(Allocate command) {
    if (command==null) throw invalid();
    uuid(command.id());
    uuid(command.tripId());
    uuid(command.receiptId());
  }

  private AccessPlan require(VerifiedTenantContext c,CoilOperations operation) {
    return require(c,operation.permission(),CoilOperations.RESOURCE);
  }

  private AccessPlan require(VerifiedTenantContext c,String permission,String resource) {
    if (c==null) throw denied();
    var plan=access.plan(Subject.tenant(c.accountId(),c.tenantId(),c.memberId()),permission,resource);
    if(!plan.tenantWide())throw denied();
    return plan;
  }

  /** 复合单据命令及需行锁的读取共享事务许可，业务仍复核全部引用。 */
  private <T> T write(AccessPlan plan,java.util.function.Supplier<T> work){
    return stock.transaction(() -> {
      writes.authorizeOperation(plan);
      return work.get();
    });
  }

  private static void checkAmount(Quota quota,BigDecimal delta,String level) {
    if (!DispatchQuota.allowsChange(quota.reserved(), quota.remaining(), delta)) throw limit(level);
  }

  private static Rejection limit(String level) { return new Rejection(409,"OUTBOUND_"+level+"_LIMIT"); }
  private static Rejection expired() { return new Rejection(409,"OUTBOUND_AUTHORIZATION_EXPIRED"); }

  private static void positive(BigDecimal quantity) {
    nonnegative(quantity);
    if (quantity.signum()==0) throw invalid();
  }

  private static void nonnegative(BigDecimal quantity) {
    if (quantity==null || quantity.signum()<0 || quantity.stripTrailingZeros().scale()>12
        || quantity.precision()-quantity.scale()>18) throw invalid();
  }
}
