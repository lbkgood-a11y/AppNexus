package com.appnexus.masterdata.domain.partner;

import java.util.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import static com.appnexus.masterdata.api.masterdata.MasterData.*;

/** 客商及明细的纯领域约束；日期由应用层提供，不读取系统时钟、数据库或会话。 */
public final class PartnerRules {
  private PartnerRules() {}
  /** 固定字段白名单，列名仅由这些构建内标识生成；不允许请求决定 SQL 标识符。 */
  public static final List<String> CONTACT=List.of("contactType","contactName","phone","address","isDefault","email","remarks");
  public static final List<String> BANK=List.of("taxAccount","basicAccount","isDefault","bankName","accountNumber","accountAlias","currency","cnaps","swiftCode","remarks");
  /** 多重角色以逗号存储；保留原 BOTH 语义，拒绝重复、空片段及未知角色。 */
  public static boolean validTypes(String value){
    if("BOTH".equals(value))return true;
    var types=Arrays.asList(value.split(",",-1));
    return !types.isEmpty()&&new HashSet<>(types).size()==types.size()&&types.stream().allMatch(type->type.matches("[A-Z][A-Z0-9_]{0,63}"));
  }
  /**
   * 校验完整聚合；业务日期不转时间点，金额单位万元，最多18位整数及6位小数。
   * @param input 主档及明细输入；明细 null 表示保留，空集合表示清空
   * @param values 已通过字段白名单并补齐缺省空串的主档属性
   * @param today 应用提供的当前 UTC 自然日，成立日期不得晚于此日，不接受客户端覆盖
   * @throws Rejection 主档格式、明细数量、默认项或银行字段不符合约束
   */
  public static void validate(Input input,Map<String,String> values,LocalDate today){
    try{
      String date=values.get("establishedOn");if(!date.isEmpty()&&(!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")||LocalDate.parse(date).isAfter(today)))throw invalid();
      String capital=values.get("registeredCapital");if(!capital.isEmpty()&&(!capital.matches("[0-9]{1,18}(\\.[0-9]{1,6})?")||new BigDecimal(capital).signum()<0))throw invalid();
      if(!values.get("registeredCurrency").matches("(?:[A-Z]{3})?"))throw invalid();
      String listed=values.get("listed");if(!listed.isEmpty()&&!Set.of("true","false").contains(listed))throw invalid();
    }catch(java.time.DateTimeException|NumberFormatException e){throw invalid();}
    validateRows(input.contacts(),false);validateRows(input.bankAccounts(),true);
  }
  /** 默认联系人/银行账户各最多一个；默认值、基本户与税务专用标志必须是显式布尔文本。 */
  private static void validateRows(List<DetailInput> rows,boolean bank){
    if(rows==null)return;if(rows.size()>100)throw invalid();
    var ids=new HashSet<String>();int defaults=0,basic=0;
    for(var row:rows){
      if(row==null||row.values()==null||!(bank?BANK:CONTACT).containsAll(row.values().keySet()))throw invalid();
      if(row.id()!=null&&!row.id().isBlank()){try{if(!UUID.fromString(row.id()).toString().equals(row.id())||!ids.add(row.id()))throw invalid();}catch(IllegalArgumentException e){throw invalid();}}
      for(var entry:row.values().entrySet())if(entry.getValue()==null||entry.getValue().length()>500||entry.getValue().chars().anyMatch(Character::isISOControl))throw invalid();
      for(String key:bank?List.of("bankName","accountNumber","currency"):List.of("contactType","contactName","phone","address"))if(value(row,key).isBlank())throw invalid();
      for(String key:bank?List.of("taxAccount","basicAccount","isDefault"):List.of("isDefault"))if(!Set.of("true","false").contains(value(row,key)))throw invalid();
      if("true".equals(value(row,"isDefault")))defaults++;
      if(bank){
        if("true".equals(value(row,"basicAccount")))basic++;
        if(!value(row,"currency").matches("[A-Z]{3}")||!value(row,"cnaps").matches("(?:[0-9]{12})?")||!value(row,"swiftCode").matches("(?:[A-Z]{6}[A-Z0-9]{2}(?:[A-Z0-9]{3})?)?"))throw invalid();
      }else if(!value(row,"email").isEmpty()&&(value(row,"email").length()>200||!value(row,"email").matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")))throw invalid();
    }
    if(defaults>1||basic>1)throw invalid();
  }
  private static String value(DetailInput row,String field){return row.values().getOrDefault(field,"").trim();}
  private static Rejection invalid(){return new Rejection(400,"MASTER_DATA_INPUT_INVALID");}
}
