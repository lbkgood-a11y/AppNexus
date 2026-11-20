package com.appnexus.platform.audit.port;
import java.util.*;
/** 固定审计摘要存储输出端口，仅允许平台持久化实现，不开放自由表名或SQL。 */
public interface AuditRecords {
 /** @param size 1至100 @param offset 非负分页偏移 @return 平台审计最小投影 */
 List<Map<String,Object>> platform(int size,long offset);
 /** @return 同平台过滤条件总数 */
 long platformCount();
 /** @param tenant 可信租户UUID @param size 1至100 @param offset 非负分页偏移 @return 各分支独立强制租户的摘要 */
 List<Map<String,Object>> tenant(String tenant,int size,long offset);
 /** @param tenant 同列表可信租户UUID @return 同过滤条件总数 */
 long tenantCount(String tenant);
}
