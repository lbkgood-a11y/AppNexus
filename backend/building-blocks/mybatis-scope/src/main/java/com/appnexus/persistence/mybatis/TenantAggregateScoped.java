package com.appnexus.persistence.mybatis;

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
    /**
     * @return true时要求适配器传入的二进制租户与安全链上下文一致；用于已接通的业务适配器，防止权限主体与重绑定租户混用。
     * 默认false保留既有Mapper覆盖不可信参数的协议；该参数不解除实际租户绑定和表白名单校验。
     */
    boolean requireTenantMatch() default false;
}
