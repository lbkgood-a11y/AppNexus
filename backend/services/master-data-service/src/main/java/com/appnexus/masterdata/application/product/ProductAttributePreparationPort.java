package com.appnexus.masterdata.application.product;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.List;
import java.util.Map;
/** 属性准备输出端口；固定配置及选项读取，不接受 SQL、任意字段或可执行表达式。 */
public interface ProductAttributePreparationPort {
  /** @param context 可信主体 @param configuration 固定发布配置UUID @param item 模板项UUID @return 非规格类型、修订、精度及白名单验证快照，必须恰一项 */
  List<Map<String,Object>> definitions(VerifiedTenantContext context,String configuration,String item);
  /** @param context 可信主体 @param revision 固定修订UUID @param option 标准选项UUID @return 当前租户及修订内启用选项数量，必须为1 */
  int optionCount(VerifiedTenantContext context,String revision,String option);
  /** @param type 固定类型 @param validation 白名单声明式JSON，不接受脚本 @param value 标量文本 @param count 枚举总数，单位项；保持原规则上下限及错误码 */
  void validateRule(String type,String validation,String value,int count);
}
