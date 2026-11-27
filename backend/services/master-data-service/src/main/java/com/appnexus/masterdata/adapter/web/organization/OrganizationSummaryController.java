package com.appnexus.masterdata.adapter.web.organization;
import com.appnexus.contracts.operation.EndpointPolicy;

import com.appnexus.masterdata.organization.OrganizationSummary;
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
    @com.appnexus.contracts.operation.OperationBinding(id="OrganizationUnit.summary",reference=true, enforceDeclaredPermission=true)
  @GetMapping("/api/organization/summaries")
    ResponseEntity<List<OrganizationSummary.Group>> get(HttpServletRequest request){
        var value=request.getAttribute(VerifiedTenantContext.class.getName());
        if(!(value instanceof VerifiedTenantContext context))throw OrganizationHttpProblems.problem(HttpStatus.CONFLICT,"TENANT_REQUIRED","请选择有效租户");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(summary.summarize(AccessAuthorization.Subject.tenant(context.accountId(),context.tenantId(),context.memberId())));
    }
}
