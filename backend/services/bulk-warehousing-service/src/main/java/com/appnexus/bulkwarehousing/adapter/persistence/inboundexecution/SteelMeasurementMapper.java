package com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.ConfirmSteelMeasurementUseCase;
import com.appnexus.persistence.mybatis.GuardedScope;
import com.appnexus.persistence.mybatis.TenantAggregateScoped;
import java.math.BigDecimal;
import java.sql.Timestamp;
import org.apache.ibatis.annotations.*;

/** 同作业双计量证据存储。先锁作业，再核对实际生效的钢铁模板和合格质检。 */
@Mapper
@TenantAggregateScoped(requireTenantMatch=true,tables={"bw_inbound_task","bw_inbound_notice","bw_quality_order",
        "bw_commodity_template","bw_metering_order"},statements={"lockTask","lockEvidence","insert","advance"})
public interface SteelMeasurementMapper {
    String PERMISSION=ConfirmSteelMeasurementUseCase.PERMISSION;
    /** 作业行锁和 UTC 时间。 */
    record TaskRow(long version,String status,Timestamp arrivedAt,Timestamp databaseNow) {}
    /** 已锁检验和固定模板差率。 */
    record EvidenceRow(String inspectionId,String maxVariance,boolean bothRequired) {}
    /** 持久化已验证的计量值及来源。 */
    record MeasurementRow(String id,String taskId,String inspectionId,String number,String method,
                          BigDecimal gross,BigDecimal tare,BigDecimal physical,BigDecimal theoretical,
                          BigDecimal ratio,int packages) {}

    /** 可信租户的作业行锁必须先于其他记录。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions=PERMISSION)
    @Select("""
            SELECT row_version version,status,arrived_at arrivedAt,CURRENT_TIMESTAMP(6) databaseNow
            FROM bw_inbound_task WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{task}) FOR UPDATE
            """)
    TaskRow lockTask(@Param("__verifiedTenant") byte[] tenant,@Param("task") String task);

    /** 没有 MTC 与外观合格结果、模板或有效通知时不得确认计量。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions=PERMISSION)
    @Select("""
            SELECT BIN_TO_UUID(q.id) inspectionId,JSON_UNQUOTE(JSON_EXTRACT(c.definition,'$.maxVarianceRatio')) maxVariance,
              COALESCE(JSON_UNQUOTE(JSON_EXTRACT(c.definition,'$.requireBothWeights'))='true',FALSE) bothRequired
            FROM bw_inbound_task t
            JOIN bw_inbound_notice n ON n.tenant_id=t.tenant_id AND n.id=t.notice_id
            JOIN bw_commodity_template c ON c.tenant_id=t.tenant_id AND c.id=t.template_id
            JOIN bw_quality_order q ON q.tenant_id=t.tenant_id AND q.active_task_id=t.id
            WHERE t.tenant_id=#{__verifiedTenant} AND t.id=UUID_TO_BIN(#{task})
              AND n.status IN ('ISSUED','RECEIVING') AND c.status IN ('PUBLISHED','DISABLED')
              AND c.stock_granularity='LPN' AND c.metering_strategy='DUAL'
              AND c.quality_gate_strategy='DOCUMENT_AND_APPEARANCE' AND c.deduction_strategy='ZERO'
              AND q.status='DECIDED' AND q.decision='PASS'
              AND JSON_UNQUOTE(JSON_EXTRACT(q.result_snapshot,'$.appearanceVerified'))='true'
              AND JSON_UNQUOTE(JSON_EXTRACT(q.result_snapshot,'$.mtcVerified'))='true'
            FOR UPDATE
            """)
    EvidenceRow lockEvidence(@Param("__verifiedTenant") byte[] tenant,@Param("task") String task);

    /** 同一作业仅能确认一个计量，原始票据另由统一站点票据表防重。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions=PERMISSION)
    @Insert("""
            INSERT INTO bw_metering_order(tenant_id,id,task_id,metering_no,status,method,gross_kg,tare_kg,actual_net_kg,
              theoretical_kg,package_count,variance_ratio,tare_gate_quality_id,measurement_evidence,created_by,updated_by)
            VALUES(#{__verifiedTenant},UUID_TO_BIN(#{r.id}),UUID_TO_BIN(#{r.taskId}),#{r.number},'CONFIRMED',#{r.method},
              #{r.gross,jdbcType=DECIMAL},#{r.tare,jdbcType=DECIMAL},#{r.physical,jdbcType=DECIMAL},#{r.theoretical},
              #{r.packages},#{r.ratio,jdbcType=DECIMAL},UUID_TO_BIN(#{r.inspectionId}),
              JSON_OBJECT('ingestion','OPERATOR_VERIFIED','unit','kg','strategy','DUAL'),UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
            """)
    void insert(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("r") MeasurementRow row);

    /** 计量确认结束后推进同作业待入库节点。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions=PERMISSION)
    @Update("""
            UPDATE bw_inbound_task SET status='READY_TO_RECEIVE',queue_stage='WAIT_RECEIPT',stage_started_at=CURRENT_TIMESTAMP(6),
              row_version=row_version+1,updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
            WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{task}) AND row_version=#{version} AND status='WORKING'
            """)
    int advance(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("task") String task,
                @Param("version") long version);
}
