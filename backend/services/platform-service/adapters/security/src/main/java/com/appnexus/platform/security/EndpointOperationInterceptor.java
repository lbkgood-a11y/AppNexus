package com.appnexus.platform.security;

import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 租户业务端点的统一功能权限拦截器；只消费服务端已验证上下文和方法登记权限码。
 * 平台 BFF 与本人自助端点继续由各自用例权威复核，禁止把租户权限错误套用于平台代管。
 */
public final class EndpointOperationInterceptor implements HandlerInterceptor {
    private final AccessAuthorization authorization;
    public EndpointOperationInterceptor(AccessAuthorization authorization){this.authorization=authorization;}

    @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) throws IOException {
        if(!(handler instanceof HandlerMethod method))return true;
        var operation=method.getMethodAnnotation(EndpointOperation.class);
        String path=request.getRequestURI().substring(request.getContextPath().length());
        // 组织域已有范围感知过滤器和 GRANT 专用错误契约，避免重复拦截改变探测及错误语义。
        if(operation==null||!path.startsWith("/api/")||path.startsWith("/api/organization/"))return true;
        var value=request.getAttribute(TenantExecutionFilter.ATTRIBUTE);
        if(!(value instanceof VerifiedTenantContext context))return reject(response,409,"TENANT_REQUIRED");
        var subject=AccessAuthorization.Subject.tenant(context.accountId(),context.tenantId(),context.memberId());
        if(!authorization.hasPermission(subject,operation.value()))return reject(response,403,"OPERATION_NOT_AUTHORIZED");
        return true;
    }
    /** 错误响应只返回稳定码，不暴露角色、权限来源或其他租户对象。 */
    private boolean reject(HttpServletResponse response,int status,String code) throws IOException {
        response.setStatus(status);response.setContentType("application/problem+json");response.setHeader("Cache-Control","no-store");
        response.getWriter().write("{\"status\":"+status+",\"code\":\""+code+"\"}");return false;
    }
}
