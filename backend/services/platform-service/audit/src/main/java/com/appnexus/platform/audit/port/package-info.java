/**
 * 审计记录及当前账号安全日志的公开读取输出端口。
 * <p>职责：审计记录及当前账号安全日志的公开读取输出端口；当前类型：AuditRecords、SecurityLogRepository。
 * <p>公开边界与依赖：允许 JDK 及所属上下文稳定公开类型；由对应技术适配器实现、应用用例调用，禁止依赖 Spring、MyBatis、数据库类型或其他上下文内部实现。
 * <p>迁移状态：本次仅核对并修订包说明；现有源码已参与前轮构建，完整业务、安全及部署验收不因文档补齐视为完成。
 */
package com.appnexus.platform.audit.port;
