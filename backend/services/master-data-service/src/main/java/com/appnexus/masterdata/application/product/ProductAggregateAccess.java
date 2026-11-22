package com.appnexus.masterdata.application.product;
import java.util.Map;
import com.appnexus.platform.tenant.VerifiedTenantContext;
/** 商品同聚合扩展协作输出端口；只允许本模块原商品用例实现，不向其他上下文开放。 */
public interface ProductAggregateAccess {
 /** @param c 可信主体 @param id 当前商品 @param action 原独立操作 @param lock 是否在当前事务锁定授权记录 */
 void authorizeRecord(VerifiedTenantContext c,String id,String action,boolean lock);
 /** @param c 可信主体 @param action 当前实际操作 @return 用于父商品范围的公共计划及绑定谓词 */
 com.appnexus.platform.authorization.RowAccessControl.Predicate recordPredicate(VerifiedTenantContext c,String action);
 /** @param c 可信主体 @param id 当前商品；审批处理中必须拒绝写入 */
 void ensureNotInApproval(VerifiedTenantContext c,String id);
 /** @param c 已授权主体 @param id 同租户商品 @return 当前扩展前事实快照 */
 Map<String,Object> extensionSnapshot(VerifiedTenantContext c,String id);
 /** @param c 可信主体 @param id 当前商品 @param type 固定扩展事件类型 @param before 原授权快照；与父版本、图片及审计同事务追加 */
 void extensionEvent(VerifiedTenantContext c,String id,String type,Map<String,Object> before);
 /** @param c 已授权主体 @param id 已锁商品；重验当前动态完整性，不能信任上次保存标记 */
 void requireCompleteForApproval(VerifiedTenantContext c,String id);
}
