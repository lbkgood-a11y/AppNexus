package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundDocumentsUseCase;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.authorization.AccessAuthorization.Subject;
import com.appnexus.platform.authorization.AccessPlan;
import com.appnexus.platform.authorization.AccessPlanning;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.Map;
import java.util.List;
import java.util.UUID;

/** 列表和详情分别申请独立的统一权限。 */
public final class ReadInboundDocumentsService implements ReadInboundDocumentsUseCase {
    private final AccessPlanning access;
    private final InboundDocumentQueries queries;

    /** @param access 公共权限计划 @param queries 固定仓储查询 */
    public ReadInboundDocumentsService(AccessPlanning access, InboundDocumentQueries queries) {
        this.access = access;
        this.queries = queries;
    }

    /** {@inheritDoc} */
    @Override public InboundDocumentPage list(VerifiedTenantContext context, Kind kind, String number, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100 || (long) (page - 1) * pageSize > Integer.MAX_VALUE
                || (number != null && number.length() > 64)) {
            throw new Rejection(400,"INBOUND_DOCUMENT_FILTER_INVALID");
        }
        var plan = required(context,kind,kind.listPermission());
        var filter = number == null || number.isBlank() ? null : number.strip();
        return queries.list(plan,kind,filter,(page - 1) * pageSize,pageSize);
    }

    /** {@inheritDoc} */
    @Override public Map<String,Object> detail(VerifiedTenantContext context, Kind kind, UUID id) {
        var result = queries.detail(required(context,kind,kind.detailPermission()),kind,id);
        if (result == null) throw new Rejection(404,"INBOUND_DOCUMENT_NOT_FOUND");
        return result;
    }

    /** {@inheritDoc} */
    @Override public List<TaskOption> taskOptions(VerifiedTenantContext context, Creation creation, String taskNo) {
        var plan = required(context, creation.kind(), creation.permission());
        String prefix = taskNo == null ? "" : taskNo.strip();
        if (prefix.length() > 64) throw new Rejection(400, "INBOUND_DOCUMENT_FILTER_INVALID");
        return queries.taskOptions(plan, creation, prefix);
    }

    private AccessPlan required(VerifiedTenantContext context, Kind kind, String permission) {
        var subject = new Subject(context.accountId(),context.tenantId(),context.memberId(),
                context.personId(),context.organizationId());
        var plan = access.plan(subject,permission,kind.resource());
        if (!plan.tenantWide() || !subject.equals(plan.subject()) || !permission.equals(plan.permissionCode())) {
            throw new Rejection(403,"INBOUND_DOCUMENT_ACCESS_DENIED");
        }
        return plan;
    }
}
