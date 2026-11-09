package com.appnexus.platform.configuration;

import java.util.List;
import java.util.UUID;

/**
 * 平台菜单资源目录用例；目录只描述导航与按钮资源，不代替服务端接口授权。
 * 所有读写均要求经过认证的平台管理员，写操作必须由适配器在同一事务内完成版本校验与审计。
 */
public interface MenuManagement {
    /** 菜单节点类型；BUTTON 不参与导航，EXTERNAL 只通过浏览器新标签页访问目标系统。 */
    enum Type { DIRECTORY, MENU, BUTTON, EXTERNAL }

    /** 入口适用域；AUTHENTICATED 仅表示已登录可见，外部系统仍独立鉴权。 */
    enum Scope { PLATFORM, TENANT, AUTHENTICATED }

    /**
     * 菜单节点摘要。
     * @param version 从 0 开始的乐观锁版本，更新时必须原样回传
     * @param sortOrder 同级排序值，范围 0–9999，值相同时按 code 稳定排序
     * @param scope 入口适用域，历史菜单缺省为 PLATFORM
     * @param externalUrl 仅 EXTERNAL 返回外部地址；不作为目标系统认证或授权凭据
     */
    record Menu(UUID id, UUID parentId, Type type, String code, String titleZh, String titleEn,
                String path, String component, String icon, String permission, int sortOrder,
                boolean visible, boolean enabled, long version, Scope scope, String externalUrl) {}

    /**
     * 创建或更新所需字段。
     * @param parentId 根节点为空；父节点必须是同域 DIRECTORY，禁止自身或后代
     * @param code 全平台唯一稳定标识，1–100 位字母、数字、点、冒号、下划线或连字符
     * @param titleZh 中文名称，1–100 字符
     * @param titleEn 英文名称，1–100 字符
     * @param path MENU 固定注册路径，最长 200 字符；其他类型必须为空
     * @param component MENU 必须选择已注册页面标识，最长 200 字符；不得加载任意脚本
     * @param permission BUTTON 必填权限标识，其他类型可为空，最长 100 字符
     * @param sortOrder 同级排序值，范围 0–9999；重复值按 code、id 稳定排序
     * @param scope 适用域，缺省 PLATFORM 以兼容旧请求；租户域必须有有效成员上下文
     * @param externalUrl EXTERNAL 必填 HTTP/HTTPS 绝对地址，最长 2000 字符；不允许凭据、协议相对地址或脚本
     */
    record Command(UUID parentId, Type type, String code, String titleZh, String titleEn,
                   String path, String component, String icon, String permission,
                   int sortOrder, boolean visible, boolean enabled, Scope scope, String externalUrl) {
        /** 旧调用保持平台域；不把旧菜单静默开放给所有登录用户。 */
        public Command(UUID parentId, Type type, String code, String titleZh, String titleEn,
                       String path, String component, String icon, String permission,
                       int sortOrder, boolean visible, boolean enabled) {
            this(parentId,type,code,titleZh,titleEn,path,component,icon,permission,sortOrder,visible,enabled,Scope.PLATFORM,null);
        }
        /** 缺省域延续原平台菜单边界。 */
        public Command { if(scope==null)scope=Scope.PLATFORM; }
    }

    /** 返回完整菜单目录的有序平面列表，由客户端按 parentId 构树；无权限时拒绝而不是返回空列表。 */
    List<Menu> list(UUID actor);
    /** 创建节点并追加审计。 */
    Menu create(UUID actor, Command command);
    /** 更新节点；目标父级不能是自身或后代。 */
    Menu update(UUID actor, UUID id, long expectedVersion, Command command);
    /** 删除叶子节点；存在子节点或版本已变化时拒绝。 */
    void delete(UUID actor, UUID id, long expectedVersion);
}
