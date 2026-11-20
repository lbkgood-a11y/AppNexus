/**
 * 主数据持久化适配器内部地理图的回归测试。
 * <p>职责：验证标准底图、租户节点移动与循环拒绝；边界：允许领域模型、固定 Mapper 替身及测试断言，禁止访问其他业务私表或提供生产入口。
 * <p>迁移状态：夹具已对齐 GeographyMapper，真实数据库验证由主数据集成测试负责。
 */
package com.appnexus.masterdata.adapter.persistence.masterdata;
