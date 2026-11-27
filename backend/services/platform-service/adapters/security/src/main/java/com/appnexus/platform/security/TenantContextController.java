package com.appnexus.platform.security;
import com.appnexus.contracts.operation.EndpointPolicy;
import com.appnexus.platform.tenant.TenantAccess;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 账号身份与租户访问分离；客户端只提交选择意图，成员身份由数据库验证。 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.TENANT_CONTEXT)
@RequestMapping("/bff/tenants")
public class TenantContextController {
    /** 宿主会话上下文键；仅供受信HTTP适配器读取，不接受请求体覆盖。 */
    public static final String TENANT="appnexus.tenant";
    /** 宿主会话上下文键；仅供受信HTTP适配器读取，不接受请求体覆盖。 */
    public static final String MEMBER="appnexus.member";
    /** 宿主会话上下文键；仅供受信HTTP适配器读取，不接受请求体覆盖。 */
    public static final String VERSION="appnexus.tenantVersion";
    private final TenantAccess access;
    private final com.appnexus.platform.tenant.TenantRequestIdentity identity;
    public TenantContextController(TenantAccess access, com.appnexus.platform.tenant.TenantRequestIdentity identity) {
        this.access = access;
        this.identity = identity;
    }
    /** 当前可访问租户；不提供跨账号查询参数。 */
    @com.appnexus.contracts.operation.OperationBinding(id="TenantMember.availableTenants", reference=true, eligibility=com.appnexus.contracts.operation.OperationBinding.Eligibility.SELF)
    @GetMapping
    ResponseEntity<List<TenantAccess.Membership>> list(Authentication auth) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(access.available(UUID.fromString(auth.getName())));
    }
    /** 租户仅表示选择意图；expectedVersion 用于切换时拒绝过期页面，不授予任何访问权。 */
    public record Selection(UUID tenantId, Long expectedVersion) {}
    /** 已验证上下文，不代表已经取得业务操作权限。 */
    public record Context(UUID tenantId, UUID memberId, String name, long version) {}
    /**
     * 每次读取重新验证租户及成员，停用立即影响新请求。
     * @param optional 默认 false；true 用于登录展示探测，未选择时返回 204，业务执行仍必须校验上下文
     */
    @com.appnexus.contracts.operation.OperationBinding(id="TenantMember.currentTenant", reference=true, eligibility=com.appnexus.contracts.operation.OperationBinding.Eligibility.SELF)
    @GetMapping("/current")
    ResponseEntity<Context> current(Authentication auth,HttpServletRequest request,
            @RequestParam(defaultValue="false") boolean optional) {
        var session=request.getSession();
        Object id=session.getAttribute(TENANT);
        Object memberId=session.getAttribute(MEMBER);
        long version=session.getAttribute(VERSION) instanceof Number n?n.longValue():0;
        // 探测与导航、业务过滤器验证同一租户及成员对，避免残留成员让页面误以为已完成选租户。
        var member=access.available(UUID.fromString(auth.getName())).stream()
            .filter(m -> m.tenantId().toString().equals(id)&&m.memberId().toString().equals(memberId)).findFirst();
        if(member.isEmpty()||version<1) {
            session.removeAttribute(TENANT);session.removeAttribute(MEMBER);
            if(optional) return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
            throw AccountSettingsController.problem(HttpStatus.CONFLICT,"TENANT_REQUIRED","请选择有效租户");
        }
        var m=member.get();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Context(m.tenantId(),m.memberId(),m.name(),
            version));
    }
    /**
     * 首次选择及切换均复核成员资格并轮换会话 ID；切换需匹配当前版本。
     * 客户端切换后整页重载并通知同源其他标签，旧页面业务请求由版本门禁拒绝。
     */
    @com.appnexus.contracts.operation.OperationBinding(id="TenantMember.selectTenant", reference=true, eligibility=com.appnexus.contracts.operation.OperationBinding.Eligibility.SELF)
    @PostMapping("/current")
    ResponseEntity<Context> select(@RequestBody Selection input,Authentication auth,HttpServletRequest request) {
        var m=access.available(UUID.fromString(auth.getName())).stream()
            .filter(item -> item.tenantId().equals(input.tenantId())).findFirst()
            .orElseThrow(() -> AccountSettingsController.problem(HttpStatus.FORBIDDEN,"TENANT_UNAVAILABLE","租户不可访问"));
        var session=request.getSession();
        String previous=(String)session.getAttribute(TENANT);
        long version=session.getAttribute(VERSION) instanceof Number n?n.longValue():0;
        if(previous!=null && !previous.equals(m.tenantId().toString()) &&
                (input.expectedVersion()==null || input.expectedVersion()!=version))
            throw AccountSettingsController.problem(HttpStatus.CONFLICT,"TENANT_CONTEXT_CHANGED","租户上下文已变化，请刷新后重试");
        if(!m.tenantId().toString().equals(previous)||!m.memberId().toString().equals(session.getAttribute(MEMBER))||version<1) {
            // 同一租户中的成员身份变化也必须轮换会话并推进版本，旧页面请求不能沿用原上下文。
            request.changeSessionId();
            identity.reset(request);
            session.setAttribute(TENANT,m.tenantId().toString());session.setAttribute(MEMBER,m.memberId().toString());
            session.setAttribute(VERSION,version+1);
        }
        return current(auth,request,false);
    }
}
