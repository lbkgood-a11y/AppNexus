package com.appnexus.platform.authorization;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 有效权限解释查询；结果用于审计排错，不作为业务接口的放行凭据。 */
public interface EffectiveAuthorizationQuery {
    /**
     * 单个来源角色的贡献。
     * @param roleId 来源角色 UUID
     * @param roleName 当前角色展示名称
     * @param effective 该角色分支是否形成完整有效授权
     * @param inactiveReason 未生效稳定原因；有效时为空
     * @param scopeType 数据范围类型；无数据范围要求时为空
     * @param organizationIds 组织范围值
     */
    record Source(UUID roleId,String roleName,boolean effective,String inactiveReason,
                  DataScopeAuthorization.ScopeType scopeType,Set<UUID> organizationIds) {}
    /** @param permissionCode 稳定权限码 @param effective 至少一个完整来源分支有效 @param sources 全部角色来源 */
    record Explanation(String permissionCode,boolean effective,List<Source> sources) {}

    /** 查询平台账号通过角色获得的权限解释；调用方必须先验证管理权和目标账号。 */
    List<Explanation> platformAccount(UUID accountId);
    /** 查询当前租户成员的权限解释；tenantId 与 memberId 必须来自可信管理上下文。 */
    List<Explanation> tenantMember(UUID tenantId,UUID memberId);
}
