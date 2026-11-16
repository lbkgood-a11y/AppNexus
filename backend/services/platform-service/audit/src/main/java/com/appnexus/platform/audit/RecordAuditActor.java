package com.appnexus.platform.audit;

import java.util.UUID;

/** 记录审计的可信账号来源；实现只能读取认证上下文，不能读取请求体或操作者请求头。 */
@FunctionalInterface
public interface RecordAuditActor {
    /** @return 实际认证账号 UUID；无账号的后台任务、匿名请求或机器主体返回 null，不冒用用户 */
    UUID accountId();
}
