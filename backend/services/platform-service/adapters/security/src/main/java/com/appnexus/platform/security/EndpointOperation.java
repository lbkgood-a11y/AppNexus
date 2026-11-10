package com.appnexus.platform.security;

import java.lang.annotation.*;

/**
 * 声明单个 HTTP 方法所需的稳定权限码；平台代管与租户自助可使用不同管理域权限。
 * 该注解用于契约登记和架构门禁，运行时仍由领域用例复核主体、租户、版本和授权范围。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EndpointOperation {
    /** 稳定权限码，格式为 resource:action；不得填写角色名、菜单名或 URL。 */
    String value();
    /** 平台代管租户时的独立平台权限码；缺省空，不允许将租户权限当平台权限或跳过校验。 */
    String platformPermission() default "";
}
