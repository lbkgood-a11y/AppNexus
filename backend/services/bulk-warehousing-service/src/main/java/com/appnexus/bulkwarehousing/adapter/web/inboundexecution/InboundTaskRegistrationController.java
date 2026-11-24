package com.appnexus.bulkwarehousing.adapter.web.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.CreateInboundTaskUseCase;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.contracts.operation.OperationBinding;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 来源通知详情上的车次登记入口。 */
@RestController
public final class InboundTaskRegistrationController {
    private final CreateInboundTaskUseCase registration;

    /** @param registration 已接通统一授权的登记用例 */
    public InboundTaskRegistrationController(CreateInboundTaskUseCase registration) { this.registration=registration; }

    /**
     * @param request 可信租户会话
     * @param id 来源通知 UUID
     * @return 当前通知已授权的模板和最近 20 条车次
     */
    @GetMapping(CreateInboundTaskUseCase.PATH)
    @OperationBinding(id="BulkInboundTask.create_from_notice", reference=true)
    public ResponseEntity<CreateInboundTaskUseCase.InboundArrivalContext> inboundNoticeArrivals(HttpServletRequest request,@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(registration.context(subject(request),id));
    }

    /**
     * @param request 可信租户会话
     * @param id 来源通知 UUID
     * @param command 到场信息及稳定请求编号
     * @return 已登记车次及当前状态
     */
    @PostMapping(CreateInboundTaskUseCase.PATH)
    @OperationBinding(id="BulkInboundTask.create_from_notice", reference=true)
    public ResponseEntity<CreateInboundTaskUseCase.InboundArrivalTask> registerInboundNoticeArrival(HttpServletRequest request,@PathVariable UUID id,
            @RequestBody CreateInboundTaskUseCase.InboundArrivalCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(registration.create(subject(request),id,command));
    }

    private static VerifiedTenantContext subject(HttpServletRequest request) {
        if(request.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext context)return context;
        throw new Rejection(403,"INBOUND_TASK_ACCESS_DENIED");
    }

    /** @return 稳定错误码，不回显车辆标识 */
    @ExceptionHandler(Rejection.class)
    public ResponseEntity<ProblemDetail> rejected(Rejection error) {
        var problem=ProblemDetail.forStatus(error.status());
        problem.setProperty("code",error.getMessage());
        return ResponseEntity.status(error.status()).cacheControl(CacheControl.noStore()).body(problem);
    }

    /** @return 授权失败响应 */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ProblemDetail> denied() { return rejected(new Rejection(403,"INBOUND_TASK_ACCESS_DENIED")); }
}
