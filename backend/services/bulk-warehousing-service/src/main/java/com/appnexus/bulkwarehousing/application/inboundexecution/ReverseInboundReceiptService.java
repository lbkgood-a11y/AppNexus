package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.ReverseInboundReceiptUseCase;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.authorization.AccessPlanning;
import com.appnexus.platform.tenant.VerifiedTenantContext;

/** 同作业锁内冲红未发生后续业务的合格散货，保留原始入库和重量凭证。 */
public final class ReverseInboundReceiptService implements ReverseInboundReceiptUseCase {
    private final ExecuteReceiptService execution;

    /** @param access 公共权限 @param commands 指令锁与幂等仓储 @param reversal 同事务冲红库存端口 */
    public ReverseInboundReceiptService(AccessPlanning access, ReceiptExecutionRepository commands,
                                         BulkReceiptReversalRepository reversal) {
        execution = new ExecuteReceiptService(commands, access, (plan, context, action, command, nextVersion) -> {
            var tenant = context.tenantId();
            var actor = context.accountId();
            var input = reversal.lockReversibleStock(tenant, command.taskId(), command.receiptId());
            if (input == null) throw new Rejection(409, "INBOUND_RECEIPT_STOCK_NOT_REVERSIBLE");
            if (reversal.reverseStock(tenant, actor, input) != 1
                    || reversal.restoreNoticeLine(tenant, actor, input) != 1
                    || reversal.restoreNotice(tenant, actor, input) != 1
                    || reversal.markReceiptReversed(tenant, actor, command.taskId(), command.receiptId(),
                    command.receiptVersion()) != 1
                    || reversal.markTaskCorrectionRequired(tenant, actor, command.taskId(), command.taskVersion()) != 1) {
                throw new Rejection(409, "INBOUND_RECEIPT_REVERSAL_CONFLICT");
            }
        });
    }

    /** {@inheritDoc} */
    @Override public InboundReceiptReversal reverse(VerifiedTenantContext context, Command command) {
        var result = execution.execute(context, ReceiptExecutionRepository.Action.REVERSE,
                new ExecuteReceiptService.Command(command.taskId(), command.receiptId(), command.taskVersion(),
                        command.receiptVersion(), command.idempotencyKey()));
        return new InboundReceiptReversal(result.receiptId(), result.receiptVersion());
    }
}
