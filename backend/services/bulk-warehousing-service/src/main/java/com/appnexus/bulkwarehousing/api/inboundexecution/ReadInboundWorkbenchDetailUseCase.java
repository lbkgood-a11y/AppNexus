package com.appnexus.bulkwarehousing.api.inboundexecution;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.Map;
import java.util.UUID;

/** 工作台详情只读接口，卡片由独立对象许可决定。 */
public interface ReadInboundWorkbenchDetailUseCase {
    String DETAIL=ReadInboundWorkbenchUseCase.RESOURCE+":read_workbench_detail";
    String IDENTITY=ReadInboundWorkbenchUseCase.RESOURCE+":read_workbench_identity";
    String QUALITY=RegisterInboundSampleUseCase.RESOURCE+":read_workbench_quality";
    String METER=RegisterExternalMeasurementUseCase.RESOURCE+":read_workbench_meter";
    String RECEIPT=CreateBulkReceiptDraftUseCase.RESOURCE+":read_workbench_receipt";
    /** 可独立刷新且拥有真实统一操作的读卡片。 */
    enum Card { IDENTITY, QUALITY, METERING, RECEIPT }

    /** 仅投影状态、被允许的卡片和真实业务风控，不包含设备模拟数据。 */
    record WorkbenchDetail(UUID taskId,long taskVersion,String stage,String status,boolean blind,
            Map<String,Object> identity,Map<String,Object> quality,Map<String,Object> metering,
            Map<String,Object> receipt,Map<String,Object> totals,Map<String,Object> risk) {}

    /** @param context 可信租户会话 @param task 作业 UUID @return 根据当前授权生成的详情 */
    WorkbenchDetail detail(VerifiedTenantContext context,UUID task);
    /** @param context 可信租户 @param task 作业 UUID @param card 固定卡片类型 @return 受控输出 */
    Map<String,Object> card(VerifiedTenantContext context,UUID task,Card card);
}
