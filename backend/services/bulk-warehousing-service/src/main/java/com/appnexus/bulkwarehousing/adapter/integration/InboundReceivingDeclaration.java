package com.appnexus.bulkwarehousing.adapter.integration;

import com.appnexus.bulkwarehousing.api.InboundOperations;
import com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.appnexus.contracts.businessobject.DedicatedOperations;
import java.util.Map;
import java.util.List;
import java.util.Set;

/** 入仓通知签发的唯一对象声明。专用进程内命令使用同一权限编码，无通用工作流或打印操作。 */
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
    var issue=InboundOperations.ISSUE;
    var operation=DedicatedOperations.operation("BulkInboundReceipt","issue",issue.nameZh(),issue.nameEn(),issue.permission(),
        new PageOperation("warehousing/inbound-notices","/warehousing/inbound-notices",
            issue.nameZh(),issue.nameEn(),"POST","/api/bulk-inbound-receipts:issue"));
    var update=DedicatedOperations.operation("BulkInboundReceipt","update","编辑入仓通知","Edit inbound notice",
        InboundOperations.UPDATE.permission(),new PageOperation("warehousing/inbound-notices","/warehousing/inbound-notices",
        "编辑入仓通知","Edit inbound notice","PUT","/api/bulk-inbound-notices/{id}"));
    var fields=List.of(
        new Field("noticeNo","入仓通知单号","Inbound notice number","TEXT","",false),
        new Field("appointmentNo","业务单号","Upstream document number","TEXT","",false),
        new Field("plannedConveyance","计划车牌或船号","Planned vehicle or vessel number","TEXT","",false),
        new Field("driver.driverName","司机姓名","Driver name","TEXT","",false),
        new Field("driver.driverPhone","联系电话","Driver phone","TEXT","",false),
        new Field("driver.driverIdNumber","证件号","Driver identification number","TEXT","",false),
        new Field("lines.contractNo","明细合同号","Line contract number","TEXT","",false));
    return new Descriptor(object,nameZh,nameEn,"bulk-warehousing",2,
        Category.TRANSACTION,fields,List.of(operation,update),new DataAccess("TENANT",List.of(
            new DataResource(issue.resource(),"",nameZh,nameEn,List.of(
              new DataField("driverName","司机姓名","Driver name","PERSON_NAME",Set.of("READ_ONLY","MASKED","HIDDEN")),
              new DataField("driverPhone","联系电话","Driver phone","PHONE",Set.of("READ_ONLY","MASKED","HIDDEN")),
              new DataField("driverIdNumber","证件号","Driver identification number","IDENTIFIER",Set.of("READ_ONLY","MASKED","HIDDEN"))))),Map.of(issue.code(),Set.of("ALL"),"update",Set.of("ALL"))),
        List.of(new CodingField("noticeNo",issue.code(),64)));
  }
}
