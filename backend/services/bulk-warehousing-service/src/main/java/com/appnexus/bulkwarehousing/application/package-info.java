/**
 * 职责为入仓通知签发、查询及库存总览，并用可信权限与主数据可见性校验各自范围。
 * 依赖边界允许可信租户、统一权限契约、仓储公开契约与本侧端口，禁止依赖外部主数据实现、适配器和框架。
 */
package com.appnexus.bulkwarehousing.application;
