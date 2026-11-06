package com.appnexus.platform.security;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.web.filter.OncePerRequestFilter;
/**
 * 使用 Valkey 共享计数限制登录尝试，避免多实例分别计数导致限流失效。
 * 当前以直连来源地址为准；部署可信代理前不得直接信任客户端提供的转发地址。
 */
public final class LoginAttemptFilter extends OncePerRequestFilter {
    private final StringRedisTemplate redis;
    private final long perAddress;
    private final long perAccountAddress;
    private static final DefaultRedisScript<Long> COUNT = new DefaultRedisScript<>(
        "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]) end; return n", Long.class);
    /**
     * 创建固定 60 秒窗口的登录限流器。
     *
     * @param redis 共享 Valkey 访问入口；计数服务不可用时拒绝登录，避免绕过防护
     * @param perAddress 每个来源地址在 60 秒内允许的请求数，默认配置值为 60
     * @param perAccountAddress 每个账号与来源地址组合在 60 秒内允许的请求数，默认配置值为 10
     */
    public LoginAttemptFilter(StringRedisTemplate redis, long perAddress, long perAccountAddress) {
        this.redis = redis; this.perAddress = perAddress; this.perAccountAddress = perAccountAddress;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getMethod().equals("POST") || !(request.getRequestURI().equals(request.getContextPath() + "/login") ||
            request.getRequestURI().equals(request.getContextPath() + "/bff/account/password"));
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        try {
            String ip = request.getRemoteAddr();
            String username = request.getRequestURI().endsWith("/bff/account/password") && request.getUserPrincipal() != null
                ? request.getUserPrincipal().getName()
                : Objects.toString(request.getParameter("username"), "").toLowerCase(Locale.ROOT);
            long address = redis.execute(COUNT, List.of("appnexus:login:ip:" + hash(ip)), "60");
            long pair = redis.execute(COUNT, List.of("appnexus:login:pair:" + hash(ip + ":" + username)), "60");
            if (address > perAddress || pair > perAccountAddress) {
                response.setStatus(429); response.setHeader("Retry-After", "60"); return;
            }
        } catch (org.springframework.dao.DataAccessException unavailable) {
            response.setStatus(503); return;
        }
        chain.doFilter(request, response);
    }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
