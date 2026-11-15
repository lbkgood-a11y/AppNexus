package com.appnexus.contracts.operation;
import java.lang.annotation.*;
/** 一个真实接口支持多个业务操作时的登记容器，保持各操作身份独立。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationBindings {
    /** 当前接口的明确操作定义，不从权限码猜测按钮名称。 */
    OperationBinding[] value();
}
