package com.appnexus.bulkwarehousing.adapter.web.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.GroundWeighingUseCase;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.contracts.operation.OperationBinding;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 独立过毛、过皮和保管员监卸入口，所有命令由服务器重验阶段及版本。 */
@RestController
public final class GroundWeighingController {
    private final GroundWeighingUseCase weighing;

    /** @param weighing 受控地磅差重流程 */
    public GroundWeighingController(GroundWeighingUseCase weighing) {
        this.weighing=weighing;
    }

    /** 人工复核的票号、站点、UTC 源时点及十进制公斤读数。 */
    public record GroundWeighTicketRequest(String station, String ticketNo, Instant observedAt, String kg) {}
    /** 毛重请求。 */
    public record RecordGroundGrossRequest(UUID taskId,Long taskVersion,String grossMeteringNo,GroundWeighTicketRequest ticket) {}
    /** 监卸请求。 */
    public record ConfirmGroundUnloadingRequest(Long taskVersion,Long meterVersion,
            GroundWeighingUseCase.UnloadOutcome outcome,String extraDeductionKg) {}
    /** 皮重请求。 */
    public record RecordGroundTareRequest(UUID taskId,Long taskVersion,Long meterVersion,GroundWeighTicketRequest ticket) {}

    /** @param request 可信会话 @param input 毛重命令 @return 已提交版本 */
    @PostMapping("/api/bulk-inbound-metering-orders:record-gross")
    @OperationBinding(id="BulkInboundMeteringOrder.record_gross",reference=true)
    public ResponseEntity<GroundWeighingUseCase.GroundWeighingResult> recordGroundGross(HttpServletRequest request,
            @RequestBody RecordGroundGrossRequest input) {
        return response(weighing.gross(context(request),new GroundWeighingUseCase.Gross(input.taskId(),
                version(input.taskVersion()),input.grossMeteringNo(),ticket(input.ticket()))));
    }

    /** @param request 可信会话 @param id 作业 UUID @param input 监卸及版本 @return 已提交版本 */
    @PostMapping("/api/bulk-inbound-tasks/{id}:confirm-unloading")
    @OperationBinding(id="BulkInboundTask.confirm_unloading",reference=true)
    public ResponseEntity<GroundWeighingUseCase.GroundWeighingResult> confirmGroundUnloading(HttpServletRequest request,
            @PathVariable UUID id,@RequestBody ConfirmGroundUnloadingRequest input) {
        return response(weighing.unload(context(request),new GroundWeighingUseCase.Unload(id,
                version(input.taskVersion()),version(input.meterVersion()),input.outcome(),weight(input.extraDeductionKg()))));
    }

    /** @param request 可信会话 @param input 皮重命令 @return 差重或退车事实 */
    @PostMapping("/api/bulk-inbound-metering-orders:record-tare")
    @OperationBinding(id="BulkInboundMeteringOrder.record_tare",reference=true)
    public ResponseEntity<GroundWeighingUseCase.GroundWeighingResult> recordGroundTare(HttpServletRequest request,
            @RequestBody RecordGroundTareRequest input) {
        return response(weighing.tare(context(request),new GroundWeighingUseCase.Tare(input.taskId(),
                version(input.taskVersion()),version(input.meterVersion()),ticket(input.ticket()))));
    }

    private static ResponseEntity<GroundWeighingUseCase.GroundWeighingResult> response(GroundWeighingUseCase.GroundWeighingResult value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);
    }

    private static GroundWeighingUseCase.Ticket ticket(GroundWeighTicketRequest input) {
        if(input==null) throw invalid();
        return new GroundWeighingUseCase.Ticket(input.station(),input.ticketNo(),input.observedAt(),weight(input.kg()));
    }
    private static BigDecimal weight(String value) {
        if(value==null||!value.matches("[0-9]{1,14}(\\.[0-9]{1,4})?")) throw invalid();
        return new BigDecimal(value);
    }
    private static long version(Long number) {
        if(number==null||number<0) throw invalid();
        return number;
    }
    private static Rejection invalid() { return new Rejection(400,"INBOUND_SCALE_INPUT_INVALID"); }
    private static VerifiedTenantContext context(HttpServletRequest request) {
        if(request.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext value) return value;
        throw new Rejection(403,"INBOUND_METER_ACCESS_DENIED");
    }

    /** @param rejection 稳定错误 @return 不返回凭证内容的问题详情 */
    @ExceptionHandler(Rejection.class)
    public ResponseEntity<ProblemDetail> rejectGroundWeighing(Rejection rejection) {
        var detail=ProblemDetail.forStatus(rejection.status());
        detail.setProperty("code",rejection.getMessage());
        return ResponseEntity.status(rejection.status()).cacheControl(CacheControl.noStore()).body(detail);
    }

    /** @return 统一准入拒绝 */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ProblemDetail> forbiddenGroundWeighing() {
        return rejectGroundWeighing(new Rejection(403,"INBOUND_METER_ACCESS_DENIED"));
    }
}
