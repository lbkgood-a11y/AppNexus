/**
 * 职责为进仓引用的主数据集成。当前通过已装配公开契约读取主数据。
 * 边界允许依赖仓储出站端口、公开数据契约和主数据公开入口，禁止读取对方私表或引用控制器、应用实现和 Mapper。
 */
package com.appnexus.bulkwarehousing.adapter.integration;
