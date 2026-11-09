package com.appnexus.platform.security;

import com.appnexus.platform.authorization.*;
import com.appnexus.platform.configuration.NavigationQuery;
import com.appnexus.platform.configuration.MenuManagement;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 授权中心写入 BFF；请求只提交完整 ID 集合，操作者和租户均来自服务端可信上下文。 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.AUTHORIZATION_MANAGEMENT)
public class AuthorizationManagementController {
 private final NavigationQuery navigation;
 private final FieldCatalog fieldCatalog; private final MemberAuthorizationManagement memberAuthorizations; private final RolePermissionManagement rolePermissions; private final SubjectRoleManagement subjectRoles; private final PermissionCatalog permissions; private final DataPolicyMetadataCatalog dataPolicyMetadata; private final EffectiveAuthorizationQuery effectiveAuthorizations; private final LegacyAuthorizationReconciliation reconciliation;
 public AuthorizationManagementController(NavigationQuery navigation,FieldCatalog fieldCatalog,MemberAuthorizationManagement memberAuthorizations,RolePermissionManagement rolePermissions,SubjectRoleManagement subjectRoles,PermissionCatalog permissions,DataPolicyMetadataCatalog dataPolicyMetadata,EffectiveAuthorizationQuery effectiveAuthorizations,LegacyAuthorizationReconciliation reconciliation){this.navigation=navigation;this.fieldCatalog=fieldCatalog;this.memberAuthorizations=memberAuthorizations;this.rolePermissions=rolePermissions;this.subjectRoles=subjectRoles;this.permissions=permissions;this.dataPolicyMetadata=dataPolicyMetadata;this.effectiveAuthorizations=effectiveAuthorizations;this.reconciliation=reconciliation;}
 /** @param ids 完整 ID 集合，空集合表示撤销全部 @param version 查询所得乐观锁版本 @param reason 变更原因，1–500 字符 */
 public record Replace(Set<UUID> ids,long version,String reason){}
 /** 功能权限与数据策略必须作为一个完整快照提交。 */
 public record AuthorizationReplace(Set<UUID> ids,Set<RolePermissionManagement.DataPolicy> dataPolicies,long version,String reason,Set<FieldSecurity.Policy> fieldPolicies){}

