package com.appnexus.platform.managementapi.administration;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.appnexus.contracts.businessobject.InteractionContract;
import java.util.List;
import java.util.Map;
import com.appnexus.platform.tenant.TaskManagement;
import org.springframework.stereotype.Component;

/** 平台出站任务的专用 API 和定时系统操作声明；恢复与审计由原用例同事务完成。 */
@Component
public final class PlatformTaskDeclaration implements Provider {
    /** @return 由固定 Controller 或事件 Worker 执行的操作契约，无公共命令入口 */
    @Override
    public Descriptor descriptor() {
        var access = new DataAccess("PLATFORM", List.of(
                new DataResource(TaskManagement.RESOURCE, "", "平台任务", "Platform tasks", List.of())), Map.of());
        return new Descriptor("PlatformTask", "平台任务", "Platform task", "platform", 4,
                Category.LEDGER, List.of(), List.of(
                        operation("list", "查询平台任务", "List platform tasks", "platform.task:read"),
                        operation("retry", "重试平台任务", "Retry platform task", "platform.task:retry"),
                        operation("listEvents", "查询平台事件", "List platform events", "platform.task:read"),
                        operation("readEvent", "查看事件投递", "Read event deliveries", "platform.task:read"),
                        operation("retryDelivery", "重试事件投递", "Retry event delivery", "platform.task:retry"),
                        operation("listSubscriptions", "查看事件订阅", "Read event subscriptions", "platform.task:read"),
                        operation("setSubscriptionState", "管理订阅执行状态", "Manage subscription execution", "platform.task:subscription_state"),
                        scheduledProcessing()), access);
    }

    private Operation operation(String code, String nameZh, String nameEn, String permission) {
        String id = "PlatformTask." + code;
        var interaction = new InteractionContract(id, 1, "PlatformTask", code, nameZh, nameEn,
                InteractionContract.Trigger.USER_ACTION, List.of());
        return new Operation(code, nameZh, nameEn, permission, id, null, List.of(), interaction);
    }

    private Operation scheduledProcessing() {
        var interaction = new InteractionContract(TaskManagement.PROCESS_EVENTS, 1, "PlatformTask", "processEvents",
                "消费平台事件", "Process platform events", InteractionContract.Trigger.SCHEDULE, List.of());
        return new Operation("processEvents", "消费平台事件", "Process platform events",
                TaskManagement.EVENT_WORKER_AUTHORITY, TaskManagement.PROCESS_EVENTS, null, List.of(), interaction);
    }
}
