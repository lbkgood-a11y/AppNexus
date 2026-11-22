package com.appnexus.persistence.mybatis;

import java.lang.annotation.*;

/** 声明标准查询必须携带权限谓词，或标准写入必须持有同交易的记录许可。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(GuardedScope.List.class)
public @interface GuardedScope {
    /** 同一固定 SQL 入口按业务类型声明多个资源门禁。 */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface List {
        /** 所有匹配当前调用的门禁均须通过。 */
        GuardedScope[] value();
    }

    /** 权限谓词的固定参数路径；读与锁必填。 */
    String predicate() default "";
    /** 共用语句中用于区分业务对象的参数，缺省对所有调用执行。 */
    String when() default "";
    /** 区分参数的固定值。 */
    String whenValue() default "";
    /** 写许可资源编码；写路径必填。 */
    String resource() default "";
    /** 写许可的实际操作动作，可列出允许共用的原动作。 */
    String[] actions() default {};
    /** 租户 UUID 参数路径。 */
    String tenant() default "";
    /** 记录 UUID 参数路径。 */
    String recordId() default "";
    /** 涉及归属写入的语句必须与已授权目标匹配。 */
    String target() default "";
    /** 子档案已授权关联键的固定参数路径，顺序必须与许可一致，与 UUID target 互斥。 */
    String[] parentKey() default {};
    /** 复合单据固定语句可接受的完整权限码，必须持有同事务的显式全部计划。 */
    String[] operationPermissions() default {};
    /** 只适用于没有独立记录范围策略的目录发布固定语句。 */
    String catalogPublication() default "";
    /** 专用技术操作权限，要求原认证主体及目标键由平台绑定。 */
    String serviceOperation() default "";
    /** 技术操作固定参数路径，顺序与出具许可一致。 */
    String[] serviceKeys() default {};
}
