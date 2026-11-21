package com.appnexus.platform.managementapi.administration;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 租户邀请专用入口的唯一对象声明；业务校验与权限执行沿用原用例。 */
@Component
public final class TenantInvitationDeclaration implements Provider {
    /** @return 已挂接到原页面及 API 的对象与操作契约 */
    @Override
    public Descriptor descriptor() {
        var access = new DataAccess("PLATFORM", List.of(
                new DataResource("tenant.invitation", "", "租户邀请", "Tenant invitations", List.of(
                        new DataField("accountId", "受邀账号", "Invited account", "IDENTIFIER", Set.of("HIDDEN"))))), Map.of());
        return new Descriptor("TenantInvitation", "租户邀请", "Tenant Invitation", "tenant", 3, Category.SYSTEM, List.of(), List.of(
                operation("TenantInvitation", "list", "租户邀请：查看列表", "Read invitations", "tenant.invitation:read",
                        new PageOperation("platform/tenants", "/platform/tenants", "租户邀请：查看列表", "Read invitations", "GET", "/bff/platform/tenants/{tenant}/invitations")),
                operation("TenantInvitation", "invite", "租户邀请：发送邀请", "Invite tenant members", "tenant.invitation:create",
                        new PageOperation("platform/tenants", "/platform/tenants", "租户邀请：发送邀请", "Invite tenant members", "POST", "/bff/platform/tenants/{tenant}/invitations")),
                operation("TenantInvitation", "revoke", "租户邀请：撤销", "Revoke invitations", "tenant.invitation:revoke",
                        new PageOperation("platform/tenants", "/platform/tenants", "租户邀请：撤销", "Revoke invitations", "DELETE", "/bff/platform/tenants/{tenant}/invitations/{id}")),
                operation("TenantInvitation", "accept", "租户邀请：接受邀请", "Accept tenant invitation", "",
                        new PageOperation("account/profile", "/account/profile", "租户邀请：接受邀请", "Accept tenant invitation", "POST", "/bff/account/tenant-invitations/acceptance"))), access);
    }
}
