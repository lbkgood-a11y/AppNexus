package com.appnexus.bulkwarehousing.adapter.web.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundWorkbenchUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundWorkbenchDetailUseCase;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.contracts.operation.OperationBinding;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundSiteTelemetry;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 队列只读入口，可信租户由宿主认证链提供。 */
@RestController
public final class InboundWorkbenchController {
    private final ReadInboundWorkbenchUseCase workbench;
    private final ReadInboundWorkbenchDetailUseCase details;
    private final ReadInboundSiteTelemetry site;

    /** @param workbench 授权队列查询 @param details 授权详情 */
    public InboundWorkbenchController(ReadInboundWorkbenchUseCase workbench,ReadInboundWorkbenchDetailUseCase details,
                                      ReadInboundSiteTelemetry site) {
        this.workbench=workbench;this.details=details;this.site=site;
    }

    /** @return 受权限、鲜度及当前盲检状态约束的读数。 */
    @GetMapping(ReadInboundSiteTelemetry.PATH)
    @OperationBinding(id="BulkInboundTask.read_workbench_detail",reference=true)
    public ResponseEntity<ReadInboundSiteTelemetry.InboundSiteReading> inboundSiteReading(
            HttpServletRequest request,@PathVariable UUID id) {
        if(!(request.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext context))
            throw new Rejection(403,"INBOUND_SITE_ACCESS_DENIED");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(site.read(context,id));
    }

    /**
     * @param request 可信租户请求
     * @param stage 可选队列页签
     * @param afterId 上页末行作业 UUID，首屏省略
     * @param limit 每页 1 至 100 行，默认 20
     * @param unopened 是否只查询待开单作业，默认 false
     * @return 不缓存的授权队列和状态计数
     */
    @GetMapping(ReadInboundWorkbenchUseCase.PATH)
    @OperationBinding(id="BulkInboundTask.read_workbench_queue", reference=true)
    public ResponseEntity<ReadInboundWorkbenchUseCase.QueuePage> queue(HttpServletRequest request,
            @RequestParam(required=false) ReadInboundWorkbenchUseCase.Stage stage,
            @RequestParam(required=false) UUID afterId, @RequestParam(defaultValue="20") int limit,
            @RequestParam(defaultValue="false") boolean unopened) {
        if (!(request.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext context)) {
            throw new Rejection(403, "INBOUND_QUEUE_ACCESS_DENIED");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(workbench.queue(context, stage, afterId, limit, unopened));
    }

    /**
     * @param request 可信租户请求
     * @param number 可选精确作业单号
     * @param status 可选作业状态，缺省时查询全部状态
     * @param afterId 上页末行作业 UUID
     * @param limit 每页 1 至 100 行，默认 20
     * @return 禁止缓存的授权作业台账
     */
    @GetMapping(ReadInboundWorkbenchUseCase.LIST_PATH)
    @OperationBinding(id="BulkInboundTask.read_task_list", reference=true)
    public ResponseEntity<ReadInboundWorkbenchUseCase.TaskPage> inboundTaskList(HttpServletRequest request,
            @RequestParam(required=false) String number,
            @RequestParam(required=false) ReadInboundWorkbenchUseCase.TaskStatus status,
            @RequestParam(required=false) UUID afterId,
            @RequestParam(defaultValue="20") int limit) {
        if (!(request.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext context)) {
            throw new Rejection(403, "INBOUND_TASK_LIST_ACCESS_DENIED");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(workbench.listTasks(context, number, status, afterId, limit));
    }

    /** @param request 可信会话 @param id 作业 UUID @return 当前授权的工作台聚合详情 */
    @GetMapping("/api/bulk-inbound-workbench/tasks/{id}")
    @OperationBinding(id="BulkInboundTask.read_workbench_detail",reference=true)
    public ResponseEntity<ReadInboundWorkbenchDetailUseCase.WorkbenchDetail> inboundWorkbenchDetail(HttpServletRequest request,
            @PathVariable UUID id) {
        if(!(request.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext context))
            throw new Rejection(403,"INBOUND_QUEUE_ACCESS_DENIED");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(details.detail(context,id));
    }

    /** @param request 可信会话 @param id 作业 UUID @return 可见的非盲检身份 */
    @GetMapping("/api/bulk-inbound-workbench/tasks/{id}/identity")
    @OperationBinding(id="BulkInboundTask.read_workbench_identity",reference=true)
    public ResponseEntity<Map<String,Object>> workbenchIdentity(HttpServletRequest request,@PathVariable UUID id) {
        return card(request,id,ReadInboundWorkbenchDetailUseCase.Card.IDENTITY);
    }

    /** @param request 可信会话 @param id 作业 UUID @return 授权质检卡片 */
    @GetMapping("/api/bulk-inbound-workbench/tasks/{id}/quality")
    @OperationBinding(id="BulkInboundQualityOrder.read_workbench_quality",reference=true)
    public ResponseEntity<Map<String,Object>> workbenchQuality(HttpServletRequest request,@PathVariable UUID id) {
        return card(request,id,ReadInboundWorkbenchDetailUseCase.Card.QUALITY);
    }

    /** @param request 可信会话 @param id 作业 UUID @return 授权计量卡片 */
    @GetMapping("/api/bulk-inbound-workbench/tasks/{id}/metering")
    @OperationBinding(id="BulkInboundMeteringOrder.read_workbench_meter",reference=true)
    public ResponseEntity<Map<String,Object>> workbenchMeter(HttpServletRequest request,@PathVariable UUID id) {
        return card(request,id,ReadInboundWorkbenchDetailUseCase.Card.METERING);
    }

    /** @param request 可信会话 @param id 作业 UUID @return 授权入库卡片 */
    @GetMapping("/api/bulk-inbound-workbench/tasks/{id}/receipt")
    @OperationBinding(id="BulkWarehouseReceiptOrder.read_workbench_receipt",reference=true)
    public ResponseEntity<Map<String,Object>> workbenchReceipt(HttpServletRequest request,@PathVariable UUID id) {
        return card(request,id,ReadInboundWorkbenchDetailUseCase.Card.RECEIPT);
    }

    private ResponseEntity<Map<String,Object>> card(HttpServletRequest request,UUID id,
            ReadInboundWorkbenchDetailUseCase.Card card) {
        if(!(request.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext context))
            throw new Rejection(403,"INBOUND_QUEUE_ACCESS_DENIED");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(details.card(context,id,card));
    }

    /** @param rejection 稳定业务错误 @return 不含输入原文的失败响应 */
    @ExceptionHandler(Rejection.class)
    public ResponseEntity<ProblemDetail> rejected(Rejection rejection) {
        var detail = ProblemDetail.forStatus(rejection.status());
        detail.setProperty("code", rejection.getMessage());
        return ResponseEntity.status(rejection.status()).cacheControl(CacheControl.noStore()).body(detail);
    }

    /** @return 统一准入拒绝 */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ProblemDetail> forbidden() {
        return rejected(new Rejection(403, "INBOUND_QUEUE_ACCESS_DENIED"));
    }
}
