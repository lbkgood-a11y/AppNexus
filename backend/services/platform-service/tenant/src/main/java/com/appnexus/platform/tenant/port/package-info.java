/**
 * 租户邀请记录、事务及平台任务操作的公开输出端口。
 * <p>职责：租户邀请记录、事务及平台任务操作的公开输出端口；当前类型：InvitationRecords、InvitationTransaction、TaskOperations。
 * <p>公开边界与依赖：允许 JDK 及所属上下文稳定公开类型；由对应技术适配器实现、应用用例调用，禁止依赖 Spring、MyBatis、数据库类型或其他上下文内部实现。
 * <p>迁移状态：任务管理边界已拆分；全量迁移结束后统一验证，不代表其他存量已完成。 本次仅补文档，不宣告新增运行验收。
 */
package com.appnexus.platform.tenant.port;
