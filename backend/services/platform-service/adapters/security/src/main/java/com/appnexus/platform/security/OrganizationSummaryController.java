package com.appnexus.platform.security;

import com.appnexus.platform.organization.OrganizationSummary;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 固定组织聚合查询；业务用例独立生成 AccessPlan，不能依赖前端菜单或只验证有效成员。 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.TENANT_BUSINESS)
public class OrganizationSummaryController {
    private final OrganizationSummary summary;
    public OrganizationSummaryController(OrganizationSummary summary){this.summary=summary;}
    /** 返回有权组织的类型/状态统计，不暴露无权范围计数。 */
    @EndpointOperation("organization.unit:read") @GetMapping("/api/organization/summaries")
    ResponseEntity<List<OrganizationSummary.Group>> get(HttpServletRequest request){
        var value=request.getAttribute(VerifiedTenantContext.class.getName());
        if(!(value instanceof VerifiedTenantContext context))throw AccountSettingsController.problem(HttpStatus.CONFLICT,"TENANT_REQUIRED","请选择有效租户");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(summary.summarize(AccessAuthorization.Subject.tenant(context.accountId(),context.tenantId(),context.memberId())));
    }
}
