package com.appnexus.platform.managementapi.configuration;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 导航菜单专用入口的唯一对象声明；业务校验与权限执行沿用原用例。 */
@Component
public final class NavigationMenuDeclaration implements Provider {
        /** @return 已挂接到原页面及 API 的对象与操作契约 */
        @Override
        public Descriptor descriptor() {
                var access = new DataAccess("PLATFORM", List.of(
                                new DataResource("configuration.menu", "", "导航菜单", "Navigation menus", List.of())),
                                Map.of());
                return new Descriptor("NavigationMenu", "导航菜单", "Navigation Menu", "configuration", 2,
                                Category.CONFIGURATION, List.of(), List.of(
                                                operation("NavigationMenu", "features", "导航菜单：查看功能目录",
                                                                "Read menu configuration", "configuration.menu:read",
                                                                new PageOperation("platform/features",
                                                                                "/platform/features", "导航菜单：查看功能目录",
                                                                                "Read menu configuration", "GET",
                                                                                "/bff/platform/menus/features")),
                                                operation("NavigationMenu", "pageOptions", "导航菜单：查看页面目录",
                                                                "Read menu configuration", "configuration.menu:read",
                                                                new PageOperation("platform/menus", "/platform/menus",
                                                                                "导航菜单：查看页面目录",
                                                                                "Read menu configuration", "GET",
                                                                                "/bff/platform/menus/pages")),
                                                operation("NavigationMenu", "list", "导航菜单：查看列表",
                                                                "Read menu configuration", "configuration.menu:read",
                                                                new PageOperation("platform/menus", "/platform/menus",
                                                                                "导航菜单：查看列表", "Read menu configuration",
                                                                                "GET", "/bff/platform/menus")),
                                                operation("NavigationMenu", "create", "导航菜单：新增",
                                                                "Create menu configuration",
                                                                "configuration.menu:create",
                                                                new PageOperation("platform/menus", "/platform/menus",
                                                                                "导航菜单：新增", "Create menu configuration",
                                                                                "POST", "/bff/platform/menus")),
                                                operation("NavigationMenu", "update", "导航菜单：修改",
                                                                "Edit menus (including status)",
                                                                "configuration.menu:update",
                                                                new PageOperation("platform/menus", "/platform/menus",
                                                                                "导航菜单：修改",
                                                                                "Edit menus (including status)", "PUT",
                                                                                "/bff/platform/menus/{id}")),
                                                operation("NavigationMenu", "delete", "导航菜单：删除",
                                                                "Delete menu configuration",
                                                                "configuration.menu:delete",
                                                                new PageOperation("platform/menus", "/platform/menus",
                                                                                "导航菜单：删除", "Delete menu configuration",
                                                                                "DELETE", "/bff/platform/menus/{id}")),
                                                com.appnexus.contracts.businessobject.DedicatedOperations.operation(
                                                                "NavigationMenu", "current", "本人导航",
                                                                "Current navigation", "")),
                                access);
        }
}
