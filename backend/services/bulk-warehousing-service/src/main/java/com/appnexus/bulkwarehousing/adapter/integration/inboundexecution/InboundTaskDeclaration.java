package com.appnexus.bulkwarehousing.adapter.integration.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundWorkbenchUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundSiteTelemetry;
import com.appnexus.bulkwarehousing.api.inboundexecution.InboundSiteCommands;
import com.appnexus.bulkwarehousing.api.inboundexecution.CreateInboundTaskUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.GroundWeighingUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundWorkbenchDetailUseCase;
import com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.appnexus.contracts.businessobject.DedicatedOperations;
import com.appnexus.contracts.businessobject.BusinessObjectRuntime;
import com.appnexus.contracts.businessobject.InteractionContract;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 入库作业的唯一对象声明，覆盖队列查询及保管员监卸命令。 */
public final class InboundTaskDeclaration implements Provider {
    private final InboundTaskPrintRuntime printing;

    /** @param printing 原现场授权下的公共作业票快照运行端口 */
    public InboundTaskDeclaration(InboundTaskPrintRuntime printing){this.printing=java.util.Objects.requireNonNull(printing);}
    @Override public BusinessObjectRuntime runtime(){return printing;}
    /** {@inheritDoc} */
    @Override public Descriptor descriptor() {
        var operation = DedicatedOperations.operation(ReadInboundWorkbenchUseCase.OBJECT,
                "read_workbench_queue", "查询入库作业队列", "Read inbound work queue",
                ReadInboundWorkbenchUseCase.PERMISSION,InboundWorkbenchPageOperations.entry("GET",ReadInboundWorkbenchUseCase.PATH));
        var list = DedicatedOperations.operation(ReadInboundWorkbenchUseCase.OBJECT,
                "read_task_list", "查询入库作业单列表", "Read inbound task list",
                ReadInboundWorkbenchUseCase.LIST_PERMISSION,
                new PageOperation("warehousing/inbound-tasks","/warehousing/inbound-tasks",
                        "入库作业单","Inbound tasks","GET",ReadInboundWorkbenchUseCase.LIST_PATH));
        var unload = DedicatedOperations.operation("BulkInboundTask", "confirm_unloading",
                "确认入库监卸", "Confirm inbound unloading", GroundWeighingUseCase.UNLOAD,
                InboundWorkbenchPageOperations.entry("POST","/api/bulk-inbound-tasks/{id}:confirm-unloading"),
                InboundDocumentPageOperations.METERING.entry("POST","/api/bulk-inbound-tasks/{id}:confirm-unloading"));
        var detail=DedicatedOperations.operation("BulkInboundTask","read_workbench_detail",
                "查询入库工作台详情","Read inbound workbench detail",ReadInboundWorkbenchDetailUseCase.DETAIL,
                InboundWorkbenchPageOperations.entry("GET","/api/bulk-inbound-workbench/tasks/{id}"),
                new PageOperation("warehousing/inbound-tasks","/warehousing/inbound-tasks",
                        "入库作业单","Inbound tasks","GET","/api/bulk-inbound-workbench/tasks/{id}"),
                InboundWorkbenchPageOperations.entry("GET",ReadInboundSiteTelemetry.PATH),
                InboundWorkbenchPageOperations.entry("GET",InboundSiteCommands.ROOT+"/{commandId}"));
        var gate=DedicatedOperations.operation("BulkInboundTask","raise_site_gate","入库现场抬杆","Raise inbound gate",
                InboundSiteCommands.GATE,InboundWorkbenchPageOperations.entry("POST",InboundSiteCommands.ROOT+"/gate"));
        var call=DedicatedOperations.operation("BulkInboundTask","call_site_number","入库现场叫号","Call inbound number",
                InboundSiteCommands.CALL,InboundWorkbenchPageOperations.entry("POST",InboundSiteCommands.ROOT+"/call"));
        String printId="BulkInboundTask.print_site_ticket";
        var printFields=List.of(new Field("taskNo","作业单号","Task number","TEXT","",true));
        var print=new Operation("print_site_ticket","打印入库作业票","Print inbound task slip",
                InboundSiteCommands.PRINT,"BulkInboundTask.print_site_ticket",InboundWorkbenchPageOperations.entry("POST",InboundSiteCommands.ROOT+"/print"),
                List.of(),new InteractionContract(printId,2,"BulkInboundTask",InboundTaskPrintRuntime.OPERATION,
                    "打印入库作业票","Print inbound task slip",InteractionContract.Trigger.USER_ACTION,
                    List.of(new InteractionContract.Capability(InteractionContract.Service.PRINT,
                      InteractionContract.Completion.CONFIRMATION,InboundTaskPrintRuntime.CONTRACT,"",
                      printFields,Set.of(),InboundTaskPrintRuntime.CONTRACT,1))));
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
        return new Descriptor("BulkInboundTask", "入库作业", "Inbound task", "bulk-warehousing",
                2, Category.TRANSACTION, List.of(), List.of(operation,list,unload,detail,identity,create,gate,call,print),
                new DataAccess("TENANT",
                        List.of(new DataResource(ReadInboundWorkbenchUseCase.RESOURCE, "", "入库作业",
                                "Inbound task", List.of(new DataField("taskNo","作业单号","Task number","IDENTIFIER",Set.of("READ_ONLY")),
                                  new DataField("vehiclePlate","车牌","Vehicle plate","IDENTIFIER",modes),
                                  new DataField("temporaryVehicleNo","无牌车辆现场识别号","Unplated vehicle site identifier","IDENTIFIER",modes),
                                  new DataField("waybillNo","运单号","Waybill number","IDENTIFIER",modes),
                                  new DataField("driverName","司机姓名","Driver name","PERSON_NAME",modes),
                                  new DataField("ownerId","货主标识","Cargo owner identifier","IDENTIFIER",modes)))),
                        Map.of(ReadInboundWorkbenchUseCase.OPERATION, Set.of("ALL"),
                               ReadInboundWorkbenchUseCase.LIST_OPERATION, Set.of("ALL"),"confirm_unloading",Set.of("ALL"),
                               "read_workbench_detail",Set.of("ALL"),"read_workbench_identity",Set.of("ALL"),
                               "create_from_notice",Set.of("ALL"),"raise_site_gate",Set.of("ALL"),
                               "call_site_number",Set.of("ALL"),"print_site_ticket",Set.of("ALL"))));
    }
}
