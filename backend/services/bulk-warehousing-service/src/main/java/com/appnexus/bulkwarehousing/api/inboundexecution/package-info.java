/**
 * 入库执行公开职责边界为用例与稳定读模型，供本服务 Web 适配器调用。
 * 只依赖 JDK 与可信租户契约，禁止引用内部领域、SQL 或框架。公开工作台读模型及授权的分岗作业命令。
 */
package com.appnexus.bulkwarehousing.api.inboundexecution;
