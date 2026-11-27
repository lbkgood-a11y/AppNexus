/**
 * 人员内容、执行记录、身份查询、仓储及事务的公开输出端口。
 * <p>职责：人员内容、执行记录、身份查询、仓储及事务的公开输出端口；当前类型：PersonnelContent、PersonnelExecutionRecords、PersonnelIdentityQueries、PersonnelRepository、PersonnelTransaction、PersonRepository。
 * <p>公开边界与依赖：允许 JDK 及所属上下文稳定公开类型；由对应技术适配器实现、应用用例调用，禁止依赖 Spring、MyBatis、数据库类型或其他上下文内部实现。
 * <p>迁移状态：从 SQL 映射提取边界，方法语义及应用层事务仍在后续治理中，不表示已完成全部验收。 本次仅补文档，不宣告新增运行验收。
 */
package com.appnexus.masterdata.organization.port.personnel;
