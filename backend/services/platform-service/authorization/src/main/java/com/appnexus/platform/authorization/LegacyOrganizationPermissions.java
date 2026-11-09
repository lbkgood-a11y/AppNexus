package com.appnexus.platform.authorization;

import java.util.Map;

/** 迁移期组织能力的封闭映射；禁止按动作后缀把旧授权用于其他业务资源。 */
public final class LegacyOrganizationPermissions {
    private static final Map<String, String> CAPABILITIES = Map.of(
        "organization.unit:read", "READ", "organization.unit:create", "CREATE",
        "organization.unit:update", "UPDATE", "organization.unit:delete", "DELETE",
        "organization.permission:grant", "GRANT");

    private LegacyOrganizationPermissions() {}

    /**
     * 返回旧组织能力；未登记的权限不得回退。
     * @param permissionCode 稳定完整权限码，不能只传操作名
     * @return 对应的旧能力；未知码或空值返回 null
     */
    public static String capability(String permissionCode) {
        return permissionCode == null ? null : CAPABILITIES.get(permissionCode);
    }

    /** 旧全租户范围只适用于组织单元的四种操作，GRANT 不构成数据访问。 */
    public static boolean supportsData(String permissionCode, String resource) {
        return "organization.unit".equals(resource) && capability(permissionCode) != null
            && permissionCode.startsWith("organization.unit:");
    }
}
