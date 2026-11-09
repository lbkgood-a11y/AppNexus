package com.appnexus.platform.security;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
/** 工作台上下文入口，必须经过统一业务请求过滤器，不授予业务操作权限。 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.TENANT_CONTEXT)
public class WorkspaceContextController {
    @GetMapping("/api/workspace/context")
    ResponseEntity<VerifiedTenantContext> current(HttpServletRequest request) {
        var context = request.getAttribute(TenantExecutionFilter.ATTRIBUTE);
        if (!(context instanceof VerifiedTenantContext verified))
            throw AccountSettingsController.problem(HttpStatus.CONFLICT,"TENANT_REQUIRED","请选择有效租户");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(verified);
    }
}
