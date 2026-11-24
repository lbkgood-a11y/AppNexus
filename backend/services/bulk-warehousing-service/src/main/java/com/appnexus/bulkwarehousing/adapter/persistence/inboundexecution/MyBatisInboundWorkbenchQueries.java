package com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundWorkbenchUseCase.*;
import com.appnexus.bulkwarehousing.application.inboundexecution.InboundWorkbenchQueries;
import com.appnexus.platform.authorization.AccessPlan;
import com.appnexus.platform.authorization.ScopedWriteGate;
import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 将分页和计数绑定到同一只读事务、授权许可和 MySQL 快照。 */
public final class MyBatisInboundWorkbenchQueries implements InboundWorkbenchQueries {
    private final InboundWorkbenchMapper mapper;
    private final ScopedWriteGate gate;
    private final TransactionTemplate transaction;

    /** @param mapper 固定 SQL @param gate 公共事务许可 @param manager 组合应用主数据源事务管理器 */
    public MyBatisInboundWorkbenchQueries(InboundWorkbenchMapper mapper, ScopedWriteGate gate,
                                           PlatformTransactionManager manager) {
        this.mapper = mapper;
        this.gate = gate;
        transaction = new TransactionTemplate(manager);
        // 公共操作许可拒绝只读标志；此路径只执行固定 SELECT。
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    /** {@inheritDoc} */
    @Override public QueuePage queue(AccessPlan plan, Stage stage, UUID afterId, int limit, boolean unopened) {
        return transaction.execute(status -> {
            gate.authorizeOperation(plan);
            var tenant = plan.subject().tenantId();
            byte[] tenantBytes = ByteBuffer.allocate(16).putLong(tenant.getMostSignificantBits())
                    .putLong(tenant.getLeastSignificantBits()).array();
            var counts = new EnumMap<Stage, Long>(Stage.class);
            for (Stage value : Stage.values()) counts.put(value, 0L);
            for (var count : mapper.counts(tenantBytes,unopened)) counts.put(Stage.valueOf(count.stage()), count.total());
            var query = mapper.queue(tenantBytes, stage == null ? null : stage.name(),
                    afterId == null ? null : afterId.toString(), limit + 1, unopened);
            var rows = query.stream().limit(limit).map(row -> new QueueItem(UUID.fromString(row.taskId()),row.taskNo(),
                    Stage.valueOf(row.stage()), row.stageStartedAt() == null ? null : row.stageStartedAt().toInstant()))
                    .toList();
            return new QueuePage(rows, Map.copyOf(counts), query.size() > limit ? rows.getLast().taskId() : null);
        });
    }
}
