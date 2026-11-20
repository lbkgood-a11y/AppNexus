package com.appnexus.masterdata.application.product;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import java.util.function.BiFunction;
/** 商品聚合固定快照、历史及属性存取输出端口；回调只表达同聚合只读事实，不开放SQL或跨上下文访问。 */
public interface ProductAggregateRepository {
 /** @param c 已授权主体 @param id 当前租户商品ID @param page 正整数页码 @param size 每页1—100条 @return 同一商品历史及总数，不包含其他租户记录 */
 Map<String,Object> changes(VerifiedTenantContext c,String id,int page,int size);
 /** 原值及选项创建审计；时间必须为稳定JDK值，历史未知保持null。 */
 record ValueHistory(Map<String,Map<String,Object>> values,Map<String,Map<String,Object>> choices) {}
 /** @param c 已授权主体 @param id 当前商品 @param issues 原完整性事实协作 @return 同租户完整快照 */
 Map<String,Object> snapshot(VerifiedTenantContext c,String id,BiFunction<VerifiedTenantContext,Map<String,Object>,List<String>> issues);
 /** @param c 可信主体 @param id 当前商品 @param type 固定事件 @param before 原授权事实 @param reason 原操作原因 @param snapshot 同聚合授权快照回调 @return 追加事件UUID，与业务修改同事务 */
 String appendHistory(VerifiedTenantContext c,String id,String type,Map<String,Object> before,String reason,BiFunction<VerifiedTenantContext,String,Map<String,Object>> snapshot);
 /** @param c 已授权主体 @param id 已锁商品 @return 属性及选项原创建审计 */
 ValueHistory valueHistory(VerifiedTenantContext c,String id);
 /** @param c 已授权主体 @param id 原事务已锁定商品 @param config 固定配置 @param prepared 已完成应用校验的值 @param preserved 原创建审计，null时重读，不能客户端指定 */
 void replaceValues(VerifiedTenantContext c,String id,String config,List<ProductAttributePreparation.PreparedValue> prepared,ValueHistory preserved);
}
