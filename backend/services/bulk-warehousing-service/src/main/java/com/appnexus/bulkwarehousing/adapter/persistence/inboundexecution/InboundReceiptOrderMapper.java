package com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.CreateBulkReceiptDraftUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.CreateSteelReceiptDraftUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.OpenInboundReceiptUseCase;
import com.appnexus.persistence.mybatis.GuardedScope;
import com.appnexus.persistence.mybatis.TenantAggregateScoped;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.*;

/** 入库草稿的固定锁和 SQL，来源均按租户和权威关联读取。 */
@Mapper
@TenantAggregateScoped(requireTenantMatch=true,
        tables={"bw_inbound_task","bw_inbound_notice","bw_inbound_notice_line","bw_commodity_template",
                "bw_quality_order","bw_metering_order","bw_receipt_order","bw_receipt_order_line","bw_ground_weigh_ticket"},
        statements={"lockTask","lockNotice","lockNoticeLine","eligibleTemplate","lockSample","lockMeter",
                "insertHeader","insertLine","advanceTask","lockLatestReversed","lockSteelMeter",
                "lockActiveDraft","openDraft","completeHeader"})
public interface InboundReceiptOrderMapper {
    String PERMISSION = CreateBulkReceiptDraftUseCase.PERMISSION;
    String STEEL=CreateSteelReceiptDraftUseCase.PERMISSION;
    String OPEN=OpenInboundReceiptUseCase.PERMISSION;

    /** 作业内仍在编辑的入库草稿。 */
    record ActiveDraftRow(String id, String number, long version, boolean collecting) {}

    /** 草稿作业存储投影。 */
    record TaskRow(long rowVersion, String status, String noticeId, String templateId, String unload,
                   String evidence, BigDecimal siteKg) {}
    /** 可用于散货入库的已发布策略。 */
    record BulkTemplateRow(String qualityStrategy, String formula) {}
    /** 通知主表锁内投影。 */
    record NoticeRow(String warehouseId, String ownerId, String status) {}
    /** 通知行锁内投影。 */
    record NoticeLineRow(String productId, String identityKind, String identityId, long productVersion,
                         String baseUnitId, BigDecimal remaining) {}
    /** 同作业当前采样投影。 */
    record SampleRow(String id, int version, boolean sampled, String decision, String snapshot) {}
    /** 已确认来源凭证。 */
    record MeterRow(String id, long rowVersion, BigDecimal physicalNetKg, String method, String tareQualityId) {}
    /** 已确认钢铁双计量和当前 MTC 炉批。 */
    record SteelMeterRow(String id,long version,String qualityId,String heatNo,String method,BigDecimal physicalNetKg,
                         BigDecimal theoreticalKg,int packages) {}
    /** 草稿主表持久化参数。 */
    record HeaderInsert(String id, String taskId, String noticeId, String meteringId, String qualityId,
                        String number, String qualityState, BigDecimal physicalKg,BigDecimal accountingKg,BigDecimal deductionKg,String snapshot,
                        String replacesReceiptId) {}
    /** 草稿明细持久化参数。 */
    record LineInsert(String id, String receiptId, String noticeId, String noticeLineId, String locationId,
                      String batchNo, String unitId, BigDecimal quantity, BigDecimal netKg, String snapshot,
                      String lpn,String heatNo,String measurement) {}

