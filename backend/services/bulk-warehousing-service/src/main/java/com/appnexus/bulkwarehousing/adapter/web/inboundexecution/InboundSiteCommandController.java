package com.appnexus.bulkwarehousing.adapter.web.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.InboundSiteCommands;
import com.appnexus.bulkwarehousing.api.inboundexecution.InboundSiteCommands.*;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.contracts.operation.OperationBinding;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 原工作台下的车次设备命令和回执。 */
@RestController
@RequestMapping(InboundSiteCommands.ROOT)
public final class InboundSiteCommandController {
    private final InboundSiteCommands commands;
    /** @param commands 授权现场设备用例 */
    public InboundSiteCommandController(InboundSiteCommands commands){this.commands=commands;}
    /** @return 当前授权动作的车次回执状态 */
    @PostMapping("/gate")
    @OperationBinding(id="BulkInboundTask.raise_site_gate",reference=true)
    public ResponseEntity<InboundSiteCommandStatus> gateInboundSiteCommand(HttpServletRequest r,@PathVariable UUID id,
            @RequestBody InboundSiteCommandRequest input){return result(commands.send(context(r),id,Action.RAISE_GATE,input));}
    /** @return 当前授权动作的车次回执状态 */
    @PostMapping("/call")
    @OperationBinding(id="BulkInboundTask.call_site_number",reference=true)
    public ResponseEntity<InboundSiteCommandStatus> callInboundSiteCommand(HttpServletRequest r,@PathVariable UUID id,
            @RequestBody InboundSiteCommandRequest input){return result(commands.send(context(r),id,Action.CALL_NUMBER,input));}
    /** @return 当前授权动作的车次回执状态 */
    @PostMapping("/print")
    @OperationBinding(id="BulkInboundTask.print_site_ticket",reference=true)
    public ResponseEntity<InboundSiteCommandStatus> printInboundSiteCommand(HttpServletRequest r,@PathVariable UUID id,
            @RequestBody InboundSiteCommandRequest input){return result(commands.send(context(r),id,Action.PRINT_TASK_SLIP,input));}
    /** @return 不含设备原始响应及物流身份的状态 */
    @GetMapping("/{commandId}")
    @OperationBinding(id="BulkInboundTask.read_workbench_detail",reference=true)
    public ResponseEntity<InboundSiteCommandStatus> inboundSiteCommandStatus(HttpServletRequest r,@PathVariable UUID id,
            @PathVariable UUID commandId){return result(commands.status(context(r),id,commandId));}
    private static ResponseEntity<InboundSiteCommandStatus> result(InboundSiteCommandStatus value){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);
    }
    private static VerifiedTenantContext context(HttpServletRequest request){
        if(request.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext context)return context;
        throw new Rejection(403,"INBOUND_SITE_COMMAND_ACCESS_DENIED");
    }
    /** @return 稳定拒绝码 */
    @ExceptionHandler(Rejection.class)
    public ResponseEntity<ProblemDetail> rejected(Rejection rejection){
        var body=ProblemDetail.forStatus(rejection.status());
        body.setProperty("code",rejection.getMessage());
        return ResponseEntity.status(rejection.status()).cacheControl(CacheControl.noStore()).body(body);
    }
    /** @return 当前授权不足 */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ProblemDetail> denied(){return rejected(new Rejection(403,"INBOUND_SITE_COMMAND_ACCESS_DENIED"));}
}
