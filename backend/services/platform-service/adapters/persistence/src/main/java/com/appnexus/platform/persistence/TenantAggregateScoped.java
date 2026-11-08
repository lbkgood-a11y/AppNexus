package com.appnexus.platform.persistence;

import java.lang.annotation.*;

/**
 * 声明经过审查的多表租户聚合 SQL 边界。
 *
 * <p>所有语句必须显式引用 {@code __verifiedTenant}，且只能访问 {@link #tables()} 中列出的表。
 * 该注解只用于必须跨表维护不变量的领域适配器，不能作为普通 Mapper 绕过单表固定形状门禁的捷径。</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TenantAggregateScoped {
    /** @return 该聚合允许访问的租户表精确白名单 */
    String[] tables();
    /** @return 已逐条审查并允许执行的 Mapper 方法名；新增 SQL 必须显式加入 */
    String[] statements();
}
