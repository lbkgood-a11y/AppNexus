package com.appnexus.platform.managementapi.administration;
import com.appnexus.contracts.operation.EndpointPolicy;
import com.appnexus.platform.authorization.PolicyTemplates;
import com.appnexus.platform.security.*;

import com.appnexus.platform.authorization.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 平台及当前租户策略模板入口；平台写入沿用强制 MFA，租户上下文来自统一过滤器。 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.AUTHORIZATION_MANAGEMENT)
public class PolicyTemplateController {
    private final PolicyTemplates templates;
    public PolicyTemplateController(PolicyTemplates templates){this.templates=templates;}
    /** 模板名称，1–100 字符，创建本身不更改角色。 */
    public record Save(String name){}
    /** 完整覆盖目标角色必须携带其当前版本及变更原因。 */
    public record Apply(UUID templateId,long version,String reason){}
    /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */

 @com.appnexus.contracts.operation.OperationBinding(id="AuthorizationTemplate.listPlatform", reference=true, enforceDeclaredPermission=true)
  @GetMapping("/bff/platform/roles/{role}/policy-templates")
    ResponseEntity<List<PolicyTemplates.View>> platformList(Authentication auth,@PathVariable UUID role){return ok(()->templates.list(new RoleManagement.Context(UUID.fromString(auth.getName()),null,null),role));}
    /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
    @com.appnexus.contracts.operation.OperationBinding(id="AuthorizationTemplate.savePlatform", reference=true, enforceDeclaredPermission=true)
  @PostMapping("/bff/platform/roles/{role}/policy-templates")
    ResponseEntity<PolicyTemplates.View> platformSave(Authentication auth,@PathVariable UUID role,@RequestBody Save input){return ok(()->templates.save(new RoleManagement.Context(UUID.fromString(auth.getName()),null,null),role,input.name()));}
    /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
    @com.appnexus.contracts.operation.OperationBinding(id="AuthorizationTemplate.applyPlatform", reference=true, enforceDeclaredPermission=true)
  @PostMapping("/bff/platform/roles/{role}/policy-template-application")
    ResponseEntity<RolePermissionManagement.AuthorizationAssignment> platformApply(Authentication auth,@PathVariable UUID role,@RequestBody Apply input){return ok(()->templates.apply(new RoleManagement.Context(UUID.fromString(auth.getName()),null,null),role,input.templateId(),input.version(),input.reason()));}
    /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
    @com.appnexus.contracts.operation.OperationBinding(id="AuthorizationTemplate.listTenant", reference=true, enforceDeclaredPermission=true)
  @GetMapping("/api/access/roles/{role}/policy-templates")
    ResponseEntity<List<PolicyTemplates.View>> tenantList(HttpServletRequest request,@PathVariable UUID role){return ok(()->templates.list(context(request),role));}
    /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
    @com.appnexus.contracts.operation.OperationBinding(id="AuthorizationTemplate.saveTenant", reference=true, enforceDeclaredPermission=true)
  @PostMapping("/api/access/roles/{role}/policy-templates")
    ResponseEntity<PolicyTemplates.View> tenantSave(HttpServletRequest request,@PathVariable UUID role,@RequestBody Save input){return ok(()->templates.save(context(request),role,input.name()));}
    /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
    @com.appnexus.contracts.operation.OperationBinding(id="AuthorizationTemplate.applyTenant", reference=true, enforceDeclaredPermission=true)
  @PostMapping("/api/access/roles/{role}/policy-template-application")
    ResponseEntity<RolePermissionManagement.AuthorizationAssignment> tenantApply(HttpServletRequest request,@PathVariable UUID role,@RequestBody Apply input){return ok(()->templates.apply(context(request),role,input.templateId(),input.version(),input.reason()));}
    private RoleManagement.Context context(HttpServletRequest request){if(!(request.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext value))throw AccountSettingsController.problem(HttpStatus.CONFLICT,"TENANT_REQUIRED","请选择有效租户");return new RoleManagement.Context(value.accountId(),value.tenantId(),value.memberId());}
    /** 错误按稳定契约映射，不能将无权操作或版本冲突暴露为内部异常。 */
    private <T> ResponseEntity<T> ok(java.util.function.Supplier<T> action){
        try{return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(action.get());}
        catch(SecurityException denied){throw AccountSettingsController.problem(HttpStatus.FORBIDDEN,"AUTHORIZATION_MANAGEMENT_DENIED","无授权管理权限");}
        catch(RoleManagementException conflict){throw AccountSettingsController.problem(HttpStatus.CONFLICT,conflict.code(),conflict.getMessage());}
        catch(IllegalArgumentException|NullPointerException invalid){throw AccountSettingsController.problem(HttpStatus.BAD_REQUEST,"INVALID_POLICY_TEMPLATE","策略模板参数不正确");}
    }
}
