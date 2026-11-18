package com.appnexus.masterdata.product;

import com.appnexus.masterdata.MasterData;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import tools.jackson.databind.*;

/**
 * 属性声明式规则白名单；只执行长度、数值/日期边界及枚举数量，不执行正则、脚本或SQL。
 * 定义与值共用同一解释器，避免配置看似生效但写入端未执行。
 */
final class ProductAttributeRules {
 private static final ObjectMapper JSON=new ObjectMapper();
 private ProductAttributeRules(){}
 /** 发布前校验规则类型与边界；NULL或空对象表示无附加规则。 */
 static JsonNode definition(String type,String rules){
  try{
   JsonNode n=rules==null?JSON.createObjectNode():JSON.readTree(rules);
   if(!n.isObject())throw invalid();
   Set<String> allowed=switch(type){case "TEXT"->Set.of("minLength","maxLength");case "INTEGER","DECIMAL"->Set.of("min","max");case "DATE"->Set.of("minDate","maxDate");case "SINGLE_SELECT","MULTI_SELECT"->Set.of("minItems","maxItems");case "BOOLEAN"->Set.of();default->throw invalid();};
   for(String key:n.propertyNames())if(!allowed.contains(key))throw invalid();
   for(String key:List.of("minLength","maxLength","minItems","maxItems"))if(n.has(key)&&(!n.get(key).isIntegralNumber()||n.get(key).asInt()<0||n.get(key).asInt()>(key.endsWith("Items")?50:2000)))throw invalid();
   if(n.has("minLength")&&n.has("maxLength")&&n.get("minLength").asInt()>n.get("maxLength").asInt()||n.has("minItems")&&n.has("maxItems")&&n.get("minItems").asInt()>n.get("maxItems").asInt())throw invalid();
   if(type.equals("SINGLE_SELECT")&&(n.has("minItems")&&n.get("minItems").asInt()>1||n.has("maxItems")&&n.get("maxItems").asInt()!=1))throw invalid();
   BigDecimal min=number(n,"min"),max=number(n,"max");if(min!=null&&max!=null&&min.compareTo(max)>0)throw invalid();
   LocalDate minDate=date(n,"minDate"),maxDate=date(n,"maxDate");if(minDate!=null&&maxDate!=null&&minDate.isAfter(maxDate))throw invalid();
   return n;
  }catch(MasterData.Rejection e){throw e;}catch(Exception e){throw invalid();}
 }
 /** 已填值必须满足规则；草稿只豁免缺项，不豁免非法值。 */
 static void value(String type,String rules,String value,int count){
  JsonNode n=definition(type,rules);
  try{
   if(type.equals("TEXT")){int length=value.codePointCount(0,value.length());if(n.has("minLength")&&length<n.get("minLength").asInt()||n.has("maxLength")&&length>n.get("maxLength").asInt())throw invalid();}
   if(type.equals("INTEGER")||type.equals("DECIMAL")){var v=new BigDecimal(value);var min=number(n,"min");var max=number(n,"max");if(min!=null&&v.compareTo(min)<0||max!=null&&v.compareTo(max)>0)throw invalid();}
   if(type.equals("DATE")){var v=LocalDate.parse(value);var min=date(n,"minDate");var max=date(n,"maxDate");if(min!=null&&v.isBefore(min)||max!=null&&v.isAfter(max))throw invalid();}
   if(type.endsWith("SELECT")){if(n.has("minItems")&&count<n.get("minItems").asInt()||n.has("maxItems")&&count>n.get("maxItems").asInt())throw invalid();}
  }catch(MasterData.Rejection e){throw e;}catch(Exception e){throw invalid();}
 }
 private static BigDecimal number(JsonNode n,String key){if(!n.has(key))return null;var value=n.get(key);if(!value.isString()&&!value.isNumber())throw invalid();String text=value.asText();if(!text.matches("-?[0-9]{1,18}(\\.[0-9]{1,12})?"))throw invalid();return new BigDecimal(text);}
 private static LocalDate date(JsonNode n,String key){if(!n.has(key))return null;if(!n.get(key).isString())throw invalid();return LocalDate.parse(n.get(key).asText());}
 private static MasterData.Rejection invalid(){return new MasterData.Rejection(400,"PRODUCT_ATTRIBUTE_RULE_INVALID");}
}
