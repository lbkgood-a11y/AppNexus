package com.appnexus.platform.security;

import com.appnexus.platform.authorization.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 统一按钮能力快照；只返回当前主体真实有效权限，页面映射不产生新的授权。 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.ACCOUNT_SELF_SERVICE)
public class OperationCapabilitiesController {
    private final PermissionCatalog catalog;
    private final AccessAuthorization authorization;
    public OperationCapabilitiesController(PermissionCatalog catalog,AccessAuthorization authorization){this.catalog=catalog;this.authorization=authorization;}
    /** 平台主体操作，不要求已有租户；失败时不能回退全部按钮可用。 */
    @GetMapping("/bff/operation-capabilities")
    ResponseEntity<List<String>> platform(Authentication auth){return result(AccessAuthorization.Subject.platform(UUID.fromString(auth.getName())),PermissionCatalog.ScopeType.PLATFORM);}
    /** 租户主体仅来自统一过滤器，不接受 tenantId 参数或请求头选择。 */
    @GetMapping("/api/operation-capabilities")
    ResponseEntity<List<String>> tenant(HttpServletRequest request){
        if(!(request.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext context))throw AccountSettingsController.problem(HttpStatus.CONFLICT,"TENANT_REQUIRED","请选择有效租户");
        return result(AccessAuthorization.Subject.tenant(context.accountId(),context.tenantId(),context.memberId()),PermissionCatalog.ScopeType.TENANT);
    }
    private ResponseEntity<List<String>> result(AccessAuthorization.Subject subject,PermissionCatalog.ScopeType scope){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(catalog.list(scope,false).stream().map(PermissionCatalog.Permission::code).filter(code->authorization.hasPermission(subject,code)).toList());}
}
