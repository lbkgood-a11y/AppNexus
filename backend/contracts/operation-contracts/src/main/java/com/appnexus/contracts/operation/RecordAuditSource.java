package com.appnexus.contracts.operation;

import java.lang.annotation.*;

/**
 * 声明已授权查询结果的审计投影来源；表名和键列均为构建内常量，不能由客户端指定。
 * 共享基础设施只为原接口已经返回的对象补充四个只读字段，不能作为独立查询或授权入口。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RecordAuditSource {
    /** @return 模块自有表名，必填，无默认值 */
    String table();
    /** @return 数据库对象键列，默认 id；复合关系需另行声明专用投影 */
    String key() default "id";
    /** @return 响应对象的标识字段，默认 id */
    String id() default "id";
    /** @return 含对象的响应字段路径，空字符串表示根对象或根列表 */
    String path() default "";
    /** @return 键使用 BINARY(16) 时为 true，默认 true；文本键必须显式 false */
    boolean binaryKey() default true;
    /** @return 租户表默认 true；平台公共表必须显式 false */
    boolean tenant() default true;
    /** @return 租户键使用 BINARY(16) 时为 true，默认 true */
    boolean binaryTenant() default true;
    /** @return 平台代管租户路径变量名，缺省空使用可信租户上下文；仅用于原接口已授权的代管结果 */
    String managedTenant() default "";
}
