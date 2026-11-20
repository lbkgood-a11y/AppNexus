package com.appnexus.masterdata.application.product;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import com.appnexus.platform.authorization.DataAccessReferences;
import java.util.List;
/** 商品统一数据权限执行输出端口，只允许本模块统一授权执行适配器实现，不另建决策算法。 */
public interface ProductAuthorization {
 /** @param c 可信主体 @param id 当前商品UUID @param action 当前独立操作 @param lock 是否持有原写事务行锁 */
 void authorize(VerifiedTenantContext c,String id,String action,boolean lock);
 /** @param c 可信主体 @param organization 目标组织UUID @param action 当前独立操作 @param creating 是否创建；不得用读取范围替代写入范围 */
 void target(VerifiedTenantContext c,String organization,String action,boolean creating);
 /** @param c 可信主体 @param action 实际操作 @return 当前操作允许的启用组织，不授予其他功能权限 */
 List<DataAccessReferences.Organization> organizations(VerifiedTenantContext c,String action);
 /** @param c 可信主体 @param action 需租户全范围的原维护操作，缺失必须拒绝 */
 void requireAll(VerifiedTenantContext c,String action);
}
