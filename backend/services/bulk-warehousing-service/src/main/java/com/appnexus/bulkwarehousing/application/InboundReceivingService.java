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
    if (notice==null) throw invalid();
    uuid(notice.id());
    uuid(notice.warehouseId());
    uuid(notice.ownerPartnerId());
    String number=notice.noticeNo()==null ? notice.id() : notice.noticeNo();
    if (!number.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
        || (number.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
            && !number.equalsIgnoreCase(notice.id()))) throw invalid();
    validateText(notice.remark(),500);
    var lines=notice.lines();
    boolean legacy=lines==null;
    if (legacy) {
      lines=java.util.List.of(new NoticeLine(notice.id(),notice.item(),notice.baseUnitId(),notice.limit(),null,null));
    } else if (notice.item()!=null || notice.baseUnitId()!=null || notice.limit()!=null || lines.isEmpty() || lines.size()>100) {
      throw invalid();
    }
    var lineIds=new java.util.HashSet<String>();
    for (var line:lines) {
      if (line==null || line.item()==null || line.item().kind()==null) throw invalid();
      uuid(line.id());
      uuid(line.baseUnitId());
      if (!lineIds.add(line.id())) throw invalid();
      positiveQuantity(line.limit());
      if (line.plannedQuantity()!=null) positiveQuantity(line.plannedQuantity());
      validateText(line.remark(),500);
    }
    var command=new Notice(notice.id(),notice.warehouseId(),notice.ownerPartnerId(),
        legacy ? notice.item() : null,legacy ? notice.baseUnitId() : null,legacy ? notice.limit() : null,
        number,notice.expectedArrivalAt(),notice.remark(),java.util.List.copyOf(lines));
    var template = WarehouseFlowTemplate.STEEL_CATALOG.select("receiving", null);
    if (!template.permits(WarehouseFlowTemplate.Step.NOTICE, Set.of())) throw invalid();
    authorized(plan,() -> {
      source.requireWarehouse(context, notice.warehouseId());
      source.owner(context, notice.ownerPartnerId());
      for (var line:command.lines()) {
        var item=source.identity(context,line.item());
        if (!ReceivingLimit.acceptsNoticeUnit(item.baseUnitId(),line.baseUnitId())) throw invalid();
      }
      writes.authorize(plan,UUID.fromString(notice.id()));
      store.issue(context,command,template.version());
      return null;
    });
  }

  /** {@inheritDoc} */
  @Override
  public void takeCustody(VerifiedTenantContext context, Custody custody) {
    var plan=require(context, InboundOperations.TAKE_CUSTODY);
    if (custody!=null && (custody.entryId()!=null || custody.expectedQuantity()!=null)) throw invalid();
    takeCustody(context,custody,plan);
  }

  private void takeCustody(VerifiedTenantContext context,Custody custody,AccessPlan plan) {
    if (custody == null || custody.position() == null) throw invalid();
    uuid(custody.id());
    uuid(custody.noticeId());
    uuid(custody.evidenceId());
    if (custody.noticeLineId()!=null) uuid(custody.noticeLineId());
    validateText(custody.arrivalBatchNo(),64);
    authorized(plan,() -> {
      var locked=lockNotice(context,custody.noticeId(),custody.noticeLineId());
      var notice=locked.notice();
      var normalized=new Custody(custody.id(),custody.noticeId(),custody.position(),custody.evidenceId(),
          locked.lineId(),custody.arrivalBatchNo(),custody.entryId(),normalizedAmount(custody.expectedQuantity()));
      source.owner(context, notice.ownerPartnerId());
      var position = source.position(context, custody.position());
      if (!ReceivingLimit.allowsCustody(notice.warehouseId(), position.warehouseId())) throw invalid();
      var previous = store.custody(context, custody.id());
      if (previous == null) {
        requireReceiving(locked.status());
        if (locked.received().compareTo(notice.limit())>=0) throw conflict("INBOUND_LIMIT_EXCEEDED");
        var template=WarehouseFlowTemplate.STEEL_CATALOG.resume(
            WarehouseFlowTemplate.RECEIVING_V1.code(),store.flowVersion(context,notice.id()));
        if (!template.permits(WarehouseFlowTemplate.Step.CUSTODY,Set.of(WarehouseFlowTemplate.Step.NOTICE))) {
          throw conflict("INBOUND_FLOW_STEP_NOT_ALLOWED");
        }
        writes.authorize(plan,UUID.fromString(custody.id()));
        writes.authorize(plan,UUID.fromString(notice.id()));
        store.takeCustody(context,normalized);
      }
      else if (!previous.equals(normalized)) throw conflict("INBOUND_REQUEST_CONFLICT");
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
    String batch=receipt.receiptBatchNo();
    if (batch!=null && (batch.isBlank() || !batch.equals(batch.strip())
        || batch.codePointCount(0,batch.length())>64 || batch.codePoints().anyMatch(Character::isISOControl))) {
      throw invalid();
    }
    return authorized(plan,() -> receiveInTransaction(context,receipt,plan,null));
  }

  private Posting receiveInTransaction(VerifiedTenantContext context,Receipt receipt,AccessPlan plan,String entryId) {
    var custody = store.custody(context, receipt.custodyId());
    if (custody == null) throw denied();
    if (!java.util.Objects.equals(custody.entryId(),entryId)) throw conflict("INBOUND_ENTRY_REQUIRED");
    var confirmed = Set.of(WarehouseFlowTemplate.Step.CUSTODY, WarehouseFlowTemplate.Step.NOTICE);
    if (!WarehouseFlowTemplate.STEEL_CATALOG.resume(
        WarehouseFlowTemplate.RECEIVING_V1.code(), store.flowVersion(context,custody.noticeId())).permits(WarehouseFlowTemplate.Step.RECEIVE, confirmed)) {
      throw denied();
    }
    var locked = lockNotice(context,custody.noticeId(),custody.noticeLineId());
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
    requireReceiving(locked.status());
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
    writes.authorize(plan,UUID.fromString(locked.lineId()));
    store.post(context,receipt,posting,notice.id(),locked.lineId(),digest,quantity.toString());
    return posting;
  }

  /** {@inheritDoc} */
  @Override public void recordEntry(VerifiedTenantContext context,Entry entry) {
    var plan=require(context,InboundOperations.TAKE_CUSTODY);
    if (entry==null || entry.vehicleNumber()==null || entry.arrivedAt()==null
        || entry.lines()==null || entry.lines().isEmpty() || entry.lines().size()>100) throw invalid();
    uuid(entry.id());
    uuid(entry.noticeId());
    validateText(entry.vehicleNumber(),64);
    validateText(entry.carrier(),200);
    validateText(entry.remark(),500);
    var ids=new java.util.HashSet<String>();
    for (var line:entry.lines()) {
      if (line==null) throw invalid();
      uuid(line.id());
      uuid(line.noticeLineId());
      uuid(line.evidenceId());
      if (!ids.add(line.id()) || line.position()==null) throw invalid();
      if (line.expectedQuantity()!=null) positiveQuantity(line.expectedQuantity());
    }
    authorized(plan,()->{
      // 先锁原通知并复核可信业务范围；随后认领同一批次的 UUID。
      var head=lockNotice(context,entry.noticeId(),entry.lines().getFirst().noticeLineId()).notice();
      source.requireWarehouse(context,head.warehouseId());
      source.owner(context,head.ownerPartnerId());
      writes.authorize(plan,UUID.fromString(entry.id()));
      String digest=fingerprint(entry);
      var claim=store.claimEntry(context,entry,digest);
      if (!claim.noticeId().equals(entry.noticeId()) || !claim.digest().equals(digest)) {
        throw conflict("INBOUND_REQUEST_CONFLICT");
      }
      for (var line:entry.lines()) {
        takeCustody(context,new Custody(line.id(),entry.noticeId(),line.position(),line.evidenceId(),line.noticeLineId(),
            null,entry.id(),line.expectedQuantity()),plan);
      }
      return null;
    });
  }

  /** {@inheritDoc} */
  @Override public void confirmReceiptDocument(VerifiedTenantContext context,ReceiptDocument document) {
    var plan=require(context,InboundOperations.RECEIVE);
    if (document==null || document.lines()==null || document.lines().isEmpty() || document.lines().size()>100) throw invalid();
    uuid(document.id());
    uuid(document.entryId());
    validateText(document.measurementRef(),64);
    validateText(document.remark(),500);
    var ids=new java.util.HashSet<String>();
    var custodies=new java.util.HashSet<String>();
    for (var line:document.lines()) {
      if (line==null) throw invalid();
      uuid(line.id());
      uuid(line.custodyId());
      if (!ids.add(line.id()) || !custodies.add(line.custodyId())) throw invalid();
      nonnegative(line.quantity());
      nonnegative(line.rejectedQuantity());
      if (line.quantity().signum()==0 && line.rejectedQuantity().signum()==0) throw invalid();
      if (line.quantity().signum()>0) uuid(line.measurementId());
      if (line.rejectedQuantity().signum()>0 && line.rejectionReason()==null) throw invalid();
      validateText(line.rejectionReason(),500);
    }
    authorized(plan,()->{
      // 接管与实收都先锁来源通知、再锁入场头，避免并发重放形成反向锁顺序。
      var first=store.custody(context,document.lines().getFirst().custodyId());
      if (first==null || !document.entryId().equals(first.entryId())) throw denied();
      lockNotice(context,first.noticeId(),first.noticeLineId());
      var entry=store.lockEntry(context,document.entryId());
      if (entry==null) throw denied();
      writes.authorize(plan,UUID.fromString(document.id()));
      String digest=fingerprint(document);
      if (!digest.equals(store.claimReceiptDocument(context,document,digest))) throw conflict("INBOUND_REQUEST_CONFLICT");
      for (var line:document.lines()) confirmLine(context,document,entry,plan,line);
      return null;
    });
  }

  /** 同一接管仅允许一次确认，合格数量继续由原实收与库存过账事务处理。 */
  private void confirmLine(VerifiedTenantContext context,ReceiptDocument document,InboundReceivingStore.EntryClaim entry,
      AccessPlan plan,ReceiptLine line) {
    var custody=store.custody(context,line.custodyId());
    if (custody==null || !document.entryId().equals(custody.entryId()) || !entry.noticeId().equals(custody.noticeId())) throw denied();
    var locked=lockNotice(context,custody.noticeId(),custody.noticeLineId());
    var notice=locked.notice();
    source.requireWarehouse(context,notice.warehouseId());
    source.owner(context,notice.ownerPartnerId());
    source.requireProduct(context,notice.item().productId());
    var prior=store.confirmation(context,line.custodyId());
    if (prior!=null) {
      if (!prior.documentId().equals(document.id()) || !prior.lineId().equals(line.id())) throw conflict("INBOUND_REQUEST_CONFLICT");
      return;
    }
    String receiptId=null;
    if (line.quantity().signum()>0) {
      var refs=new InboundReceiptReferences.Request(custody.position(),notice.item(),notice.ownerPartnerId(),
          notice.baseUnitId(),line.quantity().toPlainString(),new InboundReceiptReferences.SameUnit());
      var receipt=new Receipt(line.id(),custody.id(),line.measurementId(),refs,null);
      receiptId=receiveInTransaction(context,receipt,plan,document.entryId()).receiptId();
    } else if (!"COMPLETED".equals(locked.status())) {
      requireReceiving(locked.status());
    }
    store.attachReceiptLine(context,document,line,receiptId);
  }

  private static void nonnegative(BigDecimal quantity) {
    if (quantity==null || quantity.signum()<0) throw invalid();
    if (quantity.signum()>0) positiveQuantity(quantity);
  }

  private static BigDecimal normalizedAmount(BigDecimal quantity) {
    return quantity==null ? null : quantity.stripTrailingZeros();
  }

  /** 自由文本按长度编码；备注中的分隔符不能改变摘要所代表的字段边界。 */
  private static String fingerprint(Entry request) {
    var value=new StringBuilder();
    append(value,request.id(),request.noticeId(),request.vehicleNumber(),request.carrier(),request.arrivedAt(),request.remark());
    for (var line:request.lines()) append(value,line.id(),line.noticeLineId(),line.position(),
        line.evidenceId(),normalizedAmount(line.expectedQuantity()));
    return hash(value.toString());
  }

  private static String fingerprint(ReceiptDocument request) {
    var value=new StringBuilder();
    append(value,request.id(),request.entryId(),request.measurementRef(),request.remark());
    for (var line:request.lines()) append(value,line.id(),line.custodyId(),line.measurementId(),
        normalizedAmount(line.quantity()),normalizedAmount(line.rejectedQuantity()),line.rejectionReason());
    return hash(value.toString());
  }

  private static void append(StringBuilder target,Object... values) {
    for (var value:values) {
      if (value==null) { target.append("-1:"); continue; }
      String text=value.toString();
      target.append(text.length()).append(':').append(text);
    }
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

  private InboundReceivingStore.LockedNotice lockNotice(VerifiedTenantContext context,String id,String lineId) {
    var locked=store.lockNotice(context,id,lineId);
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
    // 保留原无批次请求的摘要格式，升级后仍可重放历史确认。
    String payload="Receipt[id="+receipt.id()+", custodyId="+receipt.custodyId()
        +", measurementId="+receipt.measurementId()+", references="+receipt.references()+"]";
    if (receipt.receiptBatchNo()!=null) payload+="\n"+receipt.receiptBatchNo();
    return hash(payload);
  }

  private static String hash(String payload) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  /** 新接管及实收只能在允许收货的通知状态下办理，历史重放仍可返回。 */
  private static void requireReceiving(String status) {
    if (!Set.of("ISSUED","RECEIVING").contains(status)) throw conflict("INBOUND_FLOW_STEP_NOT_ALLOWED");
  }

  private static void validateText(String value,int limit) {
    if (value!=null && (value.isBlank() || !value.equals(value.strip())
        || value.codePointCount(0,value.length())>limit || value.codePoints().anyMatch(Character::isISOControl))) {
      throw invalid();
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
