package com.appnexus.platform.tenant;
import java.util.Objects;
import java.util.UUID;
/**
 * 请求范围内的不可变租户执行上下文，由安全适配器校验后创建。
 * @param accountId 已认证全局账号
 * @param tenantId 会话绑定且已验证启用的租户
 * @param memberId 该账号在租户中的有效成员
 * @param version 会话上下文版本，必须为正数
 */
public record VerifiedTenantContext(UUID accountId, UUID tenantId, UUID memberId, long version) {
    public VerifiedTenantContext {
        Objects.requireNonNull(accountId); Objects.requireNonNull(tenantId); Objects.requireNonNull(memberId);
        if (version < 1) throw new IllegalArgumentException("Invalid context version");
    }
}
