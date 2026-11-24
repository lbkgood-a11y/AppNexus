package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.CreateBulkReceiptDraftUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.OpenInboundReceiptUseCase;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.authorization.AccessAuthorization.Subject;
import com.appnexus.platform.authorization.AccessPlanning;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.Set;
import java.util.UUID;

/** 开单独立于质检计量，沿用作业锁与有效草稿唯一约束。 */
public final class OpenInboundReceiptService implements OpenInboundReceiptUseCase {
    private final AccessPlanning access;
    private final InboundReceiptOrderRepository repository;

    /** @param access 公共授权计划 @param repository 受控入库草稿仓储 */
    public OpenInboundReceiptService(AccessPlanning access, InboundReceiptOrderRepository repository) {
        this.access = access;
        this.repository = repository;
    }

    /** {@inheritDoc} */
    @Override public OpenedInboundReceipt open(VerifiedTenantContext context, UUID taskId, long taskVersion) {
        if (taskId == null || taskVersion < 0) throw new Rejection(400, "INBOUND_RECEIPT_DRAFT_INPUT_INVALID");
        var subject = new Subject(context.accountId(), context.tenantId(), context.memberId(),
                context.personId(), context.organizationId());
        var plan = access.plan(subject, PERMISSION, CreateBulkReceiptDraftUseCase.RESOURCE);
        if (!plan.tenantWide() || !subject.equals(plan.subject()) || !PERMISSION.equals(plan.permissionCode())) {
            throw new Rejection(403, "INBOUND_RECEIPT_ACCESS_DENIED");
        }
        return repository.transaction(plan, () -> {
            var tenant = context.tenantId();
            var task = repository.lockTask(tenant, taskId);
            if (task == null) throw new Rejection(404, "INBOUND_TASK_NOT_FOUND");
            var existing = repository.lockActiveDraft(tenant, taskId);
            if (existing != null) return new OpenedInboundReceipt(existing.id(), existing.number());
            if (task.rowVersion() != taskVersion) throw new Rejection(409, "INBOUND_RECEIPT_DRAFT_VERSION_CONFLICT");
            if (!Set.of("ARRIVED", "WORKING", "READY_TO_RECEIVE").contains(task.status())
                    || task.noticeStatus() == null || !Set.of("ISSUED", "RECEIVING").contains(task.noticeStatus())) {
                throw new Rejection(409, "INBOUND_RECEIPT_DRAFT_STATE_CONFLICT");
            }
            var id = UUID.randomUUID();
            String number = "R-" + id.toString().replace("-", "");
            repository.openDraft(tenant, context.accountId(), taskId, task.noticeId(), id, number);
            return new OpenedInboundReceipt(id, number);
        });
    }
}
