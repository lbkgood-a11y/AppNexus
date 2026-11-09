package com.appnexus.platform.authorization;

import java.lang.annotation.*;

/** 字段仅声明名称与敏感类型，不包含任何角色、权限判断或业务接口专用脱敏实现。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface ProtectedField {
    /** 中文业务名称，用于授权中心检索。 */
    String nameZh();
    /** 英文业务名称。 */
    String nameEn();
    /** 内置脱敏算法类型，决定 MASKED 的安全展示方式。 */
    FieldSecurity.Kind kind();
}
