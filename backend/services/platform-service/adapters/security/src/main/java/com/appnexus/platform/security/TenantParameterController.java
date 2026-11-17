package com.appnexus.platform.security;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 租户非敏感参数入口，身份来自统一上下文，禁止请求体指定租户或操作者。 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.TENANT_BUSINESS)
@RequestMapping("/api/configuration/parameters")
public class TenantParameterController {
    private final TenantParameters parameters;
    public TenantParameterController(TenantParameters parameters){this.parameters=parameters;}
    /** 修改明确受控配置，version 首次 -1、后续为当前版本。 */
    public record Change(String value,int version){}
    /** 有效成员读取非敏感展示参数；不授予维护能力。 */
    @com.appnexus.contracts.operation.RecordAuditSource(table="tenant_parameter",key="parameter_key",id="key",binaryKey=false)
 @GetMapping ResponseEntity<List<TenantParameters.Parameter>> list(HttpServletRequest request){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(parameters.list(context(request)));}
    /** 使用租户参数独立维护权限，不能通过通用配置任意修改安全参数。 */
    @com.appnexus.contracts.operation.BusinessObjectType(code="TenantParameter",nameZh="租户参数",nameEn="Tenant Parameter",category=com.appnexus.contracts.operation.BusinessObjectType.Category.CONFIGURATION,module="configuration")
 @com.appnexus.contracts.operation.OperationBinding(id="TenantParameter.save",objectType="TenantParameter",operationCode="save",pages={"configuration/parameters"},nameZh="租户参数：保存",nameEn="Save tenant parameter",policy="主体管理域、角色权限及业务用例约束由原执行器逐请求复核")
 @EndpointOperation("configuration.parameter:save") @PutMapping("/{key}")
    ResponseEntity<TenantParameters.Parameter> save(HttpServletRequest request,@PathVariable String key,@RequestBody Change input){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(parameters.save(context(request),key,input.value(),input.version()));}
    private VerifiedTenantContext context(HttpServletRequest request){if(!(request.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext value))throw AccountSettingsController.problem(HttpStatus.CONFLICT,"TENANT_REQUIRED","请选择有效租户");return value;}
}
