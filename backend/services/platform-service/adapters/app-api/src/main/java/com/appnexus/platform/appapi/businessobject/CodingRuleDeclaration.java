package com.appnexus.platform.appapi.businessobject;

import com.appnexus.contracts.businessobject.*;
import static com.appnexus.contracts.businessobject.BusinessObjects.*;
import java.util.*;

/** 租户编码规则唯一声明；租户配置管理对象，不拥有业务正文、组织行范围或敏感字段。 */
public final class CodingRuleDeclaration implements Provider {
  private final BusinessObjectCommandRuntime commands;
  /** @param coding 延迟取得原编码配置管理用例 */
  public CodingRuleDeclaration(java.util.function.Supplier<com.appnexus.platform.businessobject.CodingManagement> coding){
    this.commands=new CodingCommandRuntime(coding,true);
  }
  @Override public Set<String> commandOperations(){return commands.inputTypes().keySet();}
  @Override public BusinessObjectCommandRuntime commandRuntime(){return commands;}
  @Override public Descriptor descriptor(){return new Descriptor("CodingRule","编码规则","Coding rule","business-object",1,Category.CONFIGURATION,List.of(),List.of(
    operation("list","读取编码规则","Read coding rules","GET"),operation("save","追加规则版本","Append rule version","PUT")));}
  private Operation operation(String code,String zh,String en,String method){
    String id="CodingRule."+code;
    var interaction=new InteractionContract(id,1,"CodingRule",code,zh,en,InteractionContract.Trigger.USER_ACTION,List.of());
    return new Operation(code,zh,en,"coding.rule:manage",id,new PageOperation("configuration/coding-rules","/configuration/coding-rules",zh,en,method,
      "/api/coding-rules"+(code.equals("save")?"/{key}":"")),List.of(),interaction);
  }
}
