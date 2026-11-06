package com.appnexus.platform.configuration;

import java.util.List;
import java.util.UUID;

/**
 * 平台菜单资源目录用例；目录只描述导航与按钮资源，不代替服务端接口授权。
 * 所有读写均要求经过认证的平台管理员，写操作必须由适配器在同一事务内完成版本校验与审计。
 */
public interface MenuManagement {
    /** 菜单节点类型；BUTTON 只用于权限资源，不参与页面路由。 */
    enum Type { DIRECTORY, MENU, BUTTON }

    /**
     * 菜单节点摘要。
     * @param version 从 0 开始的乐观锁版本，更新时必须原样回传
     * @param sortOrder 同级排序值，范围 0–9999，值相同时按 code 稳定排序
     */
    record Menu(UUID id, UUID parentId, Type type, String code, String titleZh, String titleEn,
                String path, String component, String icon, String permission, int sortOrder,
                boolean visible, boolean enabled, long version) {}

    /**
     * 创建或更新所需字段。
     * @param parentId 根节点为空；父节点必须存在且不能是 BUTTON
     * @param code 全平台唯一稳定标识，1–100 位字母、数字、点、冒号、下划线或连字符
     * @param titleZh 中文名称，1–100 字符
     * @param titleEn 英文名称，1–100 字符
     * @param path DIRECTORY/MENU 可选路由路径，最长 200 字符；BUTTON 必须为空
     * @param component MENU 可选的前端组件标识，最长 200 字符；不得由服务端直接加载任意脚本
     * @param permission BUTTON 必填权限标识，其他类型可为空，最长 100 字符
     * @param sortOrder 同级排序值，范围 0–9999
     */
    record Command(UUID parentId, Type type, String code, String titleZh, String titleEn,
                   String path, String component, String icon, String permission,
                   int sortOrder, boolean visible, boolean enabled) {}

    /** 返回完整菜单目录的有序平面列表，由客户端按 parentId 构树；无权限时拒绝而不是返回空列表。 */
    List<Menu> list(UUID actor);
    /** 创建节点并追加审计。 */
    Menu create(UUID actor, Command command);
    /** 更新节点；目标父级不能是自身或后代。 */
    Menu update(UUID actor, UUID id, long expectedVersion, Command command);
    /** 删除叶子节点；存在子节点或版本已变化时拒绝。 */
    void delete(UUID actor, UUID id, long expectedVersion);
}
