package com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.GroundWeighingUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.ConfirmSteelMeasurementUseCase;
import com.appnexus.persistence.mybatis.GuardedScope;
import com.appnexus.persistence.mybatis.TenantAggregateScoped;
import java.math.BigDecimal;
import java.sql.Timestamp;
import org.apache.ibatis.annotations.*;

/** 现场地磅票据、作业锁和监卸状态，按三个独立操作许可执行。 */
@Mapper
@TenantAggregateScoped(requireTenantMatch=true,
        tables={"bw_inbound_task","bw_inbound_notice","bw_commodity_template","bw_metering_order",
                "bw_ground_weigh_ticket","bw_quality_order"},
        statements={"lockTask","taskRules","lockMeter","lockQuality","insertGross","insertGrossTicket","insertTareTicket",
                "updateTare","saveUnload","advance"})
public interface GroundWeighingMapper {
    String GROSS=GroundWeighingUseCase.GROSS, TARE=GroundWeighingUseCase.TARE, UNLOAD=GroundWeighingUseCase.UNLOAD;

    /** 已锁作业状态与可空监卸事实。 */
    record TaskRow(long version,String status,String noticeId,String templateId,String unload,
                   String evidence,BigDecimal deduction,Timestamp arrivedAt,Timestamp databaseNow) {}
    /** 已绑定模板与来源状态。 */
    record RuleRow(String noticeStatus,String meterRule,String qualityRule) {}
    /** 毛重单行和来源票证。 */
    record MeterRow(String id,long version,String status,BigDecimal grossKg,String station,Timestamp observedAt) {}
    /** 适用的当前采样。 */
    record QualityRow(String id,int version,String decision,boolean sampled) {}
    /** 统一票证写入参数。 */
    record TicketRow(String id,String task,String meter,String station,String number,Timestamp observedAt,BigDecimal kg) {}
    String TICKET_PREFIX = """
            INSERT INTO bw_ground_weigh_ticket(tenant_id,id,task_id,metering_id,reading_phase,station_code,ticket_no,
              observed_at,weight_kg,created_by,updated_by)
            VALUES(#{__verifiedTenant},UUID_TO_BIN(#{r.id}),UUID_TO_BIN(#{r.task}),UUID_TO_BIN(#{r.meter}),
            """;
    String TICKET_SUFFIX = """
            ,#{r.station},#{r.number},#{r.observedAt},#{r.kg},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
            """;

