/**
 * 主数据与商品审批用例、存储及事务实现的 Spring 装配。
 * <p>职责：主数据与商品审批用例、存储及事务实现的 Spring 装配；当前类型：MasterDataConfiguration、ProductApprovalConfiguration。
 * <p>公开边界与依赖：允许 Spring 装配及本服务公开用例、端口和具体技术实现；仅由组合应用连接，不对外提供业务 API，禁止在配置中新增业务规则、授权决策或 SQL。
 * <p>迁移状态：类型归属已迁移，存量用例仍须依实际调用链复核，尚未统一运行验证。 本次仅补文档，不宣告新增运行验收。
 */
package com.appnexus.masterdata.bootstrap;
