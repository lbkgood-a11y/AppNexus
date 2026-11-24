package com.appnexus.bulkwarehousing.adapter.integration.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.RegisterExternalMeasurementUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.GroundWeighingUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.ConfirmSteelMeasurementUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundWorkbenchDetailUseCase;
import com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.appnexus.contracts.businessobject.DedicatedOperations;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 入库计量的唯一声明，当前接入外部整单净重操作。 */
public final class InboundMeteringOrderDeclaration implements Provider {
    /** {@inheritDoc} */
    @Override public Descriptor descriptor() {
        var operation = DedicatedOperations.operation("BulkInboundMeteringOrder", "register_external",
                "确认外部计量", "Register external inbound measurement", RegisterExternalMeasurementUseCase.PERMISSION,
                InboundWorkbenchPageOperations.entry("POST","/api/bulk-inbound-metering-orders:register-external"),
                InboundDocumentPageOperations.METERING.entry("POST","/api/bulk-inbound-metering-orders:register-external"),
                InboundDocumentPageOperations.METERING.entry("GET","/api/bulk-inbound-metering-orders:register-external"));
        var gross = DedicatedOperations.operation("BulkInboundMeteringOrder", "record_gross",
                "登记毛重", "Record inbound gross weight", GroundWeighingUseCase.GROSS,
                InboundWorkbenchPageOperations.entry("POST","/api/bulk-inbound-metering-orders:record-gross"),
                InboundDocumentPageOperations.METERING.entry("POST","/api/bulk-inbound-metering-orders:record-gross"),
                InboundDocumentPageOperations.METERING.entry("GET","/api/bulk-inbound-metering-orders:record-gross"));
        var tare = DedicatedOperations.operation("BulkInboundMeteringOrder", "record_tare",
                "登记皮重", "Record inbound tare weight", GroundWeighingUseCase.TARE,
                InboundWorkbenchPageOperations.entry("POST","/api/bulk-inbound-metering-orders:record-tare"),
                InboundDocumentPageOperations.METERING.entry("POST","/api/bulk-inbound-metering-orders:record-tare"));
        var steel=DedicatedOperations.operation("BulkInboundMeteringOrder","confirm_steel",
                "确认钢铁双计量","Confirm inbound steel measurement",ConfirmSteelMeasurementUseCase.PERMISSION,
                InboundDocumentPageOperations.METERING.entry("POST","/api/bulk-inbound-metering-orders:confirm-steel"),
                InboundDocumentPageOperations.METERING.entry("GET","/api/bulk-inbound-metering-orders:confirm-steel"));
        var card=DedicatedOperations.operation("BulkInboundMeteringOrder","read_workbench_meter",
                "查询计量卡片","Read workbench metering card",ReadInboundWorkbenchDetailUseCase.METER,
                InboundWorkbenchPageOperations.entry("GET","/api/bulk-inbound-workbench/tasks/{id}/metering"));
        var list=DedicatedOperations.operation("BulkInboundMeteringOrder","list_documents","查询计量单（磅单）列表","List metering orders",
                "warehousing.inboundmetering:list_documents",InboundDocumentPageOperations.METERING.entry("GET","/api/bulk-inbound-metering-orders"));
        var read=DedicatedOperations.operation("BulkInboundMeteringOrder","read_document","查询计量单（磅单）详情","Read metering orders detail",
                "warehousing.inboundmetering:read_document",InboundDocumentPageOperations.METERING.entry("GET","/api/bulk-inbound-metering-orders/{id}"));
        return new Descriptor("BulkInboundMeteringOrder", "入库计量单", "Inbound metering order", "bulk-warehousing",
                1, Category.TRANSACTION, List.of(new Field("meteringNo", "计量单编码", "Metering order number",
                        "TEXT", "", false),new Field("grossMeteringNo","毛重开立计量单编码","Gross metering order number",
                        "TEXT","",false),new Field("steelMeteringNo","钢铁计量单编码","Steel metering number","TEXT","",false)),
                List.of(operation,gross,tare,steel,card,list,read),
                new DataAccess("TENANT", List.of(new DataResource(RegisterExternalMeasurementUseCase.RESOURCE, "",
                        "入库计量单", "Inbound metering order", List.of())),
                        Map.of("register_external", Set.of("ALL"), "record_gross",Set.of("ALL"),
                                "record_tare",Set.of("ALL"),"confirm_steel",Set.of("ALL"),"read_workbench_meter",Set.of("ALL"),"list_documents",Set.of("ALL"),"read_document",Set.of("ALL"))),
                List.of(new CodingField("meteringNo", "register_external", 64),
                        new CodingField("grossMeteringNo","record_gross",64),new CodingField("steelMeteringNo","confirm_steel",64)));
    }
}
