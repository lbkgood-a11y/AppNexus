package com.appnexus.masterdata.product;
import com.appnexus.masterdata.MasterData;
import com.appnexus.masterdata.domain.product.AttributeRulePolicy;
import java.util.*;
import tools.jackson.databind.*;
/** 规则JSON技术适配；只解析对象并保留原标量类型，不执行脚本、SQL或表达式。 */
final class ProductAttributeRules {
 private static final ObjectMapper JSON=new ObjectMapper();
 private ProductAttributeRules(){}
 /** @param type 固定类型 @param rules JSON对象或null @return 经纯领域白名单验证的原JSON对象，不静默忽略未知键 */
 static JsonNode definition(String type,String rules){
  try{JsonNode node=rules==null?JSON.createObjectNode():JSON.readTree(rules);if(!node.isObject())throw invalid();AttributeRulePolicy.definition(type,decode(node));return node;}
  catch(MasterData.Rejection error){throw error;}catch(Exception error){throw invalid();}
 }
 /** @param type 固定类型 @param rules 白名单JSON对象 @param value 标量 @param count 枚举总数；原错误码保持 */
 static void value(String type,String rules,String value,int count){
  try{AttributeRulePolicy.value(type,decode(definition(type,rules)),value,count);}
  catch(MasterData.Rejection error){throw error;}catch(Exception error){throw invalid();}
 }
 private static AttributeRulePolicy.Rules decode(JsonNode node){
  var values=new LinkedHashMap<String,AttributeRulePolicy.Scalar>();
  for(String key:node.propertyNames()){var v=node.get(key);values.put(key,new AttributeRulePolicy.Scalar(v.asText(),v.isString(),v.isNumber(),v.isIntegralNumber(),v.isIntegralNumber()?v.asInt():0));}
  return new AttributeRulePolicy.Rules(values);
 }
 private static MasterData.Rejection invalid(){return new MasterData.Rejection(400,"PRODUCT_ATTRIBUTE_RULE_INVALID");}
}
