package com.appnexus.platform.security;
import com.appnexus.contracts.businessobject.BusinessObjects.*;

import com.appnexus.contracts.businessobject.*;
import com.appnexus.platform.configuration.NavigationPages;
import java.util.*;
import org.springframework.stereotype.Component;

/** 账号唯一声明；保留真实权限和接口，字段目录及响应投影共用本对象的数据权限定义。 */
@Component
public final class AccountDeclaration implements BusinessObjects.Provider {
    @Override public Descriptor descriptor(){
        var operations=List.of(
            operation("list","账号：查看列表","Read accounts","identity.account:read","GET","/bff/platform/users",List.of("platform/users","platform/tenants","platform/authorization")),
            operation("create","账号：新增","Create accounts","identity.account:create","POST","/bff/platform/users",List.of("platform/users")),
            operation("update","账号：修改","Update accounts","identity.account:update","PUT","/bff/platform/users/{id}",List.of("platform/users")),
            operation("setEnabled","账号：启停","Enable accounts","identity.account:enable","PUT","/bff/platform/users/{id}/state",List.of("platform/users")),
            operation("revokeSessions","账号：强制下线","Revoke account sessions","identity.account:revoke_sessions","POST","/bff/platform/users/{id}/revoke-sessions",List.of("platform/users")),
            operation("issueRecovery","账号：签发找回凭证","Issue password reset ticket","identity.account:reset_password","POST","/bff/platform/users/{id}/password-recovery",List.of("platform/users")));
        var access=new DataAccess("PLATFORM",List.of(new DataResource("identity.account","","账号","Account",List.of(new DataField("email","邮箱","Email","EMAIL",Set.of("MASKED","HIDDEN")),new DataField("phoneNumber","手机号","Phone number","PHONE",Set.of("MASKED","HIDDEN"))),"com.appnexus.platform.tenant.PlatformAdministration$User")),Map.of());
        return new Descriptor("Account","账号","Account","identity",1,Category.SYSTEM,List.of(),operations,access);
    }
    /** 页面别名保留原调用关系；仅声明已执行的直接业务能力，不隐式开放行范围或字段写入。 */
    private static Operation operation(String code,String zh,String en,String permission,String method,String path,List<String> pages){
        String id="Account"+"."+code;
        var interaction=new InteractionContract(id,1,"Account",code,zh,en,InteractionContract.Trigger.USER_ACTION,List.of(new InteractionContract.Capability(InteractionContract.Service.BUSINESS,InteractionContract.Completion.SYNCHRONOUS,id,"",List.of(),Set.of(),id,1)));
        var views=pages.stream().map(key->{var page=Objects.requireNonNull(NavigationPages.find(key));return new PageOperation(key,page.path(),page.titleZh(),page.titleEn(),method,path);}).toList();
        return new Operation(code,zh,en,permission,id,views.getFirst(),views.stream().skip(1).map(view->new OperationAlias(id+"@"+view.pageKey(),view)).toList(),interaction);
    }
}
