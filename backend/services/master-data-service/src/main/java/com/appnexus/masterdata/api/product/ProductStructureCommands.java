package com.appnexus.masterdata.api.product;
import java.util.*;
import com.appnexus.masterdata.api.product.ProductCatalogData.*;
/** 商品公开操作稳定数据契约；不包含HTTP、SQL、框架及内部实现类型。 */
public final class ProductStructureCommands {
 private ProductStructureCommands(){}
 /** @param version 当前商品版本 @param templateVersionId 同租户发布的规格模板版本 UUID，空值解除绑定 */
 public record SpecificationTemplate(Long version,String templateVersionId){}
 /**
  * SPU 确认供所属货号使用的普通属性模板。
  * @param version 父商品当前版本，必填
  * @param templateVersionId 同租户已发布的 SKU 模板版本 UUID；空值表示解除
  */
 public record SkuAttributeTemplate(Long version,String templateVersionId){}
 /**
  * 替换普通货号属性值，首次保存固定模板版本。
  * @param version 父商品当前版本，必填
  * @param rowVersion 货号当前版本，必填
  * @param templateVersionId 已展示的有效普通货号属性模板版本 UUID，必填
  * @param values 最多 200 个有效模板项的值，空集合仅适用于无必填项的模板
  */
 public record SkuAttributes(Long version,Long rowVersion,String templateVersionId,List<ProductMainData.Value> values){}
 /**
  * 设置货号采用的批次模板。
  * @param version 父商品当前版本，必填
  * @param rowVersion 货号当前版本，必填
  * @param templateVersionId 同租户已发布的 BATCH 模板版本 UUID，空值解除绑定
  */
 public record SkuBatchTemplate(Long version,Long rowVersion,String templateVersionId){}
/** @param name 规格名称1至200字符 @param description 自定义描述最大2000字符 @param values 标准规格固定SPEC模板项值，标量或固定选项UUID @param version 商品当前CAS */
 public record Specification(String name,String description,List<SpecValue> values,Long version){}
/** @param templateItemId 固定SPEC项UUID @param value INTEGER/DECIMAL/BOOLEAN标量或单选option UUID */
 public record SpecValue(String templateItemId,String value,List<String> optionIds,List<String> customValues){
  /** 原标准规格标量/单选调用保持兼容，CUSTOM可显式提交枚举列表；单选不得同时两种表示。 */
  public SpecValue(String templateItemId,String value){this(templateItemId,value,List.of(),List.of());}
 }
/** @param specificationId 已保存当前有效规格UUID @param code SKU独立编码，创建后不可修改 @param version 商品CAS @param measurement 可选货号计量，空值沿用商品配置 */
 public record Sku(String specificationId,String code,Long version,ProductMeasurement.SkuConfiguration measurement){
  /** 缺省沿用商品计量，兼容已有创建调用。 */
  public Sku(String specificationId,String code,Long version){this(specificationId,code,version,null);}
 }
/** @param componentId 组成商品UUID @param quantity 组成商品基础单位下正数精确数量 @param componentVersion 当前组成商品版本 @param version 组合父商品CAS */
 public record Component(String componentId,String quantity,Long componentVersion,Long version){}
/** @param enabled 目标启停状态 @param rowVersion 子记录当前CAS @param version 父商品CAS */
 public record State(Boolean enabled,Long rowVersion,Long version){}
}
