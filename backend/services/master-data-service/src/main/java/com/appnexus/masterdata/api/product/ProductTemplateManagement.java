package com.appnexus.masterdata.api.product;
import java.util.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import com.appnexus.contracts.workflow.WorkflowContext;
import com.appnexus.masterdata.api.product.ProductMainData.*;
import com.appnexus.masterdata.api.product.ProductStructureData.*;
import com.appnexus.masterdata.api.product.ProductCatalogData.*;
import com.appnexus.masterdata.api.product.ProductTemplateData.TemplateInput;
import com.appnexus.masterdata.api.product.ProductTemplateData.ItemInput;
import com.appnexus.masterdata.api.product.ProductTemplateData.VersionInput;
/** 商品公开用例，HTTP入口不得依赖内部应用实现或SQL映射。 */
public interface ProductTemplateManagement {
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Object list(VerifiedTenantContext c);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Object versions(VerifiedTenantContext c,String id);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Object items(VerifiedTenantContext c,String id);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Map<String,String> create(VerifiedTenantContext c,TemplateInput i);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Map<String,String> createVersion(VerifiedTenantContext c,String templateId,VersionInput i);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 void updateVersion(VerifiedTenantContext c,String id,VersionInput input);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 void publish(VerifiedTenantContext c,String versionId,long version);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 void update(VerifiedTenantContext c,String id,TemplateInput input);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 void status(VerifiedTenantContext c,String id,ProductCatalogData.Status input);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 void retire(VerifiedTenantContext c,String id,long version);
}
