package com.appnexus.masterdata.api.product;
import java.util.*;
import com.appnexus.masterdata.api.product.ProductCatalogData.*;
/** 商品公开操作稳定数据契约；不包含HTTP、SQL、框架及内部实现类型。 */
public final class ProductAttributeData {
 private ProductAttributeData(){}
/** 分组创建请求：code租户内唯一且1至64字符，name为1至200字符；version新建为0。 */
 public record GroupInput(String code,String name,long version){}
 /**
  * 稳定属性维护请求。初始修订仅由创建操作受理。
  * @param code 租户内唯一的属性编码，不能占用品牌等保留字段
  * @param groupId 同租户可选属性分组
  * @param version 新建为0，编辑使用当前父属性版本
  * @param initialRevision 同事务创建的首个草稿；缺省时兼容原有仅创建编码的调用方
  */
 public record AttributeInput(String code,String groupId,long version,RevisionInput initialRevision){
  /** 兼容只维护稳定属性编码及分组的调用方。 */
  public AttributeInput(String code,String groupId,long version){this(code,groupId,version,null);}
 }
/**
  * 修订草稿请求；正文发布后不可改，version是稳定属性父聚合CAS。
  *
 * @param name 名称1至200字符
 * @param dataType 固定七种受支持类型
  *
 * @param unitId 数值单位UUID可空；非数值必须空
  *
 * @param numericScale INTEGER必须0，DECIMAL为0至12，其他必须空
  *
 * @param validationJson 规则白名单：文本长度、数值/日期范围、枚举数量；拒绝脚本/SQL/任意正则
  *
 * @param version 当前父属性版本，非负
 * @param options 枚举初始选项，可空；与修订同事务保存
  */
 public record RevisionInput(String name,String dataType,String unitId,Integer numericScale,String validationJson,long version,List<OptionSeed> options){
  /** 测试/内部调用不提供初始枚举；后续只能在未发布草稿下追加。 */
  public RevisionInput(String name,String dataType,String unitId,Integer numericScale,String validationJson,long version){this(name,dataType,unitId,numericScale,validationJson,version,null);}
 }
/** 枚举选项编码1至64个ASCII字符，标签1至200字符，顺序默认由列表位置确定。 */
 public record OptionSeed(String code,String label){}
/** 草稿追加枚举：code为修订内唯一编码，label为1至200字符，sortOrder为显示顺序，version是父属性CAS。 */
 public record OptionInput(String code,String label,int sortOrder,long version){}
}
