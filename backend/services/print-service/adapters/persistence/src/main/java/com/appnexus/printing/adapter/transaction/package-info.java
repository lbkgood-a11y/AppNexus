/**
 * 打印完整用例的同数据源 Spring 事务适配。
 * <p>职责：打印完整用例的同数据源 Spring 事务适配；当前类型：SpringPrintTransaction。
 * <p>公开边界与依赖：允许 Spring 事务设施及本服务事务输出端口；仅由应用完整用例调用，保持同数据源事务与异常回滚，禁止承担业务授权、状态决策或缩小事务范围。
 * <p>迁移状态：输出用例技术边界已分离，等待全量统一验证。 本次仅补文档，不宣告新增运行验收。
 */
package com.appnexus.printing.adapter.transaction;
