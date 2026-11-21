package com.appnexus.platform.security;

import com.appnexus.contracts.businessobject.BusinessObjects.Provider;
import com.appnexus.contracts.businessobject.InteractionContract.Trigger;
import java.util.List;
import org.springframework.stereotype.Component;

/** 从统一对象声明校验进程内定时操作；仅供固定调度入口调用。 */
@Component
public final class ScheduledObjectOperations {
    private final List<Provider> providers;

    /** @param providers 当前应用装配的唯一对象声明集合 */
    public ScheduledObjectOperations(List<Provider> providers) {
        this.providers = List.copyOf(providers);
    }

    /**
     * 校验固定 Worker 绑定的系统操作，禁止把 HTTP 或公共命令操作作为调度权限。
     * @param operationId 由受控调用点固定的操作标识
     * @param authority 由调度入口固定的系统准入标识，无账号角色授权
     * @throws SecurityException 声明缺失、重复、准入不匹配或打开了页面及公共命令入口
     */
    void require(String operationId, String authority) {
        var matches = providers.stream().flatMap(provider -> provider.descriptor().operations().stream())
                .filter(operation -> operation.operationId().equals(operationId)).toList();
        if (matches.size() == 1) {
            var operation = matches.getFirst();
            if (authority.startsWith("system.") && authority.equals(operation.permissionCode())
                    && operation.interaction().trigger() == Trigger.SCHEDULE
                    && operation.pageOperation() == null && operation.aliases().isEmpty()
                    && operation.supportedServices().isEmpty()) {
                return;
            }
        }
        throw new SecurityException("SCHEDULED_OBJECT_OPERATION_DENIED:" + operationId);
    }
}
