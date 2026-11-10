package com.appnexus.platform.security;

import com.appnexus.platform.identity.AccountRepository;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

/** 将认证时的凭据版本绑定到共享会话；改密后旧版本会话不能自动升级为新版本。 */
public final class CredentialSession extends SavedRequestAwareAuthenticationSuccessHandler {
    public static final String VERSION = "appnexus.credentialVersion";
    private final AccountRepository accounts;
    private final boolean callback;
    private final com.appnexus.platform.audit.SecurityLogRepository logs;
    private final com.appnexus.platform.identity.MultiFactorAuthentication mfa;
    private final String failureUrl;

    /**
     * @param accounts 账号权威状态查询入口
     * @param target 成功后允许跳转的固定地址，不接受客户端任意跳转地址
     * @param logs 安全日志端口；成功登录必须完成日志写入后才返回
     * @param always 是否始终使用固定地址；BFF 回调为 true，密码认证恢复已保存授权请求
     * @param mfa 第二因子权威端口；启用后必须成功消费验证码才能建立认证会话
     * @param failureUrl 固定可信登录失败地址，不接受用户输入的回跳地址
     */
    public CredentialSession(AccountRepository accounts, String target, boolean always, com.appnexus.platform.audit.SecurityLogRepository logs,
            com.appnexus.platform.identity.MultiFactorAuthentication mfa,String failureUrl) {
        this.accounts = accounts;
        this.callback = always;
        this.logs = logs;
        this.mfa = mfa;
        this.failureUrl = failureUrl;
        setDefaultTargetUrl(target);
        setAlwaysUseDefaultTargetUrl(always);
    }
    /** 登录后绑定当前凭据版本；OAuth 回调不覆盖密码认证阶段已经记录的旧版本。 */
    @Override public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, jakarta.servlet.ServletException {
        var account = accounts.findById(UUID.fromString(authentication.getName())).filter(a -> a.enabled()).orElse(null);
        var session = request.getSession();
        var previous = session.getAttribute(VERSION);
        if (account == null || (callback && previous != null && !Long.valueOf(account.version()).equals(previous))) {
            new org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler()
                .logout(request, response, authentication);
            response.sendError(401);
            return;
        }
        if (!callback) {
            // 密码认证尚不足以建立已绑定 MFA 的账号会话；失败时移除框架刚建立的认证。
            try {
                if(mfa.enabled(account.id())) {
                    if(!mfa.verify(account.id(),request.getParameter("mfaCode"))) {
                        new org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler().logout(request,response,authentication);
                        logs.append(account.id(),com.appnexus.platform.audit.SecurityLog.Action.MFA_FAILED);
                        response.sendRedirect(failureUrl);
                        return;
                    }
                    session.setAttribute(MfaController.VERIFIED_AT,java.time.Instant.now());
                }
            } catch(RuntimeException failure) {
                new org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler().logout(request,response,authentication);
                response.sendError(503);return;
            }
            try { logs.append(account.id(), com.appnexus.platform.audit.SecurityLog.Action.LOGIN); }
            catch (RuntimeException failure) {
                new org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler().logout(request, response, authentication);
                throw failure;
            }
        }
        session.setAttribute(VERSION, account.version());
        if (!callback) session.setAttribute(SessionLifetime.AUTHENTICATED_AT, java.time.Instant.now());
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
