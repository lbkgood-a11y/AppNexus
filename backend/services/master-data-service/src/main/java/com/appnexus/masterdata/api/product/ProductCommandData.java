package com.appnexus.masterdata.api.product;
import java.util.*;
import com.appnexus.masterdata.api.product.ProductCatalogData.*;
/** 商品公开操作稳定数据契约；不包含HTTP、SQL、框架及内部实现类型。 */
public final class ProductCommandData {
 private ProductCommandData(){}
/** 显式启停；目标仅ENABLED/DISABLED，携带商品当前非负版本。 */
 public record Status(String status,Long version){
  /** 内部调用可传原始long，HTTP仍按可空规范字段拒绝缺省。 */
  public Status(String status,long version){this(status,Long.valueOf(version));}
 }
/** 草稿归档恢复请求，版本必填；归档非删除，不能回收编码。 */
 public record Version(Long version){
  /** 内部调用使用非负long，HTTP缺省仍可识别。 */
  public Version(long version){this(Long.valueOf(version));}
 }
/**
  * 旧规格人工确认只追加审查历史，不完成结构、不改原文；后续仍须停用并完整迁移。
  * @param disposition 仅CONFIRMED_NONE或DEFERRED_SPEC
  * @param reason 1至1000字符的确认依据，不包含敏感凭据
  * @param version 商品当前CAS，非负；审查成功只推进version，不推进structure_revision
  */
 public record LegacyReview(String disposition,String reason,Long version){}
}
