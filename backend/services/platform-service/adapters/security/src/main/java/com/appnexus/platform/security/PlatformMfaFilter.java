package com.appnexus.platform.security;

import com.appnexus.platform.identity.MultiFactorAuthentication;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Instant;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/** 平台高风险写入要求 MFA 与近期因子；不能仅靠个人中心提供可选认证按钮。 */
public final class PlatformMfaFilter extends OncePerRequestFilter {
    private final boolean required;
    private final MultiFactorAuthentication mfa;
    /**
     * @param required 默认 true；关闭仅用于受控迁移或测试，生产关闭会降低平台安全基线
     * @param mfa 第二因子权威源
     */
    public PlatformMfaFilter(boolean required,MultiFactorAuthentication mfa){
        this.required=required;this.mfa=mfa;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request){
        String path=request.getRequestURI().substring(request.getContextPath().length());
        return !required||!path.startsWith("/bff/platform/")||java.util.Set.of("GET","HEAD","OPTIONS").contains(request.getMethod());
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null||!auth.isAuthenticated()||auth instanceof AnonymousAuthenticationToken){chain.doFilter(request,response);return;}
        try{
            java.util.UUID account=java.util.UUID.fromString(auth.getName());
            // 对所有平台写入主体检查，不能仅识别旧管理员表而遗漏通过自定义平台角色授权的主体。
            // MFA 成功仍不授予管理权，后续业务用例继续执行原有权限校验。
                if(!mfa.enabled(account)){deny(response,"MFA_ENROLLMENT_REQUIRED");return;}
                var session=request.getSession(false);
                Object value=session==null?null:session.getAttribute(MfaController.VERIFIED_AT);
                Instant now=Instant.now();
                if(!(value instanceof Instant time)||time.isAfter(now)||!time.plusSeconds(300).isAfter(now)){
                    deny(response,"MFA_STEP_UP_REQUIRED");return;
                }
        }catch(org.springframework.dao.DataAccessException unavailable){response.setStatus(503);return;}
        chain.doFilter(request,response);
    }
    private void deny(HttpServletResponse response,String code)throws IOException{
        response.setStatus(403);response.setContentType("application/problem+json");
        response.setHeader("Cache-Control","no-store");
        response.getWriter().write("{\"status\":403,\"title\":\"Second factor required\",\"code\":\""+code+"\"}");
    }
}
