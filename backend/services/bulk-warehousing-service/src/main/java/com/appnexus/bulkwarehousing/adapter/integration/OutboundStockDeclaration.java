package com.appnexus.bulkwarehousing.adapter.integration;

import com.appnexus.bulkwarehousing.api.OutboundOperations;
import com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.appnexus.contracts.businessobject.DedicatedOperations;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 库存可发和监管限制各自保有唯一对象声明。 */
public final class OutboundStockDeclaration implements Provider {
  private final String object;
  private final String zh;
  private final String en;

  /** @param object 对象编码 @param zh 中文名 @param en 英文名 */
  public OutboundStockDeclaration(String object,String zh,String en) {
    this.object=object;
    this.zh=zh;
    this.en=en;
  }

  /** {@inheritDoc} */
  @Override public Descriptor descriptor() {
    var actions=Arrays.stream(OutboundOperations.values()).filter(op -> op.object().equals(object)).toList();
    var operations=actions.stream().map(OutboundStockDeclaration::operation).toList();
    var scopes=actions.stream().collect(Collectors.toMap(OutboundOperations::code,op -> Set.of("ALL")));
    return new Descriptor(object,zh,en,"bulk-warehousing",1,Category.TRANSACTION,List.of(),operations,
        new DataAccess("TENANT",List.of(new DataResource(actions.getFirst().resource(),"",zh,en,List.of())),scopes));
  }
  private static Operation operation(OutboundOperations op) {
    if (op==OutboundOperations.CHECK) {
      return WarehousingPage.flow(op.object(),op.code(),op.nameZh(),op.nameEn(),op.permission(),"GET",
          "/api/bulk-outbound-stock/{id}/eligibility");
    }
    if (op==OutboundOperations.READ_RESTRICTIONS) {
      var path="/api/bulk-stock-restrictions/by-receipt/{id}";
      return DedicatedOperations.operation(op.object(),op.code(),op.nameZh(),op.nameEn(),op.permission(),
          WarehousingPage.flowPage(op.nameZh(),op.nameEn(),"GET",path),
          WarehousingPage.transitPage(op.nameZh(),op.nameEn(),"GET",path));
    }
    String path=switch(op) {
      case FREEZE -> "/api/bulk-stock-restrictions";
      case LIFT -> "/api/bulk-stock-restrictions:lift";
      default -> null;
    };
    return path==null ? DedicatedOperations.operation(op.object(),op.code(),op.nameZh(),op.nameEn(),op.permission())
        : DedicatedOperations.operation(op.object(),op.code(),op.nameZh(),op.nameEn(),op.permission(),
            WarehousingPage.transitPage(op.nameZh(),op.nameEn(),"POST",path));
  }
}
