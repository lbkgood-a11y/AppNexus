/**
 * 应用配置 HTTP 适配器的安全回归测试。
 * <p>职责：验证地图固定上游、密钥注入及响应过滤；边界：允许同包测试注入受控 HTTP 客户端，禁止请求真实上游或扩展生产可见性。
 * <p>迁移状态：测试包已对齐真实 AmapProxyController，保留原授权与泄漏断言。
 */
package com.appnexus.platform.appapi.configuration;
