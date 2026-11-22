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
        WarehousingPage.transitPage("追溯整卷履约","Trace coil fulfillment","GET",path));
    return new Descriptor(WarehouseBalances.OBJECT,"在库货权余额","On-hand title balance","bulk-warehousing",
        1,Category.TRANSACTION,List.of(),List.of(balances,coils,trace),new DataAccess("TENANT",List.of(
            new DataResource(WarehouseBalances.RESOURCE,"","在库货权余额","On-hand title balance",List.of())),
        Map.of("read",Set.of("ALL"),"read_coils",Set.of("ALL"),"trace",Set.of("ALL"))));
  }
}
