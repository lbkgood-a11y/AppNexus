/**
 * 工作流完整用例的同数据源 Spring 事务适配。
 * <p>职责：工作流完整用例的同数据源 Spring 事务适配；当前类型：SpringWorkflowTransaction。
 * <p>公开边界与依赖：允许 Spring 事务设施及本服务事务输出端口；仅由应用完整用例调用，保持同数据源事务与异常回滚，禁止承担业务授权、状态决策或缩小事务范围。
 * <p>迁移状态：本次仅核对并修订包说明；现有源码已参与前轮构建，完整业务、安全及部署验收不因文档补齐视为完成。
 */
package com.appnexus.workflow.adapter.transaction;
