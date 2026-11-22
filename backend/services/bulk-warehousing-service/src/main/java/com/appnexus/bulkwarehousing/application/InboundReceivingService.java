package com.appnexus.bulkwarehousing.application;

import com.appnexus.bulkwarehousing.domain.evidence.MeasurementAllocation;
import com.appnexus.bulkwarehousing.domain.title.TitleTransferRules;
import com.appnexus.bulkwarehousing.domain.process.WarehouseFlowTemplate;
import java.util.Set;
import com.appnexus.bulkwarehousing.domain.receiving.ReceivingLimit;
import com.appnexus.bulkwarehousing.api.InboundOperations;
import com.appnexus.bulkwarehousing.api.InboundDisposition;
import com.appnexus.bulkwarehousing.api.InboundReceiptReferences;
import com.appnexus.bulkwarehousing.api.InboundReceiving;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.authorization.AccessPlanning;
import com.appnexus.platform.authorization.AccessPlan;
import com.appnexus.platform.authorization.ScopedWriteGate;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** 进仓接管与实收编排。按通知聚合串行计算限额，实收与待检过账同事务提交。 */
public final class InboundReceivingService implements InboundReceiving, InboundDisposition, com.appnexus.contracts.businessobject.BusinessObjects.DataAccessExecutor {
  private final InboundReceivingStore store;
  private final InboundReferenceSource source;
  private final InboundReceiptReferences references;
  private final AccessPlanning access;
  private final ScopedWriteGate writes;

  /**
   * @param store 入仓写入及事务端口
   * @param source 已授权的单项主数据读取
   * @param references 已验证的实收入账引用核验
   * @param access 现有平台权限计划
   * @param writes 在单据写事务中绑定目标记录的公共许可
   */
  public InboundReceivingService(InboundReceivingStore store, InboundReferenceSource source,
      InboundReceiptReferences references, AccessPlanning access, ScopedWriteGate writes) {
    this.store = store;
    this.source = source;
    this.references = references;
    this.access = access;
    this.writes = writes;
  }

