package com.appnexus.masterdata.api.product;
import java.util.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import com.appnexus.contracts.workflow.WorkflowContext;
import com.appnexus.masterdata.api.product.ProductMainData.*;
import com.appnexus.masterdata.api.product.ProductStructureData.*;
import com.appnexus.masterdata.api.product.ProductCatalogData.*;
import com.appnexus.masterdata.api.product.ProductStructureCommands.Specification;
import com.appnexus.masterdata.api.product.ProductStructureCommands.SpecValue;
import com.appnexus.masterdata.api.product.ProductStructureCommands.Sku;
import com.appnexus.masterdata.api.product.ProductStructureCommands.Component;
import com.appnexus.masterdata.api.product.ProductStructureCommands.State;
/** 商品公开用例，HTTP入口不得依赖内部应用实现或SQL映射。 */
public interface ProductStructureManagement {
 /** 库存可引用的商品粒度。 */
 enum IdentityKind { PRODUCT, SPECIFICATION, SKU }

 /** 同一父商品版本下可用的身份及基础单位，子对象版本在商品粒度下为空。 */
 record ProductIdentity(String productId, IdentityKind kind, String identityId,
                        String specificationId, String baseUnitId, long productVersion,
                        Long identityVersion) {}

 /**
  * 沿用商品读取授权核验商品及所选库存身份。
  * @param c 服务端可信租户和成员
  * @param productId 父商品 UUID
  * @param kind 库存核算粒度，必须明确选定
  * @param identityId 所选身份 UUID，商品粒度时与父商品相同
  * @param expectedProductVersion 非负商品档案版本，子结构变更也会推进该版本
  * @return 当前授权范围内的有效身份及基础单位
  */
 ProductIdentity resolveIdentity(VerifiedTenantContext c, String productId, IdentityKind kind,
     String identityId, long expectedProductVersion);

 /** 查询已授权商品的计量配置和基础单位。 */
 Object measurement(VerifiedTenantContext c,String product);
 /** 原子保存整套计量配置，版本冲突时拒绝。 */
 Object saveMeasurement(VerifiedTenantContext c,String product,ProductMeasurement.Change input);
 /** 在父商品版本与货号版本均匹配时修改货号计价及包装。 */
 Object saveSkuMeasurement(VerifiedTenantContext c,String product,String sku,ProductMeasurement.SkuChange change);
 /** 保存 SPU 采用的规格轴模板，原有规格保留自身采用版本。 */
 Object saveSpecificationTemplate(VerifiedTenantContext c,String product,ProductStructureCommands.SpecificationTemplate input);
 /** 以商品版本保存 SPU 确认的普通货号模板。 */
 Object saveSkuAttributeTemplate(VerifiedTenantContext c,String product,ProductStructureCommands.SkuAttributeTemplate input);
 /** 读取授权货号的普通属性定义、值和父子版本。 */
 Object skuAttributes(VerifiedTenantContext c,String product,String sku);
 /** 校验并完整保存普通属性值，不修改货号规格轴。 */
 Object saveSkuAttributes(VerifiedTenantContext c,String product,String sku,ProductStructureCommands.SkuAttributes input);
 /** 保存货号实际采用的批次规则，使用父子版本保护。 */
 Object saveSkuBatchTemplate(VerifiedTenantContext c,String product,String sku,ProductStructureCommands.SkuBatchTemplate input);
 /** 已绑定批次模板的固定字段；字段及属性修订均来自版本快照。 */
 record BatchField(String templateItemId,String name,String dataType,boolean required) {}
 /** @param templateVersionId 已确认的批次模板版本 UUID，无配置时为空 @param fields 固定字段，按模板顺序 */
 record SkuBatchRule(String templateVersionId,java.util.List<BatchField> fields) {}
 /** @param c 可信租户主体 @param product 货号所属商品 UUID @param sku 可收货 SKU UUID @param version 通知行固定商品版本
  * @return 当前货号已确认的规则，未配置时返回空字段集合 */
 SkuBatchRule skuBatchRule(VerifiedTenantContext c,String product,String sku,long version);
 /**
  * 校验接管行固定 SKU 下的批次属性，返回可随仓储实收保存的规范化快照。
  * @param c 可信租户主体
  * @param product 可信通知行所属商品 UUID
  * @param sku 可信通知行 SKU UUID
  * @param version 通知行固定商品版本
  * @param batchNo 实收批次号，已绑定模板时必填
  * @param templateVersionId 客户端确认的模板版本，须与 SKU 绑定相同
  * @param values 已填字段，最多 200 项，空集合仅在模板无必填项时可用
  * @return JSON 快照；货号未配置批次模板且无输入时返回 null
  */
 String validateSkuBatch(VerifiedTenantContext c,String product,String sku,long version,String batchNo,
     String templateVersionId,java.util.List<ProductMainData.Value> values);
 /** 商品包装换算依据，版本取自保存此配置的商品档案。 */
 record PackagingEvidence(String productId, String packagingCode, long productVersion,
     String sourceUnitId, String targetUnitId, java.math.BigDecimal factor,
     int targetScale, java.math.BigDecimal convertedQuantity) {}

 /**
  * 根据已授权商品的在用包装配置返回正式过账证据。
  * @param c 服务端可信租户和成员
  * @param productId 商品 UUID
  * @param packagingCode 已配置的商品包装编码
  * @param expectedProductVersion 预期商品版本，包装配置变化后须重新复核
  * @param quantity 正十进制包装数量，精度不得超过源单位允许值
  * @return 采用的包装、单位、版本、系数及未舍入结果
  */
 PackagingEvidence packagingEvidence(VerifiedTenantContext c, String productId,
     String packagingCode, long expectedProductVersion, String quantity);

 /**
  * 根据已授权且生效的货号获取正式过账证据。
  * @param skuId 商品所属货号 UUID，必填
  * @param packagingCode 货号包装档位编码；未配置货号计量时沿用商品包装
  * @param expectedProductVersion 预期商品版本，必填
  * @param quantity 正十进制包装数量
  * @param c 服务端可信租户和成员
  * @param productId 货号所属商品 UUID
  * @return 已校验来源单位、目标单位、系数和换算结果
  */
 PackagingEvidence skuPackagingEvidence(VerifiedTenantContext c,String productId,String skuId,
     String packagingCode,long expectedProductVersion,String quantity);

 /** 使用服务端已保存规格试算；quantity 为精确正十进制文本。 */
 Object calculatePackaging(VerifiedTenantContext c,String product,String code,String quantity);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Object read(VerifiedTenantContext c,String id);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Object createSpecification(VerifiedTenantContext c,String product,Specification i);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Object createSku(VerifiedTenantContext c,String product,Sku i);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Object addComponent(VerifiedTenantContext c,String product,Component i);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Object state(VerifiedTenantContext c,String product,String kind,String id,State i);
}
