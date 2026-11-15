package com.appnexus.contracts.operation;

import java.lang.annotation.*;

/** 真实处理接口的操作登记；不执行授权，不替代服务端业务用例复核。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(OperationBindings.class)
public @interface OperationBinding {
    /** 全局稳定操作标识，不使用按钮文案或路由生成身份。 */
    String id();
    /** 引用已由业务对象 Provider 声明的操作；true 时禁止重复提供对象、名称、页面或权限。 */
    boolean reference() default false;
    /** 所属业务或系统对象编码，必填；不按页面路径或权限码运行时推断。 */
    String objectType() default "";
    /** 对象内操作编码，必填；与对象编码共同组成全局操作身份。 */
    String operationCode() default "";
    /** 已登记页面键；同一操作可从多个页面进入。 */
    String[] pages() default {};
    /** 中文操作名称，不包含对象敏感信息。 */
    String nameZh() default "";
    /** 英文操作名称。 */
    String nameEn() default "";
    /** 权限表达式：每个元素是一组 AND 权限（用 & 分隔），多组之间 OR；缺省由 policy 描述身份约束。 */
    String[] permissions() default {};
    /** 额外执行约束，必填；仅用于解释，真实判定仍归原业务服务。 */
    String policy() default "";
    /** 展示准入策略；PERMISSION 检查功能门槛，SELF 仅限本人，OBJECT 仍须原业务对象检查。 */
    Eligibility eligibility() default Eligibility.PERMISSION;
    /** 策略种类不授予业务权限，不可作为业务接口放行依据。 */
    enum Eligibility { PERMISSION, SELF, OBJECT }

}
