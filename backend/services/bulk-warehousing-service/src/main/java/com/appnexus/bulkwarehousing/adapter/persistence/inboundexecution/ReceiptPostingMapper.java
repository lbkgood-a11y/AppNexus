package com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.PostInboundReceiptUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.ReverseInboundReceiptUseCase;
import com.appnexus.persistence.mybatis.GuardedScope;
import com.appnexus.persistence.mybatis.TenantAggregateScoped;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** 原子过账与冲红的固定 SQL，共享作业锁，各自使用独立操作许可。 */
@Mapper
@TenantAggregateScoped(requireTenantMatch=true,
        tables={"bw_inbound_task","bw_inbound_notice","bw_inbound_notice_line","bw_receipt_order",
                "bw_receipt_order_line","bw_metering_order","bw_quality_order","bw_commodity_template",
                "bw_stock_receipt","bw_receipt_execution","bw_ground_weigh_ticket"},
        statements={"lockTask","lockReceipt","findExecution","insertExecution","lockDraftLines","consumeLine",
                "advanceNotice","advanceMultiLineNotice","activeLpnExists","insertStock","markReceiptPosted","markTaskReceived",
                "findReversal","insertReversal","lockReversibleLines","reverseStock","restoreLine","restoreNotice",
                "markReceiptReversed","markTaskCorrectionRequired"})
public interface ReceiptPostingMapper {
    String POST = PostInboundReceiptUseCase.PERMISSION;
    String REVERSE = ReverseInboundReceiptUseCase.PERMISSION;

    /** 状态锁内映射。 */
    record ReceiptRow(long rowVersion, String status) {}
    /** 幂等回执映射。 */
    record ExecutionRow(String receiptId, String digest, long resultingVersion) {}
    /** 冲红只读原库存、通知和数量。 */
    record ReversalRow(String stockId, String noticeId, String noticeLineId, BigDecimal quantity) {}
    /** 来源草稿锁内、受控快照。 */
    record DraftLineRow(String lineId, String noticeId, String noticeLineId, String ownerId, String warehouseId,
                        String locationId, String productId, String identityKind, String identityId,
                        long productVersion, String unitId, String batch, BigDecimal quantity, String snapshot,
                        String qualityState, Integer qualityVersion, String qualitySnapshot,String lpn,
                        int expectedLines,int actualLines,BigDecimal headerKg,BigDecimal lineKg) {}
    /** 写库存账使用的归属、位置和数量。 */
    record StockRow(String id, String warehouse, String area, String location, long warehouseVersion,
                    long areaVersion, long locationVersion, String owner, String product, String identityKind,
                    String identity, String unit, String batch, BigDecimal quantity,
                    String qualityState, Integer qualityVersion, String qualitySnapshot,String lpn) {}

