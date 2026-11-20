/**
 * 真实 MVC 方法及对象 Provider 的构建操作目录聚合。
 * <p>职责：真实 MVC 方法及对象 Provider 的构建操作目录聚合；当前类型：OperationRegistry。
 * <p>公开边界与依赖：允许 MVC 映射、稳定操作与对象 Provider 元数据；只聚合构建目录供公开查询端口使用，禁止缓存用户权限、替代实时执行授权或访问业务数据库。
 * <p>迁移状态：从部署组合迁出实际实现，运行装配与契约验证在全量迁移后统一执行。 本次仅补文档，不宣告新增运行验收。
 */
package com.appnexus.platform.security.operation;
