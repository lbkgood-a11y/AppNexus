package com.appnexus.platform.persistence.audit;
import com.appnexus.persistence.mybatis.PlatformScoped;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;
/** 统一审计固定只读映射；平台和租户准入仍由用例校验，不提供动态表或SQL。 */
@Mapper
@PlatformScoped(tables={"platform_administration_audit","tenant_dictionary_audit","tenant_parameter_audit","integration_call_audit"})
public interface AuditQueriesMapper {
 /** @param size 每页1–100条 @param offset 从0开始偏移 @return 仅平台操作摘要，不含业务正文及凭据 */
 @Select("SELECT CAST(id AS CHAR) event_id,BIN_TO_UUID(actor_account_id) actor,action,BIN_TO_UUID(target_id) target,occurred_at FROM platform_administration_audit WHERE tenant_id IS NULL ORDER BY id DESC LIMIT #{size} OFFSET #{offset}")
 List<Map<String,Object>> platform(@Param("size") int size,@Param("offset") long offset);
 /** @return 与平台列表相同条件的总数 */
 @Select("SELECT COUNT(*) FROM platform_administration_audit WHERE tenant_id IS NULL") long platformCount();
 /** @param tenant 已授权可信租户UUID @param size 每页1–100条 @param offset 从0开始偏移 @return 同租户四类审计摘要，每个UNION分支独立绑定租户 */
 @Select("SELECT * FROM (SELECT 'platform' source,CAST(id AS CHAR) event_id,BIN_TO_UUID(actor_account_id) actor,action,BIN_TO_UUID(target_id) target,NULL trace_id,NULL status,occurred_at FROM platform_administration_audit WHERE tenant_id=UUID_TO_BIN(#{tenant}) UNION ALL SELECT 'dictionary',CAST(id AS CHAR),BIN_TO_UUID(actor_account_id),action,BIN_TO_UUID(dictionary_id),NULL,NULL,occurred_at FROM tenant_dictionary_audit WHERE tenant_id=UUID_TO_BIN(#{tenant}) UNION ALL SELECT 'parameter',CAST(id AS CHAR),BIN_TO_UUID(actor_id),'PARAMETER_UPDATED',parameter_key,NULL,NULL,occurred_at FROM tenant_parameter_audit WHERE tenant_id=UUID_TO_BIN(#{tenant}) UNION ALL SELECT 'integration',CAST(id AS CHAR),client_id,operation,NULL,trace_id,status,occurred_at FROM integration_call_audit WHERE tenant_id=UUID_TO_BIN(#{tenant})) audit_rows ORDER BY occurred_at DESC,source,event_id DESC LIMIT #{size} OFFSET #{offset}")
 List<Map<String,Object>> tenant(@Param("tenant") String tenant,@Param("size") int size,@Param("offset") long offset);
 /** @param tenant 与列表相同的可信租户 @return 相同UNION条件下的总数 */
 @Select("SELECT COUNT(*) FROM (SELECT 'platform' source,CAST(id AS CHAR) event_id,BIN_TO_UUID(actor_account_id) actor,action,BIN_TO_UUID(target_id) target,NULL trace_id,NULL status,occurred_at FROM platform_administration_audit WHERE tenant_id=UUID_TO_BIN(#{tenant}) UNION ALL SELECT 'dictionary',CAST(id AS CHAR),BIN_TO_UUID(actor_account_id),action,BIN_TO_UUID(dictionary_id),NULL,NULL,occurred_at FROM tenant_dictionary_audit WHERE tenant_id=UUID_TO_BIN(#{tenant}) UNION ALL SELECT 'parameter',CAST(id AS CHAR),BIN_TO_UUID(actor_id),'PARAMETER_UPDATED',parameter_key,NULL,NULL,occurred_at FROM tenant_parameter_audit WHERE tenant_id=UUID_TO_BIN(#{tenant}) UNION ALL SELECT 'integration',CAST(id AS CHAR),client_id,operation,NULL,trace_id,status,occurred_at FROM integration_call_audit WHERE tenant_id=UUID_TO_BIN(#{tenant})) audit_rows") long tenantCount(@Param("tenant") String tenant);
}
