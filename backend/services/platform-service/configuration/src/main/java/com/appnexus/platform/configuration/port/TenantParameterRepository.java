package com.appnexus.platform.configuration.port;
import java.util.*;
/** 固定参数存取输出端口，仅允许平台持久化适配器实现，写入及审计同事务。 */
public interface TenantParameterRepository {
 /** @param tenant 可信租户UUID @return 当前参数原值及CAS版本 */
 List<Map<String,Object>> list(String tenant);
 /** @param tenant 可信租户UUID @return 当前启用租户锁定事实，空结果不得写入 */
 List<Map<String,Object>> lockTenant(String tenant);
 /** @param tenant 可信租户 @param key 固定参数键 @param value 经业务规则验证的值 @param actor 可信账号 @return 新增行数 */
 int create(String tenant,String key,String value,String actor);
 /** @param value 已验证值 @param actor 可信账号 @param tenant 可信租户 @param key 固定参数键 @param version 原非负CAS @return 更新行数，非1冲突 */
 int update(String value,String actor,String tenant,String key,int version);
 /** @param tenant 可信租户 @param actor 可信账号 @param key 原参数键 @param next 更新后非负版本 @return 审计行数，与业务同事务 */
 int audit(String tenant,String actor,String key,int next);
}
