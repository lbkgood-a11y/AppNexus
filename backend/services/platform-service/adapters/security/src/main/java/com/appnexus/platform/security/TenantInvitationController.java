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
    @EndpointOperation("tenant.invitation:read") @GetMapping("/bff/platform/tenants/{tenant}/invitations")
    ResponseEntity<List<TenantInvitations.View>> list(Authentication auth,@PathVariable UUID tenant){return ok(invitations.list(UUID.fromString(auth.getName()),tenant));}
    @EndpointOperation("tenant.invitation:create") @PostMapping("/bff/platform/tenants/{tenant}/invitations")
    ResponseEntity<TenantInvitations.Created> invite(Authentication auth,@PathVariable UUID tenant,@RequestBody Invite input){return ok(invitations.invite(UUID.fromString(auth.getName()),tenant,input.accountId()));}
    @EndpointOperation("tenant.invitation:revoke") @DeleteMapping("/bff/platform/tenants/{tenant}/invitations/{id}")
    ResponseEntity<Void> revoke(Authentication auth,@PathVariable UUID tenant,@PathVariable UUID id){invitations.revoke(UUID.fromString(auth.getName()),tenant,id);return ResponseEntity.noContent().build();}
    /** 本人激活只创建成员身份，租户选择/业务授权仍分别执行。 */
    @PostMapping("/bff/account/tenant-invitations/acceptance")
    ResponseEntity<TenantInvitations.Accepted> accept(Authentication auth,@RequestBody Acceptance input){return ok(invitations.accept(UUID.fromString(auth.getName()),input.invitationId(),input.token()));}
    private <T> ResponseEntity<T> ok(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
}
