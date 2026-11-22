package com.appnexus.bulkwarehousing.adapter.integration;

import com.appnexus.bulkwarehousing.api.WarehouseBalances;
import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.bulkwarehousing.adapter.integration.WarehousingPage.operation;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 在库货权余额查询独立声明，授权单据余额继续归原出仓对象。 */
public final class WarehouseStockBalanceDeclaration implements Provider {
  /** {@inheritDoc} */
  @Override public Descriptor descriptor() {
    var balances=operation(WarehouseBalances.OBJECT,"read","查看在库货权","View on-hand title",
        WarehouseBalances.READ_PERMISSION,"GET","/api/bulk-warehouse-stock-balances");
    var coils=operation(WarehouseBalances.OBJECT,"read_coils","查看在库钢卷","View in-stock coils",
        WarehouseBalances.READ_PERMISSION,"GET","/api/bulk-warehouse-stock-balances/coils");
    var path="/api/bulk-warehouse-stock-balances/coils/{id}/trace";
    var trace=com.appnexus.contracts.businessobject.DedicatedOperations.operation(WarehouseBalances.OBJECT,
        "trace","追溯整卷履约","Trace coil fulfillment",WarehouseBalances.READ_PERMISSION,
        WarehousingPage.flowPage("追溯整卷履约","Trace coil fulfillment","GET",path),
        WarehousingPage.transitPage("追溯整卷履约","Trace coil fulfillment","GET",path),
        WarehousingPage.queryPage("inbound-notices","入仓通知单","Inbound notices",path),
        WarehousingPage.queryPage("inbound-receipts","入仓确认单","Inbound receipts",path),
        WarehousingPage.queryPage("inbound-titles","入仓货权登记","Inbound title registrations",path),
        WarehousingPage.queryPage("inbound-quality","入仓质量放行","Inbound quality releases",path),
        WarehousingPage.queryPage("title-transfers","货权过户单","Title transfers",path),
        WarehousingPage.queryPage("outbound-notices","出仓通知单","Outbound notices",path),
        WarehousingPage.queryPage("outbound-trips","车次作业单","Outbound trips",path),
        WarehousingPage.queryPage("outbound-confirmations","出仓确认单","Outbound confirmations",path),
        WarehousingPage.queryPage("handover-reversals","出仓交接冲销单","Handover reversals",path));
    var documentPath="/api/bulk-warehouse-documents/{kind}/{documentNo}";
    var document=com.appnexus.contracts.businessobject.DedicatedOperations.operation(WarehouseBalances.OBJECT,
        "read_document","按单号查询仓储单据","Read warehouse documents by number",WarehouseBalances.READ_PERMISSION,
        WarehousingPage.queryPage("inbound-notices","入仓通知单","Inbound notices",documentPath),
        WarehousingPage.queryPage("inbound-receipts","入仓确认单","Inbound receipt",documentPath),
        WarehousingPage.queryPage("inbound-titles","入仓货权登记","Inbound title",documentPath),
        WarehousingPage.queryPage("inbound-quality","入仓质量放行","Inbound quality",documentPath),
        WarehousingPage.queryPage("title-transfers","货权过户单","Title transfer",documentPath),
        WarehousingPage.queryPage("outbound-notices","出仓通知单","Outbound notice",documentPath),
        WarehousingPage.queryPage("outbound-trips","车次作业单","Outbound trip",documentPath),
        WarehousingPage.queryPage("outbound-confirmations","出仓确认单","Outbound confirmation",documentPath),
        WarehousingPage.queryPage("handover-reversals","交接冲销单","Handover reversal",documentPath));
    var entry=com.appnexus.contracts.businessobject.DedicatedOperations.operation(WarehouseBalances.OBJECT,
        "read_entry","查询入场接管单","Read inbound entry",WarehouseBalances.READ_PERMISSION,
        WarehousingPage.queryPage("inbound-entries","入场接管单","Inbound entries","/api/bulk-inbound-entries/{id}"));
    var receiptDocument=com.appnexus.contracts.businessobject.DedicatedOperations.operation(WarehouseBalances.OBJECT,
        "read_receipt_document","查询实收确认单","Read receipt confirmation",WarehouseBalances.READ_PERMISSION,
        WarehousingPage.queryPage("inbound-receipts","实收确认单","Inbound receipts","/api/bulk-inbound-receipt-documents/{id}"));
    var overview=com.appnexus.contracts.businessobject.DedicatedOperations.operation(WarehouseBalances.OBJECT,
        "read_overview","库存总览","Inventory overview",WarehouseBalances.OVERVIEW_PERMISSION,
        new PageOperation("warehousing/inventory-overview","/warehousing/inventory-overview",
            "库存总览","Inventory overview","GET","/api/bulk-warehouse-inventory"));
    return new Descriptor(WarehouseBalances.OBJECT,"在库货权余额","On-hand title balance","bulk-warehousing",
        1,Category.TRANSACTION,List.of(),List.of(balances,coils,trace,overview,document,entry,receiptDocument),new DataAccess("TENANT",List.of(
            new DataResource(WarehouseBalances.RESOURCE,"","在库货权余额","On-hand title balance",List.of())),
        Map.of("read",Set.of("ALL"),"read_coils",Set.of("ALL"),"trace",Set.of("ALL"),"read_overview",Set.of("ALL"),"read_document",Set.of("ALL"),"read_entry",Set.of("ALL"),"read_receipt_document",Set.of("ALL"))));
  }
}
