package com.appnexus.bulkwarehousing.adapter.web.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundDocumentsUseCase.Creation;
import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundDocumentsUseCase.TaskOption;
import java.util.List;

import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundDocumentsUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundDocumentsUseCase.Kind;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.contracts.operation.OperationBinding;
import com.appnexus.contracts.operation.RecordAuditSource;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 三单独立只读入口，不要求工作台权限。 */
@RestController
public final class InboundDocumentQueryController {
    private final ReadInboundDocumentsUseCase documents;

    /** @param documents 已接通统一授权的分岗查询 */
    public InboundDocumentQueryController(ReadInboundDocumentsUseCase documents){this.documents=documents;}

    /**
     * @param request 可信租户会话
     * @param number 单号，精确匹配；缺省时查询全部已授权单据
     * @param page 页码，从 1 开始，缺省 1
     * @param pageSize 每页条数，允许 1–100，缺省 20
     * @return 已授权单据及相同条件下的总数
     */
    @GetMapping("/api/bulk-inbound-quality-orders")
    @OperationBinding(id="BulkInboundQualityOrder.list_documents",reference=true)
    public ResponseEntity<ReadInboundDocumentsUseCase.InboundDocumentPage> listQuality(HttpServletRequest request,
            @RequestParam(name="number",required=false) String number,@RequestParam(name="page",defaultValue="1") int page,
            @RequestParam(name="pageSize",defaultValue="20") int pageSize) {
        return noStore(documents.list(context(request),Kind.QUALITY,number,page,pageSize));
    }

    /**
     * @param request 可信租户会话
     * @param id 单据 UUID
     * @return 当前租户的授权单据详情
     */
    @GetMapping("/api/bulk-inbound-quality-orders/{id}")
    @OperationBinding(id="BulkInboundQualityOrder.read_document",reference=true)
    public ResponseEntity<Map<String,Object>> readQuality(HttpServletRequest request,@PathVariable UUID id) {
        return noStore(documents.detail(context(request),Kind.QUALITY,id));
    }

    /**
     * @param request 可信租户会话
     * @param number 单号，精确匹配；缺省时查询全部已授权单据
     * @param page 页码，从 1 开始，缺省 1
     * @param pageSize 每页条数，允许 1–100，缺省 20
     * @return 已授权单据及相同条件下的总数
     */
    @GetMapping("/api/bulk-inbound-metering-orders")
    @OperationBinding(id="BulkInboundMeteringOrder.list_documents",reference=true)
    public ResponseEntity<ReadInboundDocumentsUseCase.InboundDocumentPage> listMetering(HttpServletRequest request,
            @RequestParam(name="number",required=false) String number,@RequestParam(name="page",defaultValue="1") int page,
            @RequestParam(name="pageSize",defaultValue="20") int pageSize) {
        return noStore(documents.list(context(request),Kind.METERING,number,page,pageSize));
    }

    /**
     * @param request 可信租户会话
     * @param id 单据 UUID
     * @return 当前租户的授权单据详情
     */
    @GetMapping("/api/bulk-inbound-metering-orders/{id}")
    @RecordAuditSource(table="bw_metering_order")
    @OperationBinding(id="BulkInboundMeteringOrder.read_document",reference=true)
    public ResponseEntity<Map<String,Object>> readMetering(HttpServletRequest request,@PathVariable UUID id) {
        return noStore(documents.detail(context(request),Kind.METERING,id));
    }

    /**
     * @param request 可信租户会话
     * @param number 单号，精确匹配；缺省时查询全部已授权单据
     * @param page 页码，从 1 开始，缺省 1
     * @param pageSize 每页条数，允许 1–100，缺省 20
     * @return 已授权单据及相同条件下的总数
     */
    @GetMapping("/api/bulk-inbound-receipt-orders")
    @OperationBinding(id="BulkWarehouseReceiptOrder.list_documents",reference=true)
    public ResponseEntity<ReadInboundDocumentsUseCase.InboundDocumentPage> listReceipt(HttpServletRequest request,
            @RequestParam(name="number",required=false) String number,@RequestParam(name="page",defaultValue="1") int page,
            @RequestParam(name="pageSize",defaultValue="20") int pageSize) {
        return noStore(documents.list(context(request),Kind.RECEIPT,number,page,pageSize));
    }

    /**
     * @param request 可信租户会话
     * @param id 单据 UUID
     * @return 当前租户的授权单据详情
     */
    @GetMapping("/api/bulk-inbound-receipt-orders/{id}")
    @RecordAuditSource(table="bw_receipt_order")
    @OperationBinding(id="BulkWarehouseReceiptOrder.read_document",reference=true)
    public ResponseEntity<Map<String,Object>> readReceipt(HttpServletRequest request,@PathVariable UUID id) {
        return noStore(documents.detail(context(request),Kind.RECEIPT,id));
    }

