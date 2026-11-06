package com.appnexus.platform.persistence;
import java.lang.annotation.*;
/**
 * 声明单表租户 SQL 的受控边界；必须使用固定注入标记，不支持任意 SQL 改写。
 * table 表示 固定租户表名，只允许小写字母、数字和下划线；禁止客户端动态表名
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TenantScoped { String table(); }
