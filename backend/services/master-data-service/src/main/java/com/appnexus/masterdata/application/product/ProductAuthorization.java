package com.appnexus.masterdata.application.product;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import com.appnexus.platform.authorization.DataAccessReferences;
import java.util.List;
/** 商品统一数据权限执行输出端口，只允许本模块统一授权执行适配器实现，不另建决策算法。 */
public interface ProductAuthorization {
 /** @param c 可信主体 @param id 当前商品UUID @param action 当前独立操作 @param lock 是否持有原写事务行锁 */
 void authorize(VerifiedTenantContext c,String id,String action,boolean lock);
 /** @param context 可信主体 @param action 已声明的真实操作 @return 本次操作的公共读谓词 */
 com.appnexus.platform.authorization.RowAccessControl.Predicate predicate(VerifiedTenantContext context,String action);
 /**
  * @param c 可信主体
  * @param productId 修改时已锁定、创建时由服务器分配的商品 UUID
  * @param organization 目标组织 UUID；修改缺省时保留原值
  * @param action 当前独立操作，不借用读取范围
  * @param creating 是否创建；新增必须提供归属
  */
 void target(VerifiedTenantContext c,String productId,String organization,String action,boolean creating);
 /** @param c 可信主体 @param action 实际操作 @return 当前操作允许的启用组织，不授予其他功能权限 */
 List<DataAccessReferences.Organization> organizations(VerifiedTenantContext c,String action);
 /** @param c 可信主体 @return 旧商品对账仅消费固定全部策略产生的可信谓词 */
 com.appnexus.platform.authorization.RowAccessControl.Predicate legacyRead(VerifiedTenantContext c);
 /** @param c 可信主体 @param id 当前事务将加锁并暂存的旧商品 UUID */
 void authorizeLegacyStage(VerifiedTenantContext c,String id);
}
