package com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution;

import com.appnexus.persistence.mybatis.GuardedScope;
import com.appnexus.persistence.mybatis.TenantAggregateScoped;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;
import com.appnexus.bulkwarehousing.api.inboundexecution.*;

/** 固定投影。三单均不选择司机、车辆、供应商、附件原文或身份引用，质检可盲检使用。 */
@Mapper
@TenantAggregateScoped(requireTenantMatch=true,tables={"bw_inbound_task","bw_quality_order","bw_metering_order",
        "bw_receipt_order","bw_receipt_order_line","bw_commodity_template","bw_inbound_notice"},statements={"listQuality","countQuality","readQuality","listMetering","countMetering","readMetering","listReceipt","countReceipt","readReceipt","receiptLines","labMetrics","optionsSAMPLE","optionsINSPECT","optionsGROSS","optionsEXTERNAL","optionsDUAL","optionsBULK","optionsLPN"})
public interface InboundDocumentMapper {
    /** 只为本次化验读取现行样品绑定的规则，公开适配器只投影代码及单位。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions=DecideInboundQualityUseCase.PERMISSION)
    @Select("""
            SELECT CAST(JSON_EXTRACT(c.definition,'$.qualityMetrics') AS CHAR)
            FROM bw_quality_order q JOIN bw_inbound_task t ON t.tenant_id=q.tenant_id AND t.id=q.task_id
            JOIN bw_commodity_template c ON c.tenant_id=t.tenant_id AND c.id=t.template_id
            WHERE q.tenant_id=#{__verifiedTenant} AND q.id=UUID_TO_BIN(#{id})
            AND q.is_active=1 AND q.status='REGISTERED' AND c.status IN ('PUBLISHED','DISABLED')
              AND c.quality_gate_strategy IN ('QUALIFIED_AND_UNLOADED','SAMPLED')
            """)
    String labMetrics(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

    /** 候选的安全字段投影，禁止带出物流身份或质检值。 */
    record TaskOptionRow(String taskId, String taskNo, long taskVersion, Long meteringVersion, String noticeId) {}

    String TASK_OPTIONS = """
            SELECT BIN_TO_UUID(t.id) taskId,t.task_no taskNo,t.row_version taskVersion,m.row_version meteringVersion,
              BIN_TO_UUID(t.notice_id) noticeId
            FROM bw_inbound_task t
            JOIN bw_inbound_notice n ON n.tenant_id=t.tenant_id AND n.id=t.notice_id
            JOIN bw_commodity_template c ON c.tenant_id=t.tenant_id AND c.id=t.template_id
            LEFT JOIN bw_quality_order q ON q.tenant_id=t.tenant_id AND q.active_task_id=t.id
            LEFT JOIN bw_metering_order m ON m.tenant_id=t.tenant_id AND m.task_id=t.id
            LEFT JOIN bw_receipt_order r ON r.tenant_id=t.tenant_id AND r.active_task_id=t.id
            WHERE t.tenant_id=#{__verifiedTenant} AND t.task_no LIKE #{prefix} ESCAPE '!'
              AND n.status IN ('ISSUED','RECEIVING') AND c.status IN ('PUBLISHED','DISABLED') AND
            """;
    String TASK_OPTION_LIMIT = " ORDER BY t.task_no LIMIT 20";

