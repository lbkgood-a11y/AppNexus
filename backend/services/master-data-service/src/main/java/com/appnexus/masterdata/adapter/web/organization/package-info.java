/**
 * 组织结构、授权入口、目录摘要与人员 HTTP 映射及错误缓存策略。
 * <p>职责：组织结构、授权入口、目录摘要与人员 HTTP 映射及错误缓存策略；当前类型：OrganizationCommandRuntime、OrganizationController、OrganizationDomainController、OrganizationDomainErrors、OrganizationGrantController、OrganizationSummaryController、PersonController、PersonnelCachePolicy、PersonnelErrors。
 * <p>公开边界与依赖：允许稳定业务公开契约、安全入口契约与 Spring Web；对外边界为既有 HTTP 路由，禁止新增直接 Mapper/SQL 访问、内部用例依赖或平行授权规则。
 * <p>实际依赖与存量差距：OrganizationController 当前仍使用公开 OrganizationUnitRepository 端口；本包同时引用 Spring DAO 异常，不能据本次说明修订宣称 HTTP 层已完成全部应用边界迁移。
 * <p>迁移状态：组织单元及任职人员类型化命令与既有 HTTP 入口共用原组织端口，按可信租户上下文执行。历史组织授权仍由平台公共能力执行。
 */
package com.appnexus.masterdata.adapter.web.organization;
