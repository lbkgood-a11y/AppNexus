package com.appnexus.masterdata.api.product;
import java.util.*;
import com.appnexus.masterdata.api.product.ProductCatalogData.*;
/** 商品公开操作稳定数据契约；不包含HTTP、SQL、框架及内部实现类型。 */
public final class ProductCatalogPages {
 private ProductCatalogPages(){}
/** 单位分页结果；总数使用与行完全相同的租户/筛选条件。 */
  public record UnitPage(List<Unit> rows,long total) {}
/** 分类分页结果；先验证全树合法，再返回指定父节点的当前层。 */
  public record CategoryPage(List<Category> rows,long total) {}
}
