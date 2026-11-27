package com.appnexus.platform.tenant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** 组合应用可在账号及租户校验后接入业务请求身份，不参与成员准入判定。 */
public interface TenantRequestIdentity {
    /**
     * @param request 当前请求
     * @param response 当前响应，业务扩展拒绝时写入错误
     * @param base 平台已验证的账号及租户上下文
     * @param bearer 是否使用已验证的 Bearer 认证
     * @return 扩展后的上下文，拒绝时返回 null 并结束请求
     * @throws IOException 响应写入失败
     */
    VerifiedTenantContext resolve(HttpServletRequest request, HttpServletResponse response,
            VerifiedTenantContext base, boolean bearer) throws IOException;
    /** @param request 可信租户切换已轮换会话的请求 */
    void reset(HttpServletRequest request);
}
