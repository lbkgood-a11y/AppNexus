package com.appnexus.masterdata.api.product;
import java.util.List;
/** 商品列表稳定筛选协议，不包含SQL列名或表达式。 */
public interface ProductListFilter {
 /**
  * 动态筛选严格绑定一个确定配置，最多3条件且AND连接，不支持TEXT/历史/JSONPath。
  * @param templateItemId 当前配置标记searchable的模板项
  * @param operator eq/gte/lte/between/anyOf，按类型限定
  * @param values 标量十进制/日期/布尔字符串，枚举为固定选项UUID
  */
 public record AttributeFilter(String templateItemId,String operator,List<String> values){}
}
