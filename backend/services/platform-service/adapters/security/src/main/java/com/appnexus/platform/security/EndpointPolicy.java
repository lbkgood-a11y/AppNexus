package com.appnexus.platform.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明 HTTP Controller 的权威安全域；架构测试禁止未声明的新接口面进入构建产物。
 * 注解只用于登记边界，不能替代用例层权限、租户隔离、CSRF 或字段保护。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EndpointPolicy {
    /** Controller 所属的最小安全域。 */
    Domain value();

    /**
     * 接口安全域。PUBLIC_LOGIN 仅限登录引导所需端点；其他域均要求认证并在业务层复核权威状态。
     */
    enum Domain {
        PUBLIC_LOGIN,
        ACCOUNT_SELF_SERVICE,
        TENANT_CONTEXT,
        TENANT_BUSINESS,
        PLATFORM_ADMINISTRATION,
        AUTHORIZATION_MANAGEMENT
    }
}
