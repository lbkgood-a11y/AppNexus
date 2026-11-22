package com.appnexus.bulkwarehousing.adapter.integration;

import com.appnexus.bulkwarehousing.api.InboundOperations;
import com.appnexus.contracts.businessobject.BusinessObjects.*;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 入仓履约唯一对象声明。专用进程内命令使用同一权限编码，无通用工作流或打印操作。 */
public final class InboundReceivingDeclaration implements Provider {
  private final String object;
  private final String nameZh;
  private final String nameEn;

  /** @param object 本模块的唯一对象编码 @param nameZh 中文名 @param nameEn 英文名 */
  public InboundReceivingDeclaration(String object, String nameZh, String nameEn) {
    this.object = object;
    this.nameZh = nameZh;
    this.nameEn = nameEn;
  }

  /** {@inheritDoc} */
  @Override public Descriptor descriptor() {
    var commands=Arrays.stream(InboundOperations.values()).filter(op -> op.object().equals(object)).toList();
    var resource=commands.getFirst().resource();
    var operations=commands.stream()
        .map(op -> WarehousingPage.flow(object,op.code(),op.nameZh(),op.nameEn(),op.permission(),"POST",switch(op) {
          case ISSUE -> "/api/bulk-inbound-receipts:issue";
          case TAKE_CUSTODY -> "/api/bulk-inbound-receipts:take-custody";
          case RECEIVE -> "/api/bulk-inbound-receipts";
          case REGISTER_TITLE -> "/api/bulk-inbound-title-registrations";
          case RELEASE_QUALITY -> "/api/bulk-inbound-quality-releases";
        }))
        .toList();
    var scopes=commands.stream().collect(Collectors.toMap(InboundOperations::code, op -> Set.of("ALL")));
    return new Descriptor(object,nameZh,nameEn,"bulk-warehousing",1,
        Category.TRANSACTION,List.of(),operations,new DataAccess("TENANT",List.of(
            new DataResource(resource,"",nameZh,nameEn,List.of())),scopes));
  }
}
