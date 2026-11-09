package com.appnexus.platform.authorization;

import java.util.Set;
import java.util.UUID;

/** 主体角色全量分配端口；平台账号与租户成员使用不同方法，禁止通过可选 tenantId 混用管理域。 */
public interface SubjectRoleManagement {
    /** @param roleIds 当前完整角色集合 @param authorityVersion 主体授权乐观锁版本 */
    record Assignment(Set<UUID> roleIds,long authorityVersion) {}
    /** 查询平台账号角色；操作者必须是有效平台管理员。 */
    Assignment accountRoles(UUID actorAccountId,UUID targetAccountId);
    /** 原子覆盖平台账号角色并审计；reason 为 1–500 字符。 */
    Assignment replaceAccountRoles(UUID actorAccountId,UUID targetAccountId,long expectedVersion,Set<UUID> roleIds,String reason);
    /** 查询当前租户成员角色；上下文必须包含已验证操作者成员。 */
    Assignment memberRoles(RoleManagement.Context context,UUID targetMemberId);
    /** 原子覆盖当前租户成员角色并审计；角色必须全部属于当前租户。 */
    Assignment replaceMemberRoles(RoleManagement.Context context,UUID targetMemberId,long expectedVersion,Set<UUID> roleIds,String reason);
}
