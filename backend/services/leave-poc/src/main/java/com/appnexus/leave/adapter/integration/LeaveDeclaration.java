package com.appnexus.leave.adapter.integration;
import static com.appnexus.leave.api.LeaveOperations.*;
import com.appnexus.contracts.businessobject.*;
import com.appnexus.contracts.businessobject.BusinessObjects.Field;
import static com.appnexus.contracts.businessobject.ObjectRecordMapping.*;
import java.util.*;
/** 请假唯一对象声明及公共运行映射，不承担提交、存储或事务规则。 */
public final class LeaveDeclaration implements BusinessObjects.Provider {
 private final com.appnexus.leave.application.LeavePocService rules;
 /** @param rules 原请假纯应用用例，提供能力执行前业务约束 */
 public LeaveDeclaration(com.appnexus.leave.application.LeavePocService rules){this.rules=rules;}
  private BusinessObjectRuntime runtime;
  /** 固定映射由对象所有者代码声明，不进入租户配置；所有能力共用同一运行器。 */
  public void runtimeFactory(ObjectRecordRuntimeFactory factory){runtime=factory.create(this,recordMapping(),rules::beforeCapability);}
  @Override public BusinessObjectRuntime runtime(){return Objects.requireNonNull(runtime,"OBJECT_RUNTIME_NOT_ASSEMBLED");}
  /** 请假现有文本身份表映射；派生是否人事审批只使用受控数值比较，不支持脚本。 */
  public static ObjectRecordMapping recordMapping(){
    var text=Encoding.TEXT;
    return new ObjectRecordMapping("leave_poc_request",new Column("tenant_id",text),new Column("id",text),new Column("organization_id",text),new Column("applicant",text),
      "basis_version","state","instance_id","last_delivery_id",new Column("person_id",text),Map.of(
        "days",new Value(new Column("days",text),Transform.VALUE,0),"requiresHr",new Value(new Column("days",text),Transform.GREATER_THAN,3),
        "documentNumber",new Value(new Column("id",text),Transform.VALUE,0),"startDate",new Value(new Column("start_date",text),Transform.DATE_TEXT,0),
        "endDate",new Value(new Column("end_date",text),Transform.DATE_TEXT,0),"state",new Value(new Column("state",text),Transform.VALUE,0)),
      Map.of("submit",new Transition(Set.of("SUBMITTED"),"ACCEPTED",true),"print",new Transition(Set.of("DRAFT","SUBMITTED","ACCEPTED","APPROVED","REJECTED","WITHDRAWN"),null,false)),
      new Receipt("leave_poc_approval_cycle",new Column("tenant_id",text),new Column("object_id",text),"basis_version","instance_id","delivery_id","result","completed_at"),false);
  }
  @Override public com.appnexus.contracts.businessobject.BusinessObjects.Descriptor descriptor() {
    return new com.appnexus.contracts.businessobject.BusinessObjects.Descriptor("LeaveRequest","请假单","Leave request","leave",2,com.appnexus.contracts.businessobject.BusinessObjects.Category.TRANSACTION,
      List.of(new com.appnexus.contracts.businessobject.BusinessObjects.Field("days","请假天数","Leave days","NUMBER","天",true),new com.appnexus.contracts.businessobject.BusinessObjects.Field("requiresHr","是否超过三天","More than three days","BOOLEAN","",true)),
      List.of(SUBMIT_OPERATION,PRINT_OPERATION),new com.appnexus.contracts.businessobject.BusinessObjects.DataAccess("TENANT",List.of(
        new com.appnexus.contracts.businessobject.BusinessObjects.DataResource("leave.request","","请假请求","Leave requests",List.of(
          protectedField("days","天数","Days",false),protectedField("requiresHr","人事审批条件","HR condition",false),protectedField("documentNumber","单据标识","Document number",true),
          protectedField("startDate","开始日期","Start date",true),protectedField("endDate","结束日期","End date",true),protectedField("state","状态","State",true)))),
        Map.of("submit",Set.of("ALL","ORGANIZATION","ORGANIZATION_SUBTREE"),"print",Set.of("ALL","ORGANIZATION","ORGANIZATION_SUBTREE"))));
  }
  private static com.appnexus.contracts.businessobject.BusinessObjects.DataField protectedField(String key,String zh,String en,boolean text){
    return new com.appnexus.contracts.businessobject.BusinessObjects.DataField(key,zh,en,"TEXT",text?Set.of("READ_ONLY","MASKED","HIDDEN"):Set.of("READ_ONLY","HIDDEN"));
  }
}
