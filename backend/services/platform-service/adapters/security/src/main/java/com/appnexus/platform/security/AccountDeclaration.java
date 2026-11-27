package com.appnexus.platform.security;
import com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.appnexus.contracts.businessobject.DedicatedOperations;

import com.appnexus.contracts.businessobject.*;
import com.appnexus.platform.configuration.NavigationPages;
import java.util.*;
import org.springframework.stereotype.Component;

/** 账号唯一声明；保留真实权限和接口，字段目录及响应投影共用本对象的数据权限定义。 */
@Component
public final class AccountDeclaration implements BusinessObjects.Provider {
    private java.util.function.Supplier<BusinessObjectCommandRuntime> commands=()->null;

    /** @param runtime 原平台账号用例的命令接入，启动时校验实际操作范围 */
    @org.springframework.beans.factory.annotation.Autowired
    public void commandRuntime(@org.springframework.beans.factory.annotation.Qualifier("accountPlatformCommands")
            org.springframework.beans.factory.ObjectProvider<BusinessObjectCommandRuntime> runtime){
        commands=runtime::getIfAvailable;
    }
    @Override public Set<String> commandOperations(){return Set.of("list","create","update","setEnabled","revokeSessions","issueRecovery");}
    @Override public BusinessObjectCommandRuntime commandRuntime(){return commands.get();}

