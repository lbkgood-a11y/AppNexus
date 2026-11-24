package com.appnexus.bulkwarehousing.adapter.integration.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundWorkbenchUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.CreateInboundTaskUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.GroundWeighingUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundWorkbenchDetailUseCase;
import com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.appnexus.contracts.businessobject.DedicatedOperations;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 入库作业的唯一对象声明，覆盖队列查询及保管员监卸命令。 */
public final class InboundTaskDeclaration implements Provider {
    /** {@inheritDoc} */
    @Override public Descriptor descriptor() {
        var operation = DedicatedOperations.operation(ReadInboundWorkbenchUseCase.OBJECT,
                "read_workbench_queue", "查询入库作业队列", "Read inbound work queue",
                ReadInboundWorkbenchUseCase.PERMISSION,InboundWorkbenchPageOperations.entry("GET",ReadInboundWorkbenchUseCase.PATH));
        var unload = DedicatedOperations.operation("BulkInboundTask", "confirm_unloading",
                "确认入库监卸", "Confirm inbound unloading", GroundWeighingUseCase.UNLOAD,
                InboundWorkbenchPageOperations.entry("POST","/api/bulk-inbound-tasks/{id}:confirm-unloading"),
                InboundDocumentPageOperations.METERING.entry("POST","/api/bulk-inbound-tasks/{id}:confirm-unloading"));
        var detail=DedicatedOperations.operation("BulkInboundTask","read_workbench_detail",
                "查询入库工作台详情","Read inbound workbench detail",ReadInboundWorkbenchDetailUseCase.DETAIL,
                InboundWorkbenchPageOperations.entry("GET","/api/bulk-inbound-workbench/tasks/{id}"));
        var identity=DedicatedOperations.operation("BulkInboundTask","read_workbench_identity",
                "查询非盲检作业身份","Read non-blind inbound identity",ReadInboundWorkbenchDetailUseCase.IDENTITY,
                InboundWorkbenchPageOperations.entry("GET","/api/bulk-inbound-workbench/tasks/{id}/identity"));
        var modes=Set.of("HIDDEN","MASKED","READ_ONLY");
        var create=DedicatedOperations.operation("BulkInboundTask","create_from_notice",
                "从入仓通知登记到场车次","Register inbound arrival",CreateInboundTaskUseCase.PERMISSION,
                new PageOperation("warehousing/inbound-notices","/warehousing/inbound-notices",
                        "入仓通知单","Inbound notices","POST",CreateInboundTaskUseCase.PATH),
                new PageOperation("warehousing/inbound-notices","/warehousing/inbound-notices",
                        "入仓通知单","Inbound notices","GET",CreateInboundTaskUseCase.PATH));
        return new Descriptor(ReadInboundWorkbenchUseCase.OBJECT, "入库作业", "Inbound task", "bulk-warehousing",
                1, Category.TRANSACTION, List.of(), List.of(operation,unload,detail,identity,create),
                new DataAccess("TENANT",
                        List.of(new DataResource(ReadInboundWorkbenchUseCase.RESOURCE, "", "入库作业",
                                "Inbound task", List.of(new DataField("vehiclePlate","车牌","Vehicle plate","IDENTIFIER",modes),
                                  new DataField("temporaryVehicleNo","无牌车辆现场识别号","Unplated vehicle site identifier","IDENTIFIER",modes),
                                  new DataField("waybillNo","运单号","Waybill number","IDENTIFIER",modes),
                                  new DataField("driverName","司机姓名","Driver name","PERSON_NAME",modes),
                                  new DataField("ownerId","货主标识","Cargo owner identifier","IDENTIFIER",modes)))),
                        Map.of(ReadInboundWorkbenchUseCase.OPERATION, Set.of("ALL"),"confirm_unloading",Set.of("ALL"),
                               "read_workbench_detail",Set.of("ALL"),"read_workbench_identity",Set.of("ALL"),
                               "create_from_notice",Set.of("ALL"))));
    }
}
