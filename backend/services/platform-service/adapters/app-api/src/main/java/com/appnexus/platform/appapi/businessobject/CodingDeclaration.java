package com.appnexus.platform.appapi.businessobject;

import com.appnexus.contracts.businessobject.*;
import static com.appnexus.contracts.businessobject.BusinessObjects.*;
import java.util.*;

/** 编码器配置对象唯一声明；使用所属业务对象管理权限，与独立编码规则通过版本引用关联。 */
public final class CodingDeclaration implements Provider {
  @Override public Descriptor descriptor(){return new Descriptor("CodingEncoder","编码器","Encoder","business-object",2,Category.CONFIGURATION,List.of(),List.of(
    operation("list","读取编码器","Read encoders","GET"),operation("save","保存编码器","Save encoder","PUT"),operation("ruleOptions","读取规则候选","Read rule options","GET"),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("CodingEncoder", "fieldOptions", "查询编码字段选项", "Read coding options", ""),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("CodingEncoder", "issue", "发放业务编码", "Issue business code", "")));}
  private Operation operation(String code,String zh,String en,String method){
    String id="CodingEncoder."+code;
    var interaction=new InteractionContract(id,1,"CodingEncoder",code,zh,en,InteractionContract.Trigger.USER_ACTION,List.of(
      new InteractionContract.Capability(InteractionContract.Service.BUSINESS,InteractionContract.Completion.SYNCHRONOUS,id,"",List.of(),Set.of(),"coding.encoder."+code,1)));
    return new Operation(code,zh,en,"business.object.configuration:manage",id,new PageOperation("business-objects/index","/business-objects/workbench",zh,en,method,
      code.equals("ruleOptions")?"/api/business-objects/{type}/coding-rules":"/api/business-objects/{type}/encoders"+(code.equals("save")?"/{key}":"")),List.of(),interaction);
  }
}
