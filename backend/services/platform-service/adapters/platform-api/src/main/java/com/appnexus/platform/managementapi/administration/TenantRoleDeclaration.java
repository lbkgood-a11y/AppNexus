package com.appnexus.platform.managementapi.administration;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.appnexus.contracts.businessobject.DedicatedOperations;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 租户角色专用入口的唯一对象声明；业务校验与权限执行沿用原用例。 */
@Component
public final class TenantRoleDeclaration implements Provider {
    /** @return 已挂接到原页面及 API 的对象与操作契约 */
    @Override
    public Descriptor descriptor() {
        var access = new DataAccess("TENANT", List.of(
                new DataResource("access.tenant.role", "", "租户角色", "Tenant roles", List.of()),
                new DataResource("access.tenant.role.authorization", "access.tenant.role", "角色授权", "Role authorizations", List.of())), Map.of());
        return new Descriptor("TenantRole", "租户角色", "Tenant Role", "authorization", 2, Category.CONFIGURATION, List.of(), List.of(
                operation("TenantRole", "listManaged", "租户角色：查看列表（平台代管）", "Read tenant roles", "tenant.role:read",
                        new PageOperation("platform/tenants", "/platform/tenants", "租户角色：查看列表（平台代管）", "Read tenant roles", "GET", "/bff/platform/tenants/{tenant}/roles")),
                operation("TenantRole", "createManaged", "租户角色：新增（平台代管）", "Create tenant roles", "tenant.role:create",
                        new PageOperation("platform/tenants", "/platform/tenants", "租户角色：新增（平台代管）", "Create tenant roles", "POST", "/bff/platform/tenants/{tenant}/roles")),
                operation("TenantRole", "updateManaged", "租户角色：修改（平台代管）", "Update tenant roles", "tenant.role:update",
                        new PageOperation("platform/tenants", "/platform/tenants", "租户角色：修改（平台代管）", "Update tenant roles", "PUT", "/bff/platform/tenants/{tenant}/roles/{id}")),
                operation("TenantRole", "setStatusManaged", "租户角色：启停（平台代管）", "Update tenant roles", "tenant.role:update",
                        new PageOperation("platform/tenants", "/platform/tenants", "租户角色：启停（平台代管）", "Update tenant roles", "PATCH", "/bff/platform/tenants/{tenant}/roles/{id}/status")),
                operation("TenantRole", "deleteManaged", "租户角色：删除（平台代管）", "Delete tenant roles", "tenant.role:delete",
                        new PageOperation("platform/tenants", "/platform/tenants", "租户角色：删除（平台代管）", "Delete tenant roles", "POST", "/bff/platform/tenants/{tenant}/roles/{id}/delete")),
                operation("TenantRole", "list", "租户角色：查看列表", "Read tenant roles", "access.tenant.role:read",
                        new PageOperation("roles/index", "/access/roles", "租户角色：查看列表", "Read tenant roles", "GET", "/api/access/roles"),
                        new PageOperation("roles/authorization", "/access/authorization", "租户角色：查看列表", "Read tenant roles", "GET", "/api/access/roles")),
                operation("TenantRole", "create", "租户角色：新增", "Create tenant roles", "access.tenant.role:create",
                        new PageOperation("roles/index", "/access/roles", "租户角色：新增", "Create tenant roles", "POST", "/api/access/roles")),
                operation("TenantRole", "update", "租户角色：修改", "Update tenant roles", "access.tenant.role:update",
                        new PageOperation("roles/index", "/access/roles", "租户角色：修改", "Update tenant roles", "PUT", "/api/access/roles/{id}")),
                operation("TenantRole", "setStatus", "租户角色：启停", "Update tenant roles", "access.tenant.role:update",
                        new PageOperation("roles/index", "/access/roles", "租户角色：启停", "Update tenant roles", "PATCH", "/api/access/roles/{id}/status")),
                operation("TenantRole", "delete", "租户角色：删除", "Delete tenant roles", "access.tenant.role:delete",
                        new PageOperation("roles/index", "/access/roles", "租户角色：删除", "Delete tenant roles", "POST", "/api/access/roles/{id}/delete")),
                DedicatedOperations.operation("TenantRole", "authorizationReadManaged", "角色授权：查看（平台代管）", "Grant tenant role permissions", "tenant.role:grant",
                        new PageOperation("platform/tenants", "/platform/tenants", "角色授权：查看（平台代管）", "Grant tenant role permissions", "GET", "/bff/platform/tenants/{tenant}/roles/{role}/permissions")),
                DedicatedOperations.operation("TenantRole", "authorizationReplaceManaged", "角色授权：保存授权（平台代管）", "Grant tenant role permissions", "tenant.role:grant",
                        new PageOperation("platform/tenants", "/platform/tenants", "角色授权：保存授权（平台代管）", "Grant tenant role permissions", "PUT", "/bff/platform/tenants/{tenant}/roles/{role}/permissions")),
                DedicatedOperations.operation("TenantRole", "authorizationReadTenant", "角色授权：查看（租户）", "Grant tenant role permissions", "access.tenant.role:grant",
                        new PageOperation("roles/authorization", "/access/authorization", "角色授权：查看（租户）", "Grant tenant role permissions", "GET", "/api/access/roles/{role}/permissions")),
                DedicatedOperations.operation("TenantRole", "authorizationReplaceTenant", "角色授权：保存授权（租户）", "Grant tenant role permissions", "access.tenant.role:grant",
                        new PageOperation("roles/authorization", "/access/authorization", "角色授权：保存授权（租户）", "Grant tenant role permissions", "PUT", "/api/access/roles/{role}/permissions"))), access);
    }
}
