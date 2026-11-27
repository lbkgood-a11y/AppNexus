package com.appnexus.platform.persistence.identity.integration;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** IntegrationClients固定SQL映射；没有接收SQL、表名或列名的通用执行入口。 */
@Mapper
@PlatformScoped(tables={"identity_account","integration_client","integration_client_audit","oauth2_authorization","platform_administrator","platform_tenant"})
public interface IntegrationClientMapper {
 /**
  * statement1 为原IntegrationClients固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Select("SELECT id FROM platform_tenant WHERE id=UUID_TO_BIN(#{p0}) AND enabled=TRUE FOR UPDATE")
 List<Map<String,Object>> statement1(@Param("p0") Object p0);

 /**
  * statement2 为原IntegrationClients固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p2 原调用第3个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Insert("INSERT INTO integration_client(id,tenant_id,name) VALUES(#{p0},UUID_TO_BIN(#{p1}),#{p2})")
 int statement2(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /**
  * statement3 为原IntegrationClients固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Update("UPDATE integration_client SET enabled=#{p0},authority_version=authority_version+1 WHERE id=#{p1}")
 int statement3(@Param("p0") Object p0,@Param("p1") Object p1);

 /**
  * statement4 为原IntegrationClients固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Delete("DELETE FROM oauth2_authorization WHERE registered_client_id=#{p0}")
 int statement4(@Param("p0") Object p0);

 /**
  * statement5 为原IntegrationClients固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Select("SELECT name FROM integration_client WHERE id=#{p0}")
 String statement5(@Param("p0") Object p0);

 /**
  * statement6 为原IntegrationClients固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Select("SELECT previous_secret_until>CURRENT_TIMESTAMP(6) FROM integration_client WHERE id=#{p0}")
 Boolean statement6(@Param("p0") Object p0);

 /**
  * statement7 为原IntegrationClients固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Update("UPDATE integration_client SET previous_secret_hash=#{p0},previous_secret_until=CURRENT_TIMESTAMP(6)+INTERVAL 600 SECOND WHERE id=#{p1}")
 int statement7(@Param("p0") Object p0,@Param("p1") Object p1);

 /**
  * statement8 为原IntegrationClients固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Update("UPDATE integration_client SET authority_version=authority_version+1 WHERE id=#{p0}")
 int statement8(@Param("p0") Object p0);

 /**
  * statement9 为原IntegrationClients固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Delete("DELETE FROM oauth2_authorization WHERE registered_client_id=#{p0}")
 int statement9(@Param("p0") Object p0);

 /**
  * statement10 为原IntegrationClients固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Select("SELECT name FROM integration_client WHERE id=#{p0}")
 String statement10(@Param("p0") Object p0);

 /**
  * statement11 为原IntegrationClients固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Select("SELECT id FROM integration_client WHERE id=#{p0} AND authority_version=#{p1} FOR UPDATE")
 List<Map<String,Object>> statement11(@Param("p0") Object p0,@Param("p1") Object p1);

 /**
  * statement12 为原IntegrationClients固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Select("SELECT p.account_id FROM platform_administrator p JOIN identity_account a ON a.id=p.account_id WHERE p.account_id=UUID_TO_BIN(#{p0}) AND p.enabled=TRUE AND a.enabled=TRUE FOR UPDATE")
 List<Map<String,Object>> statement12(@Param("p0") Object p0);

 /**
  * 在集成客户端变更事务中追加审计，租户归属取自数据库中的客户端绑定。
  * @param p0 已通过准入校验的操作账号 UUID
  * @param p1 本次变更的客户端注册标识
  * @param p2 用例指定的审计动作编码
  * @return 写入行数，失败时由调用方回滚事务
  */
 @Insert("INSERT INTO integration_client_audit(actor_id,client_id,action,tenant_id) VALUES(UUID_TO_BIN(#{p0}),#{p1},#{p2},(SELECT tenant_id FROM integration_client WHERE id=#{p1}))")
 int statement13(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** @param tenant 已验证租户UUID @return 最多100条客户端摘要，不含密钥 */
 @Select("SELECT id,name,enabled,authority_version FROM integration_client WHERE tenant_id=UUID_TO_BIN(#{tenant}) ORDER BY created_at,id LIMIT 100") List<Map<String,Object>> summaries(@Param("tenant") String tenant);
 /** @param id 机密客户端标识 @return 权威租户及启用事实，故障不缓存放行 */
 @Select("SELECT c.id,BIN_TO_UUID(c.tenant_id) tenant,c.authority_version,c.enabled clientEnabled,t.enabled tenantEnabled FROM integration_client c JOIN platform_tenant t ON t.id=c.tenant_id WHERE c.id=#{id}") List<Map<String,Object>> policy(@Param("id") String id);
}
