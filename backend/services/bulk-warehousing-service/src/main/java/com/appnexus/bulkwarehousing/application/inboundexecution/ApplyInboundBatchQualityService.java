package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.contracts.businessobject.BusinessObjects.ScheduledOperations;
import com.appnexus.contracts.data.TransactionalBusinessEvents;
import com.appnexus.platform.authorization.ScopedWriteGate;
import java.util.List;
import java.util.Set;

/** 共享 Outbox 消费事务中的入库质量投影。只写待检库存与其质量指标，不修改作业。 */
public final class ApplyInboundBatchQualityService implements TransactionalBusinessEvents.Consumer {
    public static final String OPERATION = "BulkInboundQualityOrder.apply_batch_quality";
    public static final String AUTHORITY = "system.warehousing.inboundquality.apply";

    private final ScheduledOperations operations;
    private final ScopedWriteGate gate;
    private final InboundBatchQualityProjection projection;

    /** @param operations 固定系统操作准入 @param gate 公共事务许可 @param projection 库存投影端口 */
    public ApplyInboundBatchQualityService(ScheduledOperations operations, ScopedWriteGate gate,
                                          InboundBatchQualityProjection projection) {
        this.operations = operations;
        this.gate = gate;
        this.projection = projection;
    }

    /** {@inheritDoc} */
    @Override public Set<TransactionalBusinessEvents.Type> types() {
        return Set.of(TransactionalBusinessEvents.Type.INBOUND_RECEIPT_POSTED,
                TransactionalBusinessEvents.Type.INBOUND_QUALITY_DECIDED);
    }

    /** {@inheritDoc} */
    @Override public String consumerId() { return "inbound-batch-quality"; }

    /** {@inheritDoc} */
    @Override public void consume(TransactionalBusinessEvents.Delivery delivery) {
        if (!types().contains(delivery.type())) throw new SecurityException("入库质量事件类型不匹配");
        gate.authorizeServiceOperation(delivery.tenant(), AUTHORITY, List.of(delivery.subjectId().toString()), () -> {
            operations.require(OPERATION, AUTHORITY);
            return true;
        });
        projection.apply(delivery.tenant(), delivery.type(), delivery.subjectId());
    }
}
