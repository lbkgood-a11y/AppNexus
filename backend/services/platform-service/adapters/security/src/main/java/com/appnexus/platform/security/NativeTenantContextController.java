package com.appnexus.platform.security;

import com.appnexus.platform.tenant.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 原生 Token 租户上下文；切换后旧 Access Token 失效，客户端通过标准刷新端点取得新凭证。 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.TENANT_CONTEXT)
public class NativeTenantContextController {
    private final TenantAccess tenants;
    private final RotatingAuthorizationService authorizations;
    public NativeTenantContextController(TenantAccess tenants,RotatingAuthorizationService authorizations){this.tenants=tenants;this.authorizations=authorizations;}
    /** 账号由 Bearer 权威授权解析，不接受浏览器 Cookie 或客户端指定账号。 */
    @GetMapping("/api/tenants")
    ResponseEntity<List<TenantAccess.Membership>> list(Authentication auth){
        requireBearer(auth);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tenants.available(UUID.fromString(auth.getName())));
    }
    /** 租户 UUID 是选择意图；版本必须与授权当前版本一致，首次为零。 */
    public record Selection(UUID tenantId,long expectedVersion){}
    /** refreshRequired 为 true 时必须调用 /oauth2/token 的 refresh_token 授权，不能继续使用旧 Access Token。 */
    public record Context(UUID tenantId,UUID memberId,long version,boolean refreshRequired){}
    /** 服务端成员校验与授权版本变更在数据库锁内完成；不返回 Token，也不自定义刷新协议。 */
    @PostMapping("/api/tenant-context")
    ResponseEntity<Context> select(Authentication auth,@RequestBody Selection input){
        var bearer=requireBearer(auth);
        if(input.tenantId()==null||input.expectedVersion()<0)throw AccountSettingsController.problem(HttpStatus.BAD_REQUEST,"INVALID_TENANT_CONTEXT","租户上下文参数不正确");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(authorizations.bindTenant(
            bearer.getToken().getTokenValue(),UUID.fromString(auth.getName()),input.tenantId(),input.expectedVersion(),tenants));
    }
    private BearerTokenAuthentication requireBearer(Authentication auth){
        if(!(auth instanceof BearerTokenAuthentication bearer))throw AccountSettingsController.problem(HttpStatus.FORBIDDEN,"BEARER_REQUIRED","此接口需要原生客户端认证");
        return bearer;
    }
}
