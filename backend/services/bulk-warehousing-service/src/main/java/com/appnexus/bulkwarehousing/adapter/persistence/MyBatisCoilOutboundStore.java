package com.appnexus.bulkwarehousing.adapter.persistence;

import static com.appnexus.bulkwarehousing.adapter.persistence.MyBatisOutboundStockStore.*;

import com.appnexus.bulkwarehousing.api.CoilOutbound.*;
import com.appnexus.bulkwarehousing.application.CoilOutboundStore;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.util.Map;
import java.util.List;

/** 对外出仓额度存储映射，所有调用参加既有实物占用事务。 */
public final class MyBatisCoilOutboundStore implements CoilOutboundStore {
  private final CoilOutboundMapper mapper;
  private final CoilTitleMapper titles;

  /** @param mapper 固定出仓语句 @param titles 与过户共用的货权代次锁 */
  public MyBatisCoilOutboundStore(CoilOutboundMapper mapper,CoilTitleMapper titles) {
    this.mapper=mapper;
    this.titles=titles;
  }

  /** {@inheritDoc} */
  @Override public void guard(VerifiedTenantContext c,String warehouseId) {
    if (mapper.lockGuard(tenant(c),warehouseId)==null) {
      mapper.ensureGuard(tenant(c),actor(c),warehouseId);
      mapper.lockGuard(tenant(c),warehouseId);
    }
  }

  /** {@inheritDoc} */
  @Override public Links links(VerifiedTenantContext c,String level,String id) {
    var row=mapper.links(tenant(c),level,id);
    return row==null ? null : new Links(text(row,"noticeId"),text(row,"authorizationId"),text(row,"warehouseId"));
  }

  /** {@inheritDoc} */
  @Override public NoticeState notice(VerifiedTenantContext c,String id) {
    var row=mapper.notice(tenant(c),id);
    return row==null ? null : new NoticeState(id,text(row,"warehouseId"),text(row,"ownerId"),text(row,"productId"),
        text(row,"identityId"),text(row,"unitId"),quota(row),decimal(row,"committed"));
  }

  /** {@inheritDoc} */
  @Override public AuthorizationState authorization(VerifiedTenantContext c,String id) {
    var row=mapper.authorization(tenant(c),id);
    return row==null ? null : new AuthorizationState(id,text(row,"noticeId"),text(row,"pickupPartnerId"),flag(row,"valid"),flag(row,"closed"),
        quota(row),decimal(row,"unassigned"),((Number)row.get("titleWatermark")).longValue());
  }

  /** {@inheritDoc} */
  @Override public TripState trip(VerifiedTenantContext c,String id) {
    var row=mapper.trip(tenant(c),id);
    return row==null ? null : new TripState(id,text(row,"authorizationId"),text(row,"vehicleNumber"),
        quota(row),((Number)row.get("loadVersion")).longValue(),text(row,"reviewId"),text(row,"limitsEvidenceId"),
        decimal(row,"payloadLimit"),decimal(row,"grossLimit"),flag(row,"retired"),text(row,"recoveryReceiptId"),flag(row,"recoveryClosed"));
  }

  /** {@inheritDoc} */
  @Override public String serial(VerifiedTenantContext c,String receiptId) {
    return mapper.serial(tenant(c),receiptId);
  }

  /** {@inheritDoc} */
  @Override public void identify(VerifiedTenantContext c,String warehouseId,Coil coil) {
    mapper.identify(tenant(c),actor(c),warehouseId,coil);
  }

  /** {@inheritDoc} */
  @Override public void issue(VerifiedTenantContext c,Notice notice,int flowVersion) {
    mapper.issue(tenant(c),actor(c),notice,flowVersion);
  }

  /** {@inheritDoc} */
  @Override public int flowVersion(VerifiedTenantContext c,String id) {
    return mapper.flowVersion(tenant(c),id);
  }

  /** {@inheritDoc} */
  @Override public void authorize(VerifiedTenantContext c,Authorization authorization) {
    var tenant=tenant(c);
    var actor=actor(c);
    titles.ensure(tenant,actor);
    mapper.authorize(tenant,actor,authorization,titles.watermark(tenant));
    mapper.increaseCommitment(tenant,actor,authorization.noticeId(),authorization.cap());
  }

