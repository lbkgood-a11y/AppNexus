package com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.RegisterInboundSampleUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.DecideInboundQualityUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.InspectInboundSteelUseCase;
import com.appnexus.persistence.mybatis.GuardedScope;
import com.appnexus.persistence.mybatis.TenantAggregateScoped;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 在独立采样或判定操作许可内维护同一作业与质检版本。 */
@Mapper
@TenantAggregateScoped(requireTenantMatch=true, tables={"bw_inbound_task","bw_inbound_notice","bw_quality_order","bw_commodity_template","bw_receipt_order"},
        statements={"lockTask","lockNoticeStatus","lockActive","deactivate","insert","advanceTask",
                "readLabTemplate","decide","advanceAfterDecision","hasPendingPostedSample",
                "supportsSteelInspection","insertSteelInspection","advanceAfterSteelInspection"})
public interface InboundQualityOrderMapper {
    String STEEL = InspectInboundSteelUseCase.PERMISSION;
    /** 作业锁内的记录映射。 */
    record TaskRow(long rowVersion, String status, String noticeId, String templateId) {}
    /** 当前质检锁内的记录映射。 */
    record ActiveRow(String id, int qualityVersion, long rowVersion, String status) {}
    /** 作业固定模板的指标定义。 */
    record TemplateRow(int version, String mode, String metrics) {}

