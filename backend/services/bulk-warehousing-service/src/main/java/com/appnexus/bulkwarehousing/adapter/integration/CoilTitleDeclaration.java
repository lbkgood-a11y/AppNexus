package com.appnexus.bulkwarehousing.adapter.integration;

import com.appnexus.bulkwarehousing.api.CoilTitleTransfer;
import com.appnexus.contracts.businessobject.BusinessObjects.*;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 不动货过户对象唯一声明，批准后专用命令执行。 */
public final class CoilTitleDeclaration implements Provider {
  /** {@inheritDoc} */
  @Override public Descriptor descriptor() {
    var operation=com.appnexus.contracts.businessobject.DedicatedOperations.operation(CoilTitleTransfer.OBJECT,
        "transfer","同仓整卷过户","Transfer coil title",CoilTitleTransfer.PERMISSION,
        new PageOperation("warehousing/coil-title","/warehousing/coil-title","同仓整卷过户","Transfer coil title",
            "POST","/api/bulk-coil-title-transfers"),
        WarehousingPage.flowPage("同仓整卷过户","Transfer coil title","POST","/api/bulk-coil-title-transfers"));
    return new Descriptor(CoilTitleTransfer.OBJECT,"整卷货权过户","Coil title transfer","bulk-warehousing",
        1,Category.TRANSACTION,List.of(),List.of(operation),new DataAccess("TENANT",List.of(
            new DataResource(CoilTitleTransfer.RESOURCE,"","整卷货权过户","Coil title transfer",List.of())),
        Map.of("transfer",Set.of("ALL"))));
  }
}
