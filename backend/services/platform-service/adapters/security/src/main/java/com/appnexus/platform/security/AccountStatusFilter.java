package com.appnexus.platform.security;
import com.appnexus.platform.identity.AccountRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;
/** 每次受保护请求复核账号与凭据版本、绝对期限；权威状态无法确认时拒绝放行。 */
public final class AccountStatusFilter extends OncePerRequestFilter {
    private final AccountRepository accounts;
    private final SessionLifetime lifetime;
    /**
     * @param accounts 账号权威仓储，查询故障时拒绝请求
     * @param lifetime Web 绝对期限策略；不用于替代 Bearer Token 有效期校验
     */
    public AccountStatusFilter(AccountRepository accounts, SessionLifetime lifetime) {
        this.accounts = accounts;
        this.lifetime = lifetime;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !(path.startsWith("/bff/") || path.startsWith("/api/") || path.equals("/oauth2/authorize") || path.equals("/userinfo"));
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            // 机密集成客户端由不透明 Token 内省逐次复核租户、启停和权限版本，不映射为人员账号。
            if (auth.getAuthorities().stream().anyMatch(value -> value.getAuthority().equals("PARTNER_CLIENT"))) {
                String path=request.getRequestURI().substring(request.getContextPath().length());
                if(path.startsWith("/api/printing/agent/"))chain.doFilter(request, response);
                else response.setStatus(401);
                return;
            }
            // OIDC 签发要求真实认证因子的时间。旧会话或 BFF 身份没有该证据时重新认证，
            // 不能以当前请求时间伪造 auth_time，也不能让异常扩散为授权码换取失败。
            if(request.getRequestURI().equals(request.getContextPath()+"/oauth2/authorize") &&
                    auth.getAuthorities().stream().noneMatch(authority -> authority instanceof
                        org.springframework.security.core.authority.FactorGrantedAuthority)) {
                // 此会话同时承载 BFF 已发起的 OAuth state/PKCE 请求，不能整会话注销，
                // 否则密码认证完成后回调会丢失 authorization request。只移除旧认证并轮换 ID。
                SecurityContextHolder.clearContext();
                var legacySession=request.getSession(false);
                if(legacySession!=null) {
                    legacySession.removeAttribute("SPRING_SECURITY_CONTEXT");
                    legacySession.removeAttribute(CredentialSession.VERSION);
                    request.changeSessionId();
                }
                chain.doFilter(request,response);
                return;
            }
            boolean active;
            try {
                var account = accounts.findById(UUID.fromString(auth.getName())).orElse(null);
                var session = request.getSession(false);
                // Bearer 调用的凭据由授权记录校验；Cookie 会话必须带认证时的版本，缺失时默认拒绝。
                boolean bearer = auth instanceof org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
                active = account != null && account.enabled() && (bearer ||
                    (session != null && !lifetime.expired(session, java.time.Instant.now()) &&
                        Long.valueOf(account.version()).equals(session.getAttribute(CredentialSession.VERSION))));
            }
            catch (IllegalArgumentException invalid) { active = false; }
            catch (org.springframework.dao.DataAccessException unavailable) { response.setStatus(503); return; }
            if (!active) {
                new SecurityContextLogoutHandler().logout(request, response, auth);
                response.setStatus(401); return;
            }
        }
        chain.doFilter(request, response);
    }
}