    /** @return 当前命令授权范围内的最小候选投影 */
    @GetMapping("/api/bulk-inbound-quality-orders:register-sample")
    @OperationBinding(id="BulkInboundQualityOrder.register_sample",reference=true)
    public ResponseEntity<List<TaskOption>> optionsSAMPLE(HttpServletRequest request,
            @RequestParam(name="taskNo", required=false) String taskNo) {
        return noStore(documents.taskOptions(context(request), Creation.SAMPLE, taskNo));
    }

    /** @return 当前命令授权范围内的最小候选投影 */
    @GetMapping("/api/bulk-inbound-quality-orders:inspect-steel")
    @OperationBinding(id="BulkInboundQualityOrder.inspect_steel",reference=true)
    public ResponseEntity<List<TaskOption>> optionsINSPECT(HttpServletRequest request,
            @RequestParam(name="taskNo", required=false) String taskNo) {
        return noStore(documents.taskOptions(context(request), Creation.INSPECT, taskNo));
    }

    /** @return 当前命令授权范围内的最小候选投影 */
    @GetMapping("/api/bulk-inbound-metering-orders:record-gross")
    @OperationBinding(id="BulkInboundMeteringOrder.record_gross",reference=true)
    public ResponseEntity<List<TaskOption>> optionsGROSS(HttpServletRequest request,
            @RequestParam(name="taskNo", required=false) String taskNo) {
        return noStore(documents.taskOptions(context(request), Creation.GROSS, taskNo));
    }

    /** @return 当前命令授权范围内的最小候选投影 */
    @GetMapping("/api/bulk-inbound-metering-orders:register-external")
    @OperationBinding(id="BulkInboundMeteringOrder.register_external",reference=true)
    public ResponseEntity<List<TaskOption>> optionsEXTERNAL(HttpServletRequest request,
            @RequestParam(name="taskNo", required=false) String taskNo) {
        return noStore(documents.taskOptions(context(request), Creation.EXTERNAL, taskNo));
    }

    /** @return 当前命令授权范围内的最小候选投影 */
    @GetMapping("/api/bulk-inbound-metering-orders:confirm-steel")
    @OperationBinding(id="BulkInboundMeteringOrder.confirm_steel",reference=true)
    public ResponseEntity<List<TaskOption>> optionsDUAL(HttpServletRequest request,
            @RequestParam(name="taskNo", required=false) String taskNo) {
        return noStore(documents.taskOptions(context(request), Creation.DUAL, taskNo));
    }

    /** @return 当前命令授权范围内的最小候选投影 */
    @GetMapping("/api/bulk-inbound-receipt-orders:create-bulk-draft")
    @OperationBinding(id="BulkWarehouseReceiptOrder.create_bulk_draft",reference=true)
    public ResponseEntity<List<TaskOption>> optionsBULK(HttpServletRequest request,
            @RequestParam(name="taskNo", required=false) String taskNo) {
        return noStore(documents.taskOptions(context(request), Creation.BULK, taskNo));
    }

    /** @return 当前命令授权范围内的最小候选投影 */
    @GetMapping("/api/bulk-inbound-receipt-orders:create-steel-draft")
    @OperationBinding(id="BulkWarehouseReceiptOrder.create_steel_draft",reference=true)
    public ResponseEntity<List<TaskOption>> optionsLPN(HttpServletRequest request,
            @RequestParam(name="taskNo", required=false) String taskNo) {
        return noStore(documents.taskOptions(context(request), Creation.LPN, taskNo));
    }

    private static VerifiedTenantContext context(HttpServletRequest request) {
        if(request.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext context)return context;
        throw new Rejection(403,"INBOUND_DOCUMENT_ACCESS_DENIED");
    }

    private static <T> ResponseEntity<T> noStore(T body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}

    /** @return 当前操作未授予 */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ProblemDetail> forbidden(){return rejected(new Rejection(403,"INBOUND_DOCUMENT_ACCESS_DENIED"));}

    /** @param rejection 稳定错误 @return 不含请求原值的失败体 */
    @ExceptionHandler(Rejection.class)
    public ResponseEntity<ProblemDetail> rejected(Rejection rejection) {
        var detail=ProblemDetail.forStatus(rejection.status());
        detail.setProperty("code",rejection.getMessage());
        return ResponseEntity.status(rejection.status()).cacheControl(CacheControl.noStore()).body(detail);
    }
}
