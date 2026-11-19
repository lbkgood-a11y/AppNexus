package com.appnexus.masterdata.application.product;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.Map;
/** 商品固定目录输出端口；禁止客户端提供SQL、表名、租户或任意排序列。 */
public interface ProductCatalogReads {
  /** @param c 已授权主体 @param kind 仅category/unit @param keyword 最多200字符，按绑定参数匹配 @param page 1–1000000 @param size 1–100 @return 当前租户启用候选与总数 */
  Object options(VerifiedTenantContext c,String kind,String keyword,int page,int size);
  /** @param c 已授权主体 @param categoryId 分类UUID @param page 1–1000000 @param size 1–100 @return 明确发布版本目录，不升级原商品 */
  Object configurations(VerifiedTenantContext c,String categoryId,int page,int size);
  /** @param c 已授权主体 @param id 固定配置UUID @return 原字段、历史选项和四槽版本投影；缺失及跨租户统一冲突 */
  Map<String,Object> configuration(VerifiedTenantContext c,String id);
  /** @param c 可信主体 @param category 分类UUID可空 @param config 固定配置UUID可空 @param form 商品形式可空 @param unit 单位UUID可空 @param fresh 是否要求当前发布/启用定义 @param locks 写入时按原共享锁顺序读取，预检false；不写数据库 */
  void validate(VerifiedTenantContext c,String category,String config,String form,String unit,boolean fresh,boolean locks);
}
