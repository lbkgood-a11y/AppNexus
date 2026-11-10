package com.appnexus.platform.security;

import com.appnexus.platform.authorization.AccessAuthorization;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

/** 注册统一端点权限拦截器，覆盖第一方租户 API 和平台管理 BFF，本人自助不套用管理权限。 */
@Configuration(proxyBeanMethods=false)
public class EndpointOperationWebConfiguration implements WebMvcConfigurer {
    private final AccessAuthorization authorization;
    public EndpointOperationWebConfiguration(AccessAuthorization authorization){this.authorization=authorization;}
    @Override public void addInterceptors(InterceptorRegistry registry){
        registry.addInterceptor(new EndpointOperationInterceptor(authorization)).addPathPatterns("/api/**","/bff/platform/**");
    }
}
