package com.appnexus.masterdata.domain.product;
import com.appnexus.masterdata.api.masterdata.MasterData.Rejection;
import java.util.List;
/** 完整性状态纯规则；事实由可信租户查询提供，不在领域访问数据库或时钟。 */
public final class ProductCompletenessRules {
 private ProductCompletenessRules(){}
 /** @param status 当前商品状态 @param structure 当前结构状态 @param issues 实际完整性问题 @return 保留历史待处理，否则按问题判断COMPLETE/INCOMPLETE；启用态不完整必须拒绝并回滚 */
 public static String state(String status,String structure,List<String> issues){
  if("ENABLED".equals(status)&&!"LEGACY_PENDING".equals(structure)&&!issues.isEmpty())throw new Rejection(400,"PRODUCT_COMPLETION_REQUIRED");
  return "LEGACY_PENDING".equals(structure)?"LEGACY_PENDING":issues.isEmpty()?"COMPLETE":"INCOMPLETE";
 }
}
