package com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundWorkbenchDetailUseCase;
import com.appnexus.persistence.mybatis.GuardedScope;
import com.appnexus.persistence.mybatis.TenantAggregateScoped;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/** 工作台详情固定字段投影。卡片和身份须由不同操作许可授权。 */
@Mapper
@TenantAggregateScoped(requireTenantMatch=true,
        tables={"bw_inbound_task","bw_commodity_template","bw_inbound_notice","bw_quality_order",
                "bw_metering_order","bw_receipt_order","bw_receipt_order_line","bw_stock_receipt"},
        statements={"task","lockTicket","identity","quality","metering","receipt","lines"})
public interface InboundWorkbenchDetailMapper {
    String DETAIL=ReadInboundWorkbenchDetailUseCase.DETAIL,IDENTITY=ReadInboundWorkbenchDetailUseCase.IDENTITY,
            QUALITY=ReadInboundWorkbenchDetailUseCase.QUALITY,METER=ReadInboundWorkbenchDetailUseCase.METER,
            RECEIPT=ReadInboundWorkbenchDetailUseCase.RECEIPT;
    String BLIND = """
            (c.quality_gate_strategy IN ('SAMPLED','QUALIFIED_AND_UNLOADED')
              AND NOT (q.status <=> 'DECIDED'))
            """;
    String TASK = """
            FROM bw_inbound_task t JOIN bw_commodity_template c ON c.tenant_id=t.tenant_id AND c.id=t.template_id
            LEFT JOIN bw_quality_order q ON q.tenant_id=t.tenant_id AND q.active_task_id=t.id
            """;

    /** 基础作业每次重读盲检标记。 */
    record TaskRow(long version,String stage,String status,boolean blind,String unload,String taskNo) {}

    /** @param tenant 可信租户 @param task 作业 UUID @return 最小作业基础字段 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions=DETAIL)
    @Select("SELECT t.row_version version,t.queue_stage stage,t.status,"+BLIND+" blind,t.unload_disposition unload,t.task_no taskNo "+
            TASK+" WHERE t.tenant_id=#{__verifiedTenant} AND t.id=UUID_TO_BIN(#{task})")
    TaskRow task(@Param("__verifiedTenant") byte[] tenant,@Param("task") String task);

    /** @param tenant 已验证租户 @param task 作业 UUID @return 锁定作业票快照，直到公共输出提交 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions=com.appnexus.bulkwarehousing.api.inboundexecution.InboundSiteCommands.PRINT)
    @Select("""
            SELECT row_version AS version,status,task_no AS taskNo FROM bw_inbound_task
            WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{task}) FOR UPDATE
            """)
    com.appnexus.bulkwarehousing.application.inboundexecution.InboundWorkbenchDetailQueries.Ticket lockTicket(
        @Param("__verifiedTenant") byte[] tenant,@Param("task") String task);

    /** 身份与源司机仅在非盲检阶段选择，返回之前仍须做字段保护。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions=IDENTITY)
    @Select("""
            SELECT t.vehicle_plate vehiclePlate,t.waybill_no waybillNo,n.driver_name driverName,BIN_TO_UUID(n.owner_id) ownerId
            """+TASK+"""
            JOIN bw_inbound_notice n ON n.tenant_id=t.tenant_id AND n.id=t.notice_id
            WHERE t.tenant_id=#{__verifiedTenant} AND t.id=UUID_TO_BIN(#{task}) AND NOT
            """+BLIND)
    Map<String,Object> identity(@Param("__verifiedTenant") byte[] tenant,@Param("task") String task);

    /** 不返回可关联车辆、司机、货主或附件的原始质检快照。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions=QUALITY)
    @Select("""
            SELECT BIN_TO_UUID(id) id,quality_no qualityNo,sample_barcode sampleBarcode,status,decision,
              version qualityVersion,row_version rowVersion
            FROM bw_quality_order WHERE tenant_id=#{__verifiedTenant} AND active_task_id=UUID_TO_BIN(#{task})
            """)
    Map<String,Object> quality(@Param("__verifiedTenant") byte[] tenant,@Param("task") String task);

    /** 不包含抓拍、站点或外部票号，公斤值使用精确十进制文本。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions=METER)
    @Select("""
            SELECT BIN_TO_UUID(id) id,metering_no meteringNo,status,method,row_version rowVersion,package_count packages,
              CAST(gross_kg AS CHAR) grossKg,CAST(tare_kg AS CHAR) tareKg,CAST(actual_net_kg AS CHAR) physicalNetKg,
              CAST(theoretical_kg AS CHAR) theoreticalKg,CAST(variance_ratio AS CHAR) varianceRatio
            FROM bw_metering_order WHERE tenant_id=#{__verifiedTenant} AND task_id=UUID_TO_BIN(#{task})
            """)
    Map<String,Object> metering(@Param("__verifiedTenant") byte[] tenant,@Param("task") String task);

    /** 入库卡片区分原单事实和库存当前质量。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions=RECEIPT)
    @Select("""
            SELECT BIN_TO_UUID(r.id) id,r.receipt_no receiptNo,r.status,r.row_version rowVersion,
              CAST(r.deduction_kg AS CHAR) deductionKg,CAST(r.accounting_weight_kg AS CHAR) accountingKg,
              COALESCE((SELECT s.quality_state FROM bw_receipt_order_line l JOIN bw_stock_receipt s
                ON s.tenant_id=l.tenant_id AND s.receipt_id=l.id WHERE l.tenant_id=r.tenant_id
                AND l.receipt_order_id=r.id AND s.reversed_at IS NULL ORDER BY l.id LIMIT 1),r.quality_state) qualityState
            FROM bw_receipt_order r WHERE r.tenant_id=#{__verifiedTenant} AND r.active_task_id=UUID_TO_BIN(#{task})
            """)
    Map<String,Object> receipt(@Param("__verifiedTenant") byte[] tenant,@Param("task") String task);

    /** 固定同一作业的当前单明细，不传原始 JSON 快照。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions=RECEIPT)
    @Select("""
            SELECT BIN_TO_UUID(l.id) id,l.lpn,l.heat_no heatNo,l.batch_no batchNo,
              JSON_UNQUOTE(JSON_EXTRACT(l.item_snapshot,'$.specification')) specification,
              JSON_UNQUOTE(JSON_EXTRACT(l.item_snapshot,'$.material')) material,
              CAST(l.accounting_weight_kg AS CHAR) accountingKg
            FROM bw_receipt_order_line l JOIN bw_receipt_order r ON r.tenant_id=l.tenant_id AND r.id=l.receipt_order_id
            WHERE l.tenant_id=#{__verifiedTenant} AND r.active_task_id=UUID_TO_BIN(#{task}) ORDER BY l.id
            """)
    List<Map<String,Object>> lines(@Param("__verifiedTenant") byte[] tenant,@Param("task") String task);
}
