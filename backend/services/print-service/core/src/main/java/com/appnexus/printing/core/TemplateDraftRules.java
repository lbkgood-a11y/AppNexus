package com.appnexus.printing.core;
import java.util.Set;
/** 模板草稿元数据纯规则；不读取JSON、数据库或调用渲染器。 */
public final class TemplateDraftRules {
  private TemplateDraftRules(){}
  /** @param code 1–64位稳定编码 @param contract 1–64位数据契约编码 @param name 去首尾空白后1–128字符 @param category A4、LABEL、WAYBILL或RECEIPT @param revision 新建-1，编辑读取非负版本 @return 元数据是否合法；内容安全由独立JSON适配器检查 */
  public static boolean valid(String code,String contract,String name,String category,long revision){
    return PrintRules.validCode(code)&&PrintRules.validCode(contract)&&name!=null&&!name.isBlank()
        &&name.trim().length()<=128&&Set.of("A4","LABEL","WAYBILL","RECEIPT").contains(category)&&revision>=-1;
  }
}
