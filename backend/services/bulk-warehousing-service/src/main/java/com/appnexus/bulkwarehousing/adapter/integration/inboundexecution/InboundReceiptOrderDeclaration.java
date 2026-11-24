package com.appnexus.bulkwarehousing.adapter.integration.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.CreateBulkReceiptDraftUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.OpenInboundReceiptUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.PostInboundReceiptUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.ReverseInboundReceiptUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.CreateSteelReceiptDraftUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundWorkbenchDetailUseCase;
import com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.appnexus.contracts.businessobject.DedicatedOperations;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 入库单的唯一声明，开放外部计量散货草稿及已合格过账。 */
public final class InboundReceiptOrderDeclaration implements Provider {
    /** {@inheritDoc} */
    @Override public Descriptor descriptor() {
        var open=DedicatedOperations.operation("BulkWarehouseReceiptOrder","open_draft",
                "打开入库单草稿","Open receipt draft",OpenInboundReceiptUseCase.PERMISSION,
                InboundWorkbenchPageOperations.entry("POST",OpenInboundReceiptUseCase.PATH));
        var operation = DedicatedOperations.operation("BulkWarehouseReceiptOrder", "create_bulk_draft",
                "创建散货入库草稿", "Create bulk receipt draft", CreateBulkReceiptDraftUseCase.PERMISSION,
                InboundDocumentPageOperations.RECEIPT.entry("POST","/api/bulk-inbound-receipt-orders:create-bulk-draft"),
                InboundDocumentPageOperations.RECEIPT.entry("GET","/api/bulk-inbound-receipt-orders:create-bulk-draft"));
        var post = DedicatedOperations.operation("BulkWarehouseReceiptOrder", "post",
                "确认入库过账", "Post inbound receipt", PostInboundReceiptUseCase.PERMISSION,
                InboundWorkbenchPageOperations.entry("POST","/api/bulk-inbound-receipt-orders/{id}:post"),
                InboundDocumentPageOperations.RECEIPT.entry("POST","/api/bulk-inbound-receipt-orders/{id}:post"));
        var reverse = DedicatedOperations.operation("BulkWarehouseReceiptOrder", "reverse",
                "冲红入库单", "Reverse inbound receipt", ReverseInboundReceiptUseCase.PERMISSION,
                InboundWorkbenchPageOperations.entry("POST","/api/bulk-inbound-receipt-orders/{id}:reverse"),
                InboundDocumentPageOperations.RECEIPT.entry("POST","/api/bulk-inbound-receipt-orders/{id}:reverse"));
        var steel=DedicatedOperations.operation("BulkWarehouseReceiptOrder","create_steel_draft",
                "创建钢铁入库草稿","Create steel receipt draft",CreateSteelReceiptDraftUseCase.PERMISSION,
                InboundDocumentPageOperations.RECEIPT.entry("POST","/api/bulk-inbound-receipt-orders:create-steel-draft"),
                InboundDocumentPageOperations.RECEIPT.entry("GET","/api/bulk-inbound-receipt-orders:create-steel-draft"));
        var card=DedicatedOperations.operation("BulkWarehouseReceiptOrder","read_workbench_receipt",
                "查询入库卡片","Read workbench receipt card",ReadInboundWorkbenchDetailUseCase.RECEIPT,
                InboundWorkbenchPageOperations.entry("GET","/api/bulk-inbound-workbench/tasks/{id}/receipt"));
        var list=DedicatedOperations.operation("BulkWarehouseReceiptOrder","list_documents","查询入库单列表","List receipt orders",
                "warehousing.receipt:list_documents",InboundDocumentPageOperations.RECEIPT.entry("GET","/api/bulk-inbound-receipt-orders"));
        var read=DedicatedOperations.operation("BulkWarehouseReceiptOrder","read_document","查询入库单详情","Read receipt orders detail",
                "warehousing.receipt:read_document",InboundDocumentPageOperations.RECEIPT.entry("GET","/api/bulk-inbound-receipt-orders/{id}"));
        return new Descriptor("BulkWarehouseReceiptOrder", "入库单", "Receipt order", "bulk-warehousing",
                1, Category.TRANSACTION, List.of(new Field("receiptNo", "入库单编码", "Receipt number",
                        "TEXT", "", false),new Field("steelReceiptNo","钢铁入库单号","Steel receipt number","TEXT","",false)),
                List.of(operation, post, reverse, steel, card,list,read,open),
                new DataAccess("TENANT", List.of(new DataResource(CreateBulkReceiptDraftUseCase.RESOURCE, "",
                        "入库单", "Receipt order", List.of())),
                        Map.of("create_bulk_draft", Set.of("ALL"), "post", Set.of("ALL"), "reverse", Set.of("ALL"),
                                "create_steel_draft",Set.of("ALL"),"read_workbench_receipt",Set.of("ALL"),"list_documents",Set.of("ALL"),
                                "read_document",Set.of("ALL"),"open_draft",Set.of("ALL"))),
                List.of(new CodingField("receiptNo", "create_bulk_draft", 64),new CodingField("steelReceiptNo","create_steel_draft",64)));
    }
}
