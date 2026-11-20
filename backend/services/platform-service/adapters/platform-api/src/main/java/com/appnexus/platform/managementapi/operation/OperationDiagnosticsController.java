package com.appnexus.platform.managementapi.operation;
import com.appnexus.platform.security.operation.OperationRegistry;

import com.appnexus.platform.authorization.*;
import com.appnexus.platform.security.EndpointPolicy;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 按目标主体反向解释操作权限；只读诊断不模拟登录，不构成执行许可。 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.AUTHORIZATION_MANAGEMENT)
public final class OperationDiagnosticsController {
 private final com.appnexus.platform.configuration.NavigationQuery navigation;
 private final DataScopeAuthorization scopes;private final FieldProtection fields;private final DataPolicyMetadataCatalog metadata;
 private final OperationRegistry registry;private final SubjectRoleManagement subjects;private final EffectiveAuthorizationQuery explanations;
 public OperationDiagnosticsController(OperationRegistry registry,SubjectRoleManagement subjects,EffectiveAuthorizationQuery explanations,com.appnexus.platform.configuration.NavigationQuery navigation,DataScopeAuthorization scopes,FieldProtection fields,DataPolicyMetadataCatalog metadata){this.scopes=scopes;this.fields=fields;this.metadata=metadata;this.navigation=navigation;this.registry=registry;this.subjects=subjects;this.explanations=explanations;}
 /**
  * 数据执行约束，不返回字段原值。
  * @param permission 诊断的权限码
  * @param resource 已登记资源
  * @param scope 有效数据范围或 NOT_APPLICABLE
  * @param organizations 同租户组织范围标识
  * @param fields 目标主体的强制字段展示上限
  */
 public record DataCheck(String permission,String resource,String scope,Set<UUID> organizations,Map<String,FieldSecurity.Mode> fields) {}
 /**
  * 分层只读诊断，不构成执行凭据。
  * @param operation 当前构建操作
  * @param status 功能授权或对象资格待检查状态
  * @param permissions 包括无来源缺失项的全部权限要求
  * @param unchecked 尚未检查的具体对象与业务条件
  * @param pages 复用真实导航规则的准入和显隐结果
  * @param data 复用执行端口的数据与字段约束
  */
 public record Diagnosis(OperationRegistry.Definition operation,String status,List<EffectiveAuthorizationQuery.Explanation> permissions,List<String> unchecked,List<com.appnexus.platform.configuration.NavigationQuery.PageCheck> pages,List<DataCheck> data) {}
 /** 平台他人查询先复用主体授权管理校验，不能仅凭目标 UUID 枚举权限。 */
 @GetMapping("/bff/platform/users/{account}/operation-diagnostics")
 public ResponseEntity<List<Diagnosis>> platform(Authentication authentication,@PathVariable UUID account){return result(()->{subjects.accountRoles(UUID.fromString(authentication.getName()),account);return explain(explanations.platformAccount(account),false,navigation.inspect(account,null,null),AccessAuthorization.Subject.platform(account));});}
 /** 租户身份仅来自可信过滤器；目标成员仍由管理用例复核同租户边界。 */
 @GetMapping("/api/access/members/{member}/operation-diagnostics")
 public ResponseEntity<List<Diagnosis>> tenant(Authentication authentication,HttpServletRequest request,@PathVariable UUID member){
  if(!(request.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext c))return ResponseEntity.status(409).build();
  return result(()->{subjects.memberRoles(new RoleManagement.Context(c.accountId(),c.tenantId(),c.memberId()),member);return explain(explanations.tenantMember(c.tenantId(),member),true,navigation.inspectMember(c.tenantId(),member),AccessAuthorization.Subject.tenant(navigation.accountForMember(c.tenantId(),member),c.tenantId(),member));});
 }
 /** 权限、导航和数据约束各自复用权威源；未输入具体对象时不伪造对象检查结果。 */
 private List<Diagnosis> explain(List<EffectiveAuthorizationQuery.Explanation> source,boolean tenant,List<com.appnexus.platform.configuration.NavigationQuery.PageCheck> pages,AccessAuthorization.Subject target){
  var supported=metadata.list(tenant?PermissionCatalog.ScopeType.TENANT:PermissionCatalog.ScopeType.PLATFORM);
  var dataCache=new HashMap<String,DataCheck>();
  var byCode=new HashMap<String,EffectiveAuthorizationQuery.Explanation>();source.forEach(value->byCode.put(value.permissionCode(),value));
  return registry.all().stream().filter(value->value.endpoints().stream().anyMatch(path->tenant?path.contains(" /api/"):path.contains(" /bff/"))).map(value->{
   var codes=value.permissions().stream().flatMap(group->Arrays.stream(group.split("&"))).distinct().toList();
   var detail=codes.stream().map(code->byCode.getOrDefault(code,new EffectiveAuthorizationQuery.Explanation(code,false,List.of()))).toList();
   boolean granted=OperationPermissionExpression.allows(value.permissions(),code->byCode.containsKey(code)&&byCode.get(code).effective());
   String status=value.eligibility()!=com.appnexus.contracts.operation.OperationBinding.Eligibility.PERMISSION?"OBJECT_CHECK_REQUIRED":granted?"FUNCTION_GRANTED":"NOT_GRANTED";
   return new Diagnosis(value,status,detail,List.of("OBJECT_AND_BUSINESS_STATE"),pages.stream().filter(page->value.pages().contains(page.page())).toList(),codes.stream().map(code->dataCache.computeIfAbsent(code,key->{
      var resources=supported.stream().filter(item->item.permissionCode().equals(code)).map(DataPolicyMetadataCatalog.ScopeSupport::resourceType).distinct().toList();
      String resource=resources.size()==1?resources.getFirst():code.substring(0,code.indexOf(':'));
      var scope=tenant&&!resources.isEmpty()?scopes.resolveDataScope(target,code,resource):null;
      return new DataCheck(code,resource,scope==null?"NOT_APPLICABLE":scope.type().name(),scope==null?Set.of():scope.organizationIds(),fields.restrictions(target,resource));
    })).toList());
  }).toList();
 }
 /** 无权查询返回稳定 403，不泄露目标是否存在；基础设施错误不伪装成缺授权。 */
 private ResponseEntity<List<Diagnosis>> result(java.util.function.Supplier<List<Diagnosis>> query){try{return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(query.get());}catch(SecurityException denied){var detail=ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,"无权诊断该主体的授权");detail.setProperty("code","AUTHORIZATION_MANAGEMENT_DENIED");throw new org.springframework.web.ErrorResponseException(HttpStatus.FORBIDDEN,detail,null);}}
}
