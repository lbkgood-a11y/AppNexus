package com.appnexus.platform.managementapi.administration;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 权限目录专用入口的唯一对象声明；业务校验与权限执行沿用原用例。 */
@Component
public final class PermissionCatalogDeclaration implements Provider {
    /** @return 已挂接到原页面及 API 的对象与操作契约 */
    @Override
    public Descriptor descriptor() {
        var tenant = new DataAccess("TENANT", List.of(
                new DataResource("access.catalog.tenant", "", "租户权限目录", "Tenant permission catalogs", List.of())), Map.of());
        var access = new DataAccess("PLATFORM", List.of(
                new DataResource("access.catalog.platform", "", "平台权限目录", "Platform permission catalogs", List.of())), Map.of(), List.of(), tenant);
        return new Descriptor("PermissionCatalog", "权限目录", "Permission Catalog", "authorization", 2, Category.CONFIGURATION, List.of(), List.of(
                operation("PermissionCatalog", "navigationPlatform", "权限目录：查看授权导航（平台）", "Grant platform role permissions", "access.role:grant",
                        new PageOperation("platform/authorization", "/platform/authorization", "权限目录：查看授权导航（平台）", "Grant platform role permissions", "GET", "/bff/platform/authorization-navigation")),
                operation("PermissionCatalog", "navigationTenant", "权限目录：查看授权导航（租户）", "Grant tenant role permissions", "access.tenant.role:grant",
                        new PageOperation("roles/authorization", "/access/authorization", "权限目录：查看授权导航（租户）", "Grant tenant role permissions", "GET", "/api/access/authorization-navigation")),
                operation("PermissionCatalog", "listPlatform", "权限目录：查看平台目录", "Grant platform role permissions", "access.role:grant",
                        new PageOperation("platform/authorization", "/platform/authorization", "权限目录：查看平台目录", "Grant platform role permissions", "GET", "/bff/platform/permissions")),
                operation("PermissionCatalog", "reconcile", "权限目录：检查授权差异", "Grant platform role permissions", "access.role:grant",
                        new PageOperation("platform/authorization", "/platform/authorization", "权限目录：检查授权差异", "Grant platform role permissions", "GET", "/bff/platform/authorization-reconciliation")),
                operation("PermissionCatalog", "fieldsPlatform", "权限目录：查看字段目录（平台）", "Grant platform role permissions", "access.role:grant",
                        new PageOperation("platform/authorization", "/platform/authorization", "权限目录：查看字段目录（平台）", "Grant platform role permissions", "GET", "/bff/platform/field-policy-metadata")),
                operation("PermissionCatalog", "fieldsTenant", "权限目录：查看字段目录（租户）", "Grant tenant role permissions", "access.tenant.role:grant",
                        new PageOperation("roles/authorization", "/access/authorization", "权限目录：查看字段目录（租户）", "Grant tenant role permissions", "GET", "/api/access/field-policy-metadata")),
                operation("PermissionCatalog", "scopesPlatform", "权限目录：查看数据范围目录（平台）", "Grant platform role permissions", "access.role:grant",
                        new PageOperation("platform/authorization", "/platform/authorization", "权限目录：查看数据范围目录（平台）", "Grant platform role permissions", "GET", "/bff/platform/data-policy-metadata")),
                operation("PermissionCatalog", "listTenant", "权限目录：查看租户目录", "Grant tenant role permissions", "access.tenant.role:grant",
                        new PageOperation("roles/authorization", "/access/authorization", "权限目录：查看租户目录", "Grant tenant role permissions", "GET", "/api/access/permissions")),
                operation("PermissionCatalog", "scopesTenant", "权限目录：查看数据范围目录（租户）", "Grant tenant role permissions", "access.tenant.role:grant",
                        new PageOperation("roles/authorization", "/access/authorization", "权限目录：查看数据范围目录（租户）", "Grant tenant role permissions", "GET", "/api/access/data-policy-metadata")),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("PermissionCatalog", "platformDecisions", "本人平台操作准入", "Platform operation decisions", ""),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("PermissionCatalog", "tenantDecisions", "本人租户操作准入", "Tenant operation decisions", ""),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("PermissionCatalog", "objects", "查询功能对象目录", "Read feature objects", "configuration.menu:read"),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("PermissionCatalog", "operations", "查询功能操作目录", "Read feature operations", "configuration.menu:read")), access);
    }
}
