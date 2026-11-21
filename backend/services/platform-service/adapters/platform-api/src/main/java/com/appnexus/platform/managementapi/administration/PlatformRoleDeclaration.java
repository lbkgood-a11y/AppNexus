package com.appnexus.platform.managementapi.administration;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.appnexus.contracts.businessobject.DedicatedOperations;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 平台角色专用入口的唯一对象声明；业务校验与权限执行沿用原用例。 */
@Component
public final class PlatformRoleDeclaration implements Provider {
    /** @return 已挂接到原页面及 API 的对象与操作契约 */
    @Override
    public Descriptor descriptor() {
        var access = new DataAccess("PLATFORM", List.of(
                new DataResource("access.role", "", "平台角色", "Platform roles", List.of()),
                new DataResource("access.role.authorization", "access.role", "角色授权", "Role authorizations", List.of())), Map.of());
        return new Descriptor("PlatformRole", "平台角色", "Platform Role", "authorization", 2, Category.CONFIGURATION, List.of(), List.of(
                operation("PlatformRole", "list", "平台角色：查看列表", "Read platform roles", "access.role:read",
                        new PageOperation("platform/roles", "/platform/roles", "平台角色：查看列表", "Read platform roles", "GET", "/bff/platform/roles"),
                        new PageOperation("platform/authorization", "/platform/authorization", "平台角色：查看列表", "Read platform roles", "GET", "/bff/platform/roles")),
                operation("PlatformRole", "create", "平台角色：新增", "Create platform roles", "access.role:create",
                        new PageOperation("platform/roles", "/platform/roles", "平台角色：新增", "Create platform roles", "POST", "/bff/platform/roles")),
                operation("PlatformRole", "update", "平台角色：修改", "Update platform roles", "access.role:update",
                        new PageOperation("platform/roles", "/platform/roles", "平台角色：修改", "Update platform roles", "PUT", "/bff/platform/roles/{id}")),
                operation("PlatformRole", "setStatus", "平台角色：启停", "Enable platform roles", "access.role:enable",
                        new PageOperation("platform/roles", "/platform/roles", "平台角色：启停", "Enable platform roles", "PATCH", "/bff/platform/roles/{id}/status")),
                operation("PlatformRole", "delete", "平台角色：删除", "Delete platform roles", "access.role:delete",
                        new PageOperation("platform/roles", "/platform/roles", "平台角色：删除", "Delete platform roles", "POST", "/bff/platform/roles/{id}/delete")),
                DedicatedOperations.operation("PlatformRole", "authorizationReadPlatform", "角色授权：查看（平台）", "Grant platform role permissions", "access.role:grant",
                        new PageOperation("platform/authorization", "/platform/authorization", "角色授权：查看（平台）", "Grant platform role permissions", "GET", "/bff/platform/roles/{role}/permissions")),
                DedicatedOperations.operation("PlatformRole", "authorizationReplacePlatform", "角色授权：保存授权（平台）", "Grant platform role permissions", "access.role:grant",
                        new PageOperation("platform/authorization", "/platform/authorization", "角色授权：保存授权（平台）", "Grant platform role permissions", "PUT", "/bff/platform/roles/{role}/permissions"))), access);
    }
}
