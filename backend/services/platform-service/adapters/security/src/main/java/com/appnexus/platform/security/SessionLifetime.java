package com.appnexus.platform.security;

import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.time.Instant;

/** Web 会话绝对期限；持续访问和租户切换不得延长认证有效期。 */
public final class SessionLifetime {
    /** 最近一次真实密码认证的 UTC 时间；OAuth 回调不得刷新此值。 */
    public static final String AUTHENTICATED_AT = "appnexus.authenticatedAt";
    private final Duration maximum;

    /**
     * @param maximum 绝对有效期，必须大于零且不超过 24 小时；部署可收紧，不能关闭
     * @throws IllegalArgumentException 配置超出平台安全上限时阻止启动
     */
    public SessionLifetime(Duration maximum) {
        if (maximum == null || maximum.isNegative() || maximum.isZero()
                || maximum.compareTo(Duration.ofHours(24)) > 0)
            throw new IllegalArgumentException("会话绝对有效期必须大于零且不超过 24 小时");
        this.maximum = maximum;
    }

    /**
     * 判断可信服务端会话是否到期；旧会话使用创建时间，不能在首次访问时延长寿命。
     * @param session 服务端会话；缺失时默认拒绝
     * @param now 服务端当前 UTC 时间，不接受客户端时间
     * @return 期限已到、时间证据异常或会话缺失时返回 true
     */
    public boolean expired(HttpSession session, Instant now) {
        if (session == null) return true;
        return expired(session.getAttribute(AUTHENTICATED_AT),Instant.ofEpochMilli(session.getCreationTime()),now);
    }

    /**
     * 在线会话目录与请求过滤器共用绝对期限，不能将已到期的其他登录展示为有效会话。
     * @param session 主体索引取得的共享会话，不接受浏览器提交的会话内容
     * @param now 服务端当前 UTC 时间
     * @return 绝对期限已到或证据异常时返回 true
     */
    public boolean expired(org.springframework.session.Session session, Instant now) {
        if(session==null)return true;
        return expired(session.getAttribute(AUTHENTICATED_AT),session.getCreationTime(),now);
    }
    private boolean expired(Object evidence,Instant created,Instant now){
        if (evidence != null && !(evidence instanceof Instant)) return true;
        Instant start = evidence instanceof Instant instant ? instant : created;
        return start.isAfter(now) || !now.isBefore(start.plus(maximum));
    }
}
