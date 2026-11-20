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
