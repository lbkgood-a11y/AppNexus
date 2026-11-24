package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundWorkbenchUseCase.QueuePage;
import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundWorkbenchUseCase.Stage;
import com.appnexus.platform.authorization.AccessPlan;
import java.util.UUID;

/** 工作台队列的定向只读端口。 */
public interface InboundWorkbenchQueries {
    /**
     * @param plan 当前队列读取授权，适配器在事务内执行
     * @param stage 可选队列节点
     * @param afterId 排他续查 UUID
     * @param limit 每页 1 至 100 行
     * @return 同一只读事务内构建的队列及状态计数
     */
    QueuePage queue(AccessPlan plan, Stage stage, UUID afterId, int limit, boolean unopened);
}
