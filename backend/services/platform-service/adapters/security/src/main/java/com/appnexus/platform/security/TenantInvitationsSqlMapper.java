package com.appnexus.platform.security;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** TenantInvitations固定SQL映射；没有接收SQL、表名或列名的通用执行入口。 */
@Mapper
@PlatformScoped(tables={"access_member_role","identity_account","platform_administration_audit","platform_administrator","platform_tenant","tenant_invitation","tenant_member"})
public interface TenantInvitationsSqlMapper {
 /**
  * statement1 为原TenantInvitations固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Select("SELECT id FROM tenant_member WHERE tenant_id=UUID_TO_BIN(#{p0}) AND account_id=UUID_TO_BIN(#{p1}) AND removed_at IS NULL FOR UPDATE")
 List<Map<String,Object>> statement1(@Param("p0") Object p0,@Param("p1") Object p1);

 /**
  * statement2 为原TenantInvitations固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Update("UPDATE tenant_invitation SET status='REVOKED' WHERE tenant_id=UUID_TO_BIN(#{p0}) AND account_id=UUID_TO_BIN(#{p1}) AND status='PENDING'")
 int statement2(@Param("p0") Object p0,@Param("p1") Object p1);

 /**
  * statement3 为原TenantInvitations固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p2 原调用第3个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p3 原调用第4个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p4 原调用第5个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p5 原调用第6个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Insert("INSERT INTO tenant_invitation(id,tenant_id,account_id,token_hash,expires_at,invited_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),#{p3},#{p4},UUID_TO_BIN(#{p5}))")
 int statement3(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5);

 /**
  * statement4 为原TenantInvitations固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Select("SELECT BIN_TO_UUID(tenant_id) tenant FROM tenant_invitation WHERE id=UUID_TO_BIN(#{p0}) AND account_id=UUID_TO_BIN(#{p1})")
 List<Map<String,Object>> statement4(@Param("p0") Object p0,@Param("p1") Object p1);

 /**
  * statement5 为原TenantInvitations固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p2 原调用第3个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Select("SELECT token_hash FROM tenant_invitation WHERE id=UUID_TO_BIN(#{p0}) AND tenant_id=UUID_TO_BIN(#{p1}) AND account_id=UUID_TO_BIN(#{p2}) AND status='PENDING' AND expires_at>CURRENT_TIMESTAMP(6) FOR UPDATE")
 List<Map<String,Object>> statement5(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /**
  * statement6 为原TenantInvitations固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Select("SELECT BIN_TO_UUID(id) id,removed_at FROM tenant_member WHERE tenant_id=UUID_TO_BIN(#{p0}) AND account_id=UUID_TO_BIN(#{p1}) FOR UPDATE")
 List<Map<String,Object>> statement6(@Param("p0") Object p0,@Param("p1") Object p1);

 /**
  * statement7 为原TenantInvitations固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p2 原调用第3个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Insert("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}))")
 int statement7(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /**
  * statement8 为原TenantInvitations固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Delete("DELETE FROM access_member_role WHERE tenant_id=UUID_TO_BIN(#{p0}) AND member_id=UUID_TO_BIN(#{p1})")
 int statement8(@Param("p0") Object p0,@Param("p1") Object p1);

 /**
  * statement9 为原TenantInvitations固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Update("UPDATE tenant_member SET enabled=TRUE,removed_at=NULL,authority_version=authority_version+1 WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1})")
 int statement9(@Param("p0") Object p0,@Param("p1") Object p1);

 /**
  * statement10 为原TenantInvitations固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Update("UPDATE tenant_invitation SET status='ACCEPTED' WHERE id=UUID_TO_BIN(#{p0})")
 int statement10(@Param("p0") Object p0);

 /**
  * statement11 为原TenantInvitations固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Update("UPDATE tenant_invitation SET status='REVOKED' WHERE id=UUID_TO_BIN(#{p0}) AND tenant_id=UUID_TO_BIN(#{p1}) AND status='PENDING'")
 int statement11(@Param("p0") Object p0,@Param("p1") Object p1);

 /**
  * statement12 为原TenantInvitations固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Select("SELECT p.account_id FROM platform_administrator p JOIN identity_account a ON a.id=p.account_id WHERE p.account_id=UUID_TO_BIN(#{p0}) AND p.enabled=TRUE AND a.enabled=TRUE FOR UPDATE")
 List<Map<String,Object>> statement12(@Param("p0") Object p0);

 /**
  * statement13 为原TenantInvitations固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Select("SELECT id FROM platform_tenant WHERE id=UUID_TO_BIN(#{p0}) AND enabled=TRUE FOR UPDATE")
 List<Map<String,Object>> statement13(@Param("p0") Object p0);

 /**
  * statement14 为原TenantInvitations固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Select("SELECT id FROM identity_account WHERE id=UUID_TO_BIN(#{p0}) AND enabled=TRUE FOR UPDATE")
 List<Map<String,Object>> statement14(@Param("p0") Object p0);

 /**
  * statement15 为原TenantInvitations固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p2 原调用第3个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p3 原调用第4个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Insert("INSERT INTO platform_administration_audit(actor_account_id,tenant_id,target_id,action) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),#{p3})")
 int statement15(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3);

 /** @param tenant 受权平台操作者已验证的租户UUID @return 最多100个邀请摘要，禁止返回Token哈希 */
 @Select("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(account_id) account,status,expires_at FROM tenant_invitation WHERE tenant_id=UUID_TO_BIN(#{tenant}) ORDER BY created_at DESC,id LIMIT 100")
 List<Map<String,Object>> summaries(@Param("tenant") String tenant);
}
