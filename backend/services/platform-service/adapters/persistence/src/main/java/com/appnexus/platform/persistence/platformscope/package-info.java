/**
 * 平台管理及跨租户受控数据访问适配。
 *
 * <p>职责：平台管理及跨租户受控数据访问适配。
 * <p>边界与依赖：跨租户读取必须有独立授权及明确范围；不能作为普通租户业务绕过隔离的入口。
 * <p>当前状态：MySqlRowAccessControl 实现公开 RowAccessControl，生成组织或组合规则条件；
 * DataGrantPolicySupport处理原授权策略JSON与分批主体查验，匹配语义由authorization纯规则拥有；
 * 主体组织通过organization公开端口解析，同次执行只读取一次，不缓存跨请求身份。
 * 编译器不读取业务表，调用方仍须独立强制可信租户。其他跨租户读取须有独立授权及明确范围。
 * <p>维护约定：新增或修改类型时同步更新本说明，遵守《后端标准工程结构与 AI 生成约束》；
 * 包说明不代替实际分层、执行授权、事务与架构测试。
 */
package com.appnexus.platform.persistence.platformscope;
