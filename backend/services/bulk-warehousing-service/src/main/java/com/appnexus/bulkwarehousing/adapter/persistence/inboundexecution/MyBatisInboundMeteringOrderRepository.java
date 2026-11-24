package com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution;

import com.appnexus.bulkwarehousing.application.inboundexecution.InboundMeteringOrderRepository;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.authorization.AccessPlan;
import com.appnexus.platform.authorization.ScopedWriteGate;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 将外部整单计量、质量读锁、队列推进与审计绑定到同一个事务。 */
public final class MyBatisInboundMeteringOrderRepository implements InboundMeteringOrderRepository {
    private final InboundMeteringOrderMapper mapper;
    private final ScopedWriteGate gate;
    private final TransactionTemplate transaction;

    /** @param mapper 固定 SQL @param gate 公共许可 @param manager 组合应用主数据源事务管理器 */
    public MyBatisInboundMeteringOrderRepository(InboundMeteringOrderMapper mapper, ScopedWriteGate gate,
                                                 PlatformTransactionManager manager) {
        this.mapper = mapper;
        this.gate = gate;
        this.transaction = new TransactionTemplate(manager);
    }

    /** {@inheritDoc} */
    @Override public <T> T transaction(AccessPlan plan, Supplier<T> work) {
        try {
            return transaction.execute(status -> {
                gate.authorizeOperation(plan);
                return work.get();
            });
        } catch (DuplicateKeyException e) {
            throw new Rejection(409, "INBOUND_METER_IDENTIFIER_CONFLICT");
        }
    }

    /** {@inheritDoc} */
    @Override public Task lockTask(UUID tenant, UUID task) {
        byte[] key = bytes(tenant);
        var row = mapper.lockTask(key, task.toString());
        if (row == null) return null;
        String notice = mapper.lockNotice(key, row.noticeId());
        var template = mapper.readTemplate(key, row.templateId());
        return new Task(row.version(), row.status(), notice, template == null ? null : template.meteringStrategy(),
                template == null ? null : template.qualityStrategy());
    }

    /** {@inheritDoc} */
    @Override public Sample lockSample(UUID tenant, UUID task) {
        var row = mapper.lockSample(bytes(tenant), task.toString());
        return row == null ? null : new Sample(UUID.fromString(row.id()), row.version(), row.decision(), row.sampled());
    }

    /** {@inheritDoc} */
    @Override public void insertExternal(UUID tenant, UUID actor, Confirmation confirmation) {
        mapper.insertExternal(bytes(tenant), actor.toString(), confirmation.id().toString(),
                confirmation.taskId().toString(), confirmation.sampleId().toString(), confirmation.meteringNo(),
                confirmation.source().name(), confirmation.externalReference(), confirmation.netKg());
    }

    /** {@inheritDoc} */
    @Override public int advanceTask(UUID tenant, UUID actor, UUID task, long version) {
        return mapper.advanceTask(bytes(tenant), actor.toString(), task.toString(), version);
    }

    private static byte[] bytes(UUID tenant) {
        return ByteBuffer.allocate(16).putLong(tenant.getMostSignificantBits()).putLong(tenant.getLeastSignificantBits()).array();
    }
}
