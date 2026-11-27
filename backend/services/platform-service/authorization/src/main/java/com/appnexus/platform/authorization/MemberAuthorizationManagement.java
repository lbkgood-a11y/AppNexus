package com.appnexus.platform.authorization;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 成员独立授权端口；角色持续提供功能权限，成员分支保存有效期及范围模式，数据范围按继承状态读取角色当前策略或成员自定义策略。 */
public interface MemberAuthorizationManagement {
    /**
     * 已审核的单个角色授权分支。
     * @param roleId 当前租户角色 UUID
     * @param permissionIds 角色当前完整功能集合；保存时必须与角色一致，成员不能独立增减功能
     * @param dataPolicies 同一分支的操作数据范围，不允许跨分支拼接
     * @param expiresAt 到期 UTC 时间；NULL 为长期，过期分支不参与权限判定
     * @param inheritDataScope true 表示持续继承角色当前数据范围；false 表示使用成员自定义范围
     * @param roleVersion 本次编辑读取的角色版本；保存时用于并发校验
     * @param objectScopes 成员自定义的对象默认范围；空映射清除，NULL 表示旧逐操作保存
     * @param inheritedPermissionIds 服务端计算的默认范围来源；保存时不采用客户端值
     */
    record Grant(UUID roleId, Set<UUID> permissionIds, Set<RolePermissionManagement.DataPolicy> dataPolicies,
                 Instant expiresAt, long roleVersion, boolean inheritDataScope,
                 Map<String,RolePermissionManagement.ObjectScope> objectScopes, Set<UUID> inheritedPermissionIds) {
        public Grant {
            objectScopes = objectScopes == null ? null : Map.copyOf(objectScopes);
            inheritedPermissionIds = inheritedPermissionIds == null ? Set.of() : Set.copyOf(inheritedPermissionIds);
        }
        /** 旧的完整操作快照保持原分支模式。 */
        public Grant(UUID roleId, Set<UUID> permissionIds, Set<RolePermissionManagement.DataPolicy> dataPolicies,
                     Instant expiresAt, long roleVersion, boolean inheritDataScope) {
            this(roleId, permissionIds, dataPolicies, expiresAt, roleVersion, inheritDataScope, null, Set.of());
        }
        /** 旧调用保持自定义语义，避免将已有独立范围隐式转换为继承。 */
        public Grant(UUID roleId, Set<UUID> permissionIds, Set<RolePermissionManagement.DataPolicy> dataPolicies,
                     Instant expiresAt, long roleVersion) {
            this(roleId, permissionIds, dataPolicies, expiresAt, roleVersion, false);
        }
    }
    /** @param grants 已分配角色的全部分支 @param authorityVersion 成员授权版本，保存时须原样回传 */
    record Assignment(List<Grant> grants, long authorityVersion) {}

    /** 查询可信租户内成员授权；复核管理权和成员归属，不向普通成员暴露其他成员授权。 */
    Assignment get(RoleManagement.Context context, UUID memberId);

    /**
     * 原子更新单条授权分支并推进成员版本、追加审计，其他角色分支保持原样。
     * @param context 服务端可信管理上下文
     * @param memberId 当前租户有效成员 UUID
     * @param grant 完整分支配置；角色与权限须在当前管理域，范围仅接受已实现的元数据
     * @param expectedVersion 查询获得的成员授权版本，非负，冲突时必须重新读取
     * @param reason 中文或其他语言变更原因，1–500 字符，不能包含凭据
     * @return 保存后的全部分支与新版本
     * @throws RoleManagementException 版本冲突、分支不存在或范围不合法
     */
    Assignment replace(RoleManagement.Context context, UUID memberId, Grant grant, long expectedVersion, String reason);
}
