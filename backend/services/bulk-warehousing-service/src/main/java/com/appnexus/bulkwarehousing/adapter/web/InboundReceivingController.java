package com.appnexus.bulkwarehousing.adapter.web;

import com.appnexus.bulkwarehousing.api.InboundReceiving;
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

  /**
   * @param receiving 按统一操作和数据范围执行业务的仓储入口
   */
  public InboundReceivingController(InboundReceiving receiving) {
    this.receiving = receiving;
  }

  /** @param request 经安全链的请求 @param notice 已批准的通知范围 */
  @PostMapping("/api/bulk-inbound-receipts:issue")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @OperationBinding(id="BulkInboundReceipt.issue",reference=true)
  public void issue(HttpServletRequest request, @RequestBody InboundReceiving.Notice notice) {
    receiving.issue(context(request),notice);
  }

  /** @param request 可信租户请求 @param id 通知 UUID @param input 原版本与完整修改内容 */
  @PutMapping("/api/bulk-inbound-notices/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @OperationBinding(id="BulkInboundReceipt.update",reference=true)
  public void update(HttpServletRequest request,@PathVariable String id,@RequestBody InboundReceiving.Update input) {
    if(input==null || input.notice()==null || !id.equals(input.notice().id()))throw new Rejection(400,"INBOUND_INPUT_INVALID");
    receiving.update(context(request),input);
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

  /** @return 通知参数未通过签发校验 */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ProblemDetail> invalid() { return problem(400,"INBOUND_INPUT_INVALID"); }

  private static ResponseEntity<ProblemDetail> problem(int status,String code) {
    var body=ProblemDetail.forStatus(status);
    body.setProperty("code",code);
    return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
  }
}