  /** {@inheritDoc} */
  @Override public void close(VerifiedTenantContext c,AuthorizationState authorization) {
    var tenant=tenant(c);
    var actor=actor(c);
    if (mapper.close(tenant,actor,authorization.id())!=1) {
      throw new com.appnexus.contracts.businessobject.BusinessObjects.Rejection(409,"OUTBOUND_REQUEST_CONFLICT");
    }
    var commitment=authorization.quota().cap().subtract(authorization.quota().issued());
    mapper.increaseCommitment(tenant,actor,authorization.noticeId(),commitment.negate());
  }

  /** {@inheritDoc} */
  @Override public void plan(VerifiedTenantContext c,Trip trip) {
    mapper.plan(tenant(c),actor(c),trip);
  }

  /** {@inheritDoc} */
  @Override public void adjust(VerifiedTenantContext c,String noticeId,String authorizationId,String tripId,
      BigDecimal parentDelta,BigDecimal tripDelta,BigDecimal unassignedDelta) {
    var tenant=tenant(c);
    var actor=actor(c);
    if (parentDelta.signum()!=0) mapper.noticeDelta(tenant,actor,noticeId,parentDelta);
    if (parentDelta.signum()!=0 || unassignedDelta.signum()!=0) {
      mapper.authorizationDelta(tenant,actor,authorizationId,parentDelta,unassignedDelta);
    }
    if (tripId!=null && tripDelta.signum()!=0) mapper.tripDelta(tenant,actor,tripId,tripDelta);
  }

  /** {@inheritDoc} */
  @Override public Allocated allocated(VerifiedTenantContext c,String id) {
    var row=mapper.allocated(tenant(c),id);
    return row==null ? null : new Allocated(id,text(row,"tripId"),text(row,"receiptId"),text(row,"serial"),
        decimal(row,"quantity"),flag(row,"released"));
  }

  /** {@inheritDoc} */
  @Override public void allocate(VerifiedTenantContext c,Allocate request,String serial,BigDecimal quantity) {
    mapper.allocate(tenant(c),actor(c),request,serial,quantity);
  }

  /** {@inheritDoc} */
  @Override public boolean otherVehicleLoad(VerifiedTenantContext c,String warehouseId,TripState trip) {
    return mapper.otherVehicleLoad(tenant(c),warehouseId,trip.vehicleNumber(),trip.id());
  }

  /** {@inheritDoc} */
  @Override public List<RecoveryOrigin> recoveryOrigins(VerifiedTenantContext c,String authorizationId,String warehouseId,
      String afterId,int limit) {
    return mapper.recoveryOrigins(tenant(c),authorizationId,warehouseId,afterId,limit).stream()
        .map(row -> new RecoveryOrigin(text(row,"receiptId"),text(row,"transitId"))).toList();
  }

  /** {@inheritDoc} */
  @Override public List<RecoveryEvent> recoveryEvents(VerifiedTenantContext c,String authorizationId,String receiptId) {
    return mapper.recoveryEvents(tenant(c),authorizationId,receiptId).stream().map(row -> new RecoveryEvent(
        text(row,"tripId"),text(row,"vehicleNumber"),decimal(row,"cap").stripTrailingZeros(),
        decimal(row,"reserved").stripTrailingZeros(),decimal(row,"issued").stripTrailingZeros(),text(row,"kind"),
        text(row,"id"),text(row,"evidenceId"),text(row,"occurredAt"))).toList();
  }

  /** {@inheritDoc} */
  @Override public String activeRecoveryTrip(VerifiedTenantContext c,String authorizationId,String receiptId) {
    return mapper.activeRecoveryTrip(tenant(c),authorizationId,receiptId);
  }

  /** {@inheritDoc} */
  @Override public void closeRecoveryTrip(VerifiedTenantContext c,String tripId) {
    mapper.closeRecoveryTrip(tenant(c),actor(c),tripId);
  }

  /** {@inheritDoc} */
  @Override public List<Allocated> activeAllocations(VerifiedTenantContext c,String tripId) {
    return mapper.activeAllocations(tenant(c),tripId).stream().map(row -> new Allocated(text(row,"id"),tripId,
        text(row,"receiptId"),text(row,"serial"),decimal(row,"quantity"),false)).toList();
  }

  /** {@inheritDoc} */
  @Override public void invalidateReview(VerifiedTenantContext c,String tripId) {
    mapper.invalidateReview(tenant(c),actor(c),tripId);
  }

