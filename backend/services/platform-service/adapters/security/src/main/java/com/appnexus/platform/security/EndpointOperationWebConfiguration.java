package com.appnexus.platform.security;

import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.security.operation.OperationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

/** 注册统一端点权限拦截器，覆盖第一方租户 API 和平台管理 BFF，本人自助不套用管理权限。 */
@Configuration(proxyBeanMethods=false)
public class EndpointOperationWebConfiguration implements WebMvcConfigurer {
    private final AccessAuthorization authorization;
    private final ObjectProvider<OperationRegistry> operations;

    /** @param authorization 统一授权入口
     * @param operations 延迟获取真实 MVC 操作目录，避免构建 MVC 时反向初始化映射
     */
    public EndpointOperationWebConfiguration(AccessAuthorization authorization, ObjectProvider<OperationRegistry> operations){
        this.authorization=authorization;
        this.operations=operations;
    }
    @Override public void addInterceptors(InterceptorRegistry registry){
        registry.addInterceptor(new EndpointOperationInterceptor(authorization,operations)).addPathPatterns("/api/**","/bff/platform/**");
    }
}
