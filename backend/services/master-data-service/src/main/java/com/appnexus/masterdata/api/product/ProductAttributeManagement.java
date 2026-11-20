package com.appnexus.masterdata.api.product;
import java.util.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import com.appnexus.contracts.workflow.WorkflowContext;
import com.appnexus.masterdata.api.product.ProductMainData.*;
import com.appnexus.masterdata.api.product.ProductStructureData.*;
import com.appnexus.masterdata.api.product.ProductCatalogData.*;
import com.appnexus.masterdata.api.product.ProductAttributeData.GroupInput;
import com.appnexus.masterdata.api.product.ProductAttributeData.AttributeInput;
import com.appnexus.masterdata.api.product.ProductAttributeData.RevisionInput;
import com.appnexus.masterdata.api.product.ProductAttributeData.OptionSeed;
import com.appnexus.masterdata.api.product.ProductAttributeData.OptionInput;
/** 商品公开用例，HTTP入口不得依赖内部应用实现或SQL映射。 */
public interface ProductAttributeManagement {
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Object groups(VerifiedTenantContext c);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Object attributes(VerifiedTenantContext c);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Object revisions(VerifiedTenantContext c,String id);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Object options(VerifiedTenantContext c,String id);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Map<String,String> createGroup(VerifiedTenantContext c,GroupInput i);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Map<String,String> createAttribute(VerifiedTenantContext c,AttributeInput i);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Map<String,String> createRevision(VerifiedTenantContext c,String attributeId,RevisionInput i);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 void publishRevision(VerifiedTenantContext c,String id,long version);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 Map<String,String> createOption(VerifiedTenantContext c,String revisionId,OptionInput i);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 void updateGroup(VerifiedTenantContext c,String id,GroupInput input);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 void updateAttribute(VerifiedTenantContext c,String id,AttributeInput input);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 void setStatus(VerifiedTenantContext c,String id,ProductCatalogData.Status input,boolean group);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 void retireRevision(VerifiedTenantContext c,String id,long version);
 /** 商品公开操作；沿用可信主体、功能权限、记录范围、字段保护及版本事务约束。 */
 void updateRevision(VerifiedTenantContext c,String id,RevisionInput input);
}
