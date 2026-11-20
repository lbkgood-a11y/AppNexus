/**
 * 主数据记录及商品属性、目录、结构和模板的完整事务适配。
 * <p>职责：主数据记录及商品属性、目录、结构和模板的完整事务适配；当前类型：SpringProductAttributeTransaction、SpringProductCatalogTransaction、SpringProductExecutionTransaction、SpringProductStructureTransaction、SpringProductTemplateTransaction、SpringRecordTransaction。
 * <p>公开边界与依赖：允许 Spring 事务设施及本服务事务输出端口；仅由应用完整用例调用，保持同数据源事务与异常回滚，禁止承担业务授权、状态决策或缩小事务范围。
 * <p>迁移状态：完成职责命名与包迁移，完整运行及安全验证在全量迁移后统一执行。 本次仅补文档，不宣告新增运行验收。
 */
package com.appnexus.masterdata.adapter.transaction;
