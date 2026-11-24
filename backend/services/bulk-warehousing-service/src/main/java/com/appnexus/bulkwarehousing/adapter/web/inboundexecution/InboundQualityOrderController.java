package com.appnexus.bulkwarehousing.adapter.web.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.RegisterInboundSampleUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.DecideInboundQualityUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.InspectInboundSteelUseCase;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.contracts.operation.OperationBinding;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 独立质检单写入口，工作台可复用该操作。 */
@RestController
public final class InboundQualityOrderController {
    private final RegisterInboundSampleUseCase samples;
    private final DecideInboundQualityUseCase decisions;
    private final InspectInboundSteelUseCase steel;

    /** @param samples 采样用例 @param decisions 实验室判定 @param steel 钢铁附件核验 */
    public InboundQualityOrderController(RegisterInboundSampleUseCase samples, DecideInboundQualityUseCase decisions,
                                         InspectInboundSteelUseCase steel) {
        this.samples = samples;
        this.decisions = decisions;
        this.steel = steel;
    }

    /** 采样及复检请求版本。复检时同时提供旧质检 ID 和行版本。 */
    public record RegisterSampleRequest(UUID taskId, Long taskVersion, UUID priorQualityId, Long priorRowVersion,
                                        String qualityNo, String barcode) {}

    /** 实测数值使用作业模板中的指标单位；请求不含判定结论。 */
    public record DecideInboundQualityRequest(UUID taskId, Long taskVersion, Long qualityRowVersion,
                                              Map<String, BigDecimal> metrics) {}
    /** 钢铁质保书和照片引用及人工核验结论。 */
    public record InspectInboundSteelRequest(UUID taskId, Long taskVersion, String steelQualityNo,
            String heatNo,String certificateNo,UUID certificateId,UUID appearanceId,
            Boolean certificateVerified,Boolean appearanceVerified) {}

    /**
     * @param request 安全链验证后的可信租户请求
     * @param input 采样或复检的作业、版本、编码和样品条码
     * @return 已登记质检单 UUID 及作业版本
     */
    @PostMapping("/api/bulk-inbound-quality-orders:register-sample")
    @OperationBinding(id="BulkInboundQualityOrder.register_sample", reference=true)
    public ResponseEntity<RegisterInboundSampleUseCase.Registered> registerSample(HttpServletRequest request,
            @RequestBody RegisterSampleRequest input) {
        if (input.taskVersion() == null) throw new Rejection(400, "INBOUND_SAMPLE_INPUT_INVALID");
        var result = samples.register(context(request), new RegisterInboundSampleUseCase.Command(input.taskId(),
                input.taskVersion(), input.priorQualityId(), input.priorRowVersion(), input.qualityNo(), input.barcode()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }

    /**
     * @param request 已通过租户会话验证的请求
     * @param id 当前质检单 UUID
     * @param input 作业与质检行版本及实测指标
     * @return 同作业模板计算的判定和新版本
     */
    @PostMapping("/api/bulk-inbound-quality-orders/{id}:decide")
    @OperationBinding(id="BulkInboundQualityOrder.decide_quality", reference=true)
    public ResponseEntity<DecideInboundQualityUseCase.Decided> decideQuality(HttpServletRequest request,
            @PathVariable UUID id, @RequestBody DecideInboundQualityRequest input) {
        if (input.taskVersion() == null || input.qualityRowVersion() == null) {
            throw new Rejection(400, "INBOUND_LAB_INPUT_INVALID");
        }
        var result = decisions.decide(context(request), new DecideInboundQualityUseCase.Command(input.taskId(),
                input.taskVersion(), id, input.qualityRowVersion(), input.metrics()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }

    /** @param request 可信认证会话 @param input 关联原作业的外观和质保书 @return 判定及作业版本 */
    @PostMapping("/api/bulk-inbound-quality-orders:inspect-steel")
    @OperationBinding(id="BulkInboundQualityOrder.inspect_steel",reference=true)
    public ResponseEntity<InspectInboundSteelUseCase.SteelInspection> inspectSteel(HttpServletRequest request,
            @RequestBody InspectInboundSteelRequest input) {
        if(input.taskVersion()==null || input.certificateVerified()==null || input.appearanceVerified()==null) {
            throw new Rejection(400,"INBOUND_STEEL_INSPECTION_INPUT_INVALID");
        }
        var result=steel.inspect(context(request),new InspectInboundSteelUseCase.Command(input.taskId(),
                input.taskVersion(),input.steelQualityNo(),input.heatNo(),input.certificateNo(),input.certificateId(),
                input.appearanceId(),input.certificateVerified(),input.appearanceVerified()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }

    private static VerifiedTenantContext context(HttpServletRequest request) {
        if (request.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext subject) {
            return subject;
        }
        throw new Rejection(403, "INBOUND_SAMPLE_ACCESS_DENIED");
    }

    /** @param rejection 稳定业务错误 @return 不含请求原文的问题详情 */
    @ExceptionHandler(Rejection.class)
    public ResponseEntity<ProblemDetail> sampleRejected(Rejection rejection) {
        var detail = ProblemDetail.forStatus(rejection.status());
        detail.setProperty("code", rejection.getMessage());
        return ResponseEntity.status(rejection.status()).cacheControl(CacheControl.noStore()).body(detail);
    }

    /** @return 统一准入失败 */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ProblemDetail> sampleForbidden() {
        return sampleRejected(new Rejection(403, "INBOUND_SAMPLE_ACCESS_DENIED"));
    }
}
