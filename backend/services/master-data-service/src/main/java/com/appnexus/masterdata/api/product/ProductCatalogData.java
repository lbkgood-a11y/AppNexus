package com.appnexus.masterdata.api.product;

import com.appnexus.masterdata.api.masterdata.MasterData.Audit;
import java.util.Map;

/** 商品配置切片的固定契约；不接收租户、账号、审计或任意扩展字段。 */
public final class ProductCatalogData {
  private ProductCatalogData() {}

  /**
   * 单位维护请求；编码创建后不变，不提供数量换算。
   * @param code 编码，1至64个ASCII字母数字点下划线短横线，首位字母数字
   * @param name 名称，去首尾空白后1至200字符
   * @param dimensionCode 维度编码，1至64字符，无默认值；创建后不可修改以保护历史解释
   * @param precisionScale 显示小数位0至12，必填；创建后不可修改，防止历史含义漂移
   * @param version 修改必填非负CAS版本；创建时传0
   */
  public record UnitInput(String code,String name,String dimensionCode,int precisionScale,long version) {}
  /** 单位只读结果；audit由数据库和可信上下文生成。 */
  public record Unit(String id,String code,String name,String dimensionCode,int precisionScale,boolean enabled,long version,Audit audit) {}
  /**
   * 分类维护请求；移动沿同租户树执行，不隐式继承模板。
   * @param code 编码创建后不可修改
   * @param name 分类名称，1至200字符
   * @param parentId 父分类UUID，NULL为根；含子树总深度不得超过8
   * @param version 当前非负聚合版本，创建0
   */
  public record CategoryInput(String code,String name,String parentId,long version) {}
  /** 分类结果；depth根为1，currentConfigId仅指向本分类的已发布配置。 */
  public record Category(String id,String code,String name,String parentId,int depth,boolean enabled,String currentConfigId,long version,Audit audit) {}
  /**
   * 显式启停请求；不可借编辑操作越过启停权限。
   * @param enabled 目标启停，必填，无缺省
   * @param version 当前非负版本
   */
  public record Status(Boolean enabled,long version) {}
  /**
   * 发布不可变完整四槽配置；空槽明确NULL，非空固定已发布模板版本，不创建假模板。
   * @param templates 必传 BASIC/SPEC/QUALITY/EXTENSION，可选 SKU/BATCH 下游默认模板槽；缺省或空值表示该槽无绑定
   * @param serviceUnitRequired 服务单位是否必填，必填；false允许服务无单位
   * @param version 当前分类版本；发布成功推进分类version
   */
  public record Publish(Map<String,String> templates,Boolean serviceUnitRequired,long version) {}
  /** 发布配置只读结果；四槽显式版本或NULL，原路径与审计永久保留。 */
  public record Configuration(String id,String categoryId,long revision,String path,boolean serviceUnitRequired,Map<String,String> templates,Audit audit) {}
}
