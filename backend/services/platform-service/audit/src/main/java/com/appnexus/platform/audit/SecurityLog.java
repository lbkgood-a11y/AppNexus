package com.appnexus.platform.audit;
import java.time.Instant;
/**
 * 当前账号可查看的最小安全记录；不保存请求正文、凭据或用户代理原文。
 * @param id 数据库递增记录标识，仅用于排序与分页
 * @param action 稳定事件代码，由受控枚举产生
 * @param occurredAt 服务端 UTC 时间点
 */
public record SecurityLog(long id, Action action, Instant occurredAt) {
    /** 仅包含当前已经实现的成功操作，不能据此推断失败尝试未发生。 */
    public enum Action { LOGIN, LOGOUT, PROFILE_UPDATED, PASSWORD_CHANGED, OTHER_SESSIONS_REVOKED }
}
