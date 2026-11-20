/**
 * 合作方授权范围内的引用目录 HTTP 映射。
 * <p>职责：合作方授权范围内的引用目录 HTTP 映射；当前类型：PartnerReferenceController。
 * <p>公开边界与依赖：允许稳定业务公开契约、安全入口契约与 Spring Web；对外边界为既有 HTTP 路由，禁止新增直接 Mapper/SQL 访问、内部用例依赖或平行授权规则。
 * <p>迁移状态：完成入口职责落位，存量直连技术实现继续拆分，未进行运行验证。 本次仅补文档，不宣告新增运行验收。
 */
package com.appnexus.platform.partnerapi.administration;
