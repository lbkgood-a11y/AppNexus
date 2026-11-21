package com.appnexus.platform.security;
import com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.appnexus.contracts.businessobject.DedicatedOperations;

import com.appnexus.contracts.businessobject.*;
import com.appnexus.platform.configuration.NavigationPages;
import java.util.*;
import org.springframework.stereotype.Component;

/** 租户成员唯一声明；保留真实权限和接口，字段目录及响应投影共用本对象的数据权限定义。 */
@Component
public final class TenantMemberDeclaration implements BusinessObjects.Provider {
    @Override public Descriptor descriptor(){
        var operations=List.of(
            operation("listManaged","租户成员：查看列表（平台代管）","Read tenant members","tenant.member:read","GET","/bff/platform/tenants/{tenant}/members",List.of("platform/tenants")),
            operation("createManaged","租户成员：新增（平台代管）","Add tenant members","tenant.member:create","POST","/bff/platform/tenants/{tenant}/members",List.of("platform/tenants")),
            operation("setEnabledManaged","租户成员：启停（平台代管）","Enable tenant members","tenant.member:enable","PUT","/bff/platform/tenants/{tenant}/members/{member}/state",List.of("platform/tenants")),
            operation("removeManaged","租户成员：移除（平台代管）","Remove tenant members","tenant.member:remove","DELETE","/bff/platform/tenants/{tenant}/members/{member}",List.of("platform/tenants")),
            operation("authorizationOptions","查看租户授权账号目录","Read tenant authorization accounts","access.tenant.role:grant","GET","/api/access/member-options",List.of("roles/authorization")),
                DedicatedOperations.operation("TenantMember", "rolesReadManaged", "成员角色关联：查看（平台代管）", "Grant tenant role permissions", "tenant.role:grant",
                        new PageOperation("platform/tenants", "/platform/tenants", "成员角色关联：查看（平台代管）", "Grant tenant role permissions", "GET", "/bff/platform/tenants/{tenant}/members/{member}/roles")),
                DedicatedOperations.operation("TenantMember", "rolesReplaceManaged", "成员角色关联：保存授权（平台代管）", "Grant tenant role permissions", "tenant.role:grant",
                        new PageOperation("platform/tenants", "/platform/tenants", "成员角色关联：保存授权（平台代管）", "Grant tenant role permissions", "PUT", "/bff/platform/tenants/{tenant}/members/{member}/roles")),
                DedicatedOperations.operation("TenantMember", "rolesExplainManaged", "成员角色关联：解释有效权限（平台代管）", "Grant tenant role permissions", "tenant.role:grant",
                        new PageOperation("platform/tenants", "/platform/tenants", "成员角色关联：解释有效权限（平台代管）", "Grant tenant role permissions", "GET", "/bff/platform/tenants/{tenant}/members/{member}/effective-permissions")),
                DedicatedOperations.operation("TenantMember", "rolesRead", "成员角色关联：查看", "Grant tenant role permissions", "access.tenant.role:grant",
                        new PageOperation("roles/authorization", "/access/authorization", "成员角色关联：查看", "Grant tenant role permissions", "GET", "/api/access/members/{member}/roles")),
                DedicatedOperations.operation("TenantMember", "rolesReplace", "成员角色关联：保存授权", "Grant tenant role permissions", "access.tenant.role:grant",
                        new PageOperation("roles/authorization", "/access/authorization", "成员角色关联：保存授权", "Grant tenant role permissions", "PUT", "/api/access/members/{member}/roles")),
                DedicatedOperations.operation("TenantMember", "rolesExplainSelf", "成员角色关联：解释本人权限", "Grant tenant role permissions", "access.tenant.role:grant",
                        new PageOperation("roles/authorization", "/access/authorization", "成员角色关联：解释本人权限", "Grant tenant role permissions", "GET", "/api/access/effective-permissions")),
                DedicatedOperations.operation("TenantMember", "rolesExplain", "成员角色关联：解释有效权限", "Grant tenant role permissions", "access.tenant.role:grant",
                        new PageOperation("roles/authorization", "/access/authorization", "成员角色关联：解释有效权限", "Grant tenant role permissions", "GET", "/api/access/members/{member}/effective-permissions")),
                DedicatedOperations.operation("TenantMember", "authorizationRead", "成员授权：查看", "Grant tenant role permissions", "access.tenant.role:grant",
                        new PageOperation("roles/authorization", "/access/authorization", "成员授权：查看", "Grant tenant role permissions", "GET", "/api/access/members/{member}/authorizations")),
                DedicatedOperations.operation("TenantMember", "authorizationReplace", "成员授权：保存授权", "Grant tenant role permissions", "access.tenant.role:grant",
                        new PageOperation("roles/authorization", "/access/authorization", "成员授权：保存授权", "Grant tenant role permissions", "PUT", "/api/access/members/{member}/authorizations")),
                DedicatedOperations.operation("TenantMember", "authorizationReadManaged", "成员授权：查看（平台代管）", "Grant tenant role permissions", "tenant.role:grant",
                        new PageOperation("platform/tenants", "/platform/tenants", "成员授权：查看（平台代管）", "Grant tenant role permissions", "GET", "/bff/platform/tenants/{tenant}/members/{member}/authorizations")),
                DedicatedOperations.operation("TenantMember", "authorizationReplaceManaged", "成员授权：保存授权（平台代管）", "Grant tenant role permissions", "tenant.role:grant",
                        new PageOperation("platform/tenants", "/platform/tenants", "成员授权：保存授权（平台代管）", "Grant tenant role permissions", "PUT", "/bff/platform/tenants/{tenant}/members/{member}/authorizations")),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("TenantMember", "operationDiagnostics", "租户成员操作诊断", "Diagnose member operations", ""),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("TenantMember", "nativeTenants", "原生端本人租户", "My native tenants", ""),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("TenantMember", "selectNativeTenant", "原生端切换本人租户", "Select native tenant", ""),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("TenantMember", "availableTenants", "本人可用租户", "My available tenants", ""),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("TenantMember", "currentTenant", "本人当前租户", "My current tenant", ""),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("TenantMember", "selectTenant", "切换本人租户", "Select tenant", ""),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("TenantMember", "workspaceContext", "当前工作台上下文", "Current workspace context", ""));
        var access=new DataAccess("PLATFORM",List.of(new DataResource("identity.member","","租户成员","Tenant member",List.of(new DataField("displayName","成员姓名","Member name","PERSON_NAME",Set.of("MASKED","HIDDEN"))),"com.appnexus.platform.tenant.PlatformAdministration$Member")),Map.of());
        return new Descriptor("TenantMember","租户成员","Tenant member","tenant",2,Category.SYSTEM,List.of(),operations,access);
    }
    /** 页面别名保留原调用关系；仅声明已执行的直接业务能力，不隐式开放行范围或字段写入。 */
    private static Operation operation(String code,String zh,String en,String permission,String method,String path,List<String> pages){
        String id="TenantMember"+"."+code;
        var interaction=new InteractionContract(id,1,"TenantMember",code,zh,en,InteractionContract.Trigger.USER_ACTION,List.of(new InteractionContract.Capability(InteractionContract.Service.BUSINESS,InteractionContract.Completion.SYNCHRONOUS,id,"",List.of(),Set.of(),id,1)));
        var views=pages.stream().map(key->{var page=Objects.requireNonNull(NavigationPages.find(key));return new PageOperation(key,page.path(),page.titleZh(),page.titleEn(),method,path);}).toList();
        // 同一业务操作的页面别名使用页面键区分，避免与主操作标识重复。
  return new Operation(code,zh,en,permission,id,views.getFirst(),views.stream().skip(1).map(view->new OperationAlias(id+"@"+view.pageKey(),view)).toList(),interaction);
    }
}
