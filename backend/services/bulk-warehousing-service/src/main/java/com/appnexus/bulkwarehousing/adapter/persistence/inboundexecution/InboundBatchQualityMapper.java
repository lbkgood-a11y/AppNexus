package com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution;

import com.appnexus.persistence.mybatis.GuardedScope;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;

/** 固定后台 SQL。每条语句强制绑定 Outbox 事务签发的租户及来源单标识。 */
@Mapper
@PlatformScoped(tables={"bw_inbound_task","bw_quality_order","bw_receipt_order","bw_receipt_order_line","bw_stock_receipt"})
public interface InboundBatchQualityMapper {
    String APPLY = """
            UPDATE bw_stock_receipt s
            JOIN bw_receipt_order_line l ON l.tenant_id=s.tenant_id AND l.id=s.receipt_id
            JOIN bw_receipt_order r ON r.tenant_id=l.tenant_id AND r.id=l.receipt_order_id
            JOIN bw_quality_order q ON q.tenant_id=r.tenant_id AND q.task_id=r.task_id AND q.id=r.quality_id
            JOIN bw_inbound_task t ON t.tenant_id=r.tenant_id AND t.id=r.task_id
            SET s.quality_state=IF(q.decision='PASS','QUALIFIED','QUALITY_FROZEN'),
              s.quality_version=q.version,s.quality_snapshot=q.result_snapshot,
              s.pending_quantity=IF(q.decision='PASS',0,s.pending_quantity),
              s.updated_by=NULL,s.updated_at=CURRENT_TIMESTAMP(6)
            WHERE s.tenant_id=UUID_TO_BIN(#{tenant}) AND r.status='POSTED' AND r.quality_state='PENDING_LAB'
              AND t.status='RECEIVED' AND q.is_active=1 AND q.status='DECIDED'
              AND q.decision IN ('PASS','FAIL') AND q.result_snapshot IS NOT NULL
              AND q.version>COALESCE(s.quality_version,0)
              AND s.quality_state='PENDING_LAB' AND s.reversed_at IS NULL
            """;

    /** 按入库单事件引用先锁定作业；库存状态在同一事务内更新。 */
    @GuardedScope(tenant="tenant", serviceOperation="system.warehousing.inboundquality.apply", serviceKeys={"subject"})
    @Select("""
            SELECT BIN_TO_UUID(t.id) FROM bw_inbound_task t WHERE t.tenant_id=UUID_TO_BIN(#{tenant})
              AND t.id=(SELECT r.task_id FROM bw_receipt_order r WHERE r.tenant_id=t.tenant_id AND r.id=UUID_TO_BIN(#{subject}))
            FOR UPDATE
            """)
    String lockByReceipt(@Param("tenant") String tenant, @Param("subject") String subject);

    /** 按当前判定事件引用先锁定来源作业。 */
    @GuardedScope(tenant="tenant", serviceOperation="system.warehousing.inboundquality.apply", serviceKeys={"subject"})
    @Select("""
            SELECT BIN_TO_UUID(t.id) FROM bw_inbound_task t WHERE t.tenant_id=UUID_TO_BIN(#{tenant})
              AND t.id=(SELECT q.task_id FROM bw_quality_order q WHERE q.tenant_id=t.tenant_id AND q.id=UUID_TO_BIN(#{subject}))
            FOR UPDATE
            """)
    String lockByQuality(@Param("tenant") String tenant, @Param("subject") String subject);

    /** 从该单据的实际现行质量证据推进库存，消费重放和过期版本均不更新。 */
    @GuardedScope(tenant="tenant", serviceOperation="system.warehousing.inboundquality.apply", serviceKeys={"subject"})
    @Update(APPLY+" AND r.id=UUID_TO_BIN(#{subject})")
    int applyByReceipt(@Param("tenant") String tenant, @Param("subject") String subject);

    /** 仅更新与当前质检版本具有真实入库关联的库存覆盖范围。 */
    @GuardedScope(tenant="tenant", serviceOperation="system.warehousing.inboundquality.apply", serviceKeys={"subject"})
    @Update(APPLY+" AND q.id=UUID_TO_BIN(#{subject})")
    int applyByQuality(@Param("tenant") String tenant, @Param("subject") String subject);
}
