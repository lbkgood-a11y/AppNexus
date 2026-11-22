package com.appnexus.bulkwarehousing.adapter.web;

import com.appnexus.bulkwarehousing.api.InboundReceiptReferences.*;
import com.appnexus.bulkwarehousing.api.InboundReceiving;
import com.appnexus.bulkwarehousing.api.InboundDisposition;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.contracts.operation.OperationBinding;
import com.appnexus.masterdata.api.masterdata.MasterData;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 第一方入仓命令入口；认证主体取自宿主安全请求属性。 */
@RestController
public final class InboundReceivingController {
  private final InboundReceiving receiving;
  private final InboundDisposition disposition;

  /**
   * @param receiving 按统一操作和数据范围执行业务的仓储入口
   * @param disposition 独立权利登记及质量放行入口
   */
  public InboundReceivingController(InboundReceiving receiving,InboundDisposition disposition) {
    this.receiving = receiving;
    this.disposition = disposition;
  }

  /** @param request 经安全链的请求 @param notice 已批准的通知范围 */
  @PostMapping("/api/bulk-inbound-receipts:issue")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @OperationBinding(id="BulkInboundReceipt.issue",reference=true)
  public void issue(HttpServletRequest request, @RequestBody InboundReceiving.Notice notice) {
    receiving.issue(context(request),notice);
  }

  /** @param request 经安全链的请求 @param custody 实际接管位置、范围及证据 */
  @PostMapping("/api/bulk-inbound-receipts:take-custody")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @OperationBinding(id="BulkInboundReceipt.take_custody",reference=true)
  public void takeCustody(HttpServletRequest request, @RequestBody InboundReceiving.Custody custody) {
    receiving.takeCustody(context(request),custody);
  }

  /** @param request 可信安全请求 @param entry 同一通知下一车多品规的主单及接管行 */
  @PostMapping("/api/bulk-inbound-entries")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @OperationBinding(id="BulkInboundReceipt.record_entry",reference=true)
  public void recordEntry(HttpServletRequest request,@RequestBody InboundReceiving.Entry entry) {
    receiving.recordEntry(context(request),entry);
  }

  /** @param request 可信安全请求 @param document 来源入场及实际合格和拒收明细 */
  @PostMapping("/api/bulk-inbound-receipt-documents")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @OperationBinding(id="BulkInboundReceipt.confirm_document",reference=true)
  public void confirmReceiptDocument(HttpServletRequest request,@RequestBody InboundReceiving.ReceiptDocument document) {
    receiving.confirmReceiptDocument(context(request),document);
  }

  /** 收货的传输契约。无换算规则和包装编码时使用基础单位。 */
  public record ReceiptInput(String id,String custodyId,String measurementId,Position position,Item item,
      String ownerPartnerId,String sourceUnitId,String quantity,String conversionRuleId,
      Long conversionRuleVersion,String packagingCode,String receiptBatchNo) {}

  /** @param request 经安全链的请求 @param input 有效计量证据及实收数量 @return 已提交过账 */
  @PostMapping("/api/bulk-inbound-receipts")
  @OperationBinding(id="BulkInboundReceipt.receive",reference=true)
  public InboundReceiving.Posting receive(HttpServletRequest request,@RequestBody ReceiptInput input) {
    var references=new Request(input.position(),input.item(),input.ownerPartnerId(),input.sourceUnitId(),
        input.quantity(),conversion(input));
    return receiving.receive(context(request),new InboundReceiving.Receipt(
        input.id(),input.custodyId(),input.measurementId(),references,input.receiptBatchNo()));
  }

  /** @param request 经安全链的请求 @param decision 货权依据 @return 当前登记及待检量 */
  @PostMapping("/api/bulk-inbound-title-registrations")
  @OperationBinding(id="BulkInboundTitleRegistration.register",reference=true)
  public InboundDisposition.Status registerTitle(HttpServletRequest request,@RequestBody InboundDisposition.TitleDecision decision) {
    return disposition.registerTitle(context(request),decision);
  }

  /** @param request 经安全链的请求 @param decision 已核验合格证据 @return 当前登记及待检量 */
  @PostMapping("/api/bulk-inbound-quality-releases")
  @OperationBinding(id="BulkInboundQualityRelease.release",reference=true)
  public InboundDisposition.Status releaseQuality(HttpServletRequest request,@RequestBody InboundDisposition.Decision decision) {
    return disposition.releaseQuality(context(request),decision);
  }

  private static Conversion conversion(ReceiptInput input) {
    if (input.conversionRuleId()!=null && input.conversionRuleVersion()!=null && input.packagingCode()==null)
      return new UnitRule(input.conversionRuleId(),input.conversionRuleVersion());
    if (input.conversionRuleId()!=null || input.conversionRuleVersion()!=null)
      throw new Rejection(400,"INBOUND_INPUT_INVALID");
    return input.packagingCode()==null ? new SameUnit() : new Packaging(input.packagingCode());
  }

  private static VerifiedTenantContext context(HttpServletRequest request) {
    if (request.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext context)
      return context;
    throw new Rejection(403,"INBOUND_ACCESS_DENIED");
  }

  /** @param error 仓储稳定拒绝 @return 不含原始输入的统一错误体 */
  @ExceptionHandler(Rejection.class)
  public ResponseEntity<ProblemDetail> reject(Rejection error) { return problem(error.status(),error.getMessage()); }

  /** @param error 主数据稳定拒绝 @return 不含主数据原文的失败体 */
  @ExceptionHandler(MasterData.Rejection.class)
  public ResponseEntity<ProblemDetail> rejectReference(MasterData.Rejection error) {
    return problem(error.status,error.getMessage());
  }

  /** @return 权限计划拒绝 */
  @ExceptionHandler(SecurityException.class)
  public ResponseEntity<ProblemDetail> denied() { return problem(403,"INBOUND_ACCESS_DENIED"); }

  /** @return 证据与收货输入不匹配 */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ProblemDetail> invalid() { return problem(400,"INBOUND_INPUT_INVALID"); }

  private static ResponseEntity<ProblemDetail> problem(int status,String code) {
    var body=ProblemDetail.forStatus(status);
    body.setProperty("code",code);
    return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
  }
}
