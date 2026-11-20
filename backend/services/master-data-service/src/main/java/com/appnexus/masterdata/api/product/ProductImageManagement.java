package com.appnexus.masterdata.api.product;
import java.util.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import com.appnexus.contracts.workflow.WorkflowContext;
import com.appnexus.masterdata.api.product.ProductMainData.*;
import com.appnexus.masterdata.api.product.ProductStructureData.*;
import com.appnexus.masterdata.api.product.ProductCatalogData.*;
import com.appnexus.masterdata.api.product.ProductImageData.Content;
/** 商品公开用例，HTTP入口不得依赖内部应用实现或SQL映射。 */
public interface ProductImageManagement {
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Object list(VerifiedTenantContext c,String product);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Object upload(VerifiedTenantContext c,String product,long version,byte[] bytes);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Content content(VerifiedTenantContext c,String product,String id);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Object detach(VerifiedTenantContext c,String product,String id,long version);
}
