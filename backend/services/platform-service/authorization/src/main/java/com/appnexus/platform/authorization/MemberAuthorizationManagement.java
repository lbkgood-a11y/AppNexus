package com.appnexus.platform.authorization;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 成员独立授权端口；角色提供操作上限，快照固定成员范围，角色默认策略不参与运行时扩权。 */
public interface MemberAuthorizationManagement {
    /**
     * 已审核的单个角色授权分支。
     * @param roleId 当前租户角色 UUID
     * @param permissionIds 明确审核过的权限集合；空集合表示本分支不授予功能
     * @param dataPolicies 同一分支的操作数据范围，不允许跨分支拼接
     * @param expiresAt 到期 UTC 时间；NULL 为长期，过期分支不参与权限判定
     * @param roleVersion 生成本次快照时的角色版本
     */
    record Grant(UUID roleId, Set<UUID> permissionIds, Set<RolePermissionManagement.DataPolicy> dataPolicies,
                 Instant expiresAt, long roleVersion) {}
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