    /** @param tenant 可信租户键 @param task 作业 UUID @return 先加锁的作业和有效来源信息 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions={RegisterInboundSampleUseCase.PERMISSION,DecideInboundQualityUseCase.PERMISSION,STEEL})
    @Select("""
            SELECT row_version rowVersion,status,BIN_TO_UUID(notice_id) noticeId,BIN_TO_UUID(template_id) templateId
            FROM bw_inbound_task WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{task}) FOR UPDATE
            """)
    TaskRow lockTask(@Param("__verifiedTenant") byte[] tenant, @Param("task") String task);

    /** @param tenant 可信租户键 @param notice 已锁作业的来源 UUID @return 来源状态及共享锁 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions={RegisterInboundSampleUseCase.PERMISSION,DecideInboundQualityUseCase.PERMISSION,STEEL})
    @Select("SELECT status FROM bw_inbound_notice WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{notice}) FOR SHARE")
    String lockNoticeStatus(@Param("__verifiedTenant") byte[] tenant, @Param("notice") String notice);

    /** @param tenant 可信租户键 @param task 已加锁作业 UUID @return 当前质检版本或空 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions={RegisterInboundSampleUseCase.PERMISSION,DecideInboundQualityUseCase.PERMISSION,STEEL})
    @Select("""
            SELECT BIN_TO_UUID(id) id,version qualityVersion,row_version rowVersion,status
            FROM bw_quality_order WHERE tenant_id=#{__verifiedTenant}
              AND active_task_id=UUID_TO_BIN(#{task}) FOR UPDATE
            """)
    ActiveRow lockActive(@Param("__verifiedTenant") byte[] tenant, @Param("task") String task);

    /** 仅在原版本仍为现行时将其归档，保留历史检验结果。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=RegisterInboundSampleUseCase.PERMISSION)
    @Update("""
            UPDATE bw_quality_order SET is_active=0,row_version=row_version+1,
              updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
            WHERE tenant_id=#{__verifiedTenant} AND task_id=UUID_TO_BIN(#{task})
              AND id=UUID_TO_BIN(#{id}) AND row_version=#{version} AND is_active=1
            """)
    int deactivate(@Param("__verifiedTenant") byte[] tenant, @Param("actor") String actor,
                   @Param("task") String task, @Param("id") String id, @Param("version") long version);

    /** 写入已登记样品，采样时点和四列审计同事务保存。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=RegisterInboundSampleUseCase.PERMISSION)
    @Insert("""
            INSERT INTO bw_quality_order(tenant_id,id,task_id,quality_no,version,is_active,sample_barcode,
              sampled_at,status,created_by,updated_by)
            VALUES(#{__verifiedTenant},UUID_TO_BIN(#{id}),UUID_TO_BIN(#{task}),#{number},#{version},1,
              #{barcode},CURRENT_TIMESTAMP(6),'REGISTERED',UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
            """)
    void insert(@Param("__verifiedTenant") byte[] tenant, @Param("actor") String actor, @Param("task") String task,
                @Param("id") String id, @Param("number") String number, @Param("version") int version,
                @Param("barcode") String barcode);

    /** 登记后按作业原版本推进，复检使待入库状态回到有效门槛校验。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=RegisterInboundSampleUseCase.PERMISSION)
    @Update("""
            UPDATE bw_inbound_task SET row_version=row_version+1,
              stage_started_at=IF(status='READY_TO_RECEIVE',CURRENT_TIMESTAMP(6),stage_started_at),
              queue_stage=IF(status='READY_TO_RECEIVE','WAIT_QUALITY',queue_stage),
              status=IF(status='READY_TO_RECEIVE','WORKING',status),
              updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
            WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{task}) AND row_version=#{version}
            """)
    int advanceTask(@Param("__verifiedTenant") byte[] tenant, @Param("actor") String actor,
                    @Param("task") String task, @Param("version") long version);

    /** 获取作业已固定的模板，停用只限制新作业。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=DecideInboundQualityUseCase.PERMISSION)
    @Select("""
            SELECT version,quality_gate_strategy mode,CAST(JSON_EXTRACT(definition,'$.qualityMetrics') AS CHAR) metrics
            FROM bw_commodity_template WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{template})
              AND status IN ('PUBLISHED','DISABLED') FOR SHARE
            """)
    TemplateRow readLabTemplate(@Param("__verifiedTenant") byte[] tenant, @Param("template") String template);

    /** 同作业、现行、已采样并且行版本一致才可写结果。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=DecideInboundQualityUseCase.PERMISSION)
    @Update("""
            UPDATE bw_quality_order SET decision=#{decision},result_snapshot=#{snapshot},status='DECIDED',
              row_version=row_version+1,updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
            WHERE tenant_id=#{__verifiedTenant} AND task_id=UUID_TO_BIN(#{task}) AND id=UUID_TO_BIN(#{id})
              AND row_version=#{version} AND is_active=1 AND status='REGISTERED' AND sampled_at IS NOT NULL
            """)
    int decide(@Param("__verifiedTenant") byte[] tenant, @Param("actor") String actor, @Param("task") String task,
               @Param("id") String id, @Param("version") long version, @Param("decision") String decision,
               @Param("snapshot") String snapshot);

    /** 判定只推进版本及重审提示，不能直接执行计量或库存过账。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=DecideInboundQualityUseCase.PERMISSION)
    @Update("""
            UPDATE bw_inbound_task SET row_version=row_version+1,
              stage_started_at=IF(#{failed} AND status='READY_TO_RECEIVE',CURRENT_TIMESTAMP(6),stage_started_at),
              queue_stage=IF(#{failed} AND status='READY_TO_RECEIVE','WAIT_QUALITY',queue_stage),
              status=IF(#{failed} AND status='READY_TO_RECEIVE','WORKING',status),
              updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
            WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{task}) AND row_version=#{version}
            """)
    int advanceAfterDecision(@Param("__verifiedTenant") byte[] tenant, @Param("actor") String actor,
                             @Param("task") String task, @Param("version") long version, @Param("failed") boolean failed);

    /** 已完成作业只允许为确实存在的待化验过账版回填结果。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=DecideInboundQualityUseCase.PERMISSION)
    @Select("""
            SELECT 1 FROM bw_receipt_order WHERE tenant_id=#{__verifiedTenant}
              AND active_task_id=UUID_TO_BIN(#{task}) AND quality_id=UUID_TO_BIN(#{quality})
              AND status='POSTED' AND quality_state='PENDING_LAB' FOR UPDATE
            """)
    Integer hasPendingPostedSample(@Param("__verifiedTenant") byte[] tenant, @Param("task") String task,
                                   @Param("quality") String quality);

    /** 查作业固定的发布模板，以模板策略及库存粒度决定钢铁核验适用性。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions=STEEL)
    @Select("""
            SELECT 1 FROM bw_commodity_template WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{template})
              AND status IN ('PUBLISHED','DISABLED') AND stock_granularity='LPN'
              AND quality_gate_strategy='DOCUMENT_AND_APPEARANCE' FOR SHARE
            """)
    Integer supportsSteelInspection(@Param("__verifiedTenant") byte[] tenant,@Param("template") String template);

    /** 首次文档核验没有采样动作，证据由受控附件端口确认后写入。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions=STEEL)
    @Insert("""
            INSERT INTO bw_quality_order(tenant_id,id,task_id,quality_no,version,is_active,status,decision,
              result_snapshot,created_by,updated_by)
            VALUES(#{__verifiedTenant},UUID_TO_BIN(#{id}),UUID_TO_BIN(#{task}),#{number},1,1,'DECIDED',#{decision},
              #{snapshot},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
            """)
    void insertSteelInspection(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
                               @Param("task") String task,@Param("id") String id,@Param("number") String number,
                               @Param("decision") String decision,@Param("snapshot") String snapshot);

    /** 合格后进入计量待办；未通过留在质检。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions=STEEL)
    @Update("""
            UPDATE bw_inbound_task SET status='WORKING',queue_stage=IF(#{decision}='PASS','WAIT_GROSS','WAIT_QUALITY'),
              stage_started_at=CURRENT_TIMESTAMP(6),row_version=row_version+1,
              updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
            WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{task}) AND row_version=#{version}
              AND status IN ('ARRIVED','WORKING')
            """)
    int advanceAfterSteelInspection(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
                                    @Param("task") String task,@Param("version") long version,
                                    @Param("decision") String decision);
}
