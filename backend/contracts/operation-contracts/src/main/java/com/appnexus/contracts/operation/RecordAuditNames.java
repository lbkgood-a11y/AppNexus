package com.appnexus.contracts.operation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 声明已授权租户查询响应中已有的审计块，仅为可见的账号标识补充显示名。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RecordAuditNames {
    /** @return 根响应到审计块的固定字段路径，集合元素透明展开，禁止从请求接收路径 */
    String[] paths();
    /** @return true 表示授权平台审计，无租户上下文时仍通过平台审计权限解析 */
    boolean platform() default false;
    /** @return true 表示追加日志条目使用 actor 与 actorName 字段 */
    boolean event() default false;
}
