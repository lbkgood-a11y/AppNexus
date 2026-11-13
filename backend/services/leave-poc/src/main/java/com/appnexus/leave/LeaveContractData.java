package com.appnexus.leave;

import java.util.*;
import com.appnexus.contracts.document.InteractionContract;

/** 请假数据适配器；只接收已授权业务记录，不取数、不补缺失字段、不改变业务状态。 */
final class LeaveContractData {
  private LeaveContractData() {}
  /** 审批决策数据与策略依据共用同一计算，天数必须是业务存储的真实数值。 */
  static Map<String,Object> approval(Map<String,Object> row){
    Object days=row.get("days");
    if(!(days instanceof Number number))throw new com.appnexus.contracts.data.DataContract.Violation("days","DATA_FIELD_TYPE_INVALID");
    return checked(LeavePocService.SUBMIT_ACTION,InteractionContract.Service.WORKFLOW,Map.of("days",number,"requiresHr",number.intValue()>3));
  }
  /** 现有审批引擎使用字符串传输；仅在严格类型校验后显式序列化，禁止猜测或补默认值。 */
  static Map<String,String> facts(Map<String,Object> row){
    var result=new LinkedHashMap<String,String>();approval(row).forEach((key,value)->result.put(key,value.toString()));return Map.copyOf(result);
  }
  /** 打印投影只提供契约字段；不将申请原因、组织及人员信息透传打印服务。 */
  static Map<String,Object> printing(String id,Map<String,Object> row){
    var data=new LinkedHashMap<String,Object>();data.put("documentNumber",id);
    data.put("startDate",Objects.toString(row.get("start_date"),null));data.put("endDate",Objects.toString(row.get("end_date"),null));
    data.put("days",row.get("days"));data.put("state",row.get("state"));
    return checked(LeavePocService.PRINT_ACTION,InteractionContract.Service.PRINT,data);
  }
  private static Map<String,Object> checked(com.appnexus.contracts.document.Documents.Action action,InteractionContract.Service service,Map<String,Object> data){
    action.interaction().capabilities().stream().filter(c->c.service()==service).findFirst().orElseThrow().schema().validate(data);return Map.copyOf(data);
  }
}
