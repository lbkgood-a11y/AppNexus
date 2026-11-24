package com.appnexus.bulkwarehousing.adapter.web.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.RegisterExternalMeasurementUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.ConfirmSteelMeasurementUseCase;
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

/** 独立计量写入口，作业工作台调用同一操作。 */
@RestController
public final class InboundMeteringOrderController {
    private final RegisterExternalMeasurementUseCase useCase;
    private final ConfirmSteelMeasurementUseCase steel;

    /** @param useCase 外部整单计量确认 @param steel 钢铁双计量 */
    public InboundMeteringOrderController(RegisterExternalMeasurementUseCase useCase,ConfirmSteelMeasurementUseCase steel) {
        this.useCase=useCase;this.steel=steel;
    }

    /** 人工复核凭证请求，净重正数且最多四位小数，单位千克。 */
    public record RegisterExternalMeasurementRequest(UUID taskId, Long taskVersion, String meteringNo,
            RegisterExternalMeasurementUseCase.Source source, String externalReference, String netKg) {}

    /** 两次人工复核地磅票据的源时点和公斤读数。 */
    public record SteelWeighingTicketRequest(String station,String ticketNo,Instant observedAt,String kg) {}
    /** 钢铁计量口径、件数、公斤理重和可选磅票。 */
    public record ConfirmSteelMeasurementRequest(UUID taskId,Long taskVersion,String steelMeteringNo,
            ConfirmSteelMeasurementUseCase.Basis basis,Integer packages,String theoreticalKg,
            SteelWeighingTicketRequest gross,SteelWeighingTicketRequest tare) {}

    /** @param request 安全链验证的可信会话 @param input 外部凭证及作业版本 @return 已提交计量结果 */
    @PostMapping("/api/bulk-inbound-metering-orders:register-external")
    @OperationBinding(id="BulkInboundMeteringOrder.register_external", reference=true)
    public ResponseEntity<RegisterExternalMeasurementUseCase.ExternalMeasurementRegistration> registerExternal(HttpServletRequest request,
            @RequestBody RegisterExternalMeasurementRequest input) {
        if (!(request.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext context)) {
            throw new Rejection(403, "INBOUND_METER_ACCESS_DENIED");
        }
        if (input.taskVersion() == null || input.netKg() == null
                || !input.netKg().matches("[0-9]{1,14}(\\.[0-9]{1,4})?")) {
            throw new Rejection(400, "INBOUND_METER_INPUT_INVALID");
        }
        var result = useCase.registerExternal(context, new RegisterExternalMeasurementUseCase.Command(input.taskId(),
                input.taskVersion(), input.meteringNo(), input.source(), input.externalReference(), new BigDecimal(input.netKg())));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }

    /** @param request 可信会话 @param input 实际和理计事实及作业版本 @return 已确认双计量 */
    @PostMapping("/api/bulk-inbound-metering-orders:confirm-steel")
    @OperationBinding(id="BulkInboundMeteringOrder.confirm_steel",reference=true)
    public ResponseEntity<ConfirmSteelMeasurementUseCase.SteelMeasurement> confirmSteel(HttpServletRequest request,
            @RequestBody ConfirmSteelMeasurementRequest input) {
        if(!(request.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext context))
            throw new Rejection(403,"INBOUND_METER_ACCESS_DENIED");
        if(input.taskVersion()==null||input.packages()==null) throw steelInvalid();
        var result=steel.confirm(context,new ConfirmSteelMeasurementUseCase.Command(input.taskId(),input.taskVersion(),
                input.steelMeteringNo(),input.basis(),input.packages(),steelWeight(input.theoreticalKg()),
                steelTicket(input.gross()),steelTicket(input.tare())));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }

    private static GroundWeighingUseCase.Ticket steelTicket(SteelWeighingTicketRequest value) {
        return value==null?null:new GroundWeighingUseCase.Ticket(value.station(),value.ticketNo(),value.observedAt(),steelWeight(value.kg()));
    }
    private static BigDecimal steelWeight(String v) {
        if(v==null||!v.matches("[0-9]{1,14}(\\.[0-9]{1,4})?")) throw steelInvalid();
        return new BigDecimal(v);
    }
    private static Rejection steelInvalid() {return new Rejection(400,"INBOUND_STEEL_MEASUREMENT_INPUT_INVALID");}

    /** @param rejection 稳定业务拒绝 @return 问题详情，不回显业务输入 */
    @ExceptionHandler(Rejection.class)
    public ResponseEntity<ProblemDetail> meterRejected(Rejection rejection) {
        var detail = ProblemDetail.forStatus(rejection.status());
        detail.setProperty("code", rejection.getMessage());
        return ResponseEntity.status(rejection.status()).cacheControl(CacheControl.noStore()).body(detail);
    }

    /** @return 禁止未获准访问 */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ProblemDetail> meterForbidden() {
        return meterRejected(new Rejection(403, "INBOUND_METER_ACCESS_DENIED"));
    }
}
