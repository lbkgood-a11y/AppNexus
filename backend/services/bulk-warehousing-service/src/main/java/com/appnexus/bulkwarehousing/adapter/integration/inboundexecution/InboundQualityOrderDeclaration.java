package com.appnexus.bulkwarehousing.adapter.integration.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.RegisterInboundSampleUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.DecideInboundQualityUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.InspectInboundSteelUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundWorkbenchDetailUseCase;
import com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.appnexus.contracts.businessobject.DedicatedOperations;
import com.appnexus.contracts.businessobject.InteractionContract;
import com.appnexus.bulkwarehousing.application.inboundexecution.ApplyInboundBatchQualityService;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 质检单的唯一声明，覆盖采样、复检及过账前的实验室判定。 */
public final class InboundQualityOrderDeclaration implements Provider {
    /** {@inheritDoc} */
    @Override public Descriptor descriptor() {
        var operation = DedicatedOperations.operation("BulkInboundQualityOrder", "register_sample",
                "入库采样登记", "Register inbound sample", RegisterInboundSampleUseCase.PERMISSION,
                InboundWorkbenchPageOperations.entry("POST","/api/bulk-inbound-quality-orders:register-sample"),
                InboundDocumentPageOperations.QUALITY.entry("POST","/api/bulk-inbound-quality-orders:register-sample"),
                InboundDocumentPageOperations.QUALITY.entry("GET","/api/bulk-inbound-quality-orders:register-sample"));
        var decide = DedicatedOperations.operation("BulkInboundQualityOrder", "decide_quality",
                "判定入库质量", "Decide inbound quality", DecideInboundQualityUseCase.PERMISSION,
                InboundDocumentPageOperations.QUALITY.entry("POST","/api/bulk-inbound-quality-orders/{id}:decide"));
        var inspect = DedicatedOperations.operation("BulkInboundQualityOrder", "inspect_steel",
                "核验钢铁实物和质保书", "Inspect inbound steel", InspectInboundSteelUseCase.PERMISSION,
                InboundDocumentPageOperations.QUALITY.entry("POST","/api/bulk-inbound-quality-orders:inspect-steel"),
                InboundDocumentPageOperations.QUALITY.entry("GET","/api/bulk-inbound-quality-orders:inspect-steel"));
        var card=DedicatedOperations.operation("BulkInboundQualityOrder","read_workbench_quality",
                "查询质检卡片","Read workbench quality card",ReadInboundWorkbenchDetailUseCase.QUALITY,
                InboundWorkbenchPageOperations.entry("GET","/api/bulk-inbound-workbench/tasks/{id}/quality"));
        var list=DedicatedOperations.operation("BulkInboundQualityOrder","list_documents","查询质检单列表","List quality orders",
                "warehousing.inboundquality:list_documents",InboundDocumentPageOperations.QUALITY.entry("GET","/api/bulk-inbound-quality-orders"));
        var read=DedicatedOperations.operation("BulkInboundQualityOrder","read_document","查询质检单详情","Read quality orders detail",
                "warehousing.inboundquality:read_document",InboundDocumentPageOperations.QUALITY.entry("GET","/api/bulk-inbound-quality-orders/{id}"));
        return new Descriptor("BulkInboundQualityOrder", "入库质检单", "Inbound quality order", "bulk-warehousing",
                1, Category.TRANSACTION,
                List.of(new Field("qualityNo", "质检单编码", "Quality order number", "TEXT", "", false),
                        new Field("steelQualityNo","钢铁核验单编码","Steel inspection number","TEXT","",false)),
                List.of(operation, decide, inspect, card, applyBatchQuality(),list,read),
                new DataAccess("TENANT", List.of(new DataResource(RegisterInboundSampleUseCase.RESOURCE, "",
                        "入库质检单", "Inbound quality order", List.of())),
                        Map.of("register_sample", Set.of("ALL"), "decide_quality", Set.of("ALL"), "inspect_steel",Set.of("ALL"),
                                "read_workbench_quality",Set.of("ALL"),"list_documents",Set.of("ALL"),"read_document",Set.of("ALL"))),
                List.of(new CodingField("qualityNo", "register_sample", 64),new CodingField("steelQualityNo","inspect_steel",64)));
    }

    private Operation applyBatchQuality() {
        String id=ApplyInboundBatchQualityService.OPERATION, code="apply_batch_quality";
        var interaction = new InteractionContract(id, 1, "BulkInboundQualityOrder", code,
                "应用入库批次质量", "Apply inbound batch quality", InteractionContract.Trigger.SCHEDULE, List.of());
        return new Operation(code, "应用入库批次质量", "Apply inbound batch quality",
                ApplyInboundBatchQualityService.AUTHORITY, id, null, List.of(), interaction);
    }
}
