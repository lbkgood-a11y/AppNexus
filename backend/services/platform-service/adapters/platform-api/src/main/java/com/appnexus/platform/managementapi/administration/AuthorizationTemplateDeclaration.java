package com.appnexus.platform.managementapi.administration;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 授权模板专用入口的唯一对象声明；业务校验与权限执行沿用原用例。 */
@Component
public final class AuthorizationTemplateDeclaration implements Provider {
    /** @return 已挂接到原页面及 API 的对象与操作契约 */
    @Override
    public Descriptor descriptor() {
        var tenant = new DataAccess("TENANT", List.of(
                new DataResource("authorization.template.tenant", "", "租户授权模板", "Tenant policy templates", List.of())), Map.of());
        var access = new DataAccess("PLATFORM", List.of(
                new DataResource("authorization.template.platform", "", "平台授权模板", "Platform policy templates", List.of())), Map.of(), List.of(), tenant);
        return new Descriptor("AuthorizationTemplate", "授权模板", "Authorization Template", "authorization", 2, Category.CONFIGURATION, List.of(), List.of(
                operation("AuthorizationTemplate", "listPlatform", "授权模板：查看平台目录", "Grant platform role permissions", "access.role:grant",
                        new PageOperation("platform/authorization", "/platform/authorization", "授权模板：查看平台目录", "Grant platform role permissions", "GET", "/bff/platform/roles/{role}/policy-templates")),
                operation("AuthorizationTemplate", "savePlatform", "授权模板：保存平台模板", "Grant platform role permissions", "access.role:grant",
                        new PageOperation("platform/authorization", "/platform/authorization", "授权模板：保存平台模板", "Grant platform role permissions", "POST", "/bff/platform/roles/{role}/policy-templates")),
                operation("AuthorizationTemplate", "applyPlatform", "授权模板：应用平台模板", "Grant platform role permissions", "access.role:grant",
                        new PageOperation("platform/authorization", "/platform/authorization", "授权模板：应用平台模板", "Grant platform role permissions", "POST", "/bff/platform/roles/{role}/policy-template-application")),
                operation("AuthorizationTemplate", "listTenant", "授权模板：查看租户目录", "Grant tenant role permissions", "access.tenant.role:grant",
                        new PageOperation("roles/authorization", "/access/authorization", "授权模板：查看租户目录", "Grant tenant role permissions", "GET", "/api/access/roles/{role}/policy-templates")),
                operation("AuthorizationTemplate", "saveTenant", "授权模板：保存租户模板", "Grant tenant role permissions", "access.tenant.role:grant",
                        new PageOperation("roles/authorization", "/access/authorization", "授权模板：保存租户模板", "Grant tenant role permissions", "POST", "/api/access/roles/{role}/policy-templates")),
                operation("AuthorizationTemplate", "applyTenant", "授权模板：应用租户模板", "Grant tenant role permissions", "access.tenant.role:grant",
                        new PageOperation("roles/authorization", "/access/authorization", "授权模板：应用租户模板", "Grant tenant role permissions", "POST", "/api/access/roles/{role}/policy-template-application"))), access);
    }
}