  /** {@inheritDoc} */
  @Override public void vehicleLimits(VerifiedTenantContext c,VehicleLimits limits) {
    mapper.vehicleLimits(tenant(c),actor(c),limits);
  }

  /** {@inheritDoc} */
  @Override public RecordedReview review(VerifiedTenantContext c,String id) {
    var row=mapper.findReview(tenant(c),id);
    if (row==null) return null;
    var reasons=text(row,"reasons");
    return new RecordedReview(new Review(id,text(row,"tripId"),ReviewState.valueOf(text(row,"state")),
        decimal(row,"net").stripTrailingZeros(),reasons.isEmpty() ? List.of() : List.of(reasons.split(",")),
        ((Number)row.get("loadVersion")).longValue()),text(row,"digest"),decimal(row,"gross"));
  }

  /** {@inheritDoc} */
  @Override public void review(VerifiedTenantContext c,Weighing input,Review result,String digest) {
    var tenant=tenant(c);
    var actor=actor(c);
    mapper.insertReview(tenant,actor,input,result,String.join(",",result.reasons()),digest);
    for (var coil:input.coils()) mapper.insertWeighLine(tenant,actor,result.id(),coil);
    if (mapper.currentReview(tenant,actor,result)!=1) {
      throw new com.appnexus.contracts.businessobject.BusinessObjects.Rejection(409,"OUTBOUND_LOAD_CHANGED");
    }
  }

  /** {@inheritDoc} */
  @Override public void applyMeasured(VerifiedTenantContext c,WeighedCoil coil) {
    mapper.applyMeasured(tenant(c),actor(c),coil);
  }

  /** {@inheritDoc} */
  @Override public PostedDispatch dispatch(VerifiedTenantContext c,String id) {
    var row=mapper.findDispatch(tenant(c),id);
    if (row==null) return null;
    var result=new Dispatch(id,text(row,"tripId"),decimal(row,"issued").stripTrailingZeros(),
        decimal(row,"deduction").stripTrailingZeros(),text(row,"unitId"));
    return new PostedDispatch(result,text(row,"digest"),flag(row,"reversed"));
  }

  /** {@inheritDoc} */
  @Override public void dispatch(VerifiedTenantContext c,Handover request,Dispatch result,String digest,
      NoticeState notice,AuthorizationState authorization,List<DispatchedCoil> coils) {
    var tenant=tenant(c);
    var actor=actor(c);
    mapper.postDispatch(tenant,actor,request,result,digest);
    for (var coil:coils) {
      var allocation=coil.allocation();
      var id=allocation.receiptId();
      var book=coil.bookQuantity();
      mapper.postDispatchLine(tenant,actor,result.id(),allocation.id(),id,coil.sourceWarehouseId(),coil.sourceLocationId(),allocation.quantity(),book);
      if (mapper.deductStock(tenant,actor,id,book)!=1 || mapper.deductTitle(tenant,actor,id,notice.ownerId(),book)!=1) {
        throw new com.appnexus.contracts.businessobject.BusinessObjects.Rejection(409,"OUTBOUND_STOCK_NOT_READY");
      }
    }
    var qty=result.issuedQuantity();
    if (mapper.issueNotice(tenant,actor,notice.id(),qty)!=1
        || mapper.issueAuthorization(tenant,actor,authorization.id(),qty)!=1
        || mapper.issueTrip(tenant,actor,result.tripId(),qty)!=1) {
      throw new com.appnexus.contracts.businessobject.BusinessObjects.Rejection(409,"OUTBOUND_ISSUE_CONFLICT");
    }
  }

  /** {@inheritDoc} */
  @Override public List<IssuedCoil> issuedCoils(VerifiedTenantContext c,String id) {
    return mapper.issuedCoils(tenant(c),id).stream().map(row -> new IssuedCoil(
        text(row,"allocationId"),text(row,"receiptId"),decimal(row,"issued"),decimal(row,"deduction"))).toList();
  }

  /** {@inheritDoc} */
  @Override public ReturnEvidence acceptedReturn(VerifiedTenantContext c,String id) {
    var row=mapper.findReturn(tenant(c),id);
    return row==null ? null : new ReturnEvidence(text(row,"handoverId"),text(row,"digest"));
  }

