package com.appnexus.bulkwarehousing.api.inboundexecution;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 工作台只读队列与状态计数。 */
public interface ReadInboundWorkbenchUseCase {
    String OBJECT = "BulkInboundTask";
    String RESOURCE = "warehousing.inboundtask";
    String OPERATION = "read_workbench_queue";
    String PERMISSION = RESOURCE + ":read_workbench_queue";
    String PATH = "/api/bulk-inbound-workbench/queue";

    String LIST_OPERATION = "read_task_list";
    String LIST_PERMISSION = RESOURCE + ":" + LIST_OPERATION;
    String LIST_PATH = "/api/bulk-inbound-tasks";

    /** 不含物流身份的作业台账投影。 */
    record TaskRow(UUID id, String number, String status, Stage stage, Instant arrivedAt, TaskAudit audit) {}

    /** 数据库记录的 UTC 审计时点；历史未知值和无可信账号的执行保留空值。 */
    record TaskAudit(String createdBy, Instant createdAt, String updatedBy, Instant updatedAt) {}

    /** 末页的续查标识为空。 */
    record TaskPage(List<TaskRow> rows, UUID nextAfterId) {}

    /**
     * @param context 服务端验证的租户上下文
     * @param number 精确作业单号，空表示全部
     * @param status 可选作业状态，缺省时查询全部状态
     * @param afterId 上页末行作业 UUID
     * @param limit 每页 1 至 100 行
     * @return 包含历史作业的授权结果
     */
    TaskPage listTasks(VerifiedTenantContext context, String number, TaskStatus status, UUID afterId, int limit);

    /** 作业台账可筛选的生命周期状态，包含历史终态。 */
    enum TaskStatus { ARRIVED, WORKING, READY_TO_RECEIVE, RECEIVED, RETURN_PENDING, RETURNED, CORRECTION_REQUIRED }

    /** 作业队列有效节点。 */
    enum Stage { WAIT_QUALITY, WAIT_GROSS, UNLOADING, WAIT_TARE, WAIT_RECEIPT }

    /** 不含可反查供货或车辆身份的作业队列投影。 */
    record QueueItem(UUID taskId, String taskNo, Stage stage, Instant stageStartedAt) {}

    /** 计数覆盖相同授权范围内的完整有效队列，与所选页签和分页游标无关。 */
    record QueuePage(List<QueueItem> rows, Map<Stage, Long> counts, UUID nextAfterId) {}

    /**
     * @param context 服务端验证的租户会话
     * @param stage 可选队列节点，空表示全部有效节点
     * @param afterId 上页最后一个作业 UUID，首屏为空
     * @param limit 请求行数，1 至 100
     * @param unopened 为 true 时只读取尚无有效入库单的作业
     * @return 一致授权快照中的数据页及各阶段计数
     */
    QueuePage queue(VerifiedTenantContext context, Stage stage, UUID afterId, int limit, boolean unopened);
}
