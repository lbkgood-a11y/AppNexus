package com.appnexus.masterdata.api.product;
import java.util.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import com.appnexus.contracts.workflow.WorkflowContext;
import com.appnexus.masterdata.api.product.ProductMainData.*;
import com.appnexus.masterdata.api.product.ProductStructureData.*;
import com.appnexus.masterdata.api.product.ProductCatalogData.*;
import com.appnexus.masterdata.api.product.ProductCatalogPages.UnitPage;
import com.appnexus.masterdata.api.product.ProductCatalogPages.CategoryPage;
/** 商品公开用例，HTTP入口不得依赖内部应用实现或SQL映射。 */
public interface ProductCatalogManagement {
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 default UnitPage units(VerifiedTenantContext c,String keyword,int page,int size){
   return units(c,keyword,page,size,null);
 }
 /** 分组编码为空时查询全部；分页语义与原查询一致。 */
 UnitPage units(VerifiedTenantContext c,String keyword,int page,int size,String group);
 /** 分组名称和创建后不可变的编码；更新须提交当前非负版本。 */
 record UnitGroupInput(String code,String name,Long version) {}
 /** 查询同租户分组，由计量单位查询权限控制。 */
 List<Map<String,Object>> unitGroups(VerifiedTenantContext c);
 /** 创建分组，由计量单位创建权限控制。 */
 void createUnitGroup(VerifiedTenantContext c,UnitGroupInput input);
 /** 修改分组名称，由计量单位编辑权限控制。 */
 void renameUnitGroup(VerifiedTenantContext c,String code,UnitGroupInput input);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Unit createUnit(VerifiedTenantContext c,UnitInput input);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Unit updateUnit(VerifiedTenantContext c,String id,UnitInput input);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Unit statusUnit(VerifiedTenantContext c,String id,Status input);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 CategoryPage categories(VerifiedTenantContext c,String parentId,String keyword,int page,int size);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Category createCategory(VerifiedTenantContext c,CategoryInput input);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Category updateCategory(VerifiedTenantContext c,String id,CategoryInput input);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Category statusCategory(VerifiedTenantContext c,String id,Status input);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Configuration publish(VerifiedTenantContext c,String id,Publish input);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Configuration configuration(VerifiedTenantContext c,String categoryId,String configId);
}
