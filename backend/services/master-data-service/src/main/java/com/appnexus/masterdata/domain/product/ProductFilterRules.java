package com.appnexus.masterdata.domain.product;
import com.appnexus.masterdata.api.masterdata.MasterData.Rejection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
/** 动态标量过滤纯规则；不生成SQL，不接受客户端列名或表达式。 */
public final class ProductFilterRules {
 private ProductFilterRules(){}
 /** @param type 真实可搜索属性类型 @param scale 小数声明精度，仅DECIMAL使用 @param operator eq/gte/lte/between，按类型收窄 @param input 已检查非空且无null的文本值 @return 精确绑定值；非法范围及格式按原错误码拒绝 */
 public static List<Object> scalar(String type,int scale,String operator,List<String> input){
  var values=new ArrayList<Object>();
   if(!Set.of("eq","gte","lte","between").contains(operator)||operator.equals("between")&&input.size()!=2||!operator.equals("between")&&input.size()!=1)throw invalid();
   try{
    switch(type){
     case "INTEGER"->{for(String value:input){if(!value.matches("-?(0|[1-9][0-9]*)"))throw invalid();values.add(Long.valueOf(value));}}
     case "DECIMAL"->{for(String value:input){if(!value.matches("-?[0-9]+(\\.[0-9]+)?"))throw invalid();var number=new BigDecimal(value);if(number.scale()>scale||number.precision()-number.scale()>18)throw invalid();values.add(number);}}
     case "DATE"->{if(!Set.of("eq","between").contains(operator))throw invalid();for(String value:input)values.add(LocalDate.parse(value));}
     case "BOOLEAN"->{if(!operator.equals("eq")||!Set.of("true","false").contains(input.getFirst()))throw invalid();values.add(Boolean.valueOf(input.getFirst()));}
     default->throw invalid();
    }
   }catch(IllegalArgumentException|java.time.DateTimeException e){throw invalid();}
   if(operator.equals("between")&&((Comparable)values.getFirst()).compareTo(values.get(1))>0)throw invalid();
  return values;
 }
 private static Rejection invalid(){return new Rejection(400,"PRODUCT_V1_INPUT_INVALID");}
}
