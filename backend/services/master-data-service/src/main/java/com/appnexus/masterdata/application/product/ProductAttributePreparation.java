package com.appnexus.masterdata.application.product;
import com.appnexus.masterdata.api.product.ProductMainData.Value;
import com.appnexus.masterdata.api.masterdata.MasterData;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDate;
/** 复杂属性输入准备；定义和选项事实经固定输出端口取得，不依赖 JDBC 或 JSON。 */
public final class ProductAttributePreparation {
 private final ProductAttributePreparationPort port;
 /** @param port 当前聚合事务内的固定定义和选项读取端口 */
 public ProductAttributePreparation(ProductAttributePreparationPort port){this.port=port;}
 /** @param source 已验证原输入 @param revision 固定属性修订UUID @param type 类型编码 @param text 文本 @param integer 精确整数 @param decimal 精确小数 @param bool 布尔 @param date ISO自然日 */
 public record PreparedValue(Value source,String revision,String type,Object text,Object integer,Object decimal,Object bool,Object date){}
 /** @param c 已授权可信主体 @param config 固定配置UUID，可空且此时无属性 @param values 最多200项，null按空集合；标量与枚举互斥 @return 精确类型化值，不写库 */

 public List<PreparedValue> prepare(VerifiedTenantContext c,String config,List<Value> values){
  if(values==null)values=List.of();if(values.size()>200||config==null&&!values.isEmpty())throw invalid();
  var seen=new HashSet<String>();var result=new ArrayList<PreparedValue>();
  for(Value value:values){
   if(value==null)throw invalid();uuid(value.templateItemId());if(!seen.add(value.templateItemId()))throw invalid();
   var definitions=port.definitions(c,config,value.templateItemId());
   if(definitions.size()!=1)throw invalid();var definition=definitions.getFirst();String type=(String)definition.get("data_type"),revision=(String)definition.get("revision");
   Object text=null,integer=null,decimal=null,bool=null,date=null;List<String> options=value.optionIds()==null?List.of():value.optionIds();List<String> customs=value.customValues()==null?List.of():value.customValues();
   if(!Set.of("SINGLE_SELECT","MULTI_SELECT").contains(type)&&!customs.isEmpty())throw invalid();
   if(!Set.of("SINGLE_SELECT","MULTI_SELECT").contains(type)&&value.value()==null)throw invalid();
   try{switch(type){
    case "TEXT" -> {text(value.value(),2000,true);if(!options.isEmpty())throw invalid();text=value.value();}
    case "INTEGER" -> {if(value.value()==null||!value.value().matches("-?(0|[1-9][0-9]*)"))throw invalid();integer=Long.parseLong(value.value());}
    case "DECIMAL" -> {if(value.value()==null||!value.value().matches("-?[0-9]+(\\.[0-9]+)?"))throw invalid();var number=new BigDecimal(value.value());int scale=((Number)definition.get("numeric_scale")).intValue();if(number.scale()>scale||number.precision()-number.scale()>18)throw invalid();decimal=number;}
    case "BOOLEAN" -> {if(!Set.of("true","false").contains(value.value()))throw invalid();bool=Boolean.parseBoolean(value.value());}
    case "DATE" -> {date=LocalDate.parse(value.value());}
    case "SINGLE_SELECT","MULTI_SELECT" -> {if(value.value()!=null||options.size()+customs.size()==0||options.size()+customs.size()>50||type.equals("SINGLE_SELECT")&&options.size()+customs.size()!=1||new HashSet<>(options).size()!=options.size())throw invalid();}
    default -> throw invalid();
   }}catch(IllegalArgumentException|java.time.DateTimeException e){throw invalid();}
   if(!Set.of("SINGLE_SELECT","MULTI_SELECT").contains(type)&&!options.isEmpty())throw invalid();

   for(String option:options){uuid(option);if(port.optionCount(c,revision,option)!=1)throw invalid();}
   if(!customs.isEmpty()){if(!Boolean.TRUE.equals(definition.get("allow_custom"))||new HashSet<>(customs).size()!=customs.size())throw invalid();for(String custom:customs)text(custom,2000,true);}
   port.validateRule(type,(String)definition.get("validation"),value.value(),options.size()+customs.size());
   result.add(new PreparedValue(value,revision,type,text,integer,decimal,bool,date));
  }
  return result;
 }
 private static void text(String value,int max,boolean required){if(value==null){if(required)throw invalid();return;}if(required&&value.isBlank()||value.length()>max||value.chars().anyMatch(x->Character.isISOControl(x)&&x!='\n'&&x!='\r'&&x!='\t'))throw invalid();}
 private static void uuid(String id){try{if(!UUID.fromString(id).toString().equals(id))throw invalid();}catch(IllegalArgumentException|NullPointerException e){throw invalid();}}
 private static MasterData.Rejection invalid(){return new MasterData.Rejection(400,"PRODUCT_V1_INPUT_INVALID");}
}