    /** 先锁作业，再读取模板、计量和质检。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions={GROSS,TARE,UNLOAD})
    @Select("""
            SELECT row_version version,status,BIN_TO_UUID(notice_id) noticeId,BIN_TO_UUID(template_id) templateId,
              unload_disposition unload,BIN_TO_UUID(unload_evidence_id) evidence,unload_deduction_kg deduction,
              arrived_at arrivedAt,CURRENT_TIMESTAMP(6) databaseNow
            FROM bw_inbound_task WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{task}) FOR UPDATE
            """)
    TaskRow lockTask(@Param("__verifiedTenant") byte[] tenant,@Param("task") String task);

    /** 按已锁作业的通知与模板固定引用读取有效规则。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions={GROSS,TARE,UNLOAD})
    @Select("""
            SELECT n.status noticeStatus,c.metering_strategy meterRule,c.quality_gate_strategy qualityRule
            FROM bw_inbound_notice n JOIN bw_commodity_template c ON c.tenant_id=n.tenant_id AND c.id=UUID_TO_BIN(#{template})
            WHERE n.tenant_id=#{__verifiedTenant} AND n.id=UUID_TO_BIN(#{notice})
              AND c.status IN ('PUBLISHED','DISABLED') AND c.stock_granularity='BULK' FOR SHARE
            """)
    RuleRow taskRules(@Param("__verifiedTenant") byte[] tenant,@Param("notice") String notice,@Param("template") String template);

    /** 防止同时创建计量，读取同作业真正登记的毛重票据。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions={GROSS,TARE,UNLOAD})
    @Select("""
            SELECT BIN_TO_UUID(m.id) id,m.row_version version,m.status,m.gross_kg grossKg,g.station_code station,g.observed_at observedAt
            FROM bw_metering_order m LEFT JOIN bw_ground_weigh_ticket g
              ON g.tenant_id=m.tenant_id AND g.metering_id=m.id AND g.reading_phase='GROSS'
            WHERE m.tenant_id=#{__verifiedTenant} AND m.task_id=UUID_TO_BIN(#{task}) FOR UPDATE
            """)
    MeterRow lockMeter(@Param("__verifiedTenant") byte[] tenant,@Param("task") String task);

    /** 皮重只采用锁内的现行质量事实。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions=TARE)
    @Select("""
            SELECT BIN_TO_UUID(id) id,version,decision,(sampled_at IS NOT NULL) sampled FROM bw_quality_order
            WHERE tenant_id=#{__verifiedTenant} AND active_task_id=UUID_TO_BIN(#{task}) FOR UPDATE
            """)
    QualityRow lockQuality(@Param("__verifiedTenant") byte[] tenant,@Param("task") String task);

    /** 独立计量单写入毛重。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions=GROSS)
    @Insert("""
            INSERT INTO bw_metering_order(tenant_id,id,task_id,metering_no,status,method,gross_kg,measurement_evidence,created_by,updated_by)
            VALUES(#{__verifiedTenant},UUID_TO_BIN(#{id}),UUID_TO_BIN(#{task}),#{number},'GROSS_RECORDED','SCALE_DIFFERENCE',
              #{kg},JSON_OBJECT('ingestion','OPERATOR_VERIFIED','unit','kg'),UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
            """)
    void insertGross(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("task") String task,
                     @Param("id") String id,@Param("number") String number,@Param("kg") BigDecimal kg);

    /** 同租户站点票据不能重复用于两个岗位阶段或两个作业。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions={GROSS,ConfirmSteelMeasurementUseCase.PERMISSION})
    @Insert(TICKET_PREFIX + "'GROSS'" + TICKET_SUFFIX)
    void insertGrossTicket(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("r") TicketRow ticket);

    /** 同一唯一约束防止皮重重复使用另一次毛重票。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions={TARE,ConfirmSteelMeasurementUseCase.PERMISSION})
    @Insert(TICKET_PREFIX + "'TARE'" + TICKET_SUFFIX)
    void insertTareTicket(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("r") TicketRow ticket);

    /** 正常差重确认净重，退车只留下原始读数。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions=TARE)
    @Update("""
            UPDATE bw_metering_order SET tare_kg=#{kg},actual_net_kg=#{net,jdbcType=DECIMAL},
              status=IF(#{net,jdbcType=DECIMAL} IS NULL,'RETURN_RECORDED','CONFIRMED'),row_version=row_version+1,
              tare_gate_quality_id=UUID_TO_BIN(#{quality,jdbcType=VARCHAR}),
              updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
            WHERE tenant_id=#{__verifiedTenant} AND task_id=UUID_TO_BIN(#{task}) AND id=UUID_TO_BIN(#{id})
              AND row_version=#{version} AND status='GROSS_RECORDED' AND method='SCALE_DIFFERENCE'
            """)
    int updateTare(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("task") String task,
                   @Param("id") String id,@Param("version") long version,@Param("kg") BigDecimal kg,
                   @Param("quality") String quality,@Param("net") BigDecimal net);

    /** 监卸由保管员独立确认，保存结论、现场扣量及可信审计。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions=UNLOAD)
    @Update("""
            UPDATE bw_inbound_task SET unload_disposition=#{conclusion},unload_deduction_kg=#{deduction},
              unload_evidence_id=UUID_TO_BIN(#{evidence}),unload_confirmed_by=UUID_TO_BIN(#{actor}),
              unload_confirmed_at=CURRENT_TIMESTAMP(6),status=#{state},queue_stage='WAIT_TARE',stage_started_at=CURRENT_TIMESTAMP(6),
              row_version=row_version+1,updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
            WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{task}) AND row_version=#{version}
              AND unload_confirmed_at IS NULL
            """)
    int saveUnload(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("task") String task,
                   @Param("version") long version,@Param("conclusion") String conclusion,@Param("deduction") BigDecimal deduction,
                   @Param("evidence") String evidence,@Param("state") String state);

    /** 保持作业行版本的比较并推进待办或关闭退车。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions={GROSS,TARE})
    @Update("""
            UPDATE bw_inbound_task SET status=#{state},queue_stage=#{queue,jdbcType=VARCHAR},
              stage_started_at=IF(#{queue,jdbcType=VARCHAR} IS NULL,NULL,CURRENT_TIMESTAMP(6)),row_version=row_version+1,
              updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
            WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{task}) AND row_version=#{version}
            """)
    int advance(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("task") String task,
                @Param("version") long version,@Param("state") String state,@Param("queue") String queue);
}
