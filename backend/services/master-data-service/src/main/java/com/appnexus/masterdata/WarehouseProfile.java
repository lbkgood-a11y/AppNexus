package com.appnexus.masterdata;
import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDate;
/** 仓库资料约束；数量按十进制文本传递，避免浮点精度损失；旧记录允许缺省，新值必须符合格式。 */
final class WarehouseProfile {
 private WarehouseProfile(){}
 /** 面积单位平方米、库容单位吨；最多18位整数和6位小数；坐标单位度，必须成对填写，日期为业务日期。 */
 static void validate(Map<String,String> values){
  for(String field:List.of("totalArea","capacity"))if(!values.get(field).matches("(?:[0-9]{1,18}(\\.[0-9]{1,6})?)?"))throw invalid();
  for(String field:List.of("personalGuarantee","wholeLease"))if(!Set.of("","true","false").contains(values.get(field)))throw invalid();
  try{
   for(String field:List.of("builtOn","openedOn")){String value=values.get(field);if(!value.isEmpty()&&(!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")||!LocalDate.parse(value).toString().equals(value)))throw invalid();}
   if(values.get("longitude").isEmpty()!=values.get("latitude").isEmpty())throw invalid();
   for(String field:List.of("longitude","latitude")){String value=values.get(field);if(!value.isEmpty()&&(!value.matches("-?[0-9]{1,3}(\\.[0-9]{1,6})?")||new BigDecimal(value).abs().compareTo(BigDecimal.valueOf(field.equals("longitude")?180:90))>0))throw invalid();}
  }catch(java.time.DateTimeException|NumberFormatException e){throw invalid();}
  String email=values.get("email");if(!email.isEmpty()&&(email.length()>200||!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")))throw invalid();
 }
 private static MasterData.Rejection invalid(){return new MasterData.Rejection(400,"MASTER_DATA_INPUT_INVALID");}
}
