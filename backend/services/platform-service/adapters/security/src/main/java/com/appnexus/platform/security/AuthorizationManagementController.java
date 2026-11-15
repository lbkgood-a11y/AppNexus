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
 @com.appnexus.contracts.operation.BusinessObjectType(code="PermissionCatalog",nameZh="权限目录",nameEn="Permission Catalog",category=com.appnexus.contracts.operation.BusinessObjectType.Category.CONFIGURATION,module="authorization")
 @com.appnexus.contracts.operation.OperationBinding(id="PermissionCatalog.navigationPlatform",objectType="PermissionCatalog",operationCode="navigationPlatform",pages={"platform/authorization"},nameZh="权限目录：查看授权导航（平台）",nameEn="Grant platform role permissions",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation("access.role:grant") @GetMapping("/bff/platform/authorization-navigation") ResponseEntity<List<NavigationQuery.AuthorizationNode>> platformNavigation(Authentication a){return ok(()->{subjectRoles.accountRoles(actor(a),actor(a));return navigation.authorizationCatalog(MenuManagement.Scope.PLATFORM);});}
 /** 租户从可信上下文确定，普通成员不能枚举授权目录。 */
 @com.appnexus.contracts.operation.OperationBinding(id="PermissionCatalog.navigationTenant",objectType="PermissionCatalog",operationCode="navigationTenant",pages={"roles/authorization"},nameZh="权限目录：查看授权导航（租户）",nameEn="Grant tenant role permissions",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation("access.tenant.role:grant") @GetMapping("/api/access/authorization-navigation") ResponseEntity<List<NavigationQuery.AuthorizationNode>> tenantNavigation(Authentication a,HttpServletRequest r){return ok(()->{var c=tenant(a,r);subjectRoles.memberRoles(c,c.actorMemberId());return navigation.authorizationCatalog(MenuManagement.Scope.TENANT);});}
 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
 @com.appnexus.contracts.operation.OperationBinding(id="PermissionCatalog.listPlatform",objectType="PermissionCatalog",operationCode="listPlatform",pages={"platform/authorization"},nameZh="权限目录：查看平台目录",nameEn="Grant platform role permissions",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation("access.role:grant") @GetMapping("/bff/platform/permissions") ResponseEntity<List<PermissionCatalog.Permission>> platformPermissionCatalog(Authentication a,@RequestParam(defaultValue="PLATFORM") PermissionCatalog.ScopeType scopeType){return ok(()->{subjectRoles.accountRoles(actor(a),actor(a));return permissions.list(scopeType,true);});}
 /** 只读旧授权对账；差异归零前不得关闭运行时双读或删除历史表。 */
 @com.appnexus.contracts.operation.OperationBinding(id="PermissionCatalog.reconcile",objectType="PermissionCatalog",operationCode="reconcile",pages={"platform/authorization"},nameZh="权限目录：检查授权差异",nameEn="Grant platform role permissions",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation("access.role:grant") @GetMapping("/bff/platform/authorization-reconciliation") ResponseEntity<LegacyAuthorizationReconciliation.Report> reconciliation(Authentication a,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="50") int size){return ok(()->reconciliation.report(actor(a),page,size));}
 /** 字段目录只返回已有真实执行点的能力；租户域尚未登记时返回空集合。 */
 @com.appnexus.contracts.operation.OperationBinding(id="PermissionCatalog.fieldsPlatform",objectType="PermissionCatalog",operationCode="fieldsPlatform",pages={"platform/authorization"},nameZh="权限目录：查看字段目录（平台）",nameEn="Grant platform role permissions",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation("access.role:grant") @GetMapping("/bff/platform/field-policy-metadata") ResponseEntity<List<FieldSecurity.Descriptor>> platformFields(Authentication a,@RequestParam(defaultValue="PLATFORM") PermissionCatalog.ScopeType scopeType){return ok(()->{subjectRoles.accountRoles(actor(a),actor(a));return fieldCatalog.list(scopeType);});}
 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
 @com.appnexus.contracts.operation.OperationBinding(id="PermissionCatalog.fieldsTenant",objectType="PermissionCatalog",operationCode="fieldsTenant",pages={"roles/authorization"},nameZh="权限目录：查看字段目录（租户）",nameEn="Grant tenant role permissions",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation("access.tenant.role:grant") @GetMapping("/api/access/field-policy-metadata") ResponseEntity<List<FieldSecurity.Descriptor>> tenantFields(Authentication a,HttpServletRequest r){return ok(()->{var c=tenant(a,r);subjectRoles.memberRoles(c,c.actorMemberId());return fieldCatalog.list(PermissionCatalog.ScopeType.TENANT);});}
 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
 @com.appnexus.contracts.operation.OperationBinding(id="PermissionCatalog.scopesPlatform",objectType="PermissionCatalog",operationCode="scopesPlatform",pages={"platform/authorization"},nameZh="权限目录：查看数据范围目录（平台）",nameEn="Grant platform role permissions",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation("access.role:grant") @GetMapping("/bff/platform/data-policy-metadata") ResponseEntity<List<DataPolicyMetadataCatalog.ScopeSupport>> platformDataPolicyMetadata(Authentication a,@RequestParam(defaultValue="PLATFORM") PermissionCatalog.ScopeType scopeType){return ok(()->{subjectRoles.accountRoles(actor(a),actor(a));return dataPolicyMetadata.list(scopeType);});}
 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
 @com.appnexus.contracts.operation.BusinessObjectType(code="AccountRoleAssignment",nameZh="账号角色关联",nameEn="Account Role Assignment",category=com.appnexus.contracts.operation.BusinessObjectType.Category.CONFIGURATION,module="authorization")
 @com.appnexus.contracts.operation.OperationBinding(id="AccountRoleAssignment.explain",objectType="AccountRoleAssignment",operationCode="explain",pages={"platform/authorization"},nameZh="账号角色关联：解释有效权限",nameEn="Grant platform role permissions",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation("access.role:grant") @GetMapping("/bff/platform/users/{account}/effective-permissions") ResponseEntity<List<EffectiveAuthorizationQuery.Explanation>> platformEffectivePermissions(Authentication a,@PathVariable UUID account){return ok(()->{subjectRoles.accountRoles(actor(a),account);return effectiveAuthorizations.platformAccount(account);});}

 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */

 @com.appnexus.contracts.operation.BusinessObjectType(code="RoleAuthorization",nameZh="角色授权",nameEn="Role Authorization",category=com.appnexus.contracts.operation.BusinessObjectType.Category.CONFIGURATION,module="authorization")
 @com.appnexus.contracts.operation.OperationBinding(id="RoleAuthorization.readPlatform",objectType="RoleAuthorization",operationCode="readPlatform",pages={"platform/authorization"},nameZh="角色授权：查看（平台）",nameEn="Grant platform role permissions",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation("access.role:grant") @GetMapping("/bff/platform/roles/{role}/permissions") ResponseEntity<RolePermissionManagement.AuthorizationAssignment> platformPermissions(Authentication a,@PathVariable UUID role){return ok(()->rolePermissions.getAuthorization(platform(a),role));}
 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
 @com.appnexus.contracts.operation.OperationBinding(id="RoleAuthorization.replacePlatform",objectType="RoleAuthorization",operationCode="replacePlatform",pages={"platform/authorization"},nameZh="角色授权：保存授权（平台）",nameEn="Grant platform role permissions",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation("access.role:grant") @PutMapping("/bff/platform/roles/{role}/permissions") ResponseEntity<RolePermissionManagement.AuthorizationAssignment> replacePlatformPermissions(Authentication a,@PathVariable UUID role,@RequestBody AuthorizationReplace input){return ok(()->rolePermissions.replaceAuthorization(platform(a),role,input.version(),input.ids(),input.dataPolicies(),input.fieldPolicies(),input.reason()));}
 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
 @com.appnexus.contracts.operation.OperationBinding(id="AccountRoleAssignment.read",objectType="AccountRoleAssignment",operationCode="read",pages={"platform/authorization"},nameZh="账号角色关联：查看",nameEn="Grant platform role permissions",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation("access.role:grant") @GetMapping("/bff/platform/users/{account}/roles") ResponseEntity<SubjectRoleManagement.Assignment> accountRoles(Authentication a,@PathVariable UUID account){return ok(()->subjectRoles.accountRoles(actor(a),account));}
 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
 @com.appnexus.contracts.operation.OperationBinding(id="AccountRoleAssignment.replace",objectType="AccountRoleAssignment",operationCode="replace",pages={"platform/authorization"},nameZh="账号角色关联：保存授权",nameEn="Grant platform role permissions",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation("access.role:grant") @PutMapping("/bff/platform/users/{account}/roles") ResponseEntity<SubjectRoleManagement.Assignment> replaceAccountRoles(Authentication a,@PathVariable UUID account,@RequestBody Replace input){return ok(()->subjectRoles.replaceAccountRoles(actor(a),account,input.version(),input.ids(),input.reason()));}

 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */

 @com.appnexus.contracts.operation.OperationBinding(id="RoleAuthorization.readManaged",objectType="RoleAuthorization",operationCode="readManaged",pages={"platform/tenants"},nameZh="角色授权：查看（平台代管）",nameEn="Grant tenant role permissions",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation(value="access.tenant.role:grant",platformPermission="tenant.role:grant") @GetMapping("/bff/platform/tenants/{tenant}/roles/{role}/permissions") ResponseEntity<RolePermissionManagement.AuthorizationAssignment> managedPermissions(Authentication a,@PathVariable UUID tenant,@PathVariable UUID role){return ok(()->rolePermissions.getAuthorization(managed(a,tenant),role));}
 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
 @com.appnexus.contracts.operation.OperationBinding(id="RoleAuthorization.replaceManaged",objectType="RoleAuthorization",operationCode="replaceManaged",pages={"platform/tenants"},nameZh="角色授权：保存授权（平台代管）",nameEn="Grant tenant role permissions",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation(value="access.tenant.role:grant",platformPermission="tenant.role:grant") @PutMapping("/bff/platform/tenants/{tenant}/roles/{role}/permissions") ResponseEntity<RolePermissionManagement.AuthorizationAssignment> replaceManagedPermissions(Authentication a,@PathVariable UUID tenant,@PathVariable UUID role,@RequestBody AuthorizationReplace input){return ok(()->rolePermissions.replaceAuthorization(managed(a,tenant),role,input.version(),input.ids(),input.dataPolicies(),input.fieldPolicies(),input.reason()));}
 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
 @com.appnexus.contracts.operation.BusinessObjectType(code="MemberRoleAssignment",nameZh="成员角色关联",nameEn="Member Role Assignment",category=com.appnexus.contracts.operation.BusinessObjectType.Category.CONFIGURATION,module="authorization")
 @com.appnexus.contracts.operation.OperationBinding(id="MemberRoleAssignment.readManaged",objectType="MemberRoleAssignment",operationCode="readManaged",pages={"platform/tenants"},nameZh="成员角色关联：查看（平台代管）",nameEn="Grant tenant role permissions",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation(value="access.tenant.role:grant",platformPermission="tenant.role:grant") @GetMapping("/bff/platform/tenants/{tenant}/members/{member}/roles") ResponseEntity<SubjectRoleManagement.Assignment> managedMemberRoles(Authentication a,@PathVariable UUID tenant,@PathVariable UUID member){return ok(()->subjectRoles.memberRoles(managed(a,tenant),member));}
 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
 @com.appnexus.contracts.operation.OperationBinding(id="MemberRoleAssignment.replaceManaged",objectType="MemberRoleAssignment",operationCode="replaceManaged",pages={"platform/tenants"},nameZh="成员角色关联：保存授权（平台代管）",nameEn="Grant tenant role permissions",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation(value="access.tenant.role:grant",platformPermission="tenant.role:grant") @PutMapping("/bff/platform/tenants/{tenant}/members/{member}/roles") ResponseEntity<SubjectRoleManagement.Assignment> replaceManagedMemberRoles(Authentication a,@PathVariable UUID tenant,@PathVariable UUID member,@RequestBody Replace input){return ok(()->subjectRoles.replaceMemberRoles(managed(a,tenant),member,input.version(),input.ids(),input.reason()));}
 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
 @com.appnexus.contracts.operation.OperationBinding(id="MemberRoleAssignment.explainManaged",objectType="MemberRoleAssignment",operationCode="explainManaged",pages={"platform/tenants"},nameZh="成员角色关联：解释有效权限（平台代管）",nameEn="Grant tenant role permissions",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation(value="access.tenant.role:grant",platformPermission="tenant.role:grant") @GetMapping("/bff/platform/tenants/{tenant}/members/{member}/effective-permissions") ResponseEntity<List<EffectiveAuthorizationQuery.Explanation>> managedEffectivePermissions(Authentication a,@PathVariable UUID tenant,@PathVariable UUID member){return ok(()->{subjectRoles.memberRoles(managed(a,tenant),member);return effectiveAuthorizations.tenantMember(tenant,member);});}

 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */

 @com.appnexus.contracts.operation.OperationBinding(id="RoleAuthorization.readTenant",objectType="RoleAuthorization",operationCode="readTenant",pages={"roles/authorization"},nameZh="角色授权：查看（租户）",nameEn="Grant tenant role permissions",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation("access.tenant.role:grant") @GetMapping("/api/access/roles/{role}/permissions") ResponseEntity<RolePermissionManagement.AuthorizationAssignment> tenantPermissions(Authentication a,HttpServletRequest r,@PathVariable UUID role){return ok(()->rolePermissions.getAuthorization(tenant(a,r),role));}
 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
 @com.appnexus.contracts.operation.OperationBinding(id="RoleAuthorization.replaceTenant",objectType="RoleAuthorization",operationCode="replaceTenant",pages={"roles/authorization"},nameZh="角色授权：保存授权（租户）",nameEn="Grant tenant role permissions",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation("access.tenant.role:grant") @PutMapping("/api/access/roles/{role}/permissions") ResponseEntity<RolePermissionManagement.AuthorizationAssignment> replaceTenantPermissions(Authentication a,HttpServletRequest r,@PathVariable UUID role,@RequestBody AuthorizationReplace input){return ok(()->rolePermissions.replaceAuthorization(tenant(a,r),role,input.version(),input.ids(),input.dataPolicies(),input.fieldPolicies(),input.reason()));}
 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
 @com.appnexus.contracts.operation.OperationBinding(id="MemberRoleAssignment.read",objectType="MemberRoleAssignment",operationCode="read",pages={"roles/authorization"},nameZh="成员角色关联：查看",nameEn="Grant tenant role permissions",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation("access.tenant.role:grant") @GetMapping("/api/access/members/{member}/roles") ResponseEntity<SubjectRoleManagement.Assignment> memberRoles(Authentication a,HttpServletRequest r,@PathVariable UUID member){return ok(()->subjectRoles.memberRoles(tenant(a,r),member));}
 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
 @com.appnexus.contracts.operation.OperationBinding(id="MemberRoleAssignment.replace",objectType="MemberRoleAssignment",operationCode="replace",pages={"roles/authorization"},nameZh="成员角色关联：保存授权",nameEn="Grant tenant role permissions",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation("access.tenant.role:grant") @PutMapping("/api/access/members/{member}/roles") ResponseEntity<SubjectRoleManagement.Assignment> replaceMemberRoles(Authentication a,HttpServletRequest r,@PathVariable UUID member,@RequestBody Replace input){return ok(()->subjectRoles.replaceMemberRoles(tenant(a,r),member,input.version(),input.ids(),input.reason()));}
 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
 @com.appnexus.contracts.operation.OperationBinding(id="PermissionCatalog.listTenant",objectType="PermissionCatalog",operationCode="listTenant",pages={"roles/authorization"},nameZh="权限目录：查看租户目录",nameEn="Grant tenant role permissions",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation("access.tenant.role:grant") @GetMapping("/api/access/permissions") ResponseEntity<List<PermissionCatalog.Permission>> tenantPermissionCatalog(Authentication a,HttpServletRequest r){return ok(()->{var context=tenant(a,r);subjectRoles.memberRoles(context,context.actorMemberId());return permissions.list(PermissionCatalog.ScopeType.TENANT,true);});}
 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
 @com.appnexus.contracts.operation.OperationBinding(id="PermissionCatalog.scopesTenant",objectType="PermissionCatalog",operationCode="scopesTenant",pages={"roles/authorization"},nameZh="权限目录：查看数据范围目录（租户）",nameEn="Grant tenant role permissions",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation("access.tenant.role:grant") @GetMapping("/api/access/data-policy-metadata") ResponseEntity<List<DataPolicyMetadataCatalog.ScopeSupport>> tenantDataPolicyMetadata(Authentication a,HttpServletRequest r){return ok(()->{var context=tenant(a,r);subjectRoles.memberRoles(context,context.actorMemberId());return dataPolicyMetadata.list(PermissionCatalog.ScopeType.TENANT);});}
 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
 @com.appnexus.contracts.operation.OperationBinding(id="MemberRoleAssignment.explainSelf",objectType="MemberRoleAssignment",operationCode="explainSelf",pages={"roles/authorization"},nameZh="成员角色关联：解释本人权限",nameEn="Grant tenant role permissions",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation("access.tenant.role:grant") @GetMapping("/api/access/effective-permissions") ResponseEntity<List<EffectiveAuthorizationQuery.Explanation>> tenantEffectivePermissions(Authentication a,HttpServletRequest r){return ok(()->{var context=tenant(a,r);subjectRoles.memberRoles(context,context.actorMemberId());return effectiveAuthorizations.tenantMember(context.tenantId(),context.actorMemberId());});}

 /**
  * 查询当前可信租户内目标成员的角色权限解释，先复核管理权及目标归属以防跨租户探测。
  * @param member 目标成员 UUID，不能使用账号 UUID 或客户端租户标识替代
  * @return 仅供排错的角色贡献解释，不作为业务鉴权凭据
  */
 @com.appnexus.contracts.operation.OperationBinding(id="MemberRoleAssignment.explain",objectType="MemberRoleAssignment",operationCode="explain",pages={"roles/authorization"},nameZh="成员角色关联：解释有效权限",nameEn="Grant tenant role permissions",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation("access.tenant.role:grant") @GetMapping("/api/access/members/{member}/effective-permissions")
 ResponseEntity<List<EffectiveAuthorizationQuery.Explanation>> memberEffectivePermissions(Authentication a,HttpServletRequest r,@PathVariable UUID member){return ok(()->{var context=tenant(a,r);subjectRoles.memberRoles(context,member);return effectiveAuthorizations.tenantMember(context.tenantId(),member);});}

 /** 独立成员授权快照写入；version 为成员授权版本，grant.roleVersion 为当前角色版本。 */
 public record MemberAuthorizationReplace(MemberAuthorizationManagement.Grant grant,long version,String reason) {}
 /** 读取当前可信租户成员各角色分支的数据范围与有效期。 */
 @com.appnexus.contracts.operation.BusinessObjectType(code="MemberAuthorization",nameZh="成员授权",nameEn="Member Authorization",category=com.appnexus.contracts.operation.BusinessObjectType.Category.CONFIGURATION,module="authorization")
 @com.appnexus.contracts.operation.OperationBinding(id="MemberAuthorization.read",objectType="MemberAuthorization",operationCode="read",pages={"roles/authorization"},nameZh="成员授权：查看",nameEn="Grant tenant role permissions",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation("access.tenant.role:grant") @GetMapping("/api/access/members/{member}/authorizations")
 ResponseEntity<MemberAuthorizationManagement.Assignment> memberAuthorizations(Authentication a,HttpServletRequest r,@PathVariable UUID member){return ok(()->memberAuthorizations.get(tenant(a,r),member));}
 /** 单分支保存与成员版本推进、审计原子提交，禁止客户端指定租户。 */
 @com.appnexus.contracts.operation.OperationBinding(id="MemberAuthorization.replace",objectType="MemberAuthorization",operationCode="replace",pages={"roles/authorization"},nameZh="成员授权：保存授权",nameEn="Grant tenant role permissions",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation("access.tenant.role:grant") @PutMapping("/api/access/members/{member}/authorizations")
 ResponseEntity<MemberAuthorizationManagement.Assignment> replaceMemberAuthorizations(Authentication a,HttpServletRequest r,@PathVariable UUID member,@RequestBody MemberAuthorizationReplace input){return ok(()->memberAuthorizations.replace(tenant(a,r),member,input.grant(),input.version(),input.reason()));}
 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
 @com.appnexus.contracts.operation.OperationBinding(id="MemberAuthorization.readManaged",objectType="MemberAuthorization",operationCode="readManaged",pages={"platform/tenants"},nameZh="成员授权：查看（平台代管）",nameEn="Grant tenant role permissions",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation(value="access.tenant.role:grant",platformPermission="tenant.role:grant") @GetMapping("/bff/platform/tenants/{tenant}/members/{member}/authorizations")
 ResponseEntity<MemberAuthorizationManagement.Assignment> managedAuthorizations(Authentication a,@PathVariable UUID tenant,@PathVariable UUID member){return ok(()->memberAuthorizations.get(managed(a,tenant),member));}
 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
 @com.appnexus.contracts.operation.OperationBinding(id="MemberAuthorization.replaceManaged",objectType="MemberAuthorization",operationCode="replaceManaged",pages={"platform/tenants"},nameZh="成员授权：保存授权（平台代管）",nameEn="Grant tenant role permissions",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation(value="access.tenant.role:grant",platformPermission="tenant.role:grant") @PutMapping("/bff/platform/tenants/{tenant}/members/{member}/authorizations")
 ResponseEntity<MemberAuthorizationManagement.Assignment> replaceManagedAuthorizations(Authentication a,@PathVariable UUID tenant,@PathVariable UUID member,@RequestBody MemberAuthorizationReplace input){return ok(()->memberAuthorizations.replace(managed(a,tenant),member,input.grant(),input.version(),input.reason()));}

 private RoleManagement.Context platform(Authentication a){return new RoleManagement.Context(actor(a),null,null);} private RoleManagement.Context managed(Authentication a,UUID tenant){return new RoleManagement.Context(actor(a),Objects.requireNonNull(tenant),null);}
 private RoleManagement.Context tenant(Authentication a,HttpServletRequest request){if(!(request.getAttribute(TenantExecutionFilter.ATTRIBUTE) instanceof VerifiedTenantContext c))throw AccountSettingsController.problem(HttpStatus.CONFLICT,"TENANT_REQUIRED","请选择有效租户");return new RoleManagement.Context(actor(a),c.tenantId(),c.memberId());}
 private UUID actor(Authentication a){return UUID.fromString(a.getName());} private <T> ResponseEntity<T> ok(java.util.function.Supplier<T> action){try{return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(action.get());}catch(SecurityException denied){throw AccountSettingsController.problem(HttpStatus.FORBIDDEN,"AUTHORIZATION_MANAGEMENT_DENIED","无授权管理权限");}catch(RoleManagementException business){HttpStatus status=business.code().endsWith("NOT_FOUND")?HttpStatus.NOT_FOUND:HttpStatus.CONFLICT;throw AccountSettingsController.problem(status,business.code(),business.getMessage());}catch(IllegalArgumentException|NullPointerException invalid){throw AccountSettingsController.problem(HttpStatus.BAD_REQUEST,"INVALID_AUTHORIZATION_INPUT","授权参数不符合要求");}}
}
