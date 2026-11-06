package com.appnexus.platform.authorization;
import java.time.Instant;
import java.util.*;
/** 组织能力授权生命周期端口；查询调用层及变更适配器必须校验调用者具备 GRANT 管理能力。 */
public interface OrganizationGrantManagement {
    /** 可授权成员摘要，不返回账号凭据或其他租户资料。 */
    record Member(UUID memberId, String displayName, UUID organizationId, String organizationName) {}
    /** 主要组织归属仅由同租户 GRANT 管理者维护；null 清空归属，不改变操作授权。 */
    void assignOrganization(UUID tenantId, UUID targetMemberId, UUID operatorMemberId, UUID organizationId);
    /** @param tenantId 已验证的当前租户；调用层必须校验 GRANT，不能开放成员目录给普通用户 */
    List<Member> members(UUID tenantId);
    record Grant(UUID memberId, OrganizationCapability capability, UUID grantedBy, Instant grantedAt, Instant expiresAt) {}
    /** 列出指定租户有效成员的授权；tenantId 和 operatorMemberId 必须来自验证上下文。 */
    List<Grant> list(UUID tenantId, UUID targetMemberId);
    /**
     * 授予或更新一项能力；授权者不能来自客户端参数。
     * @return 持久化后的授权
     */
    Grant grant(UUID tenantId, UUID targetMemberId, UUID operatorMemberId,
        OrganizationCapability capability, Instant expiresAt);
    /** 撤销授权并记录撤销者；不存在的授权返回 false。 */
    boolean revoke(UUID tenantId, UUID targetMemberId, UUID operatorMemberId,
        OrganizationCapability capability);
}
