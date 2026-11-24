package com.appnexus.bulkwarehousing.adapter.web.inboundexecution;

import com.appnexus.bulkwarehousing.api.InboundReceiptReferences.Position;
import com.appnexus.bulkwarehousing.api.inboundexecution.CreateBulkReceiptDraftUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.CreateSteelReceiptDraftUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.PostInboundReceiptUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.ReverseInboundReceiptUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.OpenInboundReceiptUseCase;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.masterdata.api.masterdata.MasterData;
import com.appnexus.contracts.operation.OperationBinding;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 独立散货入库草稿入口。 */
@RestController
public final class InboundReceiptOrderController {
    private final CreateBulkReceiptDraftUseCase drafts;
    private final PostInboundReceiptUseCase posting;
    private final ReverseInboundReceiptUseCase reversal;
    private final CreateSteelReceiptDraftUseCase steelDrafts;
    private final OpenInboundReceiptUseCase opening;

    /** @param drafts 散货草稿 @param posting 授权过账 @param reversal 授权冲红 @param steelDrafts 钢铁 LPN 草稿 */
    public InboundReceiptOrderController(CreateBulkReceiptDraftUseCase drafts, PostInboundReceiptUseCase posting,
                                         ReverseInboundReceiptUseCase reversal,CreateSteelReceiptDraftUseCase steelDrafts,
                                         OpenInboundReceiptUseCase opening) {
        this.drafts = drafts;
        this.posting = posting;
        this.reversal = reversal;
        this.steelDrafts=steelDrafts;
        this.opening=opening;
    }

    /** 已选车次及其并发版本。 */
    public record OpenInboundReceiptRequest(UUID taskId, Long taskVersion) {}

    /** @return 已存在或新开的入库草稿，不产生库存 */
    @PostMapping(OpenInboundReceiptUseCase.PATH)
    @OperationBinding(id="BulkWarehouseReceiptOrder.open_draft", reference=true)
    public ResponseEntity<OpenInboundReceiptUseCase.OpenedInboundReceipt> openInboundReceipt(
            HttpServletRequest request,@RequestBody OpenInboundReceiptRequest input) {
        if (!(request.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext context)) {
            throw new Rejection(403,"INBOUND_RECEIPT_ACCESS_DENIED");
        }
        if (input.taskVersion() == null) throw new Rejection(400,"INBOUND_RECEIPT_DRAFT_INPUT_INVALID");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(opening.open(context,input.taskId(),input.taskVersion()));
    }

    /** 草稿 HTTP 输入仅指定位置、批次、引用和版本；计量重量从仓储事实读取。 */
    public record CreateBulkReceiptDraftRequest(UUID taskId, Long taskVersion, Long meteringVersion, UUID noticeLineId,
            String receiptNo, String batchNo, Position position, UUID kilogramUnitId,
            UUID conversionRuleId, Long conversionRuleVersion, UUID replacesReceiptId) {}

    /** 过账仅传作业及原版本，数量、质量、额度和位置从锁内事实读取。 */
    public record PostInboundReceiptRequest(UUID taskId, Long taskVersion, Long receiptVersion) {}
    /** 冲红只接收原作业与已过账原单的行版本。 */
    public record ReverseInboundReceiptRequest(UUID taskId, Long taskVersion, Long receiptVersion) {}
    /** 逐卷或捆的理重和来源、核验炉批与位置输入。 */
    public record SteelReceiptLineRequest(UUID noticeLineId,String lpn,String heatNo,String specification,String material,
            String theoreticalKg,Position position,String batchNo,UUID kilogramUnitId,UUID conversionRuleId,
            Long conversionRuleVersion) {}
    /** LPN 草稿请求。 */
    public record CreateSteelReceiptDraftRequest(UUID taskId,Long taskVersion,Long meteringVersion,String steelReceiptNo,
                                                 List<SteelReceiptLineRequest> lines) {}

