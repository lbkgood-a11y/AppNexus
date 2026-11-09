package com.appnexus.platform.authorization;

import java.util.List;
import java.util.UUID;

/** 数据策略只读元数据目录；管理端只能展示后端已登记并能够安全执行的范围类型。 */
public interface DataPolicyMetadataCatalog {
    /**
     * 单项权限支持的数据范围。
     * @param permissionId 权限 UUID
     * @param permissionCode 稳定权限码
     * @param scopeType 可保存的结构化范围类型，不包含 DENY
     * @param resourceType 仓储编译器注册的资源类型
     */
    record ScopeSupport(UUID permissionId,String permissionCode,
                        DataScopeAuthorization.ScopeType scopeType,String resourceType) {}

    /**
     * 查询指定权限管理域的全部数据范围元数据。
     * @param scopeType 平台或租户权限管理域
     * @return 按权限码、范围类型稳定排序的不可变列表
     */
    List<ScopeSupport> list(PermissionCatalog.ScopeType scopeType);
}
