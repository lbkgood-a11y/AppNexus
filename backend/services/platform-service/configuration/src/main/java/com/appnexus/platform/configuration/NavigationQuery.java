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
     * 页面准入诊断，隐藏入口仍可能允许直接进入。
     * @param page 构建内页面标识
     * @param path 已登记路由地址
     * @param accessible 当前主体是否满足真实导航准入
     * @param visible 至少一个合法菜单入口及其全部祖先可见
     * @param reasons 稳定阻断/隐藏原因；可见时为空
     */
    record PageCheck(String page,String path,boolean accessible,boolean visible,List<String> reasons) {}
    /**
     * 指定主体页面诊断；调用方必须先验证目标管理授权，不能直接暴露给普通用户。
     * @param accountId 目标账号 UUID，必填；不存在或停用时返回不可用原因
     * @param tenantId 目标租户 UUID；平台诊断为 null
     * @param memberId 同租户成员 UUID；平台诊断为 null
     * @return 当前管理域全部登记页面的准入与显隐结果
     */
    List<PageCheck> inspect(UUID accountId,UUID tenantId,UUID memberId);
    /**
     * 同租户成员诊断；调用方先验证成员管理授权，账号由持久化关系解析。
     * @param tenantId 已验证管理租户 UUID，必填
     * @param memberId 目标成员 UUID，必须属于该租户
     * @return 目标成员的页面准入诊断，不执行业务写入
     * @throws SecurityException 目标不属于该租户，不泄露其他租户成员资料
     */
    List<PageCheck> inspectMember(UUID tenantId,UUID memberId);
    /** 已授权管理调用解析目标成员账号；严格限定租户，不用于公开身份枚举。 */
    UUID accountForMember(UUID tenantId,UUID memberId);

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
