package com.appnexus.printing.core;
import com.appnexus.contracts.printing.PrintRejection;
import com.appnexus.contracts.data.DataContract;
import java.util.*;
/** 打印场景字段与数据纯规则；只接受JDK JSON值，不依赖Jackson、SQL或技术执行器。 */
public final class PrintDataPolicy {
 private PrintDataPolicy(){}
 /** @param value 已解析对象、集合与标量 @param reserved 已有固定设计契约编码 @return 白名单租户契约元数据，保留原字段协议 */
 @SuppressWarnings("unchecked") public static Map<String,Object> cleanContract(Object value,Set<Object> reserved){
  if(!(value instanceof Map<?,?>))throw invalid();var definition=(Map<String,Object>)value;String code=text(definition,"code");if(!PrintRules.validCode(code)||reserved.contains(code))throw invalid();
  for(String label:List.of("nameZh","nameEn"))if(!(definition.get(label) instanceof String s)||s.isBlank()||s.length()>128)throw invalid();validateFields(definition.get("fields"),true);
  var clean=new LinkedHashMap<String,Object>();clean.put("code",code);clean.put("nameZh",definition.get("nameZh"));clean.put("nameEn",definition.get("nameEn"));clean.put("fields",definition.get("fields"));return clean;
 }
 /** @param value 已解析字段数组，最多50项 @param arrays 是否允许一层明细，明细不能再嵌套 */
 @SuppressWarnings("unchecked") private static void validateFields(Object value,boolean arrays){
  if(!(value instanceof List<?> fields)||fields.isEmpty()||fields.size()>50)throw invalid();var keys=new HashSet<String>();
  for(Object raw:fields){if(!(raw instanceof Map<?,?>))throw invalid();var field=(Map<String,Object>)raw;String key=text(field,"key");if(!key.matches("[A-Za-z][A-Za-z0-9_]{0,63}")||Set.of("constructor","prototype","__proto__").contains(key)||!keys.add(key))throw invalid();for(String label:List.of("labelZh","labelEn"))if(!(field.get(label) instanceof String s)||s.isBlank()||s.length()>128)throw invalid();if(!(field.get("sample") instanceof String sample)||sample.length()>2000)throw invalid();if(field.containsKey("columns")){if(!arrays)throw invalid();validateFields(field.get("columns"),false);}}
 }
 /** @param data 已解析请求事实 @param definition 同一原发布或业务契约；业务严格类型与历史文本协议分别保留 */
 @SuppressWarnings("unchecked") public static void validate(Object data,Map<String,Object> definition){
  if(!(data instanceof Map<?,?>))throw invalid();var values=(Map<String,Object>)data;var fields=(List<Map<String,Object>>)definition.get("fields");
  if(fields.stream().allMatch(f->f.containsKey("type"))){var schema=new DataContract(definition.get("code").toString(),((Number)definition.getOrDefault("version",1)).intValue(),"PRINT",fields.stream().map(f->new DataContract.Field(f.get("key").toString(),DataContract.Type.valueOf(f.get("type").toString()),Boolean.TRUE.equals(f.get("required")),Objects.toString(f.get("unit"),""))).toList());schema.validate(values);return;}
  for(var property:values.entrySet()){var field=fields.stream().filter(f->f.get("key").equals(property.getKey())).findFirst().orElseThrow(()->new PrintRejection("PRINT_CONTRACT_INVALID",400));Object value=property.getValue();if(field.containsKey("columns")){if(!(value instanceof List<?> rows)||rows.size()>500)throw invalid();for(Object row:rows)validate(row,Map.of("fields",field.get("columns")));}else if(!(value instanceof String text)||text.length()>2000)throw invalid();}
 }
 /** @param data 原单据事实 @param number 原单号；若正文明确包含documentNumber，必须与保存单号相同 */
 public static void documentNumber(Object data,String number){if(data instanceof Map<?,?> map&&map.containsKey("documentNumber")&&!number.equals(textValue(map.get("documentNumber"))))throw invalid();}
 private static String text(Map<String,Object> object,String key){return object.containsKey(key)?textValue(object.get(key)):"";}
 private static String textValue(Object value){if(value==null)return "null";return value instanceof String||value instanceof Number||value instanceof Boolean?value.toString():"";}
 private static PrintRejection invalid(){return new PrintRejection("PRINT_INPUT_INVALID",400);}
}