  /** {@inheritDoc} */
  @Override public String acceptedReturnFor(VerifiedTenantContext c,String id) {
    return mapper.returnFor(tenant(c),id);
  }

  /** {@inheritDoc} */
  @Override public void acceptReturn(VerifiedTenantContext c,AcceptedReturn request,String digest) {
    var tenant=tenant(c);
    var actor=actor(c);
    mapper.insertReturn(tenant,actor,request,digest);
    for (var coil:request.coils()) mapper.insertReturnLine(tenant,actor,request.id(),coil);
  }

  /** {@inheritDoc} */
  @Override public PostedReversal reversal(VerifiedTenantContext c,String id) {
    var row=mapper.findReversal(tenant(c),id);
    if (row==null) return null;
    var result=new Reversal(id,text(row,"handoverId"),ReversalKind.valueOf(text(row,"kind")),
        decimal(row,"quota").stripTrailingZeros(),decimal(row,"stock").stripTrailingZeros(),
        decimal(row,"reservation").stripTrailingZeros(),text(row,"unitId"));
    return new PostedReversal(result,text(row,"digest"));
  }

  /** {@inheritDoc} */
  @Override public void reverse(VerifiedTenantContext c,ReverseHandover request,Reversal result,String digest,
      NoticeState notice,AuthorizationState authorization,TripState trip,List<IssuedCoil> coils) {
    var tenant=tenant(c);
    var actor=actor(c);
    mapper.insertReversal(tenant,actor,request,result,digest);
    for (var coil:coils) {
      if (mapper.restoreStock(tenant,actor,coil.receiptId(),coil.book())!=1
          || mapper.restoreTitle(tenant,actor,coil.receiptId(),notice.ownerId(),coil.book())!=1) throw reversalConflict();
    }
    var quantity=result.restoredQuota();
    var hold=result.restoredReservation();
    var commitment=authorization.closed() ? quantity : BigDecimal.ZERO;
    if (mapper.restoreNoticeQuota(tenant,actor,notice.id(),quantity,hold,commitment)!=1
        || mapper.restoreAuthorizationQuota(tenant,actor,authorization.id(),quantity,hold)!=1
        || mapper.restoreTripQuota(tenant,actor,trip.id(),quantity,hold)!=1
        || mapper.reverseLines(tenant,actor,request.handoverId())!=coils.size()
        || mapper.reverseHeader(tenant,actor,request.handoverId())!=1) throw reversalConflict();
  }

  private static com.appnexus.contracts.businessobject.BusinessObjects.Rejection reversalConflict() {
    return new com.appnexus.contracts.businessobject.BusinessObjects.Rejection(409,"OUTBOUND_REVERSAL_CONFLICT");
  }

  /** {@inheritDoc} */
  @Override public PostedPartialReturn partialReturn(VerifiedTenantContext c,String id) {
    var row=mapper.findPartialReturn(tenant(c),id);
    if (row==null) return null;
    return new PostedPartialReturn(new PartialReturnResult(id,text(row,"handoverId"),
        decimal(row,"quota").stripTrailingZeros(),decimal(row,"stock").stripTrailingZeros(),text(row,"unitId")),
        text(row,"digest"));
  }

  /** {@inheritDoc} */
  @Override public PartialReturnResult legacyReturnDocument(VerifiedTenantContext c,String id) {
    var row=mapper.legacyReturnDocument(tenant(c),id);
    return row==null ? null : new PartialReturnResult(id,text(row,"handoverId"),
        decimal(row,"quota"),decimal(row,"stock"),text(row,"unitId"));
  }

  /** {@inheritDoc} */
  @Override public List<ReturnDocumentLine> returnDocumentLines(VerifiedTenantContext c,String id) {
    return mapper.returnDocumentLines(tenant(c),id).stream().map(row->new ReturnDocumentLine(
        text(row,"receiptId"),text(row,"warehouseId"),text(row,"transitId"),text(row,"qualityEvidenceId"),
        decimal(row,"quantity"))).toList();
  }

  /** {@inheritDoc} */
  @Override public List<IssuedCoil> activeIssuedCoils(VerifiedTenantContext c,String id) {
    return mapper.activeIssuedCoils(tenant(c),id).stream().map(row -> new IssuedCoil(
        text(row,"allocationId"),text(row,"receiptId"),decimal(row,"issued"),decimal(row,"deduction"))).toList();
  }

