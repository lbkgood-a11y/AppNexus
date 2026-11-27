package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundDocumentsUseCase.Creation;
import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundDocumentsUseCase.TaskOption;
import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundDocumentsUseCase.LabMetricOption;
import java.util.List;

import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundDocumentsUseCase.Kind;
import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundDocumentsUseCase.InboundDocumentPage;
import com.appnexus.platform.authorization.AccessPlan;
import java.util.Map;
import java.util.UUID;

/** 分岗查询输出端口。 */
public interface InboundDocumentQueries {
    /** 按化验操作许可读取模板输入项目。 */
    List<LabMetricOption> labMetrics(AccessPlan plan, UUID id);
    /** 查询已绑定当前命令许可的候选作业。 */
    List<TaskOption> taskOptions(AccessPlan plan, Creation creation, String prefix);
    /** @return 同事务的授权结果及总数 */
    InboundDocumentPage list(AccessPlan plan, Kind kind, String number, int offset, int limit);
    /** @return 当前授权详情；不存在时为空 */
    Map<String,Object> detail(AccessPlan plan, Kind kind, UUID id);
}
