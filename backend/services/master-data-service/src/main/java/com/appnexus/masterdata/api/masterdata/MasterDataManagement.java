package com.appnexus.masterdata.api.masterdata;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import com.appnexus.masterdata.api.masterdata.MasterData.*;
/** 主数据管理公开用例；调用方不得绕过服务端权限、行范围及字段保护直接访问存储。 */
public interface MasterDataManagement {
 /** 主数据公开操作；沿用可信主体、功能权限、数据范围及原子事务约束。 */
 Object partnerAccess(VerifiedTenantContext c);
 /** 主数据公开操作；沿用可信主体、功能权限、数据范围及原子事务约束。 */
 Object dictionaryOptions(VerifiedTenantContext c,Kind kind);
 /** 主数据公开操作；沿用可信主体、功能权限、数据范围及原子事务约束。 */
 Object warehouseOptions(VerifiedTenantContext c);
 /** 主数据公开操作；沿用可信主体、功能权限、数据范围及原子事务约束。 */
 Object geographyCatalog(VerifiedTenantContext c,Kind kind);
 /** 主数据公开操作；沿用可信主体、功能权限、数据范围及原子事务约束。 */
 Object geographyOptions(VerifiedTenantContext c,Kind kind,String keyword,int page,int size);
 /** 主数据公开操作；沿用可信主体、功能权限、数据范围及原子事务约束。 */
 Object geographyTree(VerifiedTenantContext c,Kind kind,String parent,int page,int size);
 /** 主数据公开操作；沿用可信主体、功能权限、数据范围及原子事务约束。 */
 Page list(VerifiedTenantContext c,Kind kind,String keyword,Boolean enabled,int page,int size);
 /** 主数据公开操作；沿用可信主体、功能权限、数据范围及原子事务约束。 */
 Page list(VerifiedTenantContext c,Kind kind,String keyword,Boolean enabled,int page,int size,String id);
 /** 主数据公开操作；沿用可信主体、功能权限、数据范围及原子事务约束。 */
 Page list(VerifiedTenantContext c,Kind kind,String keyword,Boolean enabled,int page,int size,String id,String scope);
 /** 主数据公开操作；沿用可信主体、功能权限、数据范围及原子事务约束。 */
 Map<String,String> create(VerifiedTenantContext c,Kind kind,Input input);
 /** 主数据公开操作；沿用可信主体、功能权限、数据范围及原子事务约束。 */
 void update(VerifiedTenantContext c,Kind kind,String id,Input input);
 /** 主数据公开操作；沿用可信主体、功能权限、数据范围及原子事务约束。 */
 void setStatus(VerifiedTenantContext c,Kind kind,String id,Status input);
}
