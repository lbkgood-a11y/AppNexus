package com.appnexus.masterdata.api.product;
import java.util.List;
import com.appnexus.masterdata.api.product.ProductMainData.Value;
/** 商品结构迁移稳定输入，不包含客户端主体、快照或技术类型。 */
public interface ProductStructureData {
 /**
  * 全量目标结构，不接收latest、租户、操作者或客户端快照。
  * @param sourceVersion 当前父CAS，必填非负
  * @param sourceStructureRevision 当前结构修订，必填非负
  * @param sourceCategoryConfigId 当前固定配置UUID，NULL必须与源一致
  * @param categoryId 目标分类UUID，DRAFT允许空
  * @param configId 目标确定配置UUID，DRAFT允许空；执行一次切换整个四槽配置
  * @param productForm 目标PHYSICAL/SERVICE，DRAFT可空
  * @param unitId 目标单位，服务按目标配置可空
  * @param values 全量目标值，必须明确列表，空列表表示清空
  * @param droppedItemIds 源值中退出目标的模板项UUID，必须明确完整确认；排序不影响摘要
  * @param reason 结构更改理由，1至1000字符，不能填写凭据
  * @param legacyDisposition 旧档仅CONFIRMED_NONE才可转完整；原文永不修改
  * @param specificationMode 目标规格模式，可空保持原模式；变化必须明确退出规格
  * @param retiredSpecificationIds 明确退出的规格UUID列表，必须与真实依赖及迁移策略匹配
  */
 public record Migration(Long sourceVersion,Long sourceStructureRevision,String sourceCategoryConfigId,String categoryId,String configId,String productForm,String unitId,List<Value> values,List<String> droppedItemIds,String reason,String legacyDisposition,String specificationMode,List<String> retiredSpecificationIds){
  /** 原V1调用保持当前规格模式，不默认清除后续规格；新模式变更必须显式确认退出规格身份。 */
  public Migration(Long sourceVersion,Long sourceStructureRevision,String sourceCategoryConfigId,String categoryId,String configId,String productForm,String unitId,List<Value> values,List<String> droppedItemIds,String reason,String legacyDisposition){this(sourceVersion,sourceStructureRevision,sourceCategoryConfigId,categoryId,configId,productForm,unitId,values,droppedItemIds,reason,legacyDisposition,null,List.of());}
 }
 /** 执行原封不动的预览载荷；token仅内存使用，不记录日志/历史。 */
 public record MigrationCommand(Migration change,String previewToken){}

}
