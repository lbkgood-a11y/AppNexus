package com.appnexus.masterdata.adapter.web.organization;

import com.appnexus.masterdata.organization.PersonnelExecution;
import com.appnexus.platform.tenant.TenantRequestIdentity;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 兼容仍需要人员身份的主数据与业务入口；身份选择不能改变账号权限。 */
@Component
final class PersonnelRequestIdentity implements TenantRequestIdentity {
    private final PersonnelExecution personnel;

    PersonnelRequestIdentity(PersonnelExecution personnel) {
        this.personnel = personnel;
    }

    @Override
    public VerifiedTenantContext resolve(HttpServletRequest request, HttpServletResponse response,
            VerifiedTenantContext base, boolean bearer) throws IOException {
            var session = request.getSession(false);
            String selectedPerson=session==null?null:(String)session.getAttribute("appnexus.person");
            String selectedOrganization=session==null?null:(String)session.getAttribute("appnexus.personOrganization");
            String selectedTenant=session==null?null:(String)session.getAttribute("appnexus.personTenant");
            String path=request.getRequestURI().substring(request.getContextPath().length());
            // 租户切换使旧人员选择失效；管理接口仍能修复关系，业务接口必须显式选择有效人员。
            if(!base.tenantId().toString().equals(selectedTenant)){selectedPerson=null;selectedOrganization=null;}
            // 原生 Bearer 没有 Cookie 会话，只接收选择意图并经同一公开端口复核，不能直接信任请求头。
            if(bearer){selectedPerson=request.getHeader("X-Person-Id");selectedOrganization=request.getHeader("X-Person-Organization");}
            try{if(selectedPerson!=null)UUID.fromString(selectedPerson);if(selectedOrganization!=null)UUID.fromString(selectedOrganization);}catch(IllegalArgumentException invalid){reject(response,400,"PERSON_CONTEXT_INVALID");return null;}
            boolean validSelection=selectedPerson!=null&&selectedOrganization!=null&&personnel.allowed(base.tenantId(),base.memberId(),UUID.fromString(selectedPerson),UUID.fromString(selectedOrganization));
            String expectedPerson=request.getHeader("X-Person-Id"),expectedOrganization=request.getHeader("X-Person-Organization");
            if(path.startsWith("/api/leave-poc")&&validSelection&&((expectedPerson!=null&&!expectedPerson.equalsIgnoreCase(selectedPerson))||(expectedOrganization!=null&&!expectedOrganization.equalsIgnoreCase(selectedOrganization)))){reject(response,409,"PERSON_CONTEXT_CHANGED");return null;}
            if(path.startsWith("/api/leave-poc")&&!validSelection){reject(response,409,"PERSON_CONTEXT_REQUIRED");return null;}
            if(!validSelection){selectedPerson=null;selectedOrganization=null;}
            return new VerifiedTenantContext(base.accountId(),base.tenantId(),base.memberId(),base.version(),
                selectedPerson==null?null:UUID.fromString(selectedPerson),selectedOrganization==null?null:UUID.fromString(selectedOrganization));
    }

    @Override
    public void reset(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) return;
        session.removeAttribute("appnexus.person");
        session.removeAttribute("appnexus.personOrganization");
        session.removeAttribute("appnexus.personTenant");
    }

    /** 使用原稳定错误码，便于旧客户端重新选择业务身份。 */
    private void reject(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setHeader("Cache-Control","no-store");
        response.getWriter().write("{\"status\":"+status+",\"code\":\""+code+"\"}");
    }
}
