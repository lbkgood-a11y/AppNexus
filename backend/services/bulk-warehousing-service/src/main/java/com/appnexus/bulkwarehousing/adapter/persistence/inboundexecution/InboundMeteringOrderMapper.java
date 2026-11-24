package com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.RegisterExternalMeasurementUseCase;
import com.appnexus.persistence.mybatis.GuardedScope;
import com.appnexus.persistence.mybatis.TenantAggregateScoped;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.*;

/** 外部净重登记使用的固定语句，许可及租户由公共守卫核验。 */
@Mapper
@TenantAggregateScoped(requireTenantMatch=true,
        tables={"bw_inbound_task","bw_inbound_notice","bw_commodity_template","bw_quality_order","bw_metering_order"},
        statements={"lockTask","lockNotice","readTemplate","lockSample","insertExternal","advanceTask"})
public interface InboundMeteringOrderMapper {
    /** 作业锁内记录。 */
    record TaskRow(long version, String status, String noticeId, String templateId) {}
    /** 已采用模板的策略键。 */
    record TemplateRow(String meteringStrategy, String qualityStrategy) {}
    /** 来源作业的现行样品。 */
    record SampleRow(String id, int version, String decision, boolean sampled) {}

    /** 先加作业锁，再读取其他记录，防止同时复检或再次计量。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=RegisterExternalMeasurementUseCase.PERMISSION)
    @Select("""
            SELECT row_version version,status,BIN_TO_UUID(notice_id) noticeId,BIN_TO_UUID(template_id) templateId
            FROM bw_inbound_task WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{task}) FOR UPDATE
            """)
    TaskRow lockTask(@Param("__verifiedTenant") byte[] tenant, @Param("task") String task);

    /** 读取已锁作业绑定的来源通知状态。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=RegisterExternalMeasurementUseCase.PERMISSION)
    @Select("SELECT status FROM bw_inbound_notice WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) FOR SHARE")
    String lockNotice(@Param("__verifiedTenant") byte[] tenant, @Param("id") String id);

    /** 已绑定的发布版本在停用后仍可执行，草稿不能参与。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=RegisterExternalMeasurementUseCase.PERMISSION)
    @Select("""
            SELECT metering_strategy meteringStrategy,quality_gate_strategy qualityStrategy
            FROM bw_commodity_template WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id})
              AND status IN ('PUBLISHED','DISABLED') FOR SHARE
            """)
    TemplateRow readTemplate(@Param("__verifiedTenant") byte[] tenant, @Param("id") String id);

    /** 读取实际采样时点与当前审核结论，未采样不产生有效的暂估门槛。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=RegisterExternalMeasurementUseCase.PERMISSION)
    @Select("""
            SELECT BIN_TO_UUID(id) id,version,decision,(sampled_at IS NOT NULL) sampled
            FROM bw_quality_order WHERE tenant_id=#{__verifiedTenant}
              AND active_task_id=UUID_TO_BIN(#{task}) FOR UPDATE
            """)
    SampleRow lockSample(@Param("__verifiedTenant") byte[] tenant, @Param("task") String task);

    /** 保存人工复核的整单凭证及明确的千克净重。唯一约束阻止另一个作业消费同一来源单。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=RegisterExternalMeasurementUseCase.PERMISSION)
    @Insert("""
            INSERT INTO bw_metering_order(tenant_id,id,task_id,metering_no,status,method,actual_net_kg,
              tare_gate_quality_id,measurement_evidence,external_source,external_reference,created_by,updated_by)
            VALUES(#{__verifiedTenant},UUID_TO_BIN(#{id}),UUID_TO_BIN(#{task}),#{number},'CONFIRMED','EXTERNAL_NET',
              #{net},UUID_TO_BIN(#{sample}),JSON_OBJECT('source',#{source},'reference',#{reference},
                'ingestion','OPERATOR_VERIFIED','unit','kg'),#{source},#{reference},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
            """)
    void insertExternal(@Param("__verifiedTenant") byte[] tenant, @Param("actor") String actor,
                        @Param("id") String id, @Param("task") String task, @Param("sample") String sample,
                        @Param("number") String number, @Param("source") String source,
                        @Param("reference") String reference, @Param("net") BigDecimal net);

    /** 同事务推进作业版本和入库待办，后续入库仍须重新核对采样与质量。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=RegisterExternalMeasurementUseCase.PERMISSION)
    @Update("""
            UPDATE bw_inbound_task SET status='READY_TO_RECEIVE',queue_stage='WAIT_RECEIPT',
              stage_started_at=CURRENT_TIMESTAMP(6),row_version=row_version+1,
              updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
            WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{task}) AND row_version=#{version} AND status='WORKING'
            """)
    int advanceTask(@Param("__verifiedTenant") byte[] tenant, @Param("actor") String actor,
                    @Param("task") String task, @Param("version") long version);
}