  /** {@inheritDoc} */
  @Override public java.util.Set<String> enforcedResources() {
    return java.util.Arrays.stream(InboundOperations.values()).map(InboundOperations::resource)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  /** {@inheritDoc} */
  @Override
  public void issue(VerifiedTenantContext context, Notice notice) {
    var plan=require(context, InboundOperations.ISSUE);
    if (notice == null || notice.item() == null || notice.item().kind() == null) throw invalid();
    uuid(notice.id());
    uuid(notice.warehouseId());
    uuid(notice.ownerPartnerId());
    positiveQuantity(notice.limit());
    var template = WarehouseFlowTemplate.STEEL_CATALOG.select("receiving", null);
    if (!template.permits(WarehouseFlowTemplate.Step.NOTICE, Set.of())) throw invalid();
    authorized(plan,() -> {
      source.requireWarehouse(context, notice.warehouseId());
      source.owner(context, notice.ownerPartnerId());
      var item = source.identity(context, notice.item());
      if (!ReceivingLimit.acceptsNoticeUnit(item.baseUnitId(), notice.baseUnitId())) throw invalid();
      writes.authorize(plan,UUID.fromString(notice.id()));
      store.issue(context, notice,template.version());
      return null;
    });
  }

  /** {@inheritDoc} */
  @Override
  public void takeCustody(VerifiedTenantContext context, Custody custody) {
    var plan=require(context, InboundOperations.TAKE_CUSTODY);
    if (custody == null || custody.position() == null) throw invalid();
    uuid(custody.id());
    uuid(custody.noticeId());
    uuid(custody.evidenceId());
    authorized(plan,() -> {
      var notice = lockNotice(context, custody.noticeId()).notice();
      source.owner(context, notice.ownerPartnerId());
      var position = source.position(context, custody.position());
      if (!ReceivingLimit.allowsCustody(notice.warehouseId(), position.warehouseId())) throw invalid();
      var previous = store.custody(context, custody.id());
      if (previous == null) {
        var template=WarehouseFlowTemplate.STEEL_CATALOG.resume(
            WarehouseFlowTemplate.RECEIVING_V1.code(),store.flowVersion(context,notice.id()));
        if (!template.permits(WarehouseFlowTemplate.Step.CUSTODY,Set.of(WarehouseFlowTemplate.Step.NOTICE))) {
          throw conflict("INBOUND_FLOW_STEP_NOT_ALLOWED");
        }
        writes.authorize(plan,UUID.fromString(custody.id()));
        store.takeCustody(context, custody);
      }
      else if (!previous.equals(custody)) throw conflict("INBOUND_REQUEST_CONFLICT");
      return null;
    });
  }

  /** {@inheritDoc} */
  @Override
  public Posting receive(VerifiedTenantContext context, Receipt receipt) {
    var plan=require(context, InboundOperations.RECEIVE);
    if (receipt == null || receipt.references() == null) throw invalid();
    uuid(receipt.id());
    uuid(receipt.custodyId());
    uuid(receipt.measurementId());
    return authorized(plan,() -> receiveInTransaction(context, receipt, plan));
  }

  private Posting receiveInTransaction(VerifiedTenantContext context, Receipt receipt, AccessPlan plan) {
    var custody = store.custody(context, receipt.custodyId());
    if (custody == null) throw denied();
    var confirmed = Set.of(WarehouseFlowTemplate.Step.CUSTODY, WarehouseFlowTemplate.Step.NOTICE);
    if (!WarehouseFlowTemplate.STEEL_CATALOG.resume(
        WarehouseFlowTemplate.RECEIVING_V1.code(), store.flowVersion(context,custody.noticeId())).permits(WarehouseFlowTemplate.Step.RECEIVE, confirmed)) {
      throw denied();
    }
    var locked = lockNotice(context, custody.noticeId());
    var notice = locked.notice();
    // 每次重试仍执行当前仓库与货主可见性，禁止凭幂等键取得越权结果。
    source.requireWarehouse(context, notice.warehouseId());
    source.owner(context, notice.ownerPartnerId());
    var requested = receipt.references();
    if (!samePosition(custody.position(), requested.position())
        || !notice.item().equals(requested.item())
        || !notice.ownerPartnerId().equals(requested.ownerPartnerId())) throw invalid();

    String digest = digest(receipt);
    var previous = store.posted(context, custody.id());
    if (previous != null) {
      if (!digest.equals(previous.digest())) throw conflict("INBOUND_REQUEST_CONFLICT");
      // 幂等重放仍需本次商品可见性；无需重验原过账采用的历史版本。
      source.requireProduct(context,notice.item().productId());
      return previous.posting();
    }
    var verified = references.resolve(context, requested);
    var quantity = verified.quantity();
    if (!notice.baseUnitId().equals(quantity.baseUnitId())) throw invalid();
    positiveQuantity(quantity.baseQuantity());
    if (!ReceivingLimit.allows(locked.received(), quantity.baseQuantity(), notice.limit())) {
      throw conflict("INBOUND_LIMIT_EXCEEDED");
    }
    var posting = new Posting(receipt.id(), custody.id(), quantity.baseQuantity().stripTrailingZeros(), quantity.baseUnitId());
    writes.authorize(plan,UUID.fromString(receipt.id()));
    writes.authorize(plan,UUID.fromString(notice.id()));
    store.post(context, receipt, posting, notice.id(), digest, quantity.toString());
    return posting;
  }

  /** {@inheritDoc} */
  @Override public Status registerTitle(VerifiedTenantContext context, TitleDecision decision) {
    var plan=require(context, InboundOperations.REGISTER_TITLE);
    if (decision==null || decision.effectiveAt()==null) throw invalid();
    uuid(decision.receiptId());
    uuid(decision.evidenceId());
    var effectiveAt=decision.effectiveAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    if (!TitleTransferRules.registrationEffectiveAt(effectiveAt,java.time.Instant.now())) throw invalid();
    return authorized(plan,() -> {
      var stock=authorizedStock(context,decision.receiptId());
      switch (TitleTransferRules.registration(stock.titleEvidence(),decision.evidenceId(),
          stock.titleEffectiveAt(),effectiveAt)) {
        case APPLY -> {
          writes.authorize(plan,UUID.fromString(stock.receiptId()));
          requireReceiptStep(context,stock.receiptId(),WarehouseFlowTemplate.Step.REGISTER_TITLE);
          store.registerTitle(context,stock,decision.evidenceId(),effectiveAt);
        }
        case REPLAY -> { }
        case CONFLICT -> throw conflict("INBOUND_TITLE_CONFLICT");
      }
      return new Status(stock.receiptId(),stock.inbound(),stock.pending(),stock.inbound(),stock.unitId());
    });
  }

  /** {@inheritDoc} */
  @Override public Status releaseQuality(VerifiedTenantContext context, Decision decision) {
    var plan=require(context, InboundOperations.RELEASE_QUALITY);
    validate(decision);
    return authorized(plan,() -> {
      var stock=authorizedStock(context,decision.receiptId());
      switch (MeasurementAllocation.intakeQuality(stock.qualityEvidence(),decision.evidenceId(),stock.pending())) {
        case APPLY -> {
          writes.authorize(plan,UUID.fromString(stock.receiptId()));
          requireReceiptStep(context,stock.receiptId(),WarehouseFlowTemplate.Step.RELEASE_QUALITY);
          store.releaseQuality(context,stock,decision.evidenceId());
        }
        case REPLAY -> { }
        case RETURN_REVIEW_REQUIRED -> throw conflict("INBOUND_RETURN_QUALITY_REQUIRED");
        case CONFLICT -> throw conflict("INBOUND_QUALITY_CONFLICT");
      }
      var registered=stock.titleEvidence()==null ? BigDecimal.ZERO : stock.inbound();
      return new Status(stock.receiptId(),stock.inbound(),BigDecimal.ZERO,registered,stock.unitId());
    });
  }

  private void requireReceiptStep(VerifiedTenantContext context,String receiptId,WarehouseFlowTemplate.Step step) {
    var template=WarehouseFlowTemplate.STEEL_CATALOG.resume(
        WarehouseFlowTemplate.RECEIVING_V1.code(),store.receiptFlowVersion(context,receiptId));
    if (!template.permits(step,Set.of(WarehouseFlowTemplate.Step.RECEIVE))) {
      throw conflict("INBOUND_FLOW_STEP_NOT_ALLOWED");
    }
  }

  private InboundReceivingStore.Stock authorizedStock(VerifiedTenantContext context,String receiptId) {
    var stock=store.lockStock(context,receiptId);
    if (stock==null) throw denied();
    source.requireWarehouse(context,stock.warehouseId());
    source.owner(context,stock.ownerId());
    return stock;
  }

  private static void validate(Decision decision) {
    if (decision==null) throw invalid();
    uuid(decision.receiptId());
    uuid(decision.evidenceId());
  }

  private static boolean samePosition(InboundReceiptReferences.Position first,
      InboundReceiptReferences.Position second) {
    return second != null && first.warehouseId().equals(second.warehouseId())
        && first.areaId().equals(second.areaId()) && first.locationId().equals(second.locationId());
  }

  private InboundReceivingStore.LockedNotice lockNotice(VerifiedTenantContext context, String id) {
    var locked = store.lockNotice(context, id);
    if (locked == null) throw denied();
    return locked;
  }

  /** 在固定范围命令的真实事务中绑定操作，读取及写入均由 Mapper 复核。 */
  private <T>T authorized(AccessPlan plan,java.util.function.Supplier<T> work){
    return store.transaction(()->{
      writes.authorizeOperation(plan);
      return work.get();
    });
  }

  private AccessPlan require(VerifiedTenantContext context, InboundOperations operation) {
    if (context == null) throw denied();
    var subject = AccessAuthorization.Subject.tenant(context.accountId(), context.tenantId(), context.memberId());
    var plan=access.plan(subject, operation.permission(), operation.resource());
    if (!plan.tenantWide()) throw denied();
    return plan;
  }

  // 幂等摘要绑定接管、计量证据、数量与全部引用版本，避免同键修改原确认。
  private static String digest(Receipt receipt) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(receipt.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static void uuid(String value) {
    try {
      if (!UUID.fromString(value).toString().equals(value)) throw invalid();
    } catch (IllegalArgumentException | NullPointerException error) {
      throw invalid();
    }
  }

  private static void positiveQuantity(BigDecimal quantity) {
    if (quantity == null || quantity.signum() <= 0 || quantity.stripTrailingZeros().scale() > 12
        || quantity.precision() - quantity.scale() > 18) throw invalid();
  }

  private static Rejection invalid() { return new Rejection(400, "INBOUND_INPUT_INVALID"); }
  private static Rejection denied() { return new Rejection(403, "INBOUND_ACCESS_DENIED"); }
  private static Rejection conflict(String code) { return new Rejection(409, code); }
}
