package com.appnexus.platform.configuration.port;
import java.util.*;
/** 字典引用存取输出端口；仅允许平台持久化适配器实现，所有锁和写入参与调用方完整事务。 */
public interface PartnerReferenceRepository {
 /** @param client 可信客户端 @param tenant 固定租户 @param version Token权威版本 @return 锁定匹配且启用客户端的结果 */
 List<Map<String,Object>> lockAuthority(Object client,Object tenant,Object version);
 /** @param client 当前客户端 @param key 幂等键 @return 同客户端原登记及摘要，持有行锁直到事务结束 */
 List<Map<String,Object>> lockRequest(Object client,Object key);
 /** @param tenant 当前租户 @param itemTenant 必须与tenant相同 @param dictionary 受控语义码 @param value 字典业务值 @return 启用且未删除的字典项，双表独立租户条件 */
 List<Map<String,Object>> lockItem(Object tenant,Object itemTenant,Object dictionary,Object value);
 /** @param tenant 可信租户 @param item 已锁定项 @param client 可信客户端 @param key 业务引用键 @return 插入行数，已有引用保持幂等 */
 int registerUsage(Object tenant,Object item,Object client,Object key);
 /** @param client 当前客户端 @param key 幂等键 @param hash SHA-256请求摘要 @param reference 对外UUID @param tenant 固定租户 @param item 已锁定项 @param businessKey 业务引用键 @return 插入行数 */
 int saveRequest(Object client,Object key,Object hash,Object reference,Object tenant,Object item,Object businessKey);
 /** @param id 对外UUID @param client 可信客户端 @param tenant 固定租户 @return 当前主体引用投影 */
 List<Map<String,Object>> findReference(Object id,Object client,Object tenant);
}