  /** {@inheritDoc} */
  @Override public void returnCoils(VerifiedTenantContext c,PartialReturn request,PartialReturnResult result,String digest,
      NoticeState notice,AuthorizationState authorization,TripState trip,List<ReturnedLine> coils) {
    var tenant=tenant(c);
    var actor=actor(c);
    mapper.insertPartialReturn(tenant,actor,request,result,digest);
    for (var row:coils) {
      var coil=row.issued();
      var position=row.destination();
      mapper.insertPartialReturnLine(tenant,actor,request,coil,position.warehouseId(),position.locationId());
      if (!notice.warehouseId().equals(position.warehouseId())
          && mapper.moveCoilWarehouse(tenant,actor,coil.receiptId(),notice.warehouseId(),position.warehouseId())!=1) {
        throw reversalConflict();
      }
      if (mapper.restorePendingStock(tenant,actor,coil.receiptId(),coil.book(),position)!=1
          || mapper.restoreTitle(tenant,actor,coil.receiptId(),notice.ownerId(),coil.book())!=1
          || mapper.reverseSingleLine(tenant,actor,request.handoverId(),coil.allocationId())!=1) throw reversalConflict();
    }
    var quantity=result.restoredQuota();
    var commitment=authorization.closed() ? quantity : BigDecimal.ZERO;
    if (mapper.restoreNoticeQuota(tenant,actor,notice.id(),quantity,BigDecimal.ZERO,commitment)!=1
        || mapper.restoreAuthorizationQuota(tenant,actor,authorization.id(),quantity,BigDecimal.ZERO)!=1
        || mapper.subtractTripIssued(tenant,actor,trip.id(),quantity)!=1) throw reversalConflict();
    if (trip.quota().issued().compareTo(quantity)==0
        && mapper.reverseHeader(tenant,actor,request.handoverId())!=1) throw reversalConflict();
    if (request.transitId()!=null) {
      for (var row:coils) {
        if (mapper.receiveTransitLine(tenant,actor,request,row.issued().receiptId())!=1) throw reversalConflict();
      }
    }
  }

  /** {@inheritDoc} */
  @Override public ReturnedStock returnedStock(VerifiedTenantContext c,String returnId,String receiptId) {
    var row=mapper.findReturnedStock(tenant(c),returnId,receiptId);
    return row==null ? null : new ReturnedStock(decimal(row,"book"),text(row,"qualityEvidence"),
        text(row,"warehouseId"),text(row,"locationId"));
  }

  /** {@inheritDoc} */
  @Override public void releaseReturnedQuality(VerifiedTenantContext c,ReturnedQuality request,ReturnedStock stock) {
    var tenant=tenant(c);
    var actor=actor(c);
    if (mapper.historicalQualityEvidence(tenant,request)) {
      throw new com.appnexus.contracts.businessobject.BusinessObjects.Rejection(409,"OUTBOUND_RETURN_QUALITY_EVIDENCE_REUSED");
    }
    if (mapper.markReturnedQuality(tenant,actor,request)!=1
        || mapper.clearReturnedPending(tenant,actor,request.receiptId(),stock.book())!=1) throw reversalConflict();
  }

  /** {@inheritDoc} */
  @Override public Transit transit(VerifiedTenantContext c,String id) {
    var row=mapper.findTransit(tenant(c),id);
    return row==null ? null : new Transit(id,text(row,"handoverId"),text(row,"warehouseId"),text(row,"evidenceId"),text(row,"digest"));
  }

  /** {@inheritDoc} */
  @Override public List<TransitEvidence> transitEvidence(VerifiedTenantContext c,String id) {
    return mapper.transitEvidence(tenant(c),id).stream().map(row -> new TransitEvidence(
        text(row,"kind"),text(row,"id"),text(row,"receiptId"),text(row,"evidenceId"),text(row,"titleEvidenceId"),
        text(row,"previousWarehouseId"),text(row,"warehouseId"),text(row,"reason"),text(row,"qualityEvidenceId"))).toList();
  }

