package com.appnexus.platform.managementapi.operation;

import com.appnexus.platform.security.operation.OperationRegistry;
import com.appnexus.platform.configuration.MenuManagement;
import com.appnexus.contracts.operation.EndpointPolicy;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 平台只读操作目录，复用菜单读取权威校验；不授予业务权限。 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.PLATFORM_ADMINISTRATION)
public final class OperationCatalogController {
    private final OperationRegistry registry;
    private final MenuManagement menus;

    public OperationCatalogController(OperationRegistry registry, MenuManagement menus) {
        this.registry = registry;
        this.menus = menus;
    }

    /** 读取全模块对象目录；同样复核平台菜单读取权限，列表不授予对象配置资格。 */
    @com.appnexus.contracts.operation.OperationBinding(id = "PermissionCatalog.objects", reference = true, enforceDeclaredPermission = true)
    @GetMapping("/bff/platform/object-catalog")
    public ResponseEntity<List<OperationRegistry.ObjectDefinition>> objects(Authentication authentication) {
        list(authentication);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(registry.objects());
    }

    /**
     * 读取当前代码目录，不缓存敏感元数据。
     * 
     * @param authentication 服务端认证主体
     * @return 已登记操作及真实接口
     */
    @com.appnexus.contracts.operation.OperationBinding(id = "PermissionCatalog.operations", reference = true, enforceDeclaredPermission = true)
    @GetMapping("/bff/platform/operation-catalog")
    public ResponseEntity<List<OperationRegistry.Definition>> list(Authentication authentication) {
        try {
            menus.list(UUID.fromString(authentication.getName()));
        } catch (SecurityException denied) {
            var detail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "无平台功能目录读取权限");
            detail.setProperty("code", "PLATFORM_DENIED");
            throw new org.springframework.web.ErrorResponseException(HttpStatus.FORBIDDEN, detail, null);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(registry.all());
    }
}
