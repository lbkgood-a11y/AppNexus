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
            new Page("organization/personnel","/organization/personnel","人员管理","Personnel",MenuManagement.Scope.TENANT,"masterdata.person:read",Gate.PERMISSION),
            new Page("master-data/partner","/master-data/partner","客商管理","Business partners",MenuManagement.Scope.TENANT,"masterdata.partner:read",Gate.PERMISSION),
            new Page("master-data/storage","/master-data/storage","库区与库位","Storage areas and locations",MenuManagement.Scope.TENANT,"masterdata.storagearea:read",Gate.PERMISSION),
            new Page("master-data/warehouse","/master-data/warehouse","仓库管理","Warehouses",MenuManagement.Scope.TENANT,"masterdata.warehouse:read",Gate.PERMISSION),
            new Page("master-data/country-region","/master-data/country-region","国别地区","Countries and regions",MenuManagement.Scope.TENANT,"masterdata.countryregion:read",Gate.PERMISSION),
            new Page("master-data/subdivision","/master-data/subdivision","行政区划","Subdivisions",MenuManagement.Scope.TENANT,"masterdata.subdivision:read",Gate.PERMISSION),
            new Page("master-data/product-sku","/master-data/product-sku","商品明细管理","Product SKUs",MenuManagement.Scope.TENANT,"masterdata.product:read",Gate.PERMISSION),
            new Page("master-data/product","/master-data/product","商品管理","Products",MenuManagement.Scope.TENANT,"masterdata.product:read",Gate.PERMISSION),
            new Page("master-data/product-category","/master-data/product-category","商品分类","Product categories",MenuManagement.Scope.TENANT,"masterdata.productcategory:read",Gate.PERMISSION),
            new Page("master-data/product-unit","/master-data/product-unit","计量单位","Product units",MenuManagement.Scope.TENANT,"masterdata.productunit:read",Gate.PERMISSION),
            new Page("master-data/product-attribute","/master-data/product-attribute","属性管理","Product attributes",MenuManagement.Scope.TENANT,"masterdata.productattribute:read",Gate.PERMISSION),
            new Page("master-data/product-template","/master-data/product-template","属性模板","Product templates",MenuManagement.Scope.TENANT,"masterdata.producttemplate:read",Gate.PERMISSION),

            platform("platform/business-objects","/platform/business-objects","业务对象目录","Business object catalog","business.object.catalog:manage"),
            new Page("business-objects/index","/business-objects/workbench","业务配置","Business configuration",MenuManagement.Scope.TENANT,"business.object.configuration:manage",Gate.PERMISSION),
            new Page("workflow/leave","/workflow/leave","请假单 POC","Leave request POC",MenuManagement.Scope.TENANT,null,Gate.PERMISSION),
            new Page("workflow/designer","/workflow/designer","流程设计器","Workflow designer",MenuManagement.Scope.TENANT,"workflow:read",Gate.PERMISSION),
            new Page("workflow/index","/workflow/center","工作流中心","Workflow center",MenuManagement.Scope.TENANT,null,Gate.PERMISSION),
            new Page("print/index","/print/management","打印管理","Print management",MenuManagement.Scope.TENANT,"print.template:read",Gate.PERMISSION),
            platform("platform/tenants","/platform/tenants","租户管理","Tenants","tenant:read"),
            platform("platform/users","/platform/users","账号管理","Accounts","identity.account:read"),
            platform("platform/features","/platform/features","功能清单","Feature catalog","configuration.menu:read"),
            platform("platform/menus","/platform/menus","菜单管理","Menus","configuration.menu:read"),
            platform("platform/roles","/platform/roles","平台角色","Platform roles","access.role:read"),
            platform("platform/authorization","/platform/authorization","授权中心","Authorization", "access.role:grant"),
            new Page("organization/index","/organization/manage","组织管理","Organizations",MenuManagement.Scope.TENANT,"organization.unit:read",Gate.PERMISSION),
            tenant("roles/index","/access/roles","租户角色","Tenant roles","access.tenant.role:read"),
            tenant("roles/authorization","/access/authorization","授权中心","Authorization","access.tenant.role:grant"),
            new Page("configuration/coding-rules","/configuration/coding-rules","编码规则","Coding rules",MenuManagement.Scope.TENANT,"coding.rule:manage",Gate.PERMISSION),
            new Page("configuration/parameters","/configuration/parameters","租户参数","Tenant parameters",MenuManagement.Scope.TENANT,"configuration.parameter:save",Gate.PERMISSION),
            new Page("configuration/dictionaries","/configuration/dictionaries","业务字典","Dictionaries",MenuManagement.Scope.TENANT,"configuration.dictionary:read",Gate.PERMISSION)
        );
    }

    /** 未知标识返回 null，由发布校验和运行时导航失败关闭。 */
    public static Page find(String key) { return all().stream().filter(page -> page.key().equals(key)).findFirst().orElse(null); }
    private static Page platform(String key,String path,String zh,String en,String permission) {
        return new Page(key,path,zh,en,MenuManagement.Scope.PLATFORM,permission,Gate.PLATFORM_ADMIN);
    }
    private static Page tenant(String key,String path,String zh,String en,String permission) {
        return new Page(key,path,zh,en,MenuManagement.Scope.TENANT,permission,Gate.TENANT_ADMIN);
    }
}
