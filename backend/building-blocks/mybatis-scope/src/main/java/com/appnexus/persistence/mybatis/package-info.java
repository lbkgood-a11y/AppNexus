/**
 * 职责：共享固定 Mapper SQL 的单表租户、多表租户和平台受控范围元数据。
 * <p>边界：仅依赖 JDK 注解；禁止引入 Spring、MyBatis 实现、SQL 执行器、业务表、租户或授权模型。
 * 公开注解供各业务存储适配器声明，实际可信主体及SQL检查由平台持久化拦截器承担。
 * <p>当前迁移状态：三个原平台scope注解迁入本包，参数与执行语义不变；元数据不能代替数据权限接入。
 */
package com.appnexus.persistence.mybatis;
