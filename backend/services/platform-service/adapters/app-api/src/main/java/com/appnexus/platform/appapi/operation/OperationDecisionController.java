package com.appnexus.platform.appapi.operation;
import com.appnexus.platform.security.operation.OperationRegistry;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.contracts.operation.EndpointPolicy;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
/** 统一操作展示能力，只返回操作展示准入，不放行业务对象操作，真实请求仍逐次授权。 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.ACCOUNT_SELF_SERVICE)
public final class OperationDecisionController {
 private final OperationRegistry registry;private final AccessAuthorization authorization;
 public OperationDecisionController(OperationRegistry registry,AccessAuthorization authorization){this.registry=registry;this.authorization=authorization;}
 /** 平台主体来自认证会话；返回操作标识而非让前端翻译权限码。 */
 @com.appnexus.contracts.operation.OperationBinding(id="PermissionCatalog.platformDecisions", reference=true, eligibility=com.appnexus.contracts.operation.OperationBinding.Eligibility.SELF)
 @GetMapping("/bff/operation-decisions")
 public ResponseEntity<List<String>> platform(Authentication authentication){return decisions(AccessAuthorization.Subject.platform(UUID.fromString(authentication.getName())),false);}
 /** 租户来自可信上下文，客户端不得通过入参冒充成员。 */
 @com.appnexus.contracts.operation.OperationBinding(id="PermissionCatalog.tenantDecisions", reference=true, eligibility=com.appnexus.contracts.operation.OperationBinding.Eligibility.SELF)
 @GetMapping("/api/operation-decisions")
 public ResponseEntity<List<String>> tenant(HttpServletRequest request){
  if(!(request.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext c))return ResponseEntity.status(409).build();
  return decisions(AccessAuthorization.Subject.tenant(c.accountId(),c.tenantId(),c.memberId()),true);
 }
 /** 当前只判断入口展示门槛；SELF/OBJECT 的真实请求继续执行本人或对象归属检查。 */
 private ResponseEntity<List<String>> decisions(AccessAuthorization.Subject subject,boolean tenant){
  // 同一请求内按权限码去重，避免多个按钮共享权限时重复访问数据库；结果不跨请求缓存。
  var checked=new HashMap<String,Boolean>();
  var ids=registry.all().stream().filter(item->item.endpoints().stream().anyMatch(endpoint->tenant?endpoint.contains(" /api/"):endpoint.contains(" /bff/")))
   .filter(item->item.eligibility()!=com.appnexus.contracts.operation.OperationBinding.Eligibility.PERMISSION || com.appnexus.platform.authorization.OperationPermissionExpression.allows(item.permissions(),code->checked.computeIfAbsent(code,value->authorization.hasPermission(subject,value))))
   .map(OperationRegistry.Definition::id).toList();
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ids);
 }
}
