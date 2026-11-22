package com.appnexus.masterdata.api.product;
import java.util.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import com.appnexus.contracts.workflow.WorkflowContext;
import com.appnexus.masterdata.api.product.ProductMainData.*;
import com.appnexus.masterdata.api.product.ProductStructureData.*;
import com.appnexus.masterdata.api.product.ProductCatalogData.*;
import com.appnexus.masterdata.api.product.ProductCommandData.Status;
import com.appnexus.masterdata.api.product.ProductCommandData.Version;
import com.appnexus.masterdata.api.product.ProductCommandData.LegacyReview;
/** 商品公开用例，HTTP入口不得依赖内部应用实现或SQL映射。 */
public interface ProductManagement {
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Object list(VerifiedTenantContext c,String name,String code,String categoryId,String form,String status,boolean archived,int page,int size);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Object list(VerifiedTenantContext c,String name,String code,String categoryId,String form,String status,boolean archived,int page,int size,String configId,String filtersJson);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Object list(VerifiedTenantContext c);
 /** @param includeDescendants 默认 false 只匹配所选分类，true 包含下级；缺少分类时不限制分类。 */
 Object list(VerifiedTenantContext c,String name,String code,String categoryId,String form,String status,boolean archived,int page,int size,String configId,String filtersJson,boolean includeDescendants);
 /** 复用商品读取权限和分页；keyword 按编码或名称匹配，空字符串不增加限制。 */
 Object list(VerifiedTenantContext c,String name,String code,String categoryId,String form,String status,boolean archived,int page,int size,String configId,String filtersJson,boolean includeDescendants,String keyword);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Object options(VerifiedTenantContext c,String kind,String keyword,int page,int size);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Object configurations(VerifiedTenantContext c,String categoryId,int page,int size);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Map<String,Object> configuration(VerifiedTenantContext c,String id);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Map<String,Object> read(VerifiedTenantContext c,String id);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Map<String,Object> create(VerifiedTenantContext c,Input i);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Map<String,Object> update(VerifiedTenantContext c,String id,Update input);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Map<String,Object> updateStatus(VerifiedTenantContext c,String id,Status input);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Map<String,Object> archive(VerifiedTenantContext c,String id,Version input,boolean archived);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Object changes(VerifiedTenantContext c,String id,int page,int size);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Map<String,Object> previewStructure(VerifiedTenantContext c,String id,Migration change);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Map<String,Object> migrateStructure(VerifiedTenantContext c,String id,MigrationCommand command,String key);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Map<String,Object> reviewLegacySpecification(VerifiedTenantContext c,String id,LegacyReview input);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Map<String,Object> stageLegacy(VerifiedTenantContext c,String id);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Object reconcileLegacy(VerifiedTenantContext c,int page,int size);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Object organizations(VerifiedTenantContext c,String action);
}
