package com.appnexus.platform.security;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 平台及租户审计入口；普通租户调用方不能使用平台可选 tenantId 扩大读取范围。 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.AUTHORIZATION_MANAGEMENT)
public class AuditQueryController {
    private final UnifiedAudit audit;
    public AuditQueryController(UnifiedAudit audit){this.audit=audit;}
    /** 平台管理员审计，tenantId 缺省仅平台操作；分页缺省 1/20，最大 100 条。 */
    @EndpointOperation("tenant:read") @GetMapping("/bff/platform/audits")
    ResponseEntity<UnifiedAudit.Page> platform(Authentication auth,@RequestParam(required=false) UUID tenantId,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){return ok(audit.platform(UUID.fromString(auth.getName()),tenantId,page,pageSize));}
    /** 当前租户审计，租户仅从统一上下文取得；忽略任何客户端 tenantId 选择意图。 */
    @EndpointOperation("organization.permission:grant") @GetMapping("/api/audits")
    ResponseEntity<UnifiedAudit.Page> tenant(HttpServletRequest request,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){
        if(!(request.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext context))throw AccountSettingsController.problem(HttpStatus.CONFLICT,"TENANT_REQUIRED","请选择有效租户");
        return ok(audit.tenant(context,page,pageSize));
    }
    private <T> ResponseEntity<T> ok(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
}
