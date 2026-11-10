package com.appnexus.platform.configuration;

import java.util.List;

/** 构建内页面契约；与 admin 的显式组件注册表同步，禁止通过模糊路径加载任意页面。 */
public final class NavigationPages {
    private NavigationPages() {}

    /** 页面额外访问约束，延续当前真实后端管理接口边界。 */
    public enum Gate { PLATFORM_ADMIN, TENANT_ADMIN, PERMISSION }

    /**
     * 页面目录项；入口可以改名和分组，但不能改写页面最低访问约束。
     * @param key 构建内组件标识
     * @param path 唯一注册路由，必须精确匹配
     * @param titleZh 中文页面名称
     * @param titleEn 英文页面名称
     * @param scope 受控页面管理域
     * @param permission 基础操作权限码；null 表示由 gate 验证现有管理能力
     * @param gate 后端当前已有的额外准入约束
     */
    public record Page(String key,String path,String titleZh,String titleEn,MenuManagement.Scope scope,String permission,Gate gate) {}

    /** 返回已交付的真实页面，不包含模板演示、远程脚本或尚未集成的应用。 */
    public static List<Page> all() {
        return List.of(
            new Page("workflow/designer","/workflow/designer","流程设计器","Workflow designer",MenuManagement.Scope.TENANT,"workflow:read",Gate.PERMISSION),
            new Page("workflow/index","/workflow/center","工作流中心","Workflow center",MenuManagement.Scope.TENANT,null,Gate.PERMISSION),
            platform("platform/tenants","/platform/tenants","租户管理","Tenants","tenant:read"),
            platform("platform/users","/platform/users","账号管理","Accounts","identity.account:read"),
            platform("platform/menus","/platform/menus","菜单管理","Menus","configuration.menu:read"),
            platform("platform/roles","/platform/roles","平台角色","Platform roles","access.role:read"),
            platform("platform/authorization","/platform/authorization","授权中心","Authorization", "access.role:grant"),
            new Page("organization/index","/organization/manage","组织管理","Organizations",MenuManagement.Scope.TENANT,"organization.unit:read",Gate.PERMISSION),
            tenant("roles/index","/access/roles","租户角色","Tenant roles","access.tenant.role:read"),
            tenant("roles/authorization","/access/authorization","授权中心","Authorization","access.tenant.role:grant"),
            new Page("configuration/dictionaries","/configuration/dictionaries","业务字典","Dictionaries",MenuManagement.Scope.TENANT,"configuration.dictionary:read",Gate.PERMISSION)
        );
    }

    /** 未知标识返回 null，由发布校验和运行时导航失败关闭。 */
    public static Page find(String key) { return all().stream().filter(page -> page.key().equals(key)).findFirst().orElse(null); }
    /**
     * 页面实际已接入操作码，不按资源整组展开；多个按钮可共享同一个业务操作权限。
     * @param key 构建登记组件标识，不接受菜单标题或路径推断
     * @return 精确权限码；未知页面不自动继承整个资源的操作
     */
    public static List<String> operations(String key) {
        if(key==null)return List.of();
        return switch(key){
            case "workflow/index" -> List.of("workflow:operate");
            case "workflow/designer" -> List.of("workflow:read","workflow:edit","workflow:publish");
            case "platform/roles" -> List.of("access.role:read","access.role:create","access.role:update","access.role:enable","access.role:delete");
            case "platform/authorization" -> List.of("access.role:read","access.role:grant","identity.account:read");
            case "roles/index" -> List.of("access.tenant.role:read","access.tenant.role:create","access.tenant.role:update","access.tenant.role:delete");
            case "roles/authorization" -> List.of("access.tenant.role:read","access.tenant.role:grant","organization.permission:grant");
            case "platform/menus" -> List.of("configuration.menu:read","configuration.menu:create","configuration.menu:update","configuration.menu:delete");
            case "platform/users" -> List.of("identity.account:read","identity.account:create","identity.account:update","identity.account:enable","identity.account:revoke_sessions","identity.account:reset_password");
            case "platform/tenants" -> List.of("tenant:read","tenant:create","tenant:update","tenant:enable","tenant.member:read","tenant.member:create","tenant.member:enable","tenant.member:remove","tenant.invitation:read","tenant.invitation:create","tenant.invitation:revoke","integration.client:read","integration.client:create","integration.client:update","integration.client:rotate_secret","identity.account:read");
            case "organization/index" -> List.of("organization.unit:read","organization.unit:create","organization.unit:update","organization.unit:delete","organization.permission:grant");
            case "configuration/dictionaries" -> List.of("configuration.dictionary:read","configuration.dictionary:create","configuration.dictionary:update","configuration.dictionary:delete","configuration.dictionary:item_create","configuration.dictionary:item_update","configuration.dictionary:item_delete");
            default -> List.of();
        };
    }
    private static Page platform(String key,String path,String zh,String en,String permission) {
        return new Page(key,path,zh,en,MenuManagement.Scope.PLATFORM,permission,Gate.PLATFORM_ADMIN);
    }
    private static Page tenant(String key,String path,String zh,String en,String permission) {
        return new Page(key,path,zh,en,MenuManagement.Scope.TENANT,permission,Gate.TENANT_ADMIN);
    }
}