    /** 按该命令筛选候选，最终状态与版本仍由写事务复核。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=RegisterInboundSampleUseCase.PERMISSION)
    @Select(TASK_OPTIONS + "t.status IN ('ARRIVED','WORKING','READY_TO_RECEIVE','CORRECTION_REQUIRED') AND c.quality_gate_strategy IN ('SAMPLED','QUALIFIED_AND_UNLOADED') AND q.id IS NULL" + TASK_OPTION_LIMIT)
    List<TaskOptionRow> optionsSAMPLE(@Param("__verifiedTenant") byte[] tenant, @Param("prefix") String prefix);

    /** 按该命令筛选候选，最终状态与版本仍由写事务复核。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=InspectInboundSteelUseCase.PERMISSION)
    @Select(TASK_OPTIONS + "t.status IN ('ARRIVED','WORKING') AND c.stock_granularity='LPN' AND c.quality_gate_strategy='DOCUMENT_AND_APPEARANCE' AND q.id IS NULL" + TASK_OPTION_LIMIT)
    List<TaskOptionRow> optionsINSPECT(@Param("__verifiedTenant") byte[] tenant, @Param("prefix") String prefix);

    /** 按该命令筛选候选，最终状态与版本仍由写事务复核。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=GroundWeighingUseCase.GROSS)
    @Select(TASK_OPTIONS + "t.status IN ('ARRIVED','WORKING') AND c.stock_granularity='BULK' AND c.metering_strategy IN ('SCALE','SCALE_DIFFERENCE','SCALE_OR_EXTERNAL') AND m.id IS NULL" + TASK_OPTION_LIMIT)
    List<TaskOptionRow> optionsGROSS(@Param("__verifiedTenant") byte[] tenant, @Param("prefix") String prefix);

    /** 按该命令筛选候选，最终状态与版本仍由写事务复核。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=RegisterExternalMeasurementUseCase.PERMISSION)
    @Select(TASK_OPTIONS + "t.status='WORKING' AND c.metering_strategy IN ('EXTERNAL_NET','SCALE_OR_EXTERNAL') AND c.quality_gate_strategy='SAMPLED' AND q.sampled_at IS NOT NULL AND (q.decision IS NULL OR q.decision<>'FAIL') AND m.id IS NULL" + TASK_OPTION_LIMIT)
    List<TaskOptionRow> optionsEXTERNAL(@Param("__verifiedTenant") byte[] tenant, @Param("prefix") String prefix);

    /** 按该命令筛选候选，最终状态与版本仍由写事务复核。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=ConfirmSteelMeasurementUseCase.PERMISSION)
    @Select(TASK_OPTIONS + "t.status='WORKING' AND c.stock_granularity='LPN' AND c.metering_strategy='DUAL' AND c.quality_gate_strategy='DOCUMENT_AND_APPEARANCE' AND c.deduction_strategy='ZERO' AND q.status='DECIDED' AND q.decision='PASS' AND JSON_EXTRACT(q.result_snapshot,'$.mtcVerified')=TRUE AND JSON_EXTRACT(q.result_snapshot,'$.appearanceVerified')=TRUE AND m.id IS NULL" + TASK_OPTION_LIMIT)
    List<TaskOptionRow> optionsDUAL(@Param("__verifiedTenant") byte[] tenant, @Param("prefix") String prefix);

    /** 按该命令筛选候选，最终状态与版本仍由写事务复核。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=CreateBulkReceiptDraftUseCase.PERMISSION)
    @Select(TASK_OPTIONS + "t.status IN ('READY_TO_RECEIVE','CORRECTION_REQUIRED') AND c.stock_granularity='BULK' AND c.metering_strategy IN ('EXTERNAL_NET','SCALE_OR_EXTERNAL','SCALE','SCALE_DIFFERENCE') AND m.status='CONFIRMED' AND m.actual_net_kg IS NOT NULL AND q.sampled_at IS NOT NULL AND (q.decision IS NULL OR q.decision<>'FAIL') AND (c.quality_gate_strategy<>'QUALIFIED_AND_UNLOADED' OR q.decision='PASS') AND r.id IS NULL" + TASK_OPTION_LIMIT)
    List<TaskOptionRow> optionsBULK(@Param("__verifiedTenant") byte[] tenant, @Param("prefix") String prefix);

    /** 按该命令筛选候选，最终状态与版本仍由写事务复核。 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=CreateSteelReceiptDraftUseCase.PERMISSION)
    @Select(TASK_OPTIONS + "t.status='READY_TO_RECEIVE' AND c.stock_granularity='LPN' AND c.metering_strategy='DUAL' AND c.quality_gate_strategy='DOCUMENT_AND_APPEARANCE' AND c.deduction_strategy='ZERO' AND m.status='CONFIRMED' AND q.status='DECIDED' AND q.decision='PASS' AND r.id IS NULL" + TASK_OPTION_LIMIT)
    List<TaskOptionRow> optionsLPN(@Param("__verifiedTenant") byte[] tenant, @Param("prefix") String prefix);


    String QUALITY_FROM=" FROM bw_quality_order d JOIN bw_inbound_task t ON t.tenant_id=d.tenant_id AND t.id=d.task_id "+
            "WHERE d.tenant_id=#{__verifiedTenant}";
    String QUALITY_COLUMNS="BIN_TO_UUID(d.id) id,BIN_TO_UUID(d.task_id) taskId,d.quality_no number,d.status,d.row_version rowVersion,t.row_version taskVersion,d.version qualityVersion,d.is_active active,d.sample_barcode sampleBarcode,d.decision,d.sampled_at sampledAt,d.created_at createdAt";
    String QUALITY_FILTER=" AND (#{number,jdbcType=VARCHAR} IS NULL OR d.quality_no=#{number,jdbcType=VARCHAR})";

    /** 固定租户条件下的quality分页。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions="warehousing.inboundquality:list_documents")
    @Select("SELECT "+QUALITY_COLUMNS+QUALITY_FROM+QUALITY_FILTER+" ORDER BY d.created_at DESC,d.id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Map<String,Object>> listQuality(@Param("__verifiedTenant") byte[] tenant,@Param("number") String number,
                                     @Param("offset") int offset,@Param("limit") int limit);

    /** 与分页 SQL 共享筛选和可信租户。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions="warehousing.inboundquality:list_documents")
    @Select("SELECT COUNT(*)"+QUALITY_FROM+QUALITY_FILTER)
    long countQuality(@Param("__verifiedTenant") byte[] tenant,@Param("number") String number);

    /** 以单据标识定位详情，包括同作业历史版本。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions="warehousing.inboundquality:read_document")
    @Select("SELECT "+QUALITY_COLUMNS+",CAST(JSON_EXTRACT(d.result_snapshot,'$.metrics') AS CHAR) labMetrics,"
            +"JSON_UNQUOTE(JSON_EXTRACT(d.result_snapshot,'$.heatNo')) heatNo,"
            +"JSON_UNQUOTE(JSON_EXTRACT(d.result_snapshot,'$.mtcVerified')) mtcVerified,"
            +"JSON_UNQUOTE(JSON_EXTRACT(d.result_snapshot,'$.appearanceVerified')) appearanceVerified"
            +QUALITY_FROM+" AND d.id=UUID_TO_BIN(#{id})")
    Map<String,Object> readQuality(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

    String METERING_FROM=" FROM bw_metering_order d JOIN bw_inbound_task t ON t.tenant_id=d.tenant_id AND t.id=d.task_id "+
            "WHERE d.tenant_id=#{__verifiedTenant}";
    String METERING_COLUMNS="BIN_TO_UUID(d.id) id,BIN_TO_UUID(d.task_id) taskId,d.metering_no number,d.status,d.row_version rowVersion,t.row_version taskVersion,d.method,d.package_count packages,CAST(d.gross_kg AS CHAR) grossKg,CAST(d.tare_kg AS CHAR) tareKg,CAST(d.actual_net_kg AS CHAR) physicalNetKg,CAST(d.theoretical_kg AS CHAR) theoreticalKg,CAST(d.variance_ratio AS CHAR) varianceRatio,d.created_at createdAt";
    String METERING_FILTER=" AND (#{number,jdbcType=VARCHAR} IS NULL OR d.metering_no=#{number,jdbcType=VARCHAR})";

    /** 固定租户条件下的metering分页。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions="warehousing.inboundmetering:list_documents")
    @Select("SELECT "+METERING_COLUMNS+METERING_FROM+METERING_FILTER+" ORDER BY d.created_at DESC,d.id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Map<String,Object>> listMetering(@Param("__verifiedTenant") byte[] tenant,@Param("number") String number,
                                     @Param("offset") int offset,@Param("limit") int limit);

    /** 与分页 SQL 共享筛选和可信租户。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions="warehousing.inboundmetering:list_documents")
    @Select("SELECT COUNT(*)"+METERING_FROM+METERING_FILTER)
    long countMetering(@Param("__verifiedTenant") byte[] tenant,@Param("number") String number);

    /** 以单据标识定位详情，包括同作业历史版本。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions="warehousing.inboundmetering:read_document")
    @Select("SELECT "+METERING_COLUMNS+METERING_FROM+" AND d.id=UUID_TO_BIN(#{id})")
    Map<String,Object> readMetering(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

    String RECEIPT_FROM=" FROM bw_receipt_order d JOIN bw_inbound_task t ON t.tenant_id=d.tenant_id AND t.id=d.task_id "+
            "WHERE d.tenant_id=#{__verifiedTenant}";
    String RECEIPT_COLUMNS="BIN_TO_UUID(d.id) id,BIN_TO_UUID(d.task_id) taskId,d.receipt_no number,d.status,d.row_version rowVersion,t.row_version taskVersion,d.quality_state qualityState,CAST(d.physical_net_kg AS CHAR) physicalNetKg,CAST(d.accounting_weight_kg AS CHAR) accountingKg,CAST(d.deduction_kg AS CHAR) deductionKg,d.posted_at postedAt,d.created_at createdAt";
    String RECEIPT_FILTER=" AND (#{number,jdbcType=VARCHAR} IS NULL OR d.receipt_no=#{number,jdbcType=VARCHAR})";

    /** 固定租户条件下的receipt分页。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions="warehousing.receipt:list_documents")
    @Select("SELECT "+RECEIPT_COLUMNS+RECEIPT_FROM+RECEIPT_FILTER+" ORDER BY d.created_at DESC,d.id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Map<String,Object>> listReceipt(@Param("__verifiedTenant") byte[] tenant,@Param("number") String number,
                                     @Param("offset") int offset,@Param("limit") int limit);

    /** 与分页 SQL 共享筛选和可信租户。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions="warehousing.receipt:list_documents")
    @Select("SELECT COUNT(*)"+RECEIPT_FROM+RECEIPT_FILTER)
    long countReceipt(@Param("__verifiedTenant") byte[] tenant,@Param("number") String number);

    /** 以单据标识定位详情，包括同作业历史版本。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions="warehousing.receipt:read_document")
    @Select("SELECT "+RECEIPT_COLUMNS+",BIN_TO_UUID(d.notice_id) noticeId,m.row_version meteringVersion,c.stock_granularity stockGranularity,"
            +"c.metering_strategy meteringStrategy,c.quality_gate_strategy qualityStrategy,"
            +"t.status taskStatus,d.metering_id IS NULL collecting,"
            +"JSON_UNQUOTE(JSON_EXTRACT(d.posting_snapshot,'$.deduction.moistureKg')) moistureKg,"
            +"JSON_UNQUOTE(JSON_EXTRACT(d.posting_snapshot,'$.deduction.impurityKg')) impurityKg,"
            +"JSON_UNQUOTE(JSON_EXTRACT(d.posting_snapshot,'$.deduction.onSiteKg')) onSiteKg"
            + " FROM bw_receipt_order d JOIN bw_inbound_task t ON t.tenant_id=d.tenant_id AND t.id=d.task_id"
            + " JOIN bw_commodity_template c ON c.tenant_id=t.tenant_id AND c.id=t.template_id"
            + " LEFT JOIN bw_metering_order m ON m.tenant_id=t.tenant_id AND m.task_id=t.id AND m.status='CONFIRMED'"
            + " WHERE d.tenant_id=#{__verifiedTenant} AND d.id=UUID_TO_BIN(#{id})")
    Map<String,Object> readReceipt(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

    /** 仅从同一入库单读取授权明细。 */
    @GuardedScope(tenant="__verifiedTenant",operationPermissions="warehousing.receipt:read_document")
    @Select("""
            SELECT BIN_TO_UUID(id) id,lpn,heat_no heatNo,batch_no batchNo,
              JSON_UNQUOTE(JSON_EXTRACT(item_snapshot,'$.specification')) specification,
              JSON_UNQUOTE(JSON_EXTRACT(item_snapshot,'$.material')) material,
              JSON_UNQUOTE(JSON_EXTRACT(measurement_snapshot,'$.theoreticalKg')) theoreticalKg,
              CAST(accounting_weight_kg AS CHAR) accountingKg,CAST(inventory_quantity AS CHAR) inventoryQuantity
            FROM bw_receipt_order_line WHERE tenant_id=#{__verifiedTenant}
              AND receipt_order_id=UUID_TO_BIN(#{id}) ORDER BY id
            """)
    List<Map<String,Object>> receiptLines(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);
}
