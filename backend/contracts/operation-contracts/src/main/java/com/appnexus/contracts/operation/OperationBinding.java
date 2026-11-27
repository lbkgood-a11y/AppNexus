package com.appnexus.contracts.operation;

import java.lang.annotation.*;

/** 真实处理接口的操作登记；可选用对象声明作为统一端点权限门禁的来源。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(OperationBindings.class)
public @interface OperationBinding {
    /** 全局稳定操作标识，不使用按钮文案或路由生成身份。 */
    String id();
    /** 引用已由业务对象 Provider 声明的操作；必须显式为 true。 */
    boolean reference();
    /** 租户 API 和平台管理 BFF 的前置功能权限取自所引用的 Provider；默认沿用入口现有准入方式。 */
    boolean enforceDeclaredPermission() default false;
    /** 展示准入策略；PERMISSION 检查功能门槛，SELF 仅限本人，OBJECT 仍须原业务对象检查。 */
    Eligibility eligibility() default Eligibility.PERMISSION;
    /** 策略种类不授予业务权限，不可作为业务接口放行依据。 */
    enum Eligibility { PERMISSION, SELF, OBJECT }

}
