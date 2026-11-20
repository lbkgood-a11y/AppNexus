package com.appnexus.platform.security;
import com.appnexus.contracts.businessobject.BusinessObjects.*;

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
            operation("authorizationOptions","查看租户授权账号目录","Read tenant authorization accounts","access.tenant.role:grant","GET","/api/access/member-options",List.of("roles/authorization")));
        var access=new DataAccess("PLATFORM",List.of(new DataResource("identity.member","","租户成员","Tenant member",List.of(new DataField("displayName","成员姓名","Member name","PERSON_NAME",Set.of("MASKED","HIDDEN"))),"com.appnexus.platform.tenant.PlatformAdministration$Member")),Map.of());
        return new Descriptor("TenantMember","租户成员","Tenant member","tenant",1,Category.SYSTEM,List.of(),operations,access);
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
