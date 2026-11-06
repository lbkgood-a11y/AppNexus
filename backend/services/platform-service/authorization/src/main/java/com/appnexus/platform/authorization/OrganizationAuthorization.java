package com.appnexus.platform.authorization;
import java.util.UUID;
/** 组织操作授权端口；账号、成员和租户必须来自已验证的服务端执行上下文。 */
public interface OrganizationAuthorization {
    /**
     * 检查指定当前成员是否获准执行组织单元操作。
     * @param tenantId 已验证租户 UUID，不接受请求参数或请求头作为来源
     * @param memberId 该租户中的已验证成员 UUID
     * @param capability 明确的组织单元操作
     * @return 存在有效授权时为 true；缺失、撤销或过期均为 false
     */
    boolean allows(UUID tenantId, UUID memberId, OrganizationCapability capability);
}
