package com.appnexus.platform.security;

import com.appnexus.platform.configuration.NavigationQuery;
import com.appnexus.platform.tenant.TenantAccess;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 本人导航 BFF；租户及成员由会话与成员目录复核，不接受客户端 tenantId 或权限列表。 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.TENANT_CONTEXT)
public class NavigationController {
    private final NavigationQuery navigation;
    private final TenantAccess tenants;
    public NavigationController(NavigationQuery navigation,TenantAccess tenants){this.navigation=navigation;this.tenants=tenants;}

    /** 当前导航不缓存；尚未选择或已失效的租户不返回租户入口，平台与登录公共入口仍独立计算。 */
    @GetMapping("/bff/navigation")
    ResponseEntity<NavigationQuery.Snapshot> current(Authentication authentication,HttpServletRequest request) {
        var account=UUID.fromString(authentication.getName());
        var session=request.getSession(false);
        var selected=session==null?null:session.getAttribute(TenantContextController.TENANT);
        var member=tenants.available(account).stream().filter(item -> item.tenantId().toString().equals(selected)
                &&item.memberId().toString().equals(session.getAttribute(TenantContextController.MEMBER))).findFirst();
        long version=session!=null&&session.getAttribute(TenantContextController.VERSION) instanceof Number n?n.longValue():0;
        try {
            var result=member.isPresent()?navigation.current(account,member.get().tenantId(),member.get().memberId(),version):navigation.current(account,null,null,0);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
        }catch(SecurityException denied){throw AccountSettingsController.problem(HttpStatus.FORBIDDEN,"NAVIGATION_DENIED","导航上下文不可用");}
    }
}
