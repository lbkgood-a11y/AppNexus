package com.appnexus.platform.authorization;

import java.lang.annotation.*;

/** 资源响应声明；启动时自动进入授权中心字段目录，统一响应管线执行已配置策略。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ProtectedResource {
    /** 稳定业务资源标识，不接受表名或 SQL。 */
    String value();
    /** 字段策略所属管理域；租户域只使用服务端可信成员上下文。 */
    PermissionCatalog.ScopeType scope();
}
