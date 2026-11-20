/**
 * 客商、国别区划、仓库、库区与库位 HTTP 协议映射。
 * <p>职责：客商、国别区划、仓库、库区与库位 HTTP 协议映射；当前类型：BusinessPartnerController、CountryRegionController、StorageAreaController、StorageLocationController、SubdivisionController、WarehouseController。
 * <p>公开边界与依赖：允许稳定业务公开契约、安全入口契约与 Spring Web；对外边界为既有 HTTP 路由，禁止新增直接 Mapper/SQL 访问、内部用例依赖或平行授权规则。
 * <p>迁移状态：本批完成类型落位，原用例依赖仍须继续治理；包迁移不代表完整分层或运行验证通过。 本次仅补文档，不宣告新增运行验收。
 */
package com.appnexus.masterdata.adapter.web.masterdata;
