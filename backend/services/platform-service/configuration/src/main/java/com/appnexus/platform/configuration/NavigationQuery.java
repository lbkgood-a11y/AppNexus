package com.appnexus.platform.configuration;

import java.util.List;
import java.util.UUID;

/** 当前主体导航查询；菜单只描述入口资格，不能替代业务 API 的逐请求授权。 */
public interface NavigationQuery {
    /**
     * 授权视图节点；资源列表仅为元数据，不用于展开整个资源的权限。
     * @param operations 构建契约登记的页面实际操作权限码，共享操作允许在多个页面展示并按权限 ID 去重
     */
    record AuthorizationNode(UUID id,UUID parentId,String titleZh,String titleEn,int sortOrder,List<String> resources,String permission,List<String> operations) {}

    /** 读取指定域完整授权导航，不按操作者本人菜单过滤；调用入口必须先复核授权管理权。 */
    List<AuthorizationNode> authorizationCatalog(MenuManagement.Scope scope);
    /**
     * 当前导航快照，包含可访问隐藏页面；visible 仅控制导航展示。
     * @param tenantId 服务端复核后的当前租户 UUID；未选择时 null
     * @param contextVersion 服务端租户上下文版本，0 表示未选择；不作为权限凭据
     * @param items 已过滤的稳定有序菜单平面清单，不包含 BUTTON
     */
    record Snapshot(UUID tenantId,long contextVersion,List<MenuManagement.Menu> items) {}

    /**
     * 按当前账号与有效成员过滤菜单，每次重新查询权威授权，不依赖浏览器缓存。
     * @param accountId 已认证账号 UUID，必填
     * @param tenantId 可信租户 UUID；未选择时 null
     * @param memberId 与租户同时提供的已验证成员 UUID
     * @param contextVersion 服务端上下文版本，非负
     * @return 仅含启用且满足权限和祖先约束的入口
     */
    Snapshot current(UUID accountId,UUID tenantId,UUID memberId,long contextVersion);
}
