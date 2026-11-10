package com.appnexus.platform.security;

import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 平台管理与租户业务的统一操作拦截器；读取方法登记权限及服务端可信主体。
 * 平台代管使用显式平台能力，目标归属、行/字段范围及事务权威校验仍由领域用例执行。
 */
public final class EndpointOperationInterceptor implements HandlerInterceptor {
    private final AccessAuthorization authorization;
    public EndpointOperationInterceptor(AccessAuthorization authorization){this.authorization=authorization;}

    @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) throws IOException {
        if(!(handler instanceof HandlerMethod method))return true;
        var operation=method.getMethodAnnotation(EndpointOperation.class);
        String path=request.getRequestURI().substring(request.getContextPath().length());
        // 组织域已有范围感知过滤器和 GRANT 专用错误契约，避免重复拦截改变探测及错误语义。
        if(operation==null)return true;
        if(path.startsWith("/bff/platform/")){
            var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if(auth==null||!auth.isAuthenticated()||auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)
                return reject(response,401,"AUTHENTICATION_REQUIRED");
            var subject=AccessAuthorization.Subject.platform(java.util.UUID.fromString(auth.getName()));
            // 平台代管必须显式登记平台能力；目标租户/成员/角色归属继续由用例复核。
            String required=operation.platformPermission().isBlank()?operation.value():operation.platformPermission();
            if(!authorization.hasPermission(subject,required)){
                // 前置操作门禁沿用各管理入口的既有拒绝契约，避免鉴权提前执行改变调用方错误处理。
                String code=switch(method.getBeanType().getSimpleName()) {
                    case "AuthorizationManagementController", "PolicyTemplateController" -> "AUTHORIZATION_MANAGEMENT_DENIED";
                    case "RoleManagementController" -> "ROLE_MANAGEMENT_DENIED";
                    default -> "PLATFORM_DENIED";
                };
                return reject(response,403,code);
            }
            return true;
        }
        if(!path.startsWith("/api/")||(path.startsWith("/api/organization/")&&!path.equals("/api/organization/summaries")))return true;
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
