package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundWorkbenchUseCase;
import com.appnexus.contracts.businessobject.BusinessObjects.DataAccessExecutor;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.authorization.AccessAuthorization.Subject;
import com.appnexus.platform.authorization.AccessPlanning;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.Set;
import java.util.UUID;

/** 用统一权限读取作业队列。数据库投影只选择工作台队列契约字段。 */
public final class ReadInboundWorkbenchService implements ReadInboundWorkbenchUseCase, DataAccessExecutor {
    private final AccessPlanning access;
    private final InboundWorkbenchQueries queries;

    /** @param access 平台统一操作与范围计划 @param queries 受控队列读取端口 */
    public ReadInboundWorkbenchService(AccessPlanning access, InboundWorkbenchQueries queries) {
        this.access = access;
        this.queries = queries;
    }

    /** {@inheritDoc} */
    @Override public Set<String> enforcedResources() {
        return Set.of(RESOURCE);
    }

    /** {@inheritDoc} */
    @Override public QueuePage queue(VerifiedTenantContext context, Stage stage, UUID afterId, int limit, boolean unopened) {
        if (limit < 1 || limit > 100) {
            throw new Rejection(400, "INBOUND_QUEUE_LIMIT_INVALID");
        }
        var subject = new Subject(context.accountId(), context.tenantId(), context.memberId(),
                context.personId(), context.organizationId());
        var plan = access.plan(subject, PERMISSION, RESOURCE);
        if (!plan.tenantWide()) {
            throw new Rejection(403, "INBOUND_QUEUE_ACCESS_DENIED");
        }
        return queries.queue(plan, stage, afterId, limit, unopened);
    }
}
