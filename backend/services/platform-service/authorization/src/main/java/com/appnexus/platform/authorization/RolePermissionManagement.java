package com.appnexus.platform.authorization;

import java.util.Set;
import java.util.UUID;

/**
 * 角色功能权限全量配置端口；菜单树只是选择视图，持久化结果始终是独立权限 ID 集合。
 * 需要数据范围的权限必须通过功能权限与策略一体化接口提交，避免保存半成品授权。
 */
public interface RolePermissionManagement {
    /**
     * 角色当前权限配置。
     * @param permissionIds 已配置权限 ID 的不可变集合
     * @param roleVersion 角色乐观锁版本，覆盖保存时必须原样回传
     */
    record Assignment(Set<UUID> permissionIds, long roleVersion) {}

    /**
     * 单项结构化数据策略。
     * @param permissionId 本次完整权限集合中的权限 UUID
     * @param scopeType SELF、ORGANIZATION、ORGANIZATION_SUBTREE 或 ALL，禁止 DENY
     * @param resourceType 后端登记的资源类型
     * @param organizationIds 组织范围值；仅组织类范围必填，其他类型必须为空
     */
    record DataPolicy(UUID permissionId, DataScopeAuthorization.ScopeType scopeType,
                      String resourceType, Set<UUID> organizationIds) {}

    /** @param permissionIds 完整功能权限集合 @param dataPolicies 完整数据策略集合 @param roleVersion 角色乐观锁版本 @param fieldPolicies 强制字段展示限制，与普通授权合并后收口 */
    record AuthorizationAssignment(Set<UUID> permissionIds, Set<DataPolicy> dataPolicies, long roleVersion, Set<FieldSecurity.Policy> fieldPolicies) {
        /** 兼容旧调用，未配置额外字段限制。 */
        public AuthorizationAssignment(Set<UUID> permissionIds,Set<DataPolicy> dataPolicies,long roleVersion){this(permissionIds,dataPolicies,roleVersion,Set.of());}
    }

    /**
     * 查询角色权限。
     * @param context 已验证管理上下文；平台代管租户时 memberId 为空
     * @param roleId 当前管理域内的角色 UUID
     * @return 当前完整权限集合及角色版本
     */
    Assignment get(RoleManagement.Context context, UUID roleId);

    /** 返回角色完整功能权限、数据策略和乐观锁版本。 */
    AuthorizationAssignment getAuthorization(RoleManagement.Context context, UUID roleId);

    /**
     * 原子覆盖角色功能权限并追加审计。
     * @param context 已验证管理上下文
     * @param roleId 当前管理域内的角色 UUID
     * @param expectedRoleVersion 查询时获得的角色版本，必须大于等于 0
     * @param permissionIds 完整权限 ID 集合；空集合表示撤销全部功能权限
     * @param reason 变更原因，1–500 字符
     * @return 保存后的完整集合及新角色版本
     * @throws RoleManagementException 角色不存在、版本冲突或权限管理域不兼容
     */
    Assignment replace(RoleManagement.Context context, UUID roleId, long expectedRoleVersion,
                       Set<UUID> permissionIds, String reason);

    /**
     * 原子覆盖功能权限与结构化数据策略。
     * @param context 已验证管理上下文
     * @param roleId 当前管理域角色 UUID
     * @param expectedRoleVersion 查询所得角色版本
     * @param permissionIds 完整权限集合
     * @param dataPolicies 完整策略集合；所有要求范围的权限必须且只能出现一次
     * @param reason 变更原因，1–500 字符
     * @return 保存后的功能权限、策略和新角色版本
     */
    AuthorizationAssignment replaceAuthorization(RoleManagement.Context context, UUID roleId,
        long expectedRoleVersion, Set<UUID> permissionIds, Set<DataPolicy> dataPolicies, String reason);

    /** 原子保存功能、默认数据范围与强制字段限制；字段列表为 NULL 时保留旧限制以兼容旧客户端。 */
    AuthorizationAssignment replaceAuthorization(RoleManagement.Context context,UUID roleId,long expectedRoleVersion,
        Set<UUID> permissionIds,Set<DataPolicy> dataPolicies,Set<FieldSecurity.Policy> fieldPolicies,String reason);
}
