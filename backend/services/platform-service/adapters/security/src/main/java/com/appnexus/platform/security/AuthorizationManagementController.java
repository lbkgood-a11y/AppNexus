package com.appnexus.platform.security;

import com.appnexus.platform.authorization.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 授权中心写入 BFF；请求只提交完整 ID 集合，操作者和租户均来自服务端可信上下文。 */
@RestController
public class AuthorizationManagementController {
 private final RolePermissionManagement rolePermissions; private final SubjectRoleManagement subjectRoles; private final PermissionCatalog permissions; private final DataPolicyMetadataCatalog dataPolicyMetadata; private final EffectiveAuthorizationQuery effectiveAuthorizations;
 public AuthorizationManagementController(RolePermissionManagement rolePermissions,SubjectRoleManagement subjectRoles,PermissionCatalog permissions,DataPolicyMetadataCatalog dataPolicyMetadata,EffectiveAuthorizationQuery effectiveAuthorizations){this.rolePermissions=rolePermissions;this.subjectRoles=subjectRoles;this.permissions=permissions;this.dataPolicyMetadata=dataPolicyMetadata;this.effectiveAuthorizations=effectiveAuthorizations;}
 /** @param ids 完整 ID 集合，空集合表示撤销全部 @param version 查询所得乐观锁版本 @param reason 变更原因，1–500 字符 */
 public record Replace(Set<UUID> ids,long version,String reason){}
 /** 功能权限与数据策略必须作为一个完整快照提交。 */
 public record AuthorizationReplace(Set<UUID> ids,Set<RolePermissionManagement.DataPolicy> dataPolicies,long version,String reason){}

 @GetMapping("/bff/platform/permissions") ResponseEntity<List<PermissionCatalog.Permission>> platformPermissionCatalog(Authentication a,@RequestParam(defaultValue="PLATFORM") PermissionCatalog.ScopeType scopeType){return ok(()->{subjectRoles.accountRoles(actor(a),actor(a));return permissions.list(scopeType,false);});}
 @GetMapping("/bff/platform/data-policy-metadata") ResponseEntity<List<DataPolicyMetadataCatalog.ScopeSupport>> platformDataPolicyMetadata(Authentication a,@RequestParam(defaultValue="PLATFORM") PermissionCatalog.ScopeType scopeType){return ok(()->{subjectRoles.accountRoles(actor(a),actor(a));return dataPolicyMetadata.list(scopeType);});}
 @GetMapping("/bff/platform/users/{account}/effective-permissions") ResponseEntity<List<EffectiveAuthorizationQuery.Explanation>> platformEffectivePermissions(Authentication a,@PathVariable UUID account){return ok(()->{subjectRoles.accountRoles(actor(a),account);return effectiveAuthorizations.platformAccount(account);});}

 @GetMapping("/bff/platform/roles/{role}/permissions") ResponseEntity<RolePermissionManagement.AuthorizationAssignment> platformPermissions(Authentication a,@PathVariable UUID role){return ok(()->rolePermissions.getAuthorization(platform(a),role));}
 @PutMapping("/bff/platform/roles/{role}/permissions") ResponseEntity<RolePermissionManagement.AuthorizationAssignment> replacePlatformPermissions(Authentication a,@PathVariable UUID role,@RequestBody AuthorizationReplace input){return ok(()->rolePermissions.replaceAuthorization(platform(a),role,input.version(),input.ids(),input.dataPolicies(),input.reason()));}
 @GetMapping("/bff/platform/users/{account}/roles") ResponseEntity<SubjectRoleManagement.Assignment> accountRoles(Authentication a,@PathVariable UUID account){return ok(()->subjectRoles.accountRoles(actor(a),account));}
 @PutMapping("/bff/platform/users/{account}/roles") ResponseEntity<SubjectRoleManagement.Assignment> replaceAccountRoles(Authentication a,@PathVariable UUID account,@RequestBody Replace input){return ok(()->subjectRoles.replaceAccountRoles(actor(a),account,input.version(),input.ids(),input.reason()));}

 @GetMapping("/bff/platform/tenants/{tenant}/roles/{role}/permissions") ResponseEntity<RolePermissionManagement.AuthorizationAssignment> managedPermissions(Authentication a,@PathVariable UUID tenant,@PathVariable UUID role){return ok(()->rolePermissions.getAuthorization(managed(a,tenant),role));}
 @PutMapping("/bff/platform/tenants/{tenant}/roles/{role}/permissions") ResponseEntity<RolePermissionManagement.AuthorizationAssignment> replaceManagedPermissions(Authentication a,@PathVariable UUID tenant,@PathVariable UUID role,@RequestBody AuthorizationReplace input){return ok(()->rolePermissions.replaceAuthorization(managed(a,tenant),role,input.version(),input.ids(),input.dataPolicies(),input.reason()));}
 @GetMapping("/bff/platform/tenants/{tenant}/members/{member}/roles") ResponseEntity<SubjectRoleManagement.Assignment> managedMemberRoles(Authentication a,@PathVariable UUID tenant,@PathVariable UUID member){return ok(()->subjectRoles.memberRoles(managed(a,tenant),member));}
 @PutMapping("/bff/platform/tenants/{tenant}/members/{member}/roles") ResponseEntity<SubjectRoleManagement.Assignment> replaceManagedMemberRoles(Authentication a,@PathVariable UUID tenant,@PathVariable UUID member,@RequestBody Replace input){return ok(()->subjectRoles.replaceMemberRoles(managed(a,tenant),member,input.version(),input.ids(),input.reason()));}
 @GetMapping("/bff/platform/tenants/{tenant}/members/{member}/effective-permissions") ResponseEntity<List<EffectiveAuthorizationQuery.Explanation>> managedEffectivePermissions(Authentication a,@PathVariable UUID tenant,@PathVariable UUID member){return ok(()->{subjectRoles.memberRoles(managed(a,tenant),member);return effectiveAuthorizations.tenantMember(tenant,member);});}

