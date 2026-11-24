package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.PostInboundReceiptUseCase;
import com.appnexus.bulkwarehousing.application.InboundReferenceSource;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.contracts.data.TransactionalBusinessEvents;
import com.appnexus.platform.authorization.AccessPlanning;
import com.appnexus.platform.tenant.VerifiedTenantContext;

/** 将已通过采样门槛的草稿落入库存账，按当前质量保存保留量和事务事件。 */
public final class PostInboundReceiptService implements PostInboundReceiptUseCase {
    private final ExecuteReceiptService execution;

    /**
     * @param access 统一授权计划
     * @param commands 作业锁和执行回执
     * @param posting 受控库存事务写入端口，必须与 commands 使用同一数据源
     * @param references 过账时重验主数据权限与位置、商品版本
     * @param events 共用事务的 Outbox 追加端口
     */
    public PostInboundReceiptService(AccessPlanning access, ReceiptExecutionRepository commands,
                                     BulkReceiptPostingRepository posting, InboundReferenceSource references,
                                     TransactionalBusinessEvents events) {
        this.execution = new ExecuteReceiptService(commands, access, (plan, context, action, command, nextVersion) -> {
            var tenant = context.tenantId();
            var actor = context.accountId();
            var inputs = posting.lockPostableDraft(tenant, command.taskId(), command.receiptId());
            if (inputs.isEmpty()) {
                throw new Rejection(409, "INBOUND_RECEIPT_POSTABLE_DRAFT_REQUIRED");
            }
            var first=inputs.getFirst();
            references.owner(context,first.ownerId());
            for(var input:inputs) {
                references.position(context,input.position());
                var currentItem=references.identity(context,input.item());
                if(!input.baseUnitId().equals(currentItem.baseUnitId()))throw new Rejection(409,"INBOUND_RECEIPT_POST_UNIT_CONFLICT");
                if(posting.consumeNoticeLine(tenant,actor,input)!=1)throw new Rejection(409,"INBOUND_RECEIPT_POST_QUOTA_CONFLICT");
                posting.insertStock(tenant,actor,input);
            }
            int notice=inputs.size()==1?posting.advanceNotice(tenant,actor,first)
                    :posting.advanceMultiLineNotice(tenant,actor,first.noticeId());
            if (notice!=1) {
                throw new Rejection(409, "INBOUND_RECEIPT_POST_QUOTA_CONFLICT");
            }
            if (posting.markReceiptPosted(tenant, actor, command.taskId(), command.receiptId(),
                    command.receiptVersion(), first.qualityState()) != 1
                    || posting.markTaskReceived(tenant, actor, command.taskId(), command.taskVersion()) != 1) {
                throw new Rejection(409, "INBOUND_EXECUTION_VERSION_CONFLICT");
            }
            events.publish(tenant, TransactionalBusinessEvents.Type.INBOUND_RECEIPT_POSTED, command.receiptId());
        });
    }

    /** {@inheritDoc} */
    @Override public InboundReceiptPosting post(VerifiedTenantContext context, Command command) {
        var result = execution.execute(context, ReceiptExecutionRepository.Action.POST,
                new ExecuteReceiptService.Command(command.taskId(), command.receiptId(), command.taskVersion(),
                        command.receiptVersion(), command.idempotencyKey()));
        return new InboundReceiptPosting(result.receiptId(), result.receiptVersion());
    }
}
