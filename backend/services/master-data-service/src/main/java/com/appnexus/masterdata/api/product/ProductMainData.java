package com.appnexus.masterdata.api.product;

import java.util.List;

/** 商品主档稳定输入类型；不包含租户、审计、数据库对象或技术框架类型。 */
public interface ProductMainData {
 /**
  * 草稿创建；不接收状态、租户、审计或客户端版本。
  * @param code 租户内唯一编码，1至64个ASCII字符
  * @param name 名称，1至200字符
  * @param shortName 简称，可空，最多100字符
  * @param description 描述，可空，最多20000字符，允许换行与制表符
  * @param brand 品牌，可空，最多200字符
  * @param origin 产地说明，可空，最多200字符，不推断地理归属
  * @param categoryId 分类可空；configId非空时必须匹配
  * @param configId 固定发布配置UUID，不接受latest
  * @param productForm PHYSICAL/SERVICE，可空；启用必填
  * @param unitId 实物启用必填，服务按配置规则
  * @param values 类型化属性值列表，可空即无值；已绑定配置下required可缺
  * @param organizationId 新增必填的本租户启用归属组织UUID，必须处于create范围
  * @param barcode 条码文本，最多500字符，缺省空；保留前导零，不推导SKU
  */
 public record Input(String code,String name,String shortName,String categoryId,String configId,String productForm,String unitId,String description,String brand,String origin,List<Value> values,String barcode,String organizationId){
  /** 原内部调用须显式补归属后方可保存；此构造仅保留源码兼容，不赋予默认组织。 */
  public Input(String code,String name,String shortName,String categoryId,String configId,String productForm,String unitId,String description,String brand,String origin,List<Value> values,String barcode){this(code,name,shortName,categoryId,configId,productForm,unitId,description,brand,origin,values,barcode,null);}
  /** 内部原调用不提供条码时默认空；条码始终文本，不丢前导零。 */
  public Input(String code,String name,String shortName,String categoryId,String configId,String productForm,String unitId,String description,String brand,String origin,List<Value> values){this(code,name,shortName,categoryId,configId,productForm,unitId,description,brand,origin,values,"",null);}
 }
 /**
  * 动态属性输入；标量与选项互斥，不允许客户端指定类型/修订或单位。
  * @param templateItemId 当前配置真实模板项UUID
  * @param value 标量文本，整数/小数为精确十进制，日期为ISO自然日，布尔为true/false
  * @param optionIds 枚举选项UUID列表；单选总数恰1，多选总数1至50，不接受另一修订项
  * @param customValues 模板allowCustom=true时可填的明确自定义文本，不新增属性选项；与标准选项共同参与数量校验
  */
 public record Value(String templateItemId,String value,List<String> optionIds,List<String> customValues){
  /** 不传自定义值时维持原输入契约；普通标准枚举不受影响。 */
  public Value(String templateItemId,String value,List<String> optionIds){this(templateItemId,value,optionIds,List.of());}
 }
 /** 普通修改只接收主档资料/当前定义的属性值。
  * @param name 名称，必填1–200字符
  * @param shortName 简称，可空，最多100字符
  * @param description 描述，可空，最多20000字符
  * @param brand 品牌，可空，最多200字符
  * @param origin 产地，可空，最多200字符
  * @param values null 保留属性，空列表明确清空；非空逐项验证真实配置与类型
  * @param version 原非负并发版本，必填，冲突不覆盖
  * @param organizationId 修改目标组织UUID；null保留，空串拒绝，原记录与目标分别校验update范围
  * @param barcode 条码最多500字符；null 保留原值，空串显式清空
  */
 public record Update(String name,String shortName,String description,String brand,String origin,List<Value> values,Long version,String barcode,String organizationId){
  /** 缺省归属保留原值，不允许传空串清除。 */
  public Update(String name,String shortName,String description,String brand,String origin,List<Value> values,Long version,String barcode){this(name,shortName,description,brand,origin,values,version,barcode,null);}
  /** 原内部调用不修改条码，NULL保持原值。 */
  public Update(String name,String shortName,String description,String brand,String origin,List<Value> values,long version){this(name,shortName,description,brand,origin,values,version,null,null);}
 }
}
