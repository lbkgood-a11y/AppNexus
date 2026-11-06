package com.appnexus.platform.persistence;
import java.lang.annotation.*;
/**
 * 显式声明平台级 Mapper 及其可访问表白名单；不得用于普通租户业务表。
 * @param tables 允许访问的精确表名，支持只读查询白名单与受控平台表写入
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PlatformScoped { String[] tables(); }
