/**
 * 入库执行平台集成职责边界。允许依赖本服务公开用例及平台业务对象契约。
 * 禁止直接读写业务表或执行流程。当前登记作业队列、质检、外部计量和入库草稿的统一操作。
 * <p>公共作业票运行端口复用现场打印准入与作业状态，用固定最小快照接入现有 PRINT 驱动。
 */
package com.appnexus.bulkwarehousing.adapter.integration.inboundexecution;
