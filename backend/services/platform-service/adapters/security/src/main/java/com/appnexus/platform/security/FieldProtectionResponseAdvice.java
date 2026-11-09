package com.appnexus.platform.security;

import com.appnexus.platform.authorization.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** 全站响应统一脱敏入口；业务 Controller 无需逐个调用算法，可信身份缺失或处理失败时拒绝输出。 */
@RestControllerAdvice(basePackages="com.appnexus.platform")
public final class FieldProtectionResponseAdvice implements ResponseBodyAdvice<Object> {
    private final FieldProtection protection;
    private final HttpServletRequest request;
    public FieldProtectionResponseAdvice(FieldProtection protection,HttpServletRequest request){this.protection=protection;this.request=request;}
    @Override public boolean supports(MethodParameter method,Class<? extends HttpMessageConverter<?>> converter){return true;}
    @Override public Object beforeBodyWrite(Object body,MethodParameter method,MediaType mediaType,Class<? extends HttpMessageConverter<?>> converter,ServerHttpRequest serverRequest,ServerHttpResponse response) {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        AccessAuthorization.Subject subject=null;
        if(authentication!=null&&authentication.isAuthenticated()) {
            try {
                UUID account=UUID.fromString(authentication.getName());
                Object context=request.getAttribute(TenantExecutionFilter.ATTRIBUTE);
                subject=context instanceof VerifiedTenantContext tenant?AccessAuthorization.Subject.tenant(account,tenant.tenantId(),tenant.memberId()):AccessAuthorization.Subject.platform(account);
            }catch(IllegalArgumentException ignored){/* 匿名或非账号主体不能用于受保护字段；普通公开响应仍可输出。 */}
        }
        return protection.protect(subject,body);
    }
}
