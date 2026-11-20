/**
 * 对象及操作资格的只读诊断 HTTP 映射。
 * <p>职责：对象及操作资格的只读诊断 HTTP 映射；当前类型：ObjectDiagnosticsController、OperationDecisionController。
 * <p>公开边界与依赖：允许稳定业务公开契约、安全入口契约与 Spring Web；对外边界为既有 HTTP 路由，禁止新增直接 Mapper/SQL 访问、内部用例依赖或平行授权规则。
 * <p>迁移状态：从部署组合迁出实际实现，运行装配与契约验证在全量迁移后统一执行。 本次仅补文档，不宣告新增运行验收。
 */
package com.appnexus.platform.appapi.operation;
