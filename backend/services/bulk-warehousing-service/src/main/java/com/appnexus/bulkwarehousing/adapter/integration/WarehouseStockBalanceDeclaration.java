package com.appnexus.bulkwarehousing.adapter.integration;

import com.appnexus.bulkwarehousing.api.WarehouseBalances;
import com.appnexus.contracts.businessobject.BusinessObjects.*;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 入仓通知和库存总览的唯一读取声明，沿用已有数据资源及授权。 */
public final class WarehouseStockBalanceDeclaration implements Provider {
  /** {@inheritDoc} */
  @Override public Descriptor descriptor() {
    var document=com.appnexus.contracts.businessobject.DedicatedOperations.operation(WarehouseBalances.OBJECT,
        "read_document","查询入仓通知","Read inbound notice",WarehouseBalances.READ_PERMISSION,
        WarehousingPage.queryPage("inbound-notices","入仓通知单","Inbound notices",
            "/api/bulk-warehouse-documents/INBOUND_NOTICE/{documentNo}"));
    var noticeList=com.appnexus.contracts.businessobject.DedicatedOperations.operation(WarehouseBalances.OBJECT,
        "list_inbound_notices","查询入仓通知列表","List inbound notices",WarehouseBalances.READ_PERMISSION,
        WarehousingPage.queryPage("inbound-notices","入仓通知列表","Inbound notice list","/api/bulk-inbound-notices"));
    var overview=com.appnexus.contracts.businessobject.DedicatedOperations.operation(WarehouseBalances.OBJECT,
        "read_overview","库存总览","Inventory overview",WarehouseBalances.OVERVIEW_PERMISSION,
        new PageOperation("warehousing/inventory-overview","/warehousing/inventory-overview",
            "库存总览","Inventory overview","GET","/api/bulk-warehouse-inventory"));
    return new Descriptor(WarehouseBalances.OBJECT,"在库货权余额","On-hand title balance","bulk-warehousing",
        1,Category.TRANSACTION,List.of(),List.of(overview,document,noticeList),new DataAccess("TENANT",List.of(
            new DataResource(WarehouseBalances.RESOURCE,"","在库货权余额","On-hand title balance",List.of())),
        Map.of("read_overview",Set.of("ALL"),"read_document",Set.of("ALL"),"list_inbound_notices",Set.of("ALL"))));
  }
}
