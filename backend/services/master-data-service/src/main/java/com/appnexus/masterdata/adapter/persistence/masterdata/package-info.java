/**
 * 租户地理目录、客商明细、仓储层级与主数据关系存储适配。
 * <p>职责：租户地理目录、客商明细、仓储层级与主数据关系存储适配；当前类型：GeographyDefaults、MyBatisGeographyCatalog、MyBatisMasterDataRelations、PartnerDataAccess、PartnerDetails、StorageHierarchy、TenantGeography。
 * <p>公开边界与依赖：允许本服务稳定契约、输出端口、MyBatis 与必要的存储技术；供本服务用例及装配接入，禁止提供任意 SQL、读取其他业务私表或自行决定授权。
 * <p>迁移状态：类型归属已迁移，存量用例仍须依实际调用链复核，尚未统一运行验证。 本次仅补文档，不宣告新增运行验收。
 */
package com.appnexus.masterdata.adapter.persistence.masterdata;
