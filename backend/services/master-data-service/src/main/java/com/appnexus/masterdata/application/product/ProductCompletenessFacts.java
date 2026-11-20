package com.appnexus.masterdata.application.product;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
/** 商品完整性固定事实输出端口，不决定必填规则、状态或授权。 */
public interface ProductCompletenessFacts {
 /** @param c 已授权主体 @param config 原固定配置 @return 服务形式是否要求基础单位 */
 Boolean serviceUnitRequired(VerifiedTenantContext c,Object config);
 /** @param c 已授权主体 @param config 固定配置 @param id 当前商品 @return 原缺失必填属性标识 */
 List<String> missingAttributes(VerifiedTenantContext c,Object config,Object id);
 /** @param c 已授权主体 @param id 当前商品 @return 原有效规格数量 */
 long specificationCount(VerifiedTenantContext c,String id);
 /** @param c 已授权主体 @param id 当前组合商品 @return 组成商品及原冻结版本事实 */
 List<Map<String,Object>> components(VerifiedTenantContext c,String id);
 /** @param c 原写事务主体 @param id 已锁商品 @param state 领域计算的完整性状态，存储不得重新决定规则 */
 void refresh(VerifiedTenantContext c,String id,String state);
}
