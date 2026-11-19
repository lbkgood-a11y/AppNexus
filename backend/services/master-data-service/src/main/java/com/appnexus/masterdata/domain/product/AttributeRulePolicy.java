package com.appnexus.masterdata.domain.product;
import com.appnexus.masterdata.MasterData;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
/** 属性白名单纯规则；JSON语法在适配器解析，核心只执行长度、数值、日期及枚举边界。 */
public final class AttributeRulePolicy {
 private AttributeRulePolicy(){}
 /** @param text 原标量文本 @param string 是否JSON字符串 @param number 是否JSON数值 @param integral 是否JSON整数 @param integer 按原JSON语义转换的整数值 */
 public record Scalar(String text,boolean string,boolean number,boolean integral,int integer){
  public boolean isString(){return string;} public boolean isNumber(){return number;} public boolean isIntegralNumber(){return integral;}
  public String asText(){return text;} public int asInt(){return integer;}
 }
 /** @param values 已解析对象全部键值，未知键同样保留供拒绝 */
 public record Rules(Map<String,Scalar> values){
  public Set<String> keys(){return values.keySet();} public boolean has(String key){return values.containsKey(key);} public Scalar get(String key){return values.get(key);}
 }
 /** @param type 固定属性类型 @param n 完整解析规则对象，空对象表示无附加规则；非法边界按原码拒绝 */
 public static void definition(String type,Rules n){
   Set<String> allowed=switch(type){case "TEXT"->Set.of("minLength","maxLength");case "INTEGER","DECIMAL"->Set.of("min","max");case "DATE"->Set.of("minDate","maxDate");case "SINGLE_SELECT","MULTI_SELECT"->Set.of("minItems","maxItems");case "BOOLEAN"->Set.of();default->throw invalid();};
   for(String key:n.keys())if(!allowed.contains(key))throw invalid();
   for(String key:List.of("minLength","maxLength","minItems","maxItems"))if(n.has(key)&&(!n.get(key).isIntegralNumber()||n.get(key).asInt()<0||n.get(key).asInt()>(key.endsWith("Items")?50:2000)))throw invalid();
   if(n.has("minLength")&&n.has("maxLength")&&n.get("minLength").asInt()>n.get("maxLength").asInt()||n.has("minItems")&&n.has("maxItems")&&n.get("minItems").asInt()>n.get("maxItems").asInt())throw invalid();
   if(type.equals("SINGLE_SELECT")&&(n.has("minItems")&&n.get("minItems").asInt()>1||n.has("maxItems")&&n.get("maxItems").asInt()!=1))throw invalid();
   BigDecimal min=number(n,"min"),max=number(n,"max");if(min!=null&&max!=null&&min.compareTo(max)>0)throw invalid();
   LocalDate minDate=date(n,"minDate"),maxDate=date(n,"maxDate");if(minDate!=null&&maxDate!=null&&minDate.isAfter(maxDate))throw invalid();
 }
 /** @param type 固定属性类型 @param n 已完整解析规则 @param value 标量文本 @param count 标准及自定义枚举总数，单位项；草稿已填值同样校验 */
 public static void value(String type,Rules n,String value,int count){
  definition(type,n);
  try{
   if(type.equals("TEXT")){int length=value.codePointCount(0,value.length());if(n.has("minLength")&&length<n.get("minLength").asInt()||n.has("maxLength")&&length>n.get("maxLength").asInt())throw invalid();}
   if(type.equals("INTEGER")||type.equals("DECIMAL")){var v=new BigDecimal(value);var min=number(n,"min");var max=number(n,"max");if(min!=null&&v.compareTo(min)<0||max!=null&&v.compareTo(max)>0)throw invalid();}
   if(type.equals("DATE")){var v=LocalDate.parse(value);var min=date(n,"minDate");var max=date(n,"maxDate");if(min!=null&&v.isBefore(min)||max!=null&&v.isAfter(max))throw invalid();}
   if(type.endsWith("SELECT")){if(n.has("minItems")&&count<n.get("minItems").asInt()||n.has("maxItems")&&count>n.get("maxItems").asInt())throw invalid();}
  }catch(MasterData.Rejection e){throw e;}catch(Exception e){throw invalid();}
 }
 private static BigDecimal number(Rules n,String key){if(!n.has(key))return null;var value=n.get(key);if(!value.isString()&&!value.isNumber())throw invalid();String text=value.asText();if(!text.matches("-?[0-9]{1,18}(\\.[0-9]{1,12})?"))throw invalid();return new BigDecimal(text);}
 private static LocalDate date(Rules n,String key){if(!n.has(key))return null;if(!n.get(key).isString())throw invalid();return LocalDate.parse(n.get(key).asText());}
 private static MasterData.Rejection invalid(){return new MasterData.Rejection(400,"PRODUCT_ATTRIBUTE_RULE_INVALID");}
}
