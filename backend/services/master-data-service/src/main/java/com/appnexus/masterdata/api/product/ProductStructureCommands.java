package com.appnexus.masterdata.api.product;
import java.util.*;
import com.appnexus.masterdata.api.product.ProductCatalogData.*;
/** 商品公开操作稳定数据契约；不包含HTTP、SQL、框架及内部实现类型。 */
public final class ProductStructureCommands {
 private ProductStructureCommands(){}
/** @param name 规格名称1至200字符 @param description 自定义描述最大2000字符 @param values 标准规格固定SPEC模板项值，标量或固定选项UUID @param version 商品当前CAS */
 public record Specification(String name,String description,List<SpecValue> values,Long version){}
/** @param templateItemId 固定SPEC项UUID @param value INTEGER/DECIMAL/BOOLEAN标量或单选option UUID */
 public record SpecValue(String templateItemId,String value,List<String> optionIds,List<String> customValues){
  /** 原标准规格标量/单选调用保持兼容，CUSTOM可显式提交枚举列表；单选不得同时两种表示。 */
  public SpecValue(String templateItemId,String value){this(templateItemId,value,List.of(),List.of());}
 }
/** @param specificationId 已保存当前有效规格UUID @param code SKU独立编码，创建后不可修改 @param version 商品CAS */
 public record Sku(String specificationId,String code,Long version){}
/** @param componentId 组成商品UUID @param quantity 组成商品基础单位下正数精确数量 @param componentVersion 当前组成商品版本 @param version 组合父商品CAS */
 public record Component(String componentId,String quantity,Long componentVersion,Long version){}
/** @param enabled 目标启停状态 @param rowVersion 子记录当前CAS @param version 父商品CAS */
 public record State(Boolean enabled,Long rowVersion,Long version){}
}
