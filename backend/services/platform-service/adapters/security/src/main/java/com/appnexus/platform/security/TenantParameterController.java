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
    @GetMapping ResponseEntity<List<TenantParameters.Parameter>> list(HttpServletRequest request){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(parameters.list(context(request)));}
    /** 当前使用租户授权管理边界维护，不能通过通用配置任意修改安全参数。 */
    @EndpointOperation("organization.permission:grant") @PutMapping("/{key}")
    ResponseEntity<TenantParameters.Parameter> save(HttpServletRequest request,@PathVariable String key,@RequestBody Change input){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(parameters.save(context(request),key,input.value(),input.version()));}
    private VerifiedTenantContext context(HttpServletRequest request){if(!(request.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext value))throw AccountSettingsController.problem(HttpStatus.CONFLICT,"TENANT_REQUIRED","请选择有效租户");return value;}
}
