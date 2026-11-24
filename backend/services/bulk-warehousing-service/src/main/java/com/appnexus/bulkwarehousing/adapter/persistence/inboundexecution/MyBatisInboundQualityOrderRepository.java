package com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution;

import com.appnexus.bulkwarehousing.application.inboundexecution.InboundQualityOrderRepository;
import com.appnexus.bulkwarehousing.domain.inboundexecution.LabQualityRules.Metric;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.authorization.AccessPlan;
import com.appnexus.platform.authorization.ScopedWriteGate;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 先锁作业，再查来源及现行质检，租户和操作许可由公共守卫复核。 */
public final class MyBatisInboundQualityOrderRepository implements InboundQualityOrderRepository {
    private final InboundQualityOrderMapper mapper;
    private final ScopedWriteGate gate;
    private final TransactionTemplate transaction;
    private final JsonMapper json = JsonMapper.builder().build();

    /** @param mapper 固定 SQL @param gate 公共操作许可 @param manager 同一数据源事务管理器 */
    public MyBatisInboundQualityOrderRepository(InboundQualityOrderMapper mapper, ScopedWriteGate gate,
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
            throw new Rejection(409, "INBOUND_SAMPLE_IDENTIFIER_CONFLICT");
        }
    }

    /** {@inheritDoc} */
    @Override public Task lockTask(UUID tenant, UUID task) {
        var key = bytes(tenant);
        var row = mapper.lockTask(key, task.toString());
        return row == null ? null : new Task(row.rowVersion(), row.status(),
                mapper.lockNoticeStatus(key, row.noticeId()), UUID.fromString(row.templateId()));
    }

    /** {@inheritDoc} */
    @Override public Active lockActive(UUID tenant, UUID task) {
        var row = mapper.lockActive(bytes(tenant), task.toString());
        return row == null ? null : new Active(UUID.fromString(row.id()), row.qualityVersion(), row.rowVersion(), row.status());
    }

    /** {@inheritDoc} */
    @Override public int deactivate(UUID tenant, UUID actor, UUID task, Active active) {
        return mapper.deactivate(bytes(tenant), actor.toString(), task.toString(), active.id().toString(), active.rowVersion());
    }

    /** {@inheritDoc} */
    @Override public void insert(UUID tenant, UUID actor, Sample sample) {
        mapper.insert(bytes(tenant), actor.toString(), sample.taskId().toString(), sample.id().toString(),
                sample.number(), sample.version(), sample.barcode());
    }

    /** {@inheritDoc} */
    @Override public int advanceTask(UUID tenant, UUID actor, UUID task, long version) {
        return mapper.advanceTask(bytes(tenant), actor.toString(), task.toString(), version);
    }

    /** {@inheritDoc} */
    @Override public LabTemplate readLabTemplate(UUID tenant, UUID template) {
        var row = mapper.readLabTemplate(bytes(tenant), template.toString());
        if (row == null || row.metrics() == null) return null;
        try {
            Map<String, Metric> metrics = json.readValue(row.metrics(), new TypeReference<>() {});
            return new LabTemplate(row.version(), row.mode(), metrics);
        } catch (JacksonException e) {
            throw new Rejection(409, "INBOUND_LAB_TEMPLATE_UNAVAILABLE");
        }
    }

    /** {@inheritDoc} */
    @Override public int decide(UUID tenant, UUID actor, UUID task, Active active, LabDecision decision) {
        return mapper.decide(bytes(tenant), actor.toString(), task.toString(), active.id().toString(),
                active.rowVersion(), decision.decision(), json.writeValueAsString(decision));
    }

    /** {@inheritDoc} */
    @Override public int advanceAfterDecision(UUID tenant, UUID actor, UUID task, long version, boolean failed) {
        return mapper.advanceAfterDecision(bytes(tenant), actor.toString(), task.toString(), version, failed);
    }

    /** {@inheritDoc} */
    @Override public boolean hasPendingPostedSample(UUID tenant, UUID task, UUID quality) {
        return mapper.hasPendingPostedSample(bytes(tenant), task.toString(), quality.toString()) != null;
    }

    /** {@inheritDoc} */
    @Override public boolean supportsSteelInspection(UUID tenant,UUID template) {
        return mapper.supportsSteelInspection(bytes(tenant),template.toString())!=null;
    }

    /** {@inheritDoc} */
    @Override public void insertSteelInspection(UUID tenant,UUID actor,UUID task,UUID quality,String qualityNo,
                                                String decision,SteelEvidence evidence) {
        mapper.insertSteelInspection(bytes(tenant),actor.toString(),task.toString(),quality.toString(),
                qualityNo,decision,json.writeValueAsString(evidence));
    }

    /** {@inheritDoc} */
    @Override public int advanceAfterSteelInspection(UUID tenant,UUID actor,UUID task,long taskVersion,String decision) {
        return mapper.advanceAfterSteelInspection(bytes(tenant),actor.toString(),task.toString(),taskVersion,decision);
    }

    private static byte[] bytes(UUID tenant) {
        return ByteBuffer.allocate(16).putLong(tenant.getMostSignificantBits()).putLong(tenant.getLeastSignificantBits()).array();
    }
}
