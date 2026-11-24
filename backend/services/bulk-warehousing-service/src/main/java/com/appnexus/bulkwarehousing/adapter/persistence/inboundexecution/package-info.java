/**
 * 入库执行 MyBatis 职责边界为定向持久化；仅依赖应用查询端口、固定 SQL 和公共许可。
 * 禁止依赖 Web 或跨上下文数据表。当前包含工作台聚合、质检、计量和散货/钢铁入库库存事务。
 */
package com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution;
