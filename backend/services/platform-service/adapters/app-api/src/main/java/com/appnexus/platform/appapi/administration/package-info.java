/**
 * 当前账号安全日志读取及邀请拒绝的应用侧 HTTP 映射。
 * <p>职责：当前账号安全日志读取及邀请拒绝的应用侧 HTTP 映射；当前类型：InvitationErrors、SecurityLogController。
 * <p>公开边界与依赖：允许稳定业务公开契约、安全入口契约与 Spring Web；对外边界为既有 HTTP 路由，禁止新增直接 Mapper/SQL 访问、内部用例依赖或平行授权规则。
 * <p>实际依赖与存量差距：SecurityLogController 当前直接调用公开 SecurityLogRepository 读取端口；这是现有入口依赖，包说明不将其描述为已全部通过应用用例隔离。
 * <p>迁移状态：完成入口职责落位，存量直连技术实现继续拆分，未进行运行验证。 本次仅补文档，不宣告新增运行验收。
 */
package com.appnexus.platform.appapi.administration;
