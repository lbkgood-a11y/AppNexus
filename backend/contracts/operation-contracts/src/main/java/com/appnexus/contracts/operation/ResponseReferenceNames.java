package com.appnexus.contracts.operation;

import java.lang.annotation.*;

/** 从已授权响应中的固定记录路径解析可见名称，由所属目录批量提供。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ResponseReferenceNames {
    /** @return 公开响应内的引用字段声明；按数组元素展开路径。 */
    Binding[] value();

    /** 响应记录路径及其引用输入、输出字段。 */
    @interface Binding {
        /** @return 从响应根开始的路径，空串表示根记录或根数组 */
        String path() default "";
        /** @return 所属目录的稳定引用类型 */
        String type();
        /** @return 响应内持久化标识字段 */
        String id();
        /** @return true 表示 UUID；地理及业务字典编码显式设置为 false */
        boolean uuid() default true;
        /** @return 响应新增的显示名称字段 */
        String name();
        /** @return 同一记录的固定判别字段，空串表示无条件 */
        String when() default "";
        /** @return 判别字段必须匹配的协议值 */
        String equalsValue() default "";
    }
}
