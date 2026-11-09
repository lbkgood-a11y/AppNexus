package com.appnexus.platform.authorization;

import java.util.UUID;

/**
 * 统一功能权限判定端口；业务代码只提交稳定权限码，不得查询角色名、菜单或关系表。
 * RBAC 与受控迁移期组织直授权在适配器内合并；未知资源与缺少可信上下文时失败关闭。
 */
public interface AccessAuthorization {
    /**
     * 服务端验证后的执行主体。
     * @param accountId 已认证全局账号 UUID，必填
     * @param tenantId 当前可信租户 UUID；平台请求为 {@code null}
     * @param memberId 当前租户成员 UUID；平台请求为 {@code null}，租户请求必填
     */
    record Subject(UUID accountId, UUID tenantId, UUID memberId) {
        /** 创建平台执行主体。 */
        public static Subject platform(UUID accountId) { return new Subject(accountId, null, null); }
        /** 创建租户执行主体；租户与成员必须来自服务端验证上下文。 */
        public static Subject tenant(UUID accountId, UUID tenantId, UUID memberId) {
            return new Subject(accountId, tenantId, memberId);
        }
    }

    /**
     * 判断主体是否拥有功能权限。
     * @param subject 已验证执行主体，缺少必要上下文时返回 false
     * @param permissionCode 稳定权限码，格式为 {@code resource:action}
     * @return 仅在权限存在、启用、管理域匹配且主体有效授权时返回 true；未知或异常状态默认拒绝
     */
    boolean hasPermission(Subject subject, String permissionCode);
}
