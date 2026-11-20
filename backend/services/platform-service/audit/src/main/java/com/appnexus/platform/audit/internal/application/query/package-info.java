/**
 * 审计读取用例的准入、管理域与存储查询协调。
 * <p>职责：审计读取用例的准入、管理域与存储查询协调；当前类型：AuditQueryService。
 * <p>公开边界与依赖：允许 JDK、所属领域规则、稳定公开契约及明确输出端口；仅由本上下文或服务装配访问内部用例，禁止依赖 Spring、SQL、JSON、引擎实现或具体技术适配器。
 * <p>迁移状态：本次仅核对并修订包说明；现有源码已参与前轮构建，完整业务、安全及部署验收不因文档补齐视为完成。
 */
package com.appnexus.platform.audit.internal.application.query;
