/**
 * 入库执行应用职责边界包括模板策略协调、过账编排及队列查询。
 * 允许本服务领域与公开平台契约，不依赖 Web、SQL、MyBatis 或 Spring。
 * 应用实现仅供本模块装配使用。队列、质检、外部计量、散货过账和冲红接入统一授权；待检库存由
 * 同库 Outbox 消费者应用质量回写，业务事件只携带经过授权的来源标识。
 */
package com.appnexus.bulkwarehousing.application.inboundexecution;