 /** 授权目录包含本域完整菜单，必须验证管理能力，不能使用本人导航替代。 */
 @EndpointOperation("access.role:grant") @GetMapping("/bff/platform/authorization-navigation") ResponseEntity<List<NavigationQuery.AuthorizationNode>> platformNavigation(Authentication a){return ok(()->{subjectRoles.accountRoles(actor(a),actor(a));return navigation.authorizationCatalog(MenuManagement.Scope.PLATFORM);});}
 /** 租户从可信上下文确定，普通成员不能枚举授权目录。 */
 @EndpointOperation("access.tenant.role:grant") @GetMapping("/api/access/authorization-navigation") ResponseEntity<List<NavigationQuery.AuthorizationNode>> tenantNavigation(Authentication a,HttpServletRequest r){return ok(()->{var c=tenant(a,r);subjectRoles.memberRoles(c,c.actorMemberId());return navigation.authorizationCatalog(MenuManagement.Scope.TENANT);});}
 @EndpointOperation("access.role:grant") @GetMapping("/bff/platform/permissions") ResponseEntity<List<PermissionCatalog.Permission>> platformPermissionCatalog(Authentication a,@RequestParam(defaultValue="PLATFORM") PermissionCatalog.ScopeType scopeType){return ok(()->{subjectRoles.accountRoles(actor(a),actor(a));return permissions.list(scopeType,false);});}
 /** 只读旧授权对账；差异归零前不得关闭运行时双读或删除历史表。 */
 @EndpointOperation("access.role:grant") @GetMapping("/bff/platform/authorization-reconciliation") ResponseEntity<LegacyAuthorizationReconciliation.Report> reconciliation(Authentication a,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="50") int size){return ok(()->reconciliation.report(actor(a),page,size));}
 /** 字段目录只返回已有真实执行点的能力；租户域尚未登记时返回空集合。 */
 @EndpointOperation("access.role:grant") @GetMapping("/bff/platform/field-policy-metadata") ResponseEntity<List<FieldSecurity.Descriptor>> platformFields(Authentication a,@RequestParam(defaultValue="PLATFORM") PermissionCatalog.ScopeType scopeType){return ok(()->{subjectRoles.accountRoles(actor(a),actor(a));return fieldCatalog.list(scopeType);});}
 @EndpointOperation("access.tenant.role:grant") @GetMapping("/api/access/field-policy-metadata") ResponseEntity<List<FieldSecurity.Descriptor>> tenantFields(Authentication a,HttpServletRequest r){return ok(()->{var c=tenant(a,r);subjectRoles.memberRoles(c,c.actorMemberId());return fieldCatalog.list(PermissionCatalog.ScopeType.TENANT);});}
 @EndpointOperation("access.role:grant") @GetMapping("/bff/platform/data-policy-metadata") ResponseEntity<List<DataPolicyMetadataCatalog.ScopeSupport>> platformDataPolicyMetadata(Authentication a,@RequestParam(defaultValue="PLATFORM") PermissionCatalog.ScopeType scopeType){return ok(()->{subjectRoles.accountRoles(actor(a),actor(a));return dataPolicyMetadata.list(scopeType);});}
 @EndpointOperation("access.role:grant") @GetMapping("/bff/platform/users/{account}/effective-permissions") ResponseEntity<List<EffectiveAuthorizationQuery.Explanation>> platformEffectivePermissions(Authentication a,@PathVariable UUID account){return ok(()->{subjectRoles.accountRoles(actor(a),account);return effectiveAuthorizations.platformAccount(account);});}

 @EndpointOperation("access.role:grant") @GetMapping("/bff/platform/roles/{role}/permissions") ResponseEntity<RolePermissionManagement.AuthorizationAssignment> platformPermissions(Authentication a,@PathVariable UUID role){return ok(()->rolePermissions.getAuthorization(platform(a),role));}
 @EndpointOperation("access.role:grant") @PutMapping("/bff/platform/roles/{role}/permissions") ResponseEntity<RolePermissionManagement.AuthorizationAssignment> replacePlatformPermissions(Authentication a,@PathVariable UUID role,@RequestBody AuthorizationReplace input){return ok(()->rolePermissions.replaceAuthorization(platform(a),role,input.version(),input.ids(),input.dataPolicies(),input.fieldPolicies(),input.reason()));}
 @EndpointOperation("access.role:grant") @GetMapping("/bff/platform/users/{account}/roles") ResponseEntity<SubjectRoleManagement.Assignment> accountRoles(Authentication a,@PathVariable UUID account){return ok(()->subjectRoles.accountRoles(actor(a),account));}
 @EndpointOperation("access.role:grant") @PutMapping("/bff/platform/users/{account}/roles") ResponseEntity<SubjectRoleManagement.Assignment> replaceAccountRoles(Authentication a,@PathVariable UUID account,@RequestBody Replace input){return ok(()->subjectRoles.replaceAccountRoles(actor(a),account,input.version(),input.ids(),input.reason()));}

 @EndpointOperation("access.tenant.role:grant") @GetMapping("/bff/platform/tenants/{tenant}/roles/{role}/permissions") ResponseEntity<RolePermissionManagement.AuthorizationAssignment> managedPermissions(Authentication a,@PathVariable UUID tenant,@PathVariable UUID role){return ok(()->rolePermissions.getAuthorization(managed(a,tenant),role));}
 @EndpointOperation("access.tenant.role:grant") @PutMapping("/bff/platform/tenants/{tenant}/roles/{role}/permissions") ResponseEntity<RolePermissionManagement.AuthorizationAssignment> replaceManagedPermissions(Authentication a,@PathVariable UUID tenant,@PathVariable UUID role,@RequestBody AuthorizationReplace input){return ok(()->rolePermissions.replaceAuthorization(managed(a,tenant),role,input.version(),input.ids(),input.dataPolicies(),input.fieldPolicies(),input.reason()));}
 @EndpointOperation("access.tenant.role:grant") @GetMapping("/bff/platform/tenants/{tenant}/members/{member}/roles") ResponseEntity<SubjectRoleManagement.Assignment> managedMemberRoles(Authentication a,@PathVariable UUID tenant,@PathVariable UUID member){return ok(()->subjectRoles.memberRoles(managed(a,tenant),member));}
 @EndpointOperation("access.tenant.role:grant") @PutMapping("/bff/platform/tenants/{tenant}/members/{member}/roles") ResponseEntity<SubjectRoleManagement.Assignment> replaceManagedMemberRoles(Authentication a,@PathVariable UUID tenant,@PathVariable UUID member,@RequestBody Replace input){return ok(()->subjectRoles.replaceMemberRoles(managed(a,tenant),member,input.version(),input.ids(),input.reason()));}
 @EndpointOperation("access.tenant.role:grant") @GetMapping("/bff/platform/tenants/{tenant}/members/{member}/effective-permissions") ResponseEntity<List<EffectiveAuthorizationQuery.Explanation>> managedEffectivePermissions(Authentication a,@PathVariable UUID tenant,@PathVariable UUID member){return ok(()->{subjectRoles.memberRoles(managed(a,tenant),member);return effectiveAuthorizations.tenantMember(tenant,member);});}

 @EndpointOperation("access.tenant.role:grant") @GetMapping("/api/access/roles/{role}/permissions") ResponseEntity<RolePermissionManagement.AuthorizationAssignment> tenantPermissions(Authentication a,HttpServletRequest r,@PathVariable UUID role){return ok(()->rolePermissions.getAuthorization(tenant(a,r),role));}
 @EndpointOperation("access.tenant.role:grant") @PutMapping("/api/access/roles/{role}/permissions") ResponseEntity<RolePermissionManagement.AuthorizationAssignment> replaceTenantPermissions(Authentication a,HttpServletRequest r,@PathVariable UUID role,@RequestBody AuthorizationReplace input){return ok(()->rolePermissions.replaceAuthorization(tenant(a,r),role,input.version(),input.ids(),input.dataPolicies(),input.fieldPolicies(),input.reason()));}
 @EndpointOperation("access.tenant.role:grant") @GetMapping("/api/access/members/{member}/roles") ResponseEntity<SubjectRoleManagement.Assignment> memberRoles(Authentication a,HttpServletRequest r,@PathVariable UUID member){return ok(()->subjectRoles.memberRoles(tenant(a,r),member));}
 @EndpointOperation("access.tenant.role:grant") @PutMapping("/api/access/members/{member}/roles") ResponseEntity<SubjectRoleManagement.Assignment> replaceMemberRoles(Authentication a,HttpServletRequest r,@PathVariable UUID member,@RequestBody Replace input){return ok(()->subjectRoles.replaceMemberRoles(tenant(a,r),member,input.version(),input.ids(),input.reason()));}
 @EndpointOperation("access.tenant.role:grant") @GetMapping("/api/access/permissions") ResponseEntity<List<PermissionCatalog.Permission>> tenantPermissionCatalog(Authentication a,HttpServletRequest r){return ok(()->{var context=tenant(a,r);subjectRoles.memberRoles(context,context.actorMemberId());return permissions.list(PermissionCatalog.ScopeType.TENANT,false);});}
 @EndpointOperation("access.tenant.role:grant") @GetMapping("/api/access/data-policy-metadata") ResponseEntity<List<DataPolicyMetadataCatalog.ScopeSupport>> tenantDataPolicyMetadata(Authentication a,HttpServletRequest r){return ok(()->{var context=tenant(a,r);subjectRoles.memberRoles(context,context.actorMemberId());return dataPolicyMetadata.list(PermissionCatalog.ScopeType.TENANT);});}
 @EndpointOperation("access.tenant.role:grant") @GetMapping("/api/access/effective-permissions") ResponseEntity<List<EffectiveAuthorizationQuery.Explanation>> tenantEffectivePermissions(Authentication a,HttpServletRequest r){return ok(()->{var context=tenant(a,r);subjectRoles.memberRoles(context,context.actorMemberId());return effectiveAuthorizations.tenantMember(context.tenantId(),context.actorMemberId());});}

 /**
  * 查询当前可信租户内目标成员的角色权限解释，先复核管理权及目标归属以防跨租户探测。
  * @param member 目标成员 UUID，不能使用账号 UUID 或客户端租户标识替代
  * @return 仅供排错的角色贡献解释，不作为业务鉴权凭据
  */
 @EndpointOperation("access.tenant.role:grant") @GetMapping("/api/access/members/{member}/effective-permissions")
 ResponseEntity<List<EffectiveAuthorizationQuery.Explanation>> memberEffectivePermissions(Authentication a,HttpServletRequest r,@PathVariable UUID member){return ok(()->{var context=tenant(a,r);subjectRoles.memberRoles(context,member);return effectiveAuthorizations.tenantMember(context.tenantId(),member);});}

 /** 独立成员授权快照写入；version 为成员授权版本，grant.roleVersion 为当前角色版本。 */
 public record MemberAuthorizationReplace(MemberAuthorizationManagement.Grant grant,long version,String reason) {}
 /** 读取当前可信租户成员各角色分支的数据范围与有效期。 */
 @EndpointOperation("access.tenant.role:grant") @GetMapping("/api/access/members/{member}/authorizations")
 ResponseEntity<MemberAuthorizationManagement.Assignment> memberAuthorizations(Authentication a,HttpServletRequest r,@PathVariable UUID member){return ok(()->memberAuthorizations.get(tenant(a,r),member));}
 /** 单分支保存与成员版本推进、审计原子提交，禁止客户端指定租户。 */
 @EndpointOperation("access.tenant.role:grant") @PutMapping("/api/access/members/{member}/authorizations")
 ResponseEntity<MemberAuthorizationManagement.Assignment> replaceMemberAuthorizations(Authentication a,HttpServletRequest r,@PathVariable UUID member,@RequestBody MemberAuthorizationReplace input){return ok(()->memberAuthorizations.replace(tenant(a,r),member,input.grant(),input.version(),input.reason()));}
 @EndpointOperation("access.tenant.role:grant") @GetMapping("/bff/platform/tenants/{tenant}/members/{member}/authorizations")
 ResponseEntity<MemberAuthorizationManagement.Assignment> managedAuthorizations(Authentication a,@PathVariable UUID tenant,@PathVariable UUID member){return ok(()->memberAuthorizations.get(managed(a,tenant),member));}
 @EndpointOperation("access.tenant.role:grant") @PutMapping("/bff/platform/tenants/{tenant}/members/{member}/authorizations")
 ResponseEntity<MemberAuthorizationManagement.Assignment> replaceManagedAuthorizations(Authentication a,@PathVariable UUID tenant,@PathVariable UUID member,@RequestBody MemberAuthorizationReplace input){return ok(()->memberAuthorizations.replace(managed(a,tenant),member,input.grant(),input.version(),input.reason()));}

 private RoleManagement.Context platform(Authentication a){return new RoleManagement.Context(actor(a),null,null);} private RoleManagement.Context managed(Authentication a,UUID tenant){return new RoleManagement.Context(actor(a),Objects.requireNonNull(tenant),null);}
 private RoleManagement.Context tenant(Authentication a,HttpServletRequest request){if(!(request.getAttribute(TenantExecutionFilter.ATTRIBUTE) instanceof VerifiedTenantContext c))throw AccountSettingsController.problem(HttpStatus.CONFLICT,"TENANT_REQUIRED","请选择有效租户");return new RoleManagement.Context(actor(a),c.tenantId(),c.memberId());}
 private UUID actor(Authentication a){return UUID.fromString(a.getName());} private <T> ResponseEntity<T> ok(java.util.function.Supplier<T> action){try{return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(action.get());}catch(SecurityException denied){throw AccountSettingsController.problem(HttpStatus.FORBIDDEN,"AUTHORIZATION_MANAGEMENT_DENIED","无授权管理权限");}catch(RoleManagementException business){HttpStatus status=business.code().endsWith("NOT_FOUND")?HttpStatus.NOT_FOUND:HttpStatus.CONFLICT;throw AccountSettingsController.problem(status,business.code(),business.getMessage());}catch(IllegalArgumentException|NullPointerException invalid){throw AccountSettingsController.problem(HttpStatus.BAD_REQUEST,"INVALID_AUTHORIZATION_INPUT","授权参数不符合要求");}}
}
