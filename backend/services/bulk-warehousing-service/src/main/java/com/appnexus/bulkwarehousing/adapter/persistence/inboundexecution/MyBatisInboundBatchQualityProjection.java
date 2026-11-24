package com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution;

import com.appnexus.bulkwarehousing.application.inboundexecution.InboundBatchQualityProjection;
import com.appnexus.contracts.data.TransactionalBusinessEvents;
import java.util.UUID;

/** 获准的事件读取先锁所属作业，再在原消费事务内应用质量状态。 */
public final class MyBatisInboundBatchQualityProjection implements InboundBatchQualityProjection {
    private final InboundBatchQualityMapper mapper;

    /** @param mapper 固定系统 SQL */
    public MyBatisInboundBatchQualityProjection(InboundBatchQualityMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override public int apply(UUID tenant, TransactionalBusinessEvents.Type type, UUID subjectId) {
        String key=tenant.toString(), subject=subjectId.toString();
        return switch(type) {
            case INBOUND_RECEIPT_POSTED -> mapper.lockByReceipt(key,subject)==null ? 0 : mapper.applyByReceipt(key,subject);
            case INBOUND_QUALITY_DECIDED -> mapper.lockByQuality(key,subject)==null ? 0 : mapper.applyByQuality(key,subject);
        };
    }
}
