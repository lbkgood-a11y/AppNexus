package com.appnexus.platform.audit;
import java.time.Instant;
/**
 * 当前账号可查看的最小安全记录；不保存请求正文、凭据或用户代理原文。
 * @param id 数据库递增记录标识，仅用于排序与分页
 * @param action 稳定事件代码，由受控枚举产生
 * @param occurredAt 服务端 UTC 时间点
 */
public record SecurityLog(long id, Action action, Instant occurredAt) {
    /** 已接入的安全事件；失败事件不保存提交的密码、验证码或原始请求内容。 */
    public enum Action { LOGIN, LOGOUT, PROFILE_UPDATED, PASSWORD_CHANGED, SESSION_REVOKED, OTHER_SESSIONS_REVOKED,
        MFA_ENABLED, MFA_DISABLED, MFA_RECOVERY_USED, MFA_STEP_UP, PASSWORD_RECOVERY_ISSUED, PASSWORD_RECOVERED,
        LOGIN_FAILED, MFA_FAILED, REFRESH_TOKEN_REPLAY }
}
