package com.appnexus.platform.security;
import com.appnexus.platform.tenant.*;
import com.appnexus.platform.authorization.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** 为第一方业务 API 建立请求级上下文；账号自助与租户选择接口不要求预先选择租户。 */
public final class TenantExecutionFilter extends OncePerRequestFilter {
    static final String ATTRIBUTE = VerifiedTenantContext.class.getName();
    private final TenantAccess access;
    private final OrganizationAuthorization organizationAuthorization;
    public TenantExecutionFilter(TenantAccess access, OrganizationAuthorization organizationAuthorization) {
        this.access = access; this.organizationAuthorization=organizationAuthorization;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path=request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/api/")||path.equals("/api/tenants")||path.equals("/api/tenant-context");
    }
    /** 不使用 ThreadLocal 或客户端 tenantId，避免线程复用与伪造请求污染边界。 */
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        request.removeAttribute(ATTRIBUTE);
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
                reject(response,401,"AUTHENTICATION_REQUIRED"); return;
            }
            if (auth instanceof org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication service &&
                    auth.getAuthorities().stream().anyMatch(value->value.getAuthority().equals("PARTNER_CLIENT"))) {
                String path=request.getRequestURI().substring(request.getContextPath().length());
                boolean printingAgent=path.startsWith("/api/printing/agent/")&&auth.getAuthorities().stream()
                    .anyMatch(value->value.getAuthority().equals("SCOPE_print-agent:operate"));
                Object tenant=service.getTokenAttributes().get("tenantId"),client=service.getTokenAttributes().get("clientId");
                if(!printingAgent||!(tenant instanceof String tenantId)||!(client instanceof String clientId)) {
                    reject(response,403,"SERVICE_OPERATION_NOT_AUTHORIZED"); return;
                }
                request.setAttribute(com.appnexus.contracts.printing.PrintContext.class.getName(),
                    new com.appnexus.contracts.printing.PrintContext(tenantId,clientId,java.util.UUID.randomUUID().toString()));
                chain.doFilter(request,response);
                return;
            }
            var session = request.getSession(false);
            Object verifiedVersion,verifiedTenant,verifiedMember;
            if(auth instanceof org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication bearer){
                // Bearer 上下文仅来自已验证授权及 Token 版本；Cookie 和任意 tenantId 请求头均不参与。
                verifiedVersion=bearer.getTokenAttributes().get(RotatingAuthorizationService.VERSION);
                verifiedTenant=bearer.getTokenAttributes().get(RotatingAuthorizationService.TENANT);
                verifiedMember=bearer.getTokenAttributes().get(RotatingAuthorizationService.MEMBER);
            }else{
                verifiedVersion=session==null?null:session.getAttribute(TenantContextController.VERSION);
                verifiedTenant=session==null?null:session.getAttribute(TenantContextController.TENANT);
                verifiedMember=session==null?null:session.getAttribute(TenantContextController.MEMBER);
            }
            if (!(verifiedVersion instanceof Number numericVersion) || numericVersion.longValue() < 1) {
                reject(response,409,"TENANT_REQUIRED"); return;
            }
            long version=numericVersion.longValue();
            var accountId = UUID.fromString(auth.getName());
            var member = access.available(accountId).stream().filter(m ->
                m.tenantId().toString().equals(verifiedTenant) &&
                m.memberId().toString().equals(verifiedMember)).findFirst();
            if (member.isEmpty()) { reject(response,409,"TENANT_REQUIRED"); return; }
            var m = member.get();
            // 版本头只作为陈旧请求保护，不作为租户来源；租户仍由服务端会话及成员目录决定。
            String expected=request.getHeader("X-Tenant-Context-Version");
            boolean writing=!java.util.Set.of("GET","HEAD","OPTIONS").contains(request.getMethod());
            if((expected!=null && !expected.equals(Long.toString(version))) || (version>1 && writing && expected==null)) {
                reject(response,409,"TENANT_CONTEXT_CHANGED"); return;
            }
            var context=new VerifiedTenantContext(accountId,m.tenantId(),m.memberId(),version);
            request.setAttribute(ATTRIBUTE,context);
            // 工作流只接收公开契约，租户与操作者均来自上述可信会话，浏览器无权覆盖。
            request.setAttribute(com.appnexus.contracts.workflow.WorkflowContext.class.getName(),new com.appnexus.contracts.workflow.WorkflowContext(m.tenantId().toString(),accountId.toString(),java.util.UUID.randomUUID().toString()));
            // 打印服务使用独立公开契约；请求结束清理，避免容器线程复用泄漏租户。
            request.setAttribute(com.appnexus.contracts.printing.PrintContext.class.getName(),new com.appnexus.contracts.printing.PrintContext(m.tenantId().toString(),accountId.toString(),java.util.UUID.randomUUID().toString()));
            response.setHeader("X-Tenant-Context-Version",Long.toString(version));
            if(request.getRequestURI().startsWith(request.getContextPath()+"/api/organization/units") ||
               request.getRequestURI().startsWith(request.getContextPath()+"/api/organization/domain")) {
                var action=request.getMethod();
                var capability=switch(action) {
                    case "GET","HEAD" -> OrganizationCapability.READ;
                    // 移动组织是已存在对象的修改，不能因为采用命令 POST 就误要求创建权限。
                    case "POST" -> request.getRequestURI().matches(".*/domain/units/[^/]+/move")?OrganizationCapability.UPDATE:OrganizationCapability.CREATE;
                    case "PATCH","PUT" -> OrganizationCapability.UPDATE;
                    case "DELETE" -> OrganizationCapability.DELETE;
                    default -> null;
                };
                if(capability==null) { reject(response,405,"METHOD_NOT_ALLOWED"); return; }
                if(!organizationAuthorization.allows(context.tenantId(),context.memberId(),capability)) {
                    reject(response,403,"OPERATION_NOT_AUTHORIZED"); return;
                }
            }
            chain.doFilter(request,response);
        } catch (org.springframework.dao.DataAccessException unavailable) {
            if (response.isCommitted()) throw unavailable;
            reject(response,503,"CONTEXT_UNAVAILABLE");
        } finally {
            request.removeAttribute(ATTRIBUTE);
            request.removeAttribute(com.appnexus.contracts.workflow.WorkflowContext.class.getName());
            request.removeAttribute(com.appnexus.contracts.printing.PrintContext.class.getName());
        }
    }
    /** 过滤器错误使用标准错误媒体类型和稳定码，不泄露账号、成员或租户存在性。 */
    private void reject(HttpServletResponse response,int status,String code) throws IOException {
        response.setStatus(status); response.setContentType("application/problem+json");
        response.setHeader("Cache-Control","no-store");
        response.getWriter().write("{\"status\":"+status+",\"code\":\""+code+"\"}");
    }
}
