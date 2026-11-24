/**
 * 职责为入仓引用的主数据集成及仓储对象声明。当前装配入仓通知和库存总览的统一操作。
 * 依赖边界允许仓储出站端口、公开契约和主数据公开入口，禁止读对方私表或引用其控制器、应用实现和 Mapper。
 */
package com.appnexus.bulkwarehousing.adapter.integration;