    /** 先锁作业，后续任何受控写路径均采用相同定序。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions={POST,REVERSE})
    @Select("SELECT row_version FROM bw_inbound_task WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{task}) FOR UPDATE")
    Long lockTask(@Param("__verifiedTenant") byte[] tenant, @Param("task") String task);

    /** 目标单必须属于同一锁内作业，历史单据可核对重试。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions={POST,REVERSE})
    @Select("""
            SELECT row_version rowVersion,status FROM bw_receipt_order WHERE tenant_id=#{__verifiedTenant}
              AND task_id=UUID_TO_BIN(#{task}) AND id=UUID_TO_BIN(#{id}) FOR UPDATE
            """)
    ReceiptRow lockReceipt(@Param("__verifiedTenant") byte[] tenant, @Param("task") String task, @Param("id") String id);

    /** 仅从锁内当前作业读取已提交的过账回执。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=POST)
    @Select("""
            SELECT BIN_TO_UUID(receipt_id) receiptId,LOWER(HEX(request_digest)) digest,resulting_version resultingVersion
            FROM bw_receipt_execution WHERE tenant_id=#{__verifiedTenant} AND task_id=UUID_TO_BIN(#{task})
              AND operation_code='POST' AND idempotency_key=#{key} FOR UPDATE
            """)
    ExecutionRow findExecution(@Param("__verifiedTenant") byte[] tenant, @Param("task") String task, @Param("key") String key);

    /** 与余额、库存、单据状态同提交。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=POST)
    @Insert("""
            INSERT INTO bw_receipt_execution(tenant_id,task_id,receipt_id,operation_code,idempotency_key,
              request_digest,resulting_version,created_by,updated_by)
            VALUES(#{__verifiedTenant},UUID_TO_BIN(#{task}),UUID_TO_BIN(#{receipt}),'POST',#{key},
              UNHEX(#{digest}),#{version},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
            """)
    void insertExecution(@Param("__verifiedTenant") byte[] tenant, @Param("actor") String actor,
                         @Param("task") String task, @Param("receipt") String receipt, @Param("key") String key,
                         @Param("digest") String digest, @Param("version") long version);

    /** 锁内复核当前同一质检版本已合格、来源未关闭、零扣量和原外部净重。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=POST)
    @Select("""
            SELECT BIN_TO_UUID(l.id) lineId,BIN_TO_UUID(n.id) noticeId,BIN_TO_UUID(nl.id) noticeLineId,
              BIN_TO_UUID(n.owner_id) ownerId,BIN_TO_UUID(n.warehouse_id) warehouseId,BIN_TO_UUID(l.location_id) locationId,
              BIN_TO_UUID(nl.product_id) productId,nl.identity_kind identityKind,BIN_TO_UUID(nl.identity_id) identityId,
              nl.product_version productVersion,BIN_TO_UUID(l.unit_id) unitId,l.batch_no batch,
              l.inventory_quantity quantity,CAST(l.item_snapshot AS CHAR) snapshot,
              IF(q.status='DECIDED','QUALIFIED','PENDING_LAB') qualityState,
              IF(q.status='DECIDED',q.version,NULL) qualityVersion,
              IF(q.status='DECIDED',CAST(q.result_snapshot AS CHAR),NULL) qualitySnapshot,l.lpn,
              IF(c.stock_granularity='BULK',1,m.package_count) expectedLines,
              (SELECT COUNT(*) FROM bw_receipt_order_line x
                WHERE x.tenant_id=r.tenant_id AND x.receipt_order_id=r.id) actualLines,
              r.accounting_weight_kg headerKg,l.accounting_weight_kg lineKg
            FROM bw_receipt_order r
            JOIN bw_inbound_task t ON t.tenant_id=r.tenant_id AND t.id=r.task_id
            JOIN bw_inbound_notice n ON n.tenant_id=r.tenant_id AND n.id=r.notice_id
            JOIN bw_receipt_order_line l ON l.tenant_id=r.tenant_id AND l.receipt_order_id=r.id
            JOIN bw_inbound_notice_line nl ON nl.tenant_id=l.tenant_id AND nl.notice_id=n.id AND nl.id=l.notice_line_id
            JOIN bw_quality_order q ON q.tenant_id=r.tenant_id AND q.task_id=r.task_id AND q.id=r.quality_id
            JOIN bw_metering_order m ON m.tenant_id=r.tenant_id AND m.task_id=r.task_id AND m.id=r.metering_id
            JOIN bw_commodity_template c ON c.tenant_id=t.tenant_id AND c.id=t.template_id
            WHERE r.tenant_id=#{__verifiedTenant} AND r.task_id=UUID_TO_BIN(#{task}) AND r.id=UUID_TO_BIN(#{id})
              AND t.status='READY_TO_RECEIVE' AND r.status='DRAFT' AND n.status IN ('ISSUED','RECEIVING')
              AND c.status IN ('PUBLISHED','DISABLED') AND COALESCE(t.unload_disposition,'')<>'RETURN'
              AND q.is_active=1 AND m.status='CONFIRMED' AND l.unit_id=nl.base_unit_id
              AND r.physical_net_kg <=> m.actual_net_kg
              AND ((c.stock_granularity='BULK' AND c.deduction_strategy='ZERO' AND r.deduction_kg=0
                AND COALESCE(t.unload_deduction_kg,0)=0 AND c.quality_gate_strategy='SAMPLED' AND q.sampled_at IS NOT NULL
                AND ((q.status='DECIDED' AND q.decision='PASS' AND q.result_snapshot IS NOT NULL)
                  OR (q.status='REGISTERED' AND q.decision IS NULL))
                AND ((m.method='EXTERNAL_NET' AND m.external_source IS NOT NULL
                    AND c.metering_strategy IN ('EXTERNAL_NET','SCALE_OR_EXTERNAL'))
                  OR (m.method='SCALE_DIFFERENCE' AND m.actual_net_kg=m.gross_kg-m.tare_kg
                    AND c.metering_strategy IN ('SCALE','SCALE_DIFFERENCE','SCALE_OR_EXTERNAL')
                    AND (SELECT COUNT(*) FROM bw_ground_weigh_ticket g WHERE g.tenant_id=m.tenant_id
                      AND g.task_id=m.task_id AND g.metering_id=m.id)=2))
                AND r.accounting_weight_kg=m.actual_net_kg AND l.lpn IS NULL)
              OR (c.stock_granularity='BULK' AND c.deduction_strategy='QUALITY_LINEAR'
                AND c.quality_gate_strategy='QUALIFIED_AND_UNLOADED' AND c.metering_strategy IN ('SCALE','SCALE_DIFFERENCE')
                AND q.status='DECIDED' AND q.decision='PASS' AND q.is_active=1 AND m.tare_gate_quality_id=q.id
                AND t.unload_disposition IN ('ACCEPTED','ACCEPTED_WITH_DEDUCTION') AND t.unload_evidence_id IS NOT NULL
                AND m.method='SCALE_DIFFERENCE' AND m.actual_net_kg=m.gross_kg-m.tare_kg AND l.lpn IS NULL
                AND (SELECT COUNT(*) FROM bw_ground_weigh_ticket g WHERE g.tenant_id=m.tenant_id
                  AND g.task_id=m.task_id AND g.metering_id=m.id)=2
                AND JSON_EXTRACT(r.posting_snapshot,'$.quality')=q.result_snapshot
                AND JSON_EXTRACT(r.posting_snapshot,'$.deductionFormula')=JSON_EXTRACT(c.definition,'$.deductionFormula')
                AND UUID_TO_BIN(JSON_UNQUOTE(JSON_EXTRACT(r.posting_snapshot,'$.unloadingEvidenceId')))=t.unload_evidence_id
                AND CAST(JSON_UNQUOTE(JSON_EXTRACT(r.posting_snapshot,'$.deduction.onSiteKg')) AS DECIMAL(18,4))=COALESCE(t.unload_deduction_kg,0)
                AND CAST(JSON_UNQUOTE(JSON_EXTRACT(r.posting_snapshot,'$.deduction.totalKg')) AS DECIMAL(18,4))=r.deduction_kg
                AND r.accounting_weight_kg=m.actual_net_kg-r.deduction_kg AND r.accounting_weight_kg>0)
              OR (c.stock_granularity='LPN' AND c.metering_strategy='DUAL' AND c.deduction_strategy='ZERO'
                AND r.deduction_kg=0 AND COALESCE(t.unload_deduction_kg,0)=0
                AND c.quality_gate_strategy='DOCUMENT_AND_APPEARANCE' AND q.status='DECIDED' AND q.decision='PASS'
                AND JSON_UNQUOTE(JSON_EXTRACT(q.result_snapshot,'$.appearanceVerified'))='true'
                AND JSON_UNQUOTE(JSON_EXTRACT(q.result_snapshot,'$.mtcVerified'))='true'
                AND l.heat_no=JSON_UNQUOTE(JSON_EXTRACT(q.result_snapshot,'$.heatNo')) AND l.lpn IS NOT NULL
                AND m.tare_gate_quality_id=q.id AND m.package_count>0 AND m.theoretical_kg>0
                AND ((m.method='THEORETICAL' AND r.accounting_weight_kg=m.theoretical_kg)
                  OR (m.method='SCALE_DIFFERENCE' AND r.accounting_weight_kg=m.actual_net_kg
                    AND m.actual_net_kg=m.gross_kg-m.tare_kg
                    AND (SELECT COUNT(*) FROM bw_ground_weigh_ticket g WHERE g.tenant_id=m.tenant_id
                      AND g.task_id=m.task_id AND g.metering_id=m.id)=2))))
            ORDER BY nl.id,l.id
            FOR UPDATE
            """)
    List<DraftLineRow> lockDraftLines(@Param("__verifiedTenant") byte[] tenant, @Param("task") String task,
                                     @Param("id") String id);

    /** 同一通知行按原库存单位扣减剩余额度。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=POST)
    @Update("""
            UPDATE bw_inbound_notice_line SET received_quantity=received_quantity+#{quantity},
              updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
            WHERE tenant_id=#{__verifiedTenant} AND notice_id=UUID_TO_BIN(#{notice}) AND id=UUID_TO_BIN(#{line})
              AND received_quantity+#{quantity}<=approved_quantity
            """)
    int consumeLine(@Param("__verifiedTenant") byte[] tenant, @Param("actor") String actor,
                    @Param("notice") String notice, @Param("line") String line, @Param("quantity") BigDecimal quantity);

    /** 维护通知生命周期及兼容历史单行头额度，多行通知头累计仍为空。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=POST)
    @Update("""
            UPDATE bw_inbound_notice SET status='RECEIVING',received_quantity=received_quantity+#{quantity},
              version=version+1,updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
            WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{notice}) AND status IN ('ISSUED','RECEIVING')
              AND (received_quantity IS NULL OR received_quantity+#{quantity}<=approved_quantity)
            """)
    int advanceNotice(@Param("__verifiedTenant") byte[] tenant, @Param("actor") String actor,
                      @Param("notice") String notice, @Param("quantity") BigDecimal quantity);

    /** 多行通知可能由不同基础单位组成，累计只保存在各通知行。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions=POST)
    @Update("""
            UPDATE bw_inbound_notice SET status='RECEIVING',version=version+1,
              updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
            WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{notice}) AND status IN ('ISSUED','RECEIVING')
              AND received_quantity IS NULL
            """)
    int advanceMultiLineNotice(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("notice") String notice);

    /** 既有历史库存也参与 LPN 检查；同批新记录并发由唯一约束兜底。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions=POST)
    @Select("""
            SELECT 1 FROM bw_stock_receipt WHERE tenant_id=#{__verifiedTenant} AND reversed_at IS NULL
              AND COALESCE(current_warehouse_id,origin_warehouse_id)=UUID_TO_BIN(#{warehouse})
              AND UPPER(serial)=UPPER(#{lpn}) AND inbound_quantity>shipped_quantity
            LIMIT 1 FOR UPDATE
            """)
    Integer activeLpnExists(@Param("__verifiedTenant") byte[] tenant,@Param("warehouse") String warehouse,
                            @Param("lpn") String lpn);

    /** 正库存沿用现有库存账，未登记货权不自动生效。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=POST)
    @Insert("""
            INSERT INTO bw_stock_receipt(tenant_id,receipt_id,inbound_quantity,pending_quantity,origin_warehouse_id,
              origin_owner_id,product_id,identity_kind,identity_id,unit_id,receipt_batch_no,current_warehouse_id,
              current_area_id,current_location_id,current_warehouse_version,current_area_version,current_location_version,
              quality_state,quality_version,quality_snapshot,serial,created_by,updated_by)
            VALUES(#{__verifiedTenant},UUID_TO_BIN(#{s.id}),#{s.quantity},
              IF(#{s.qualityState}='PENDING_LAB',#{s.quantity},0),UUID_TO_BIN(#{s.warehouse}),
              UUID_TO_BIN(#{s.owner}),UUID_TO_BIN(#{s.product}),#{s.identityKind},UUID_TO_BIN(#{s.identity}),
              UUID_TO_BIN(#{s.unit}),#{s.batch},UUID_TO_BIN(#{s.warehouse}),UUID_TO_BIN(#{s.area}),UUID_TO_BIN(#{s.location}),
              #{s.warehouseVersion},#{s.areaVersion},#{s.locationVersion},#{s.qualityState},
              #{s.qualityVersion,jdbcType=INTEGER},#{s.qualitySnapshot,jdbcType=VARCHAR},#{s.lpn,jdbcType=VARCHAR},
              UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
            """)
    void insertStock(@Param("__verifiedTenant") byte[] tenant, @Param("actor") String actor, @Param("s") StockRow stock);

    /** 已合格草稿原子推进并保存当次质量事实，过账时点由数据库生成。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=POST)
    @Update("""
            UPDATE bw_receipt_order SET status='POSTED',quality_state=#{qualityState},posted_at=CURRENT_TIMESTAMP(6),
              posting_snapshot=JSON_SET(posting_snapshot,'$.qualityState',#{qualityState}),row_version=row_version+1,
              updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
            WHERE tenant_id=#{__verifiedTenant} AND task_id=UUID_TO_BIN(#{task}) AND id=UUID_TO_BIN(#{id})
              AND status='DRAFT' AND row_version=#{version}
            """)
    int markReceiptPosted(@Param("__verifiedTenant") byte[] tenant, @Param("actor") String actor,
                          @Param("task") String task, @Param("id") String id, @Param("version") long version,
                          @Param("qualityState") String qualityState);

    /** 完成作业并移出等待队列。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=POST)
    @Update("""
            UPDATE bw_inbound_task SET status='RECEIVED',queue_stage=NULL,stage_started_at=NULL,
              row_version=row_version+1,updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
            WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{task})
              AND status='READY_TO_RECEIVE' AND row_version=#{version}
            """)
    int markTaskReceived(@Param("__verifiedTenant") byte[] tenant, @Param("actor") String actor,
                         @Param("task") String task, @Param("version") long version);

    /** 从同作业锁内读取已提交冲红回执。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=REVERSE)
    @Select("""
            SELECT BIN_TO_UUID(receipt_id) receiptId,LOWER(HEX(request_digest)) digest,resulting_version resultingVersion
            FROM bw_receipt_execution WHERE tenant_id=#{__verifiedTenant} AND task_id=UUID_TO_BIN(#{task})
              AND operation_code='REVERSE' AND idempotency_key=#{key} FOR UPDATE
            """)
    ExecutionRow findReversal(@Param("__verifiedTenant") byte[] tenant, @Param("task") String task, @Param("key") String key);

    /** 冲红成功后将唯一回执与余额及单据状态同事务提交。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=REVERSE)
    @Insert("""
            INSERT INTO bw_receipt_execution(tenant_id,task_id,receipt_id,operation_code,idempotency_key,
              request_digest,resulting_version,created_by,updated_by)
            VALUES(#{__verifiedTenant},UUID_TO_BIN(#{task}),UUID_TO_BIN(#{receipt}),'REVERSE',#{key},
              UNHEX(#{digest}),#{version},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
            """)
    void insertReversal(@Param("__verifiedTenant") byte[] tenant, @Param("actor") String actor,
                        @Param("task") String task, @Param("receipt") String receipt, @Param("key") String key,
                        @Param("digest") String digest, @Param("version") long version);

    /** 库存必须仍是同一笔原量、原仓和原库位，且没有后续记录更新、货权、待检或发出。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=REVERSE)
    @Select("""
            SELECT BIN_TO_UUID(s.receipt_id) stockId,BIN_TO_UUID(n.id) noticeId,BIN_TO_UUID(nl.id) noticeLineId,
              l.inventory_quantity quantity
            FROM bw_receipt_order r
            JOIN bw_inbound_task t ON t.tenant_id=r.tenant_id AND t.id=r.task_id
            JOIN bw_inbound_notice n ON n.tenant_id=r.tenant_id AND n.id=r.notice_id
            JOIN bw_receipt_order_line l ON l.tenant_id=r.tenant_id AND l.receipt_order_id=r.id
            JOIN bw_inbound_notice_line nl ON nl.tenant_id=l.tenant_id AND nl.notice_id=n.id AND nl.id=l.notice_line_id
            JOIN bw_stock_receipt s ON s.tenant_id=l.tenant_id AND s.receipt_id=l.id
            WHERE r.tenant_id=#{__verifiedTenant} AND r.task_id=UUID_TO_BIN(#{task}) AND r.id=UUID_TO_BIN(#{id})
              AND t.status='RECEIVED' AND r.status='POSTED' AND r.quality_state='QUALIFIED' AND n.status='RECEIVING'
              AND s.reversed_at IS NULL AND s.pending_quantity=0 AND s.shipped_quantity=0
              AND s.title_owner_id IS NULL AND s.title_effective_at IS NULL AND s.updated_at=s.created_at
              AND s.inbound_quantity=l.inventory_quantity AND s.current_location_id=l.location_id
              AND s.current_warehouse_id=s.origin_warehouse_id AND s.origin_warehouse_id=n.warehouse_id
              AND s.origin_owner_id=n.owner_id AND s.unit_id=l.unit_id AND s.unit_id=nl.base_unit_id
              AND (SELECT COUNT(*) FROM bw_receipt_order_line all_lines
                   WHERE all_lines.tenant_id=r.tenant_id AND all_lines.receipt_order_id=r.id)=1
            FOR UPDATE
            """)
    List<ReversalRow> lockReversibleLines(@Param("__verifiedTenant") byte[] tenant, @Param("task") String task,
                                          @Param("id") String id);

    /** 标记冲红并保留正向库存事实，不把逆向操作记作出库。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=REVERSE)
    @Update("""
            UPDATE bw_stock_receipt SET reversed_at=CURRENT_TIMESTAMP(6),updated_by=UUID_TO_BIN(#{actor}),
              updated_at=CURRENT_TIMESTAMP(6)
            WHERE tenant_id=#{__verifiedTenant} AND receipt_id=UUID_TO_BIN(#{id}) AND reversed_at IS NULL
              AND inbound_quantity=#{quantity} AND shipped_quantity=0 AND pending_quantity=0
            """)
    int reverseStock(@Param("__verifiedTenant") byte[] tenant, @Param("actor") String actor,
                     @Param("id") String id, @Param("quantity") BigDecimal quantity);

    /** 退回原通知明细占用的额度，禁止累计值不足时冲减。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=REVERSE)
    @Update("""
            UPDATE bw_inbound_notice_line SET received_quantity=received_quantity-#{quantity},
              updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
            WHERE tenant_id=#{__verifiedTenant} AND notice_id=UUID_TO_BIN(#{notice}) AND id=UUID_TO_BIN(#{line})
              AND received_quantity>=#{quantity}
            """)
    int restoreLine(@Param("__verifiedTenant") byte[] tenant, @Param("actor") String actor,
                    @Param("notice") String notice, @Param("line") String line, @Param("quantity") BigDecimal quantity);

    /** 退回兼容单行通知头累计，多行通知头仍为空。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=REVERSE)
    @Update("""
            UPDATE bw_inbound_notice SET received_quantity=received_quantity-#{quantity},version=version+1,
              updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
            WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{notice}) AND status='RECEIVING'
              AND (received_quantity IS NULL OR received_quantity>=#{quantity})
            """)
    int restoreNotice(@Param("__verifiedTenant") byte[] tenant, @Param("actor") String actor,
                      @Param("notice") String notice, @Param("quantity") BigDecimal quantity);

    /** 保留原入库事实，只推进冲红状态及行版本。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=REVERSE)
    @Update("""
            UPDATE bw_receipt_order SET status='REVERSED',row_version=row_version+1,
              updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
            WHERE tenant_id=#{__verifiedTenant} AND task_id=UUID_TO_BIN(#{task}) AND id=UUID_TO_BIN(#{id})
              AND status='POSTED' AND row_version=#{version}
            """)
    int markReceiptReversed(@Param("__verifiedTenant") byte[] tenant, @Param("actor") String actor,
                            @Param("task") String task, @Param("id") String id, @Param("version") long version);

    /** 冲红后将作业移回待处理状态，历史入库单槽位已释放。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=REVERSE)
    @Update("""
            UPDATE bw_inbound_task SET status='CORRECTION_REQUIRED',queue_stage='WAIT_RECEIPT',
              stage_started_at=CURRENT_TIMESTAMP(6),row_version=row_version+1,
              updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
            WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{task}) AND status='RECEIVED' AND row_version=#{version}
            """)
    int markTaskCorrectionRequired(@Param("__verifiedTenant") byte[] tenant, @Param("actor") String actor,
                                   @Param("task") String task, @Param("version") long version);
}
