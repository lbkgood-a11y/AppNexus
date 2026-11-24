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
