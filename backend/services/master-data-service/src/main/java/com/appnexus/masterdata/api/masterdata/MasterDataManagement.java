package com.appnexus.masterdata.api.masterdata;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import com.appnexus.masterdata.api.masterdata.MasterData.*;
/** 主数据管理公开用例；调用方不得绕过服务端权限、行范围及字段保护直接访问存储。 */
public interface MasterDataManagement {
 /** 已启用仓库、库区及库位的解析结果，仅包含稳定标识和当前版本。 */
 record StoragePosition(String warehouseId, String areaId, String locationId,
                        long warehouseVersion, long areaVersion, long locationVersion) {}

 /**
  * 在同一事务内按已有读取权限校验整条保管位置关系。
  * @param c 服务端可信租户和成员
  * @param expected 三个主数据 UUID 和非负预期版本，均必填
  * @return 核验后的稳定位置引用
  * @throws MasterData.Rejection 无权、位置不可用或预期版本过时
  */
 StoragePosition resolveStoragePosition(VerifiedTenantContext c, StoragePosition expected);

 /** 当前成员可选择的仓库摘要，仅包含登录选仓所需字段。 */
 record LoginWarehouse(String id, String code, String name) {}

 /**
  * @param context 服务端可信租户成员
  * @param warehouseId 指定仓库 UUID，空值读取候选
  * @return 当前登录选仓范围内的启用仓库
  */
 List<LoginWarehouse> loginWarehouses(VerifiedTenantContext context, UUID warehouseId);

 /** 主数据公开操作；沿用可信主体、功能权限、数据范围及原子事务约束。 */
 /** @param c 当前可信请求 @param action 固定读取、新增或修改动作 @return 对应组织候选和字段限制 */
 Object partnerAccess(VerifiedTenantContext c,String action);
 /** 主数据公开操作；沿用可信主体、功能权限、数据范围及原子事务约束。 */
 Object dictionaryOptions(VerifiedTenantContext c,Kind kind);
 /** 主数据公开操作；沿用可信主体、功能权限、数据范围及原子事务约束。 */
 Object warehouseOptions(VerifiedTenantContext c);
 /** @param c 可信租户主体 @param action create/update @return 对应操作允许选择的组织 */
 Object warehouseOrganizations(VerifiedTenantContext c,String action);
 /** @param c 服务端可信租户和成员 @return 当前仓库字段限制，不含记录或原值 */
 Map<String,com.appnexus.platform.authorization.FieldSecurity.Mode> warehouseFieldRestrictions(VerifiedTenantContext c);
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