    /** 固定在所有其他记录之前锁作业，监卸扣量不适用零扣量草稿。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions={PERMISSION,STEEL,OPEN})
    @Select("""
            SELECT row_version rowVersion,status,BIN_TO_UUID(notice_id) noticeId,BIN_TO_UUID(template_id) templateId,
              unload_disposition unload,BIN_TO_UUID(unload_evidence_id) evidence,COALESCE(unload_deduction_kg,0) siteKg
            FROM bw_inbound_task WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{task}) FOR UPDATE
            """)
    TaskRow lockTask(@Param("__verifiedTenant") byte[] tenant, @Param("task") String task);

    /** 读取已锁作业的货主、仓库及通知状态。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions={PERMISSION,STEEL,OPEN})
    @Select("""
            SELECT BIN_TO_UUID(warehouse_id) warehouseId,BIN_TO_UUID(owner_id) ownerId,status
            FROM bw_inbound_notice WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) FOR SHARE
            """)
    NoticeRow lockNotice(@Param("__verifiedTenant") byte[] tenant, @Param("id") String id);

    /** 只读取该通知所属的明细和当前余额。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions={PERMISSION,STEEL})
    @Select("""
            SELECT BIN_TO_UUID(product_id) productId,identity_kind identityKind,BIN_TO_UUID(identity_id) identityId,
              product_version productVersion,BIN_TO_UUID(base_unit_id) baseUnitId,
              approved_quantity-received_quantity remaining
            FROM bw_inbound_notice_line WHERE tenant_id=#{__verifiedTenant}
              AND notice_id=UUID_TO_BIN(#{notice}) AND id=UUID_TO_BIN(#{line}) FOR SHARE
            """)
    NoticeLineRow lockNoticeLine(@Param("__verifiedTenant") byte[] tenant, @Param("notice") String notice,
                                 @Param("line") String line);

    /** 读取散货的零扣量异步或强门槛公式策略，不采用草稿配置。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions={PERMISSION,STEEL,OPEN})
    @Select("""
            SELECT quality_gate_strategy qualityStrategy,
              IF(deduction_strategy='QUALITY_LINEAR',CAST(JSON_EXTRACT(definition,'$.deductionFormula') AS CHAR),NULL) formula
            FROM bw_commodity_template WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id})
              AND status IN ('PUBLISHED','DISABLED')
              AND stock_granularity='BULK'
              AND ((deduction_strategy='ZERO' AND quality_gate_strategy='SAMPLED'
                AND metering_strategy IN ('EXTERNAL_NET','SCALE_OR_EXTERNAL','SCALE','SCALE_DIFFERENCE'))
              OR (deduction_strategy='QUALITY_LINEAR' AND quality_gate_strategy='QUALIFIED_AND_UNLOADED'
                AND metering_strategy IN ('SCALE','SCALE_DIFFERENCE')
                AND JSON_TYPE(JSON_EXTRACT(definition,'$.deductionFormula'))='OBJECT')) FOR SHARE
            """)
    BulkTemplateRow eligibleTemplate(@Param("__verifiedTenant") byte[] tenant, @Param("id") String id);

    /** 草稿生成时读取实际现行样品和化验结论。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=PERMISSION)
    @Select("""
            SELECT BIN_TO_UUID(id) id,version,(sampled_at IS NOT NULL) sampled,decision,CAST(result_snapshot AS CHAR) snapshot
            FROM bw_quality_order WHERE tenant_id=#{__verifiedTenant}
              AND active_task_id=UUID_TO_BIN(#{task}) FOR UPDATE
            """)
    SampleRow lockSample(@Param("__verifiedTenant") byte[] tenant, @Param("task") String task);

    /** 草稿采用已确认的结构化外部凭证，或具有同作业双票据的地磅净重。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=PERMISSION)
    @Select("""
            SELECT BIN_TO_UUID(m.id) id,m.row_version rowVersion,m.actual_net_kg physicalNetKg,m.method,
              BIN_TO_UUID(m.tare_gate_quality_id) tareQualityId
            FROM bw_metering_order m WHERE m.tenant_id=#{__verifiedTenant} AND m.task_id=UUID_TO_BIN(#{task})
              AND m.status='CONFIRMED' AND ((m.method='EXTERNAL_NET' AND m.external_source IS NOT NULL)
                OR (m.method='SCALE_DIFFERENCE' AND m.actual_net_kg=m.gross_kg-m.tare_kg
                  AND (SELECT COUNT(*) FROM bw_ground_weigh_ticket g WHERE g.tenant_id=m.tenant_id
                    AND g.task_id=m.task_id AND g.metering_id=m.id)=2)) FOR UPDATE
            """)
    MeterRow lockMeter(@Param("__verifiedTenant") byte[] tenant, @Param("task") String task);

    /** 冲红重开必须关联最近一次冲红的同作业原单。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=PERMISSION)
    @Select("""
            SELECT BIN_TO_UUID(id) FROM bw_receipt_order WHERE tenant_id=#{__verifiedTenant} AND task_id=UUID_TO_BIN(#{task})
              AND status='REVERSED' ORDER BY updated_at DESC,id DESC LIMIT 1 FOR UPDATE
            """)
    String lockLatestReversed(@Param("__verifiedTenant") byte[] tenant, @Param("task") String task);

    /** 插入草稿，作业有效槽位和引用一致性由约束校验。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions={PERMISSION,STEEL})
    @Insert("""
            INSERT INTO bw_receipt_order(tenant_id,id,task_id,notice_id,metering_id,quality_id,receipt_no,
              status,quality_state,physical_net_kg,deduction_kg,accounting_weight_kg,posting_snapshot,created_by,updated_by,replaces_receipt_id)
            VALUES(#{__verifiedTenant},UUID_TO_BIN(#{row.id}),UUID_TO_BIN(#{row.taskId}),UUID_TO_BIN(#{row.noticeId}),
              UUID_TO_BIN(#{row.meteringId}),UUID_TO_BIN(#{row.qualityId}),#{row.number},'DRAFT',#{row.qualityState},
              #{row.physicalKg,jdbcType=DECIMAL},#{row.deductionKg},#{row.accountingKg},#{row.snapshot},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}),
              UUID_TO_BIN(#{row.replacesReceiptId,jdbcType=VARCHAR}))
            """)
    void insertHeader(@Param("__verifiedTenant") byte[] tenant, @Param("actor") String actor,
                      @Param("row") HeaderInsert row);

    /** 在同一事务插入散货或 LPN 明细。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions={PERMISSION,STEEL})
    @Insert("""
            INSERT INTO bw_receipt_order_line(tenant_id,id,receipt_order_id,notice_id,notice_line_id,location_id,
              batch_no,unit_id,inventory_quantity,accounting_weight_kg,item_snapshot,lpn,heat_no,measurement_snapshot,created_by,updated_by)
            VALUES(#{__verifiedTenant},UUID_TO_BIN(#{row.id}),UUID_TO_BIN(#{row.receiptId}),UUID_TO_BIN(#{row.noticeId}),
              UUID_TO_BIN(#{row.noticeLineId}),UUID_TO_BIN(#{row.locationId}),#{row.batchNo},UUID_TO_BIN(#{row.unitId}),
              #{row.quantity},#{row.netKg},#{row.snapshot},#{row.lpn,jdbcType=VARCHAR},#{row.heatNo,jdbcType=VARCHAR},
              #{row.measurement,jdbcType=VARCHAR},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
            """)
    void insertLine(@Param("__verifiedTenant") byte[] tenant, @Param("actor") String actor,
                    @Param("row") LineInsert row);

    /** 作业锁内读取有效草稿，计量为空表示仍在收集要素。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions={PERMISSION,STEEL,OPEN})
    @Select("""
            SELECT BIN_TO_UUID(id) id,receipt_no number,row_version version,metering_id IS NULL collecting
            FROM bw_receipt_order WHERE tenant_id=#{__verifiedTenant}
              AND active_task_id=UUID_TO_BIN(#{task}) AND status='DRAFT' FOR UPDATE
            """)
    ActiveDraftRow lockActiveDraft(@Param("__verifiedTenant") byte[] tenant,@Param("task") String task);

    /** 只写头记录，不触碰通知额度、库存或已确认的计量事实。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions=OPEN)
    @Insert("""
            INSERT INTO bw_receipt_order(tenant_id,id,task_id,notice_id,receipt_no,status,quality_state,
              accounting_weight_kg,posting_snapshot,created_by,updated_by)
            VALUES(#{__verifiedTenant},UUID_TO_BIN(#{id}),UUID_TO_BIN(#{task}),UUID_TO_BIN(#{notice}),
              #{number},'DRAFT','COLLECTING',0,JSON_OBJECT(),UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
            """)
    void openDraft(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("task") String task,
                   @Param("notice") String notice,@Param("id") String id,@Param("number") String number);

    /** 对原草稿写入已校验事实。已齐套草稿不得由收集入口覆盖。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions={PERMISSION,STEEL})
    @Update("""
            UPDATE bw_receipt_order SET metering_id=UUID_TO_BIN(#{row.meteringId}),quality_id=UUID_TO_BIN(#{row.qualityId}),
              physical_net_kg=#{row.physicalKg,jdbcType=DECIMAL},accounting_weight_kg=#{row.accountingKg},deduction_kg=#{row.deductionKg},
              quality_state=#{row.qualityState},posting_snapshot=#{row.snapshot},row_version=row_version+1,
              updated_at=CURRENT_TIMESTAMP(6),updated_by=UUID_TO_BIN(#{actor})
            WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{row.id}) AND task_id=UUID_TO_BIN(#{row.taskId})
              AND status='DRAFT' AND metering_id IS NULL
            """)
    int completeHeader(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("row") HeaderInsert row);

    /** 草稿保存只推进作业版本，继续等待入库过账。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions={PERMISSION,STEEL})
    @Update("""
            UPDATE bw_inbound_task SET status='READY_TO_RECEIVE',row_version=row_version+1,updated_by=UUID_TO_BIN(#{actor}),
              updated_at=CURRENT_TIMESTAMP(6)
            WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{task}) AND row_version=#{version}
              AND status IN ('READY_TO_RECEIVE','CORRECTION_REQUIRED')
            """)
    int advanceTask(@Param("__verifiedTenant") byte[] tenant, @Param("actor") String actor,
                    @Param("task") String task, @Param("version") long version);

    /** 必须采用同作业仍合格的钢铁核验与已确认计量并锁定版本。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions=STEEL)
    @Select("""
            SELECT BIN_TO_UUID(m.id) id,m.row_version version,BIN_TO_UUID(q.id) qualityId,
              JSON_UNQUOTE(JSON_EXTRACT(q.result_snapshot,'$.heatNo')) heatNo,m.method,
              m.actual_net_kg physicalNetKg,m.theoretical_kg theoreticalKg,m.package_count packages
            FROM bw_inbound_task t
            JOIN bw_commodity_template c ON c.tenant_id=t.tenant_id AND c.id=t.template_id
            JOIN bw_metering_order m ON m.tenant_id=t.tenant_id AND m.task_id=t.id
            JOIN bw_quality_order q ON q.tenant_id=m.tenant_id AND q.task_id=m.task_id AND q.id=m.tare_gate_quality_id
            WHERE t.tenant_id=#{__verifiedTenant} AND t.id=UUID_TO_BIN(#{task})
              AND c.status IN ('PUBLISHED','DISABLED') AND c.stock_granularity='LPN' AND c.metering_strategy='DUAL'
              AND c.quality_gate_strategy='DOCUMENT_AND_APPEARANCE' AND c.deduction_strategy='ZERO'
              AND q.is_active=1 AND q.status='DECIDED' AND q.decision='PASS'
              AND m.status='CONFIRMED' AND m.method IN ('SCALE_DIFFERENCE','THEORETICAL')
              AND m.package_count>0 AND m.theoretical_kg>0
            FOR UPDATE
            """)
    SteelMeterRow lockSteelMeter(@Param("__verifiedTenant") byte[] tenant,@Param("task") String task);
}
