package com.appnexus.platform.tenant;
import java.util.*;
/** 租户访问公开端口：只有租户和成员均启用才允许建立上下文。 */
public interface TenantAccess {
    /** 已验证的成员关系；标识均为不可变 UUID，名称仅用于展示。 */
    record Membership(UUID tenantId, UUID memberId, String name) {}
    /**
     * @param accountId 服务端认证的全局账号，不接受客户端冒充
     * @return 当前有效成员关系，不能将返回列表解释为业务操作授权
     */
    List<Membership> available(UUID accountId);
    /** 验证目标成员仍启用且属于指定有效租户；不得仅凭成员 UUID 授权。 */
    boolean isActiveMember(UUID tenantId, UUID memberId);
    /** 授权中心使用的有效成员摘要，名称仅用于显示。 */
    record MemberOption(UUID memberId, String displayName) {}

    /**
     * @param tenantId 经调用侧准入验证的租户
     * @return 有效租户成员摘要，调用者仍须检查授权管理权限
     */
    List<MemberOption> memberOptions(UUID tenantId);
}