  /** {@inheritDoc} */
  @Override public List<TransitCoil> transitCoils(VerifiedTenantContext c,String id) {
    return mapper.transitCoils(tenant(c),id).stream().map(row -> {
      var received=text(row,"returnId");
      var loss=text(row,"lossId");
      var state=received!=null ? TransitState.RECEIVED : loss!=null ? TransitState.LOSS_SETTLED : TransitState.IN_TRANSIT;
      return new TransitCoil(text(row,"receiptId"),text(row,"serial"),decimal(row,"book").stripTrailingZeros(),
          decimal(row,"measured").stripTrailingZeros(),received,loss,state,text(row,"warehouseId"),text(row,"rerouteId"));
    }).toList();
  }

  /** {@inheritDoc} */
  @Override public TransitLink transitLink(VerifiedTenantContext c,String handoverId,String receiptId) {
    var row=mapper.findTransitLink(tenant(c),handoverId,receiptId);
    return row==null ? null : new TransitLink(text(row,"transitId"),text(row,"returnId"),text(row,"lossId"),
        text(row,"warehouseId"),text(row,"rerouteId"));
  }

  /** {@inheritDoc} */
  @Override public boolean hasTransit(VerifiedTenantContext c,String handoverId) {
    return mapper.hasTransit(tenant(c),handoverId);
  }

  /** {@inheritDoc} */
  @Override public void dispatchReturn(VerifiedTenantContext c,ReturnDispatch request,String digest,List<IssuedCoil> coils) {
    var tenant=tenant(c);
    var actor=actor(c);
    mapper.insertTransit(tenant,actor,request,digest);
    for (var coil:coils) mapper.insertTransitLine(tenant,actor,request,coil.allocationId(),coil.receiptId());
  }

  /** {@inheritDoc} */
  @Override public PostedLoss transitLoss(VerifiedTenantContext c,String id) {
    var row=mapper.findTransitLoss(tenant(c),id);
    if (row==null) return null;
    return new PostedLoss(new TransitLoss(id,text(row,"transitId"),text(row,"receiptId"),
        text(row,"responsiblePartnerId"),text(row,"evidenceId"),text(row,"reason"),
        decimal(row,"book").stripTrailingZeros(),decimal(row,"measured").stripTrailingZeros()),text(row,"digest"));
  }

  /** {@inheritDoc} */
  @Override public void settleTransitLoss(VerifiedTenantContext c,TransitLoss loss,String digest) {
    var tenant=tenant(c);
    var actor=actor(c);
    mapper.insertTransitLoss(tenant,actor,loss,digest);
    if (mapper.markTransitLoss(tenant,actor,loss)!=1) throw reversalConflict();
  }

  /** {@inheritDoc} */
  @Override public PostedReopening transitReopening(VerifiedTenantContext c,String id) {
    var row=mapper.findTransitReopening(tenant(c),id);
    return row==null ? null : new PostedReopening(text(row,"lossId"),text(row,"transitId"),text(row,"evidenceId"));
  }

  /** {@inheritDoc} */
  @Override public void reopenTransitLoss(VerifiedTenantContext c,ReopenTransitLoss command,String receiptId) {
    var tenant=tenant(c);
    var actor=actor(c);
    mapper.insertTransitReopening(tenant,actor,command,receiptId);
    if (mapper.clearTransitLoss(tenant,actor,command,receiptId)!=1) throw reversalConflict();
  }

  /** {@inheritDoc} */
  @Override public PostedReroute transitReroute(VerifiedTenantContext c,String id) {
    var row=mapper.findTransitReroute(tenant(c),id);
    if (row==null) return null;
    var result=new RerouteTransitCoil(id,text(row,"transitId"),text(row,"receiptId"),
        text(row,"previousId"),text(row,"receivingId"),text(row,"evidenceId"),text(row,"reason"));
    return new PostedReroute(result,text(row,"digest"));
  }

  /** {@inheritDoc} */
  @Override public void rerouteTransitCoil(VerifiedTenantContext c,RerouteTransitCoil request,String digest,String expectedRerouteId) {
    var tenant=tenant(c);
    var actor=actor(c);
    mapper.insertTransitReroute(tenant,actor,request,digest);
    if (mapper.applyTransitReroute(tenant,actor,request,expectedRerouteId)!=1) throw reversalConflict();
  }

  private static Quota quota(Map<String,Object> row) {
    return new Quota(decimal(row,"cap"),decimal(row,"issued"),decimal(row,"reserved"));
  }

  private static BigDecimal decimal(Map<String,Object> row,String field) {
    return (BigDecimal)row.get(field);
  }
}
