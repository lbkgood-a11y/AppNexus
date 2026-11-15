package com.appnexus.platform.security;

import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 邀请管理与本人接受入口；平台写入沿用 MFA/CSRF，本人接受不要求已有租户上下文。 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.TENANT_CONTEXT)
public class TenantInvitationController {
    private final TenantInvitations invitations;
    public TenantInvitationController(TenantInvitations invitations){this.invitations=invitations;}
    /** 已存在账号 UUID，仅表示邀请目标，不能在接受时冒充该账号。 */
    public record Invite(UUID accountId){}
    /** 凭证不进入 URL 或日志，仅在本人 CSRF 请求体中提交。 */
    public record Acceptance(UUID invitationId,String token){@Override public String toString(){return "Acceptance[redacted]";}}
    /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
    @com.appnexus.contracts.operation.BusinessObjectType(code="TenantInvitation",nameZh="租户邀请",nameEn="Tenant Invitation",category=com.appnexus.contracts.operation.BusinessObjectType.Category.SYSTEM,module="tenant")
 @com.appnexus.contracts.operation.OperationBinding(id="TenantInvitation.list",objectType="TenantInvitation",operationCode="list",pages={"platform/tenants"},nameZh="租户邀请：查看列表",nameEn="Read invitations",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation("tenant.invitation:read") @GetMapping("/bff/platform/tenants/{tenant}/invitations")
    ResponseEntity<List<TenantInvitations.View>> list(Authentication auth,@PathVariable UUID tenant){return ok(invitations.list(UUID.fromString(auth.getName()),tenant));}
    /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
    @com.appnexus.contracts.operation.OperationBinding(id="TenantInvitation.invite",objectType="TenantInvitation",operationCode="invite",pages={"platform/tenants"},nameZh="租户邀请：发送邀请",nameEn="Invite tenant members",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation("tenant.invitation:create") @PostMapping("/bff/platform/tenants/{tenant}/invitations")
    ResponseEntity<TenantInvitations.Created> invite(Authentication auth,@PathVariable UUID tenant,@RequestBody Invite input){return ok(invitations.invite(UUID.fromString(auth.getName()),tenant,input.accountId()));}
    /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
    @com.appnexus.contracts.operation.OperationBinding(id="TenantInvitation.revoke",objectType="TenantInvitation",operationCode="revoke",pages={"platform/tenants"},nameZh="租户邀请：撤销",nameEn="Revoke invitations",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation("tenant.invitation:revoke") @DeleteMapping("/bff/platform/tenants/{tenant}/invitations/{id}")
    ResponseEntity<Void> revoke(Authentication auth,@PathVariable UUID tenant,@PathVariable UUID id){invitations.revoke(UUID.fromString(auth.getName()),tenant,id);return ResponseEntity.noContent().build();}
    /** 本人激活只创建成员身份，租户选择/业务授权仍分别执行。 */
    @com.appnexus.contracts.operation.OperationBinding(id="TenantInvitation.accept",objectType="TenantInvitation",operationCode="accept",pages={"account/profile"},nameZh="租户邀请：接受邀请",nameEn="Accept tenant invitation",eligibility=com.appnexus.contracts.operation.OperationBinding.Eligibility.SELF,policy="当前账号必须是邀请对象，单次邀请凭证及有效期由原用例验证")
    @PostMapping("/bff/account/tenant-invitations/acceptance")
    ResponseEntity<TenantInvitations.Accepted> accept(Authentication auth,@RequestBody Acceptance input){return ok(invitations.accept(UUID.fromString(auth.getName()),input.invitationId(),input.token()));}
    private <T> ResponseEntity<T> ok(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
}
