package com.appnexus.platform.security;

import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 平台受权管理固定租户集成主体；沿用平台 MFA、Cookie CSRF 和权威管理员复核。 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.PLATFORM_ADMINISTRATION)
@RequestMapping("/bff/platform")
public class IntegrationClientController {
    private final IntegrationClients clients;
    public IntegrationClientController(IntegrationClients clients){this.clients=clients;}
    /** 非空 scopes 仅允许已发布外部操作；名称最长 100 字符。 */
    public record Creation(String name,Set<String> scopes){}
    /** version 为读取的非负版本，状态和权限同时完整覆盖。 */
    public record Change(int version,boolean enabled,Set<String> scopes){}
    /** 轮换必须匹配当前版本，不能盲目覆盖另一管理员的更改。 */
    public record Rotation(int version){}
    @EndpointOperation("integration.client:read") @GetMapping("/tenants/{tenant}/clients")
    ResponseEntity<List<IntegrationClients.View>> list(Authentication auth,@PathVariable UUID tenant){return ok(clients.list(UUID.fromString(auth.getName()),tenant));}
    @EndpointOperation("integration.client:create") @PostMapping("/tenants/{tenant}/clients")
    ResponseEntity<IntegrationClients.Created> create(Authentication auth,@PathVariable UUID tenant,@RequestBody Creation input){
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(clients.create(UUID.fromString(auth.getName()),tenant,input.name(),input.scopes()));
    }
    @EndpointOperation("integration.client:update") @PutMapping("/clients/{id}")
    ResponseEntity<IntegrationClients.View> update(Authentication auth,@PathVariable String id,@RequestBody Change input){return ok(clients.update(UUID.fromString(auth.getName()),id,input.version(),input.enabled(),input.scopes()));}
    @EndpointOperation("integration.client:rotate_secret") @PostMapping("/clients/{id}/secret-rotation")
    ResponseEntity<IntegrationClients.Created> rotate(Authentication auth,@PathVariable String id,@RequestBody Rotation input){return ok(clients.rotate(UUID.fromString(auth.getName()),id,input.version()));}
    private <T> ResponseEntity<T> ok(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
}
