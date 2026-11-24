/**
 * 入库执行领域职责边界为纯业务规则，承载计量、质检门槛、扣量和 LPN 重量分摊。
 * 仅依赖 JDK，不依赖应用服务、SQL、设备或框架。公开类型供本模块应用层使用。
 * 当前已实现分摊及策略协调计算，真实单据、库存和设备事务由应用层与适配器接入。
 */
package com.appnexus.bulkwarehousing.domain.inboundexecution;