    @Override public Descriptor descriptor(){
        var operations=List.of(
            operation("list","账号：查看列表","Read accounts","identity.account:read","GET","/bff/platform/users",List.of("platform/users","platform/tenants","platform/authorization")),
            operation("create","账号：新增","Create accounts","identity.account:create","POST","/bff/platform/users",List.of("platform/users")),
            operation("update","账号：修改","Update accounts","identity.account:update","PUT","/bff/platform/users/{id}",List.of("platform/users")),
            operation("setEnabled","账号：启停","Enable accounts","identity.account:enable","PUT","/bff/platform/users/{id}/state",List.of("platform/users")),
            operation("revokeSessions","账号：强制下线","Revoke account sessions","identity.account:revoke_sessions","POST","/bff/platform/users/{id}/revoke-sessions",List.of("platform/users")),
            operation("issueRecovery","账号：签发找回凭证","Issue password reset ticket","identity.account:reset_password","POST","/bff/platform/users/{id}/password-recovery",List.of("platform/users")),
                DedicatedOperations.operation("Account", "rolesExplain", "账号角色关联：解释有效权限", "Grant platform role permissions", "access.role:grant",
                        new PageOperation("platform/authorization", "/platform/authorization", "账号角色关联：解释有效权限", "Grant platform role permissions", "GET", "/bff/platform/users/{account}/effective-permissions")),
                DedicatedOperations.operation("Account", "rolesRead", "账号角色关联：查看", "Grant platform role permissions", "access.role:grant",
                        new PageOperation("platform/authorization", "/platform/authorization", "账号角色关联：查看", "Grant platform role permissions", "GET", "/bff/platform/users/{account}/roles")),
                DedicatedOperations.operation("Account", "rolesReplace", "账号角色关联：保存授权", "Grant platform role permissions", "access.role:grant",
                        new PageOperation("platform/authorization", "/platform/authorization", "账号角色关联：保存授权", "Grant platform role permissions", "PUT", "/bff/platform/users/{account}/roles")),
                DedicatedOperations.operation("Account", "sessionList", "登录会话：查看列表", "View sessions", "",
                        new PageOperation("account/profile", "/account/profile", "登录会话：查看列表", "View sessions", "GET", "/bff/account/sessions")),
                DedicatedOperations.operation("Account", "sessionRevoke", "登录会话：撤销", "Revoke session", "",
                        new PageOperation("account/profile", "/account/profile", "登录会话：撤销", "Revoke session", "DELETE", "/bff/account/sessions/{handle}")),
                DedicatedOperations.operation("Account", "sessionRevokeOthers", "登录会话：撤销其他会话", "Revoke other sessions", "",
                        new PageOperation("account/profile", "/account/profile", "登录会话：撤销其他会话", "Revoke other sessions", "POST", "/bff/account/sessions/revoke-others")),
                DedicatedOperations.operation("Account", "profileRead", "个人资料：查看", "View profile", "",
                        new PageOperation("account/profile", "/account/profile", "个人资料：查看", "View profile", "GET", "/bff/account")),
                DedicatedOperations.operation("Account", "profileUpdate", "个人资料：修改", "Update profile", "",
                        new PageOperation("account/profile", "/account/profile", "个人资料：修改", "Update profile", "PATCH", "/bff/account")),
                DedicatedOperations.operation("Account", "profileChangePassword", "个人资料：修改本人密码", "Change password", "",
                        new PageOperation("account/profile", "/account/profile", "个人资料：修改本人密码", "Change password", "POST", "/bff/account/password")),
                DedicatedOperations.operation("Account", "mfaRead", "多因素认证：查看", "View MFA", "",
                        new PageOperation("account/profile", "/account/profile", "多因素认证：查看", "View MFA", "GET", "/bff/account/mfa")),
                DedicatedOperations.operation("Account", "mfaEnroll", "多因素认证：建立绑定", "Enroll MFA", "",
                        new PageOperation("account/profile", "/account/profile", "多因素认证：建立绑定", "Enroll MFA", "POST", "/bff/account/mfa/enrollment")),
                DedicatedOperations.operation("Account", "mfaActivate", "多因素认证：激活绑定", "Activate MFA", "",
                        new PageOperation("account/profile", "/account/profile", "多因素认证：激活绑定", "Activate MFA", "POST", "/bff/account/mfa/activation")),
                DedicatedOperations.operation("Account", "mfaStepUp", "多因素认证：二次验证", "Step up", "",
                        new PageOperation("account/profile", "/account/profile", "多因素认证：二次验证", "Step up", "POST", "/bff/account/mfa/step-up")),
                DedicatedOperations.operation("Account", "mfaDisable", "多因素认证：停用", "Disable MFA", "",
                        new PageOperation("account/profile", "/account/profile", "多因素认证：停用", "Disable MFA", "POST", "/bff/account/mfa/disable")),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("Account", "securityLogs", "本人安全日志", "My security logs", ""),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("Account", "operationDiagnostics", "平台账号操作诊断", "Diagnose account operations", ""),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("Account", "sessionStatus", "当前账号会话", "Current account session", ""),
                DedicatedOperations.operation("Account", "requestRecovery", "申请密码恢复", "Request password recovery", ""),
                DedicatedOperations.operation("Account", "completeRecovery", "完成密码恢复", "Complete password recovery", ""));
        var access=new DataAccess("PLATFORM",List.of(new DataResource("identity.account","","账号","Account",List.of(new DataField("email","邮箱","Email","EMAIL",Set.of("MASKED","HIDDEN")),new DataField("phoneNumber","手机号","Phone number","PHONE",Set.of("MASKED","HIDDEN"))),"com.appnexus.platform.tenant.PlatformAdministration$User")),Map.of());
        return new Descriptor("Account","账号","Account","identity",2,Category.SYSTEM,List.of(),operations,access);
    }
    /** 页面别名保留原调用关系；仅声明已执行的直接业务能力，不隐式开放行范围或字段写入。 */
    private static Operation operation(String code,String zh,String en,String permission,String method,String path,List<String> pages){
        String id="Account"+"."+code;
        var interaction=new InteractionContract(id,1,"Account",code,zh,en,InteractionContract.Trigger.USER_ACTION,List.of());
        var views=pages.stream().map(key->{var page=Objects.requireNonNull(NavigationPages.find(key));return new PageOperation(key,page.path(),page.titleZh(),page.titleEn(),method,path);}).toList();
        return new Operation(code,zh,en,permission,id,views.getFirst(),views.stream().skip(1).map(view->new OperationAlias(id+"@"+view.pageKey(),view)).toList(),interaction);
    }
}