 @GetMapping("/api/access/roles/{role}/permissions") ResponseEntity<RolePermissionManagement.AuthorizationAssignment> tenantPermissions(Authentication a,HttpServletRequest r,@PathVariable UUID role){return ok(()->rolePermissions.getAuthorization(tenant(a,r),role));}
 @PutMapping("/api/access/roles/{role}/permissions") ResponseEntity<RolePermissionManagement.AuthorizationAssignment> replaceTenantPermissions(Authentication a,HttpServletRequest r,@PathVariable UUID role,@RequestBody AuthorizationReplace input){return ok(()->rolePermissions.replaceAuthorization(tenant(a,r),role,input.version(),input.ids(),input.dataPolicies(),input.reason()));}
 @GetMapping("/api/access/members/{member}/roles") ResponseEntity<SubjectRoleManagement.Assignment> memberRoles(Authentication a,HttpServletRequest r,@PathVariable UUID member){return ok(()->subjectRoles.memberRoles(tenant(a,r),member));}
 @PutMapping("/api/access/members/{member}/roles") ResponseEntity<SubjectRoleManagement.Assignment> replaceMemberRoles(Authentication a,HttpServletRequest r,@PathVariable UUID member,@RequestBody Replace input){return ok(()->subjectRoles.replaceMemberRoles(tenant(a,r),member,input.version(),input.ids(),input.reason()));}
 @GetMapping("/api/access/permissions") ResponseEntity<List<PermissionCatalog.Permission>> tenantPermissionCatalog(Authentication a,HttpServletRequest r){return ok(()->{var context=tenant(a,r);subjectRoles.memberRoles(context,context.actorMemberId());return permissions.list(PermissionCatalog.ScopeType.TENANT,false);});}
 @GetMapping("/api/access/data-policy-metadata") ResponseEntity<List<DataPolicyMetadataCatalog.ScopeSupport>> tenantDataPolicyMetadata(Authentication a,HttpServletRequest r){return ok(()->{var context=tenant(a,r);subjectRoles.memberRoles(context,context.actorMemberId());return dataPolicyMetadata.list(PermissionCatalog.ScopeType.TENANT);});}
 @GetMapping("/api/access/effective-permissions") ResponseEntity<List<EffectiveAuthorizationQuery.Explanation>> tenantEffectivePermissions(Authentication a,HttpServletRequest r){return ok(()->{var context=tenant(a,r);subjectRoles.memberRoles(context,context.actorMemberId());return effectiveAuthorizations.tenantMember(context.tenantId(),context.actorMemberId());});}

 private RoleManagement.Context platform(Authentication a){return new RoleManagement.Context(actor(a),null,null);} private RoleManagement.Context managed(Authentication a,UUID tenant){return new RoleManagement.Context(actor(a),Objects.requireNonNull(tenant),null);}
 private RoleManagement.Context tenant(Authentication a,HttpServletRequest request){if(!(request.getAttribute(TenantExecutionFilter.ATTRIBUTE) instanceof VerifiedTenantContext c))throw AccountSettingsController.problem(HttpStatus.CONFLICT,"TENANT_REQUIRED","请选择有效租户");return new RoleManagement.Context(actor(a),c.tenantId(),c.memberId());}
 private UUID actor(Authentication a){return UUID.fromString(a.getName());} private <T> ResponseEntity<T> ok(java.util.function.Supplier<T> action){try{return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(action.get());}catch(SecurityException denied){throw AccountSettingsController.problem(HttpStatus.FORBIDDEN,"AUTHORIZATION_MANAGEMENT_DENIED","无授权管理权限");}catch(RoleManagementException business){HttpStatus status=business.code().endsWith("NOT_FOUND")?HttpStatus.NOT_FOUND:HttpStatus.CONFLICT;throw AccountSettingsController.problem(status,business.code(),business.getMessage());}catch(IllegalArgumentException|NullPointerException invalid){throw AccountSettingsController.problem(HttpStatus.BAD_REQUEST,"INVALID_AUTHORIZATION_INPUT","授权参数不符合要求");}}
}
