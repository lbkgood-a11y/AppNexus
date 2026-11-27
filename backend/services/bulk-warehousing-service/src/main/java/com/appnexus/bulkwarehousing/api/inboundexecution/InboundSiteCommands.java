package com.appnexus.bulkwarehousing.api.inboundexecution;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.time.Instant;
import java.util.UUID;

/** 入库现场异步指令与受控回执。 */
public interface InboundSiteCommands {
    String ROOT="/api/bulk-inbound-workbench/tasks/{id}/site-commands";
    String GATE=ReadInboundWorkbenchUseCase.RESOURCE+":raise_site_gate";
    String CALL=ReadInboundWorkbenchUseCase.RESOURCE+":call_site_number";
    String PRINT=ReadInboundWorkbenchUseCase.RESOURCE+":print_site_ticket";

    /** 固定执行意图，设备端不可收到客户端自定义指令。 */
    enum Action {
        RAISE_GATE(GATE), CALL_NUMBER(CALL), PRINT_TASK_SLIP(PRINT);
        private final String permission;
        Action(String permission){this.permission=permission;}
        /** @return 当前动作授权码 */
        public String permission(){return permission;}
    }

    /** 请求标识和显式重试意图。超时重试必须复用原标识。 */
    record InboundSiteCommandRequest(UUID commandId,boolean retry) {}
    /** 当前指令及受控回执。 */
    record InboundSiteCommandStatus(UUID commandId,UUID taskId,Action action,String state,
                                    Instant sentAt,Instant acknowledgedAt,boolean simulated) {}
    /** @return 该车次固定动作的提交或重试状态 */
    InboundSiteCommandStatus send(VerifiedTenantContext context,UUID task,Action action,InboundSiteCommandRequest input);
    /** @return 当前授权范围内的指令状态 */
    InboundSiteCommandStatus status(VerifiedTenantContext context,UUID task,UUID commandId);
}
