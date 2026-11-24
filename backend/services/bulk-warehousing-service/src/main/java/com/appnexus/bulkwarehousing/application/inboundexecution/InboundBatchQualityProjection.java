package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.contracts.data.TransactionalBusinessEvents;
import java.util.UUID;

/** 按发布对象的关联范围更新待化验库存的质量事实。 */
public interface InboundBatchQualityProjection {
    /**
     * @param tenant 事件可信租户
     * @param type 业务事件选择固定的引用路径
     * @param subjectId 服务器生成的入库单或质检单 UUID
     * @return 实际应用的库存行数；已处理或尚无结果时为零
     */
    int apply(UUID tenant, TransactionalBusinessEvents.Type type, UUID subjectId);
}
