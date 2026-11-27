package com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundWorkbenchUseCase;
import com.appnexus.persistence.mybatis.GuardedScope;
import com.appnexus.persistence.mybatis.TenantAggregateScoped;
import java.sql.Timestamp;
import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundWorkbenchUseCase.TaskStatus;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 共享租户与阶段谓词的队列数据和计数。身份及可反查身份的字段不进入投影。 */
@Mapper
@TenantAggregateScoped(requireTenantMatch=true, tables={"bw_inbound_task", "bw_inbound_notice", "bw_receipt_order"},
        statements={"queue", "counts", "listTasks"})
public interface InboundWorkbenchMapper {
    String ACTIVE = """
            FROM bw_inbound_task t
            JOIN bw_inbound_notice n ON n.tenant_id=t.tenant_id AND n.id=t.notice_id
            WHERE t.tenant_id=#{__verifiedTenant} AND n.status IN ('ISSUED','RECEIVING')
              AND t.queue_stage IN ('WAIT_QUALITY','WAIT_GROSS','UNLOADING','WAIT_TARE','WAIT_RECEIPT')
              AND (#{unopened}=FALSE OR NOT EXISTS (SELECT 1 FROM bw_receipt_order r
                WHERE r.tenant_id=t.tenant_id AND r.active_task_id=t.id))
            """;

    /** 安全台账投影的数据库映射。 */
    record TaskLedgerRow(String id, String number, String status, String stage, Timestamp arrivedAt,
                         String createdBy, Timestamp createdAt, String updatedBy, Timestamp updatedAt) {}

    /**
     * @param tenant 可信租户二进制 UUID
     * @param number 可选精确单号
     * @param status 可选生命周期状态，缺省时包含全部状态
     * @param afterId 排他续查标识
     * @param limit 页大小加一
     * @return 固定字段投影，包含历史记录
     */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=ReadInboundWorkbenchUseCase.LIST_PERMISSION)
    @Select("""
            <script>
            SELECT BIN_TO_UUID(id) id,task_no number,status,queue_stage stage,arrived_at arrivedAt,
                BIN_TO_UUID(created_by) createdBy,created_at createdAt,
                BIN_TO_UUID(updated_by) updatedBy,updated_at updatedAt
            FROM bw_inbound_task WHERE tenant_id=#{__verifiedTenant}
            <if test="number != null">AND task_no=#{number}</if>
            <if test="status != null">AND status=#{status}</if>
            <if test="afterId != null">AND id &gt; UUID_TO_BIN(#{afterId})</if>
            ORDER BY id LIMIT #{limit}
            </script>
            """)
    List<TaskLedgerRow> listTasks(@Param("__verifiedTenant") byte[] tenant, @Param("number") String number,
                                  @Param("status") TaskStatus status, @Param("afterId") String afterId,
                                  @Param("limit") int limit);

    /** 只读队列行映射。 */
    record QueueRow(String taskId, String taskNo, String stage, Timestamp stageStartedAt) {}
    /** 只读计数映射。 */
    record StageCount(String stage, long total) {}

    /**
     * @param tenant 可信租户二进制 UUID
     * @param stage 可选队列节点
     * @param afterId 排他续查 UUID；首屏为空
     * @param limit 查询容量，应用页大小加 1 行
     * @return 按主键排序的队列数据
     */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=ReadInboundWorkbenchUseCase.PERMISSION)
    @Select("""
            <script>
            SELECT BIN_TO_UUID(t.id) taskId,t.task_no taskNo,t.queue_stage stage,t.stage_started_at stageStartedAt
            """ + ACTIVE + """
            <if test="stage != null">AND t.queue_stage=#{stage}</if>
            <if test="afterId != null">AND t.id &gt; UUID_TO_BIN(#{afterId})</if>
            ORDER BY t.id LIMIT #{limit}
            </script>
            """)
    List<QueueRow> queue(@Param("__verifiedTenant") byte[] tenant, @Param("stage") String stage,
                         @Param("afterId") String afterId, @Param("limit") int limit, @Param("unopened") boolean unopened);

    /** @param tenant 可信租户二进制 UUID @return 同租户有效队列的各阶段记录数 */
    @GuardedScope(tenant="__verifiedTenant", operationPermissions=ReadInboundWorkbenchUseCase.PERMISSION)
    @Select("SELECT t.queue_stage stage, COUNT(*) total " + ACTIVE + " GROUP BY t.queue_stage")
    List<StageCount> counts(@Param("__verifiedTenant") byte[] tenant,@Param("unopened") boolean unopened);
}