    /** @param request 安全链验证的会话 @param input 草稿位置和引用 @return 已提交单据及版本 */
    @PostMapping("/api/bulk-inbound-receipt-orders:create-bulk-draft")
    @OperationBinding(id="BulkWarehouseReceiptOrder.create_bulk_draft", reference=true)
    public ResponseEntity<CreateBulkReceiptDraftUseCase.BulkReceiptDraft> createBulkDraft(HttpServletRequest request,
            @RequestBody CreateBulkReceiptDraftRequest input) {
        if (!(request.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext context)) {
            throw new Rejection(403, "INBOUND_RECEIPT_ACCESS_DENIED");
        }
        if (input.taskVersion() == null || input.meteringVersion() == null) {
            throw new Rejection(400, "INBOUND_RECEIPT_DRAFT_INPUT_INVALID");
        }
        var result = drafts.create(context, new CreateBulkReceiptDraftUseCase.Command(input.taskId(), input.taskVersion(),
                input.meteringVersion(), input.noticeLineId(), input.receiptNo(), input.batchNo(), input.position(),
                input.kilogramUnitId(), input.conversionRuleId(), input.conversionRuleVersion(), input.replacesReceiptId()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }

    /**
     * @param request 已验证的可信会话
     * @param id 同作业入库单 UUID
     * @param key Idempotency-Key，1 至 96 个 ASCII 字符，同一请求重试时保持一致
     * @param input 作业 UUID 与作业、入库单原行版本
     * @return 已提交过账或已授权的幂等结果
     */
    @PostMapping("/api/bulk-inbound-receipt-orders/{id}:post")
    @OperationBinding(id="BulkWarehouseReceiptOrder.post", reference=true)
    public ResponseEntity<PostInboundReceiptUseCase.InboundReceiptPosting> postInboundReceipt(HttpServletRequest request,
            @PathVariable UUID id, @RequestHeader("Idempotency-Key") String key, @RequestBody PostInboundReceiptRequest input) {
        if (!(request.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext context)) {
            throw new Rejection(403, "INBOUND_RECEIPT_ACCESS_DENIED");
        }
        if (input.taskVersion() == null || input.receiptVersion() == null) {
            throw new Rejection(400, "INBOUND_EXECUTION_INPUT_INVALID");
        }
        var result = posting.post(context, new PostInboundReceiptUseCase.Command(input.taskId(), id, input.taskVersion(),
                input.receiptVersion(), key));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }

    /**
     * @param request 已验证的可信会话
     * @param id 已过账入库单 UUID
     * @param key Idempotency-Key，1 至 96 个 ASCII 字符
     * @param input 作业 UUID 和作业、原单当前行版本
     * @return 已提交或已授权的幂等冲红结果
     */
    @PostMapping("/api/bulk-inbound-receipt-orders/{id}:reverse")
    @OperationBinding(id="BulkWarehouseReceiptOrder.reverse", reference=true)
    public ResponseEntity<ReverseInboundReceiptUseCase.InboundReceiptReversal> reverseInboundReceipt(HttpServletRequest request,
            @PathVariable UUID id, @RequestHeader("Idempotency-Key") String key, @RequestBody ReverseInboundReceiptRequest input) {
        if (!(request.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext context)) {
            throw new Rejection(403, "INBOUND_RECEIPT_ACCESS_DENIED");
        }
        if (input.taskVersion() == null || input.receiptVersion() == null) {
            throw new Rejection(400, "INBOUND_EXECUTION_INPUT_INVALID");
        }
        var result = reversal.reverse(context, new ReverseInboundReceiptUseCase.Command(input.taskId(), id,
                input.taskVersion(), input.receiptVersion(), key));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }

    /** @param request 可信会话 @param input 钢铁多行草稿 @return 已提交入库单版本 */
    @PostMapping("/api/bulk-inbound-receipt-orders:create-steel-draft")
    @OperationBinding(id="BulkWarehouseReceiptOrder.create_steel_draft",reference=true)
    public ResponseEntity<CreateSteelReceiptDraftUseCase.SteelReceiptDraft> createSteelDraft(HttpServletRequest request,
            @RequestBody CreateSteelReceiptDraftRequest input) {
        if(!(request.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext context))
            throw new Rejection(403,"INBOUND_RECEIPT_ACCESS_DENIED");
        if(input.taskVersion()==null||input.meteringVersion()==null||input.lines()==null) throw steelDraftInvalid();
        var result=steelDrafts.createSteel(context,new CreateSteelReceiptDraftUseCase.Command(input.taskId(),input.taskVersion(),
                input.meteringVersion(),input.steelReceiptNo(),input.lines().stream().map(InboundReceiptOrderController::steelLine).toList()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }

    private static CreateSteelReceiptDraftUseCase.Line steelLine(SteelReceiptLineRequest line) {
        if(line==null||line.theoreticalKg()==null||!line.theoreticalKg().matches("[0-9]{1,14}(\\.[0-9]{1,4})?"))
            throw steelDraftInvalid();
        return new CreateSteelReceiptDraftUseCase.Line(line.noticeLineId(),line.lpn(),line.heatNo(),line.specification(),line.material(),
                new BigDecimal(line.theoreticalKg()),line.position(),line.batchNo(),line.kilogramUnitId(),
                line.conversionRuleId(),line.conversionRuleVersion());
    }
    private static Rejection steelDraftInvalid(){return new Rejection(400,"INBOUND_STEEL_RECEIPT_INPUT_INVALID");}

    /** @param rejection 稳定业务拒绝 @return 问题详情，不回显原值 */
    @ExceptionHandler(Rejection.class)
    public ResponseEntity<ProblemDetail> rejectedDraft(Rejection rejection) {
        var detail = ProblemDetail.forStatus(rejection.status());
        detail.setProperty("code", rejection.getMessage());
        return ResponseEntity.status(rejection.status()).cacheControl(CacheControl.noStore()).body(detail);
    }

    /** @param rejection 主数据稳定拒绝 @return 主数据范围受限或引用无效 */
    @ExceptionHandler(MasterData.Rejection.class)
    public ResponseEntity<ProblemDetail> rejectedReference(MasterData.Rejection rejection) {
        return rejectedDraft(new Rejection(rejection.status, rejection.getMessage()));
    }

    /** @return 统一授权拒绝 */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ProblemDetail> forbiddenDraft() {
        return rejectedDraft(new Rejection(403, "INBOUND_RECEIPT_ACCESS_DENIED"));
    }

    /** @return 位置、单位或引用参数错误 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> invalidDraft() {
        return rejectedDraft(new Rejection(400, "INBOUND_RECEIPT_DRAFT_INPUT_INVALID"));
    }
}
