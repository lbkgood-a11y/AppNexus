package com.appnexus.bulkwarehousing.adapter.integration;

import com.appnexus.bulkwarehousing.api.InboundOperations;
import com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.appnexus.contracts.businessobject.DedicatedOperations;
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
    var operations=commands.stream().map(this::operation).collect(Collectors.toCollection(java.util.ArrayList::new));
    var scopes=commands.stream().collect(Collectors.toMap(InboundOperations::code, op -> Set.of("ALL")));
    if(InboundOperations.OBJECT.equals(object)) {
      operations.add(DedicatedOperations.operation("BulkInboundReceipt","record_entry","登记入场接管","Record inbound entry",
          InboundOperations.TAKE_CUSTODY.permission(),new PageOperation("warehousing/inbound-notices","/warehousing/inbound-notices",
          "登记入场接管","Record inbound entry","POST","/api/bulk-inbound-entries")));
      operations.add(DedicatedOperations.operation("BulkInboundReceipt","confirm_document","确认分批实收","Confirm batch receipt",
          InboundOperations.RECEIVE.permission(),new PageOperation("warehousing/inbound-entries","/warehousing/inbound-entries",
          "确认分批实收","Confirm batch receipt","POST","/api/bulk-inbound-receipt-documents")));
      scopes.put("record_entry",Set.of("ALL"));
      scopes.put("confirm_document",Set.of("ALL"));
    }
    return new Descriptor(object,nameZh,nameEn,"bulk-warehousing",1,
        Category.TRANSACTION,List.of(),operations,new DataAccess("TENANT",List.of(
            new DataResource(resource,"",nameZh,nameEn,List.of())),scopes));
  }
  /** 原业务命令保留固定接口；签发额外绑定查询页的单独表单。 */
  private Operation operation(InboundOperations operation) {
    String path=switch(operation) {
      case ISSUE -> "/api/bulk-inbound-receipts:issue";
      case TAKE_CUSTODY -> "/api/bulk-inbound-receipts:take-custody";
      case RECEIVE -> "/api/bulk-inbound-receipts";
      case REGISTER_TITLE -> "/api/bulk-inbound-title-registrations";
      case RELEASE_QUALITY -> "/api/bulk-inbound-quality-releases";
    };
    if (!Set.of(InboundOperations.ISSUE,InboundOperations.TAKE_CUSTODY,InboundOperations.RECEIVE).contains(operation)) {
      return WarehousingPage.flow(object,operation.code(),operation.nameZh(),operation.nameEn(),operation.permission(),"POST",path);
    }
    var pages=new java.util.ArrayList<PageOperation>();
    pages.add(WarehousingPage.flowPage(operation.nameZh(),operation.nameEn(),"POST",path));
    pages.add(new PageOperation("warehousing/inbound-notices","/warehousing/inbound-notices",
        operation.nameZh(),operation.nameEn(),"POST",path));
    return DedicatedOperations.operation(object,operation.code(),operation.nameZh(),operation.nameEn(),operation.permission(),
        pages.toArray(PageOperation[]::new));
  }
}
