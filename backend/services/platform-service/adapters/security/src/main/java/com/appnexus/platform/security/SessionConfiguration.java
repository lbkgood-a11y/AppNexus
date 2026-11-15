package com.appnexus.platform.security;
import org.springframework.context.annotation.*;
import org.springframework.session.web.http.*;
import org.springframework.beans.factory.annotation.Value;
/** 会话 Cookie 与 Redis 结构解析配置；不修改安全属性或授权生命周期。 */
@Configuration(proxyBeanMethods = false)
public class SessionConfiguration {
    /** 缺失生命周期字段的 Redis 残留散列按失效处理；不清空其他用户会话。 */
    @Bean
    org.springframework.session.config.SessionRepositoryCustomizer<org.springframework.session.data.redis.RedisIndexedSessionRepository> failClosedSessionMapping(){
        return repository->repository.setRedisSessionMapper(new FailClosedSessionMapper());
    }
    /**
     * 会话仅通过 HttpOnly Cookie 传递，生产 HTTPS 默认启用 Secure。
     * @param secure 是否仅经 HTTPS 发送；默认 true，本机 HTTP 由 local 配置显式关闭
     * @return SameSite=Lax 的统一 Cookie 序列化器
     */
    @Bean
    CookieSerializer cookieSerializer(@Value("${server.servlet.session.cookie.secure:true}") boolean secure) {
        var serializer = new DefaultCookieSerializer();
        serializer.setCookieName("JSESSIONID");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(secure);
        serializer.setSameSite("Lax");
        return serializer;
    }
}
