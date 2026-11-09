package com.appnexus.platform.authorization;

import java.util.List;
import java.util.UUID;

/** 权限资源只读目录；权限定义由受控迁移发布，管理页面不得临时创造业务权限。 */
public interface PermissionCatalog {
    /** 权限适用管理域。 */
    enum ScopeType { PLATFORM, TENANT }

    /**
     * 权限资源摘要。
     * @param code 创建后不可修改的稳定权限码
     * @param resource 业务资源标识
     * @param action 资源操作标识
     * @param enabled 是否参与新授权和有效权限计算
     * @param systemManaged 是否仅允许受控发布流程维护
     * @param version 从 0 开始的乐观锁版本
     */
    record Permission(UUID id, ScopeType scopeType, String code, String resource, String action,
                      String nameZh, String nameEn, String description, boolean enabled,
                      boolean systemManaged, long version) {}

    /**
     * 返回指定管理域内的已发布权限，按资源和操作稳定排序。
     * @param scopeType 平台或租户管理域，必填
     * @param includeDisabled 是否包含已停用定义；普通授权选择器应传 false
     * @return 不可变权限列表
     */
    List<Permission> list(ScopeType scopeType, boolean includeDisabled);
}
