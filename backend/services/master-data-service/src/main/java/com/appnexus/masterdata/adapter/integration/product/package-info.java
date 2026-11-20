/**
 * 商品与审批对象声明及公共运行能力适配。
 * <p>职责：商品与审批对象声明及公共运行能力适配；当前类型：ProductApprovalDeclaration、ProductDeclaration、ProductRuntime。
 * <p>公开边界与依赖：允许对象与能力公开契约、本侧稳定数据和统一权限模型；通过同一 Provider 及运行入口接入，禁止建立平行声明、授权算法或直接访问其他业务私表。
 * <p>实际依赖与存量差距：现有 ProductRuntime 还依赖本服务 ProductAuthorization、ProductRepository 应用端口及 Spring 条件装配，公开边界限于对象 Provider/运行契约，不将这些端口暴露为跨服务 API。
 * <p>迁移状态：对象声明已迁入集成适配包，真实装配与声明同步待全量统一验证。 本次仅补文档，不宣告新增运行验收。
 */
package com.appnexus.masterdata.adapter.integration.product;
