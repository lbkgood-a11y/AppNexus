package com.appnexus.platform.persistence.tenant;
import com.appnexus.platform.tenant.TenantInvitations;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** TenantInvitations固定SQL映射；没有接收SQL、表名或列名的通用执行入口。 */
@Mapper
@PlatformScoped(tables={"access_member_role","identity_account","platform_administration_audit","platform_administrator","platform_tenant","tenant_invitation","tenant_member"})
public interface TenantInvitationMapper {
 /**
  * existingMembership 为原TenantInvitations固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Select("SELECT id FROM tenant_member WHERE tenant_id=UUID_TO_BIN(#{p0}) AND account_id=UUID_TO_BIN(#{p1}) AND removed_at IS NULL FOR UPDATE")
 List<Map<String,Object>> existingMembership(@Param("p0") Object p0,@Param("p1") Object p1);

 /**
  * revokePending 为原TenantInvitations固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Update("UPDATE tenant_invitation SET status='REVOKED' WHERE tenant_id=UUID_TO_BIN(#{p0}) AND account_id=UUID_TO_BIN(#{p1}) AND status='PENDING'")
 int revokePending(@Param("p0") Object p0,@Param("p1") Object p1);

 /**
  * createInvitation 为原TenantInvitations固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p2 原调用第3个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p3 原调用第4个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p4 原调用第5个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p5 原调用第6个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Insert("INSERT INTO tenant_invitation(id,tenant_id,account_id,token_hash,expires_at,invited_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),#{p3},#{p4},UUID_TO_BIN(#{p5}))")
 int createInvitation(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5);

 /**
  * invitationOwner 为原TenantInvitations固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Select("SELECT BIN_TO_UUID(tenant_id) tenant FROM tenant_invitation WHERE id=UUID_TO_BIN(#{p0}) AND account_id=UUID_TO_BIN(#{p1})")
 List<Map<String,Object>> invitationOwner(@Param("p0") Object p0,@Param("p1") Object p1);

 /**
  * lockPendingInvitation 为原TenantInvitations固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p2 原调用第3个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Select("SELECT token_hash FROM tenant_invitation WHERE id=UUID_TO_BIN(#{p0}) AND tenant_id=UUID_TO_BIN(#{p1}) AND account_id=UUID_TO_BIN(#{p2}) AND status='PENDING' AND expires_at>CURRENT_TIMESTAMP(6) FOR UPDATE")
 List<Map<String,Object>> lockPendingInvitation(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /**
  * lockMembership 为原TenantInvitations固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Select("SELECT BIN_TO_UUID(id) id,removed_at FROM tenant_member WHERE tenant_id=UUID_TO_BIN(#{p0}) AND account_id=UUID_TO_BIN(#{p1}) FOR UPDATE")
 List<Map<String,Object>> lockMembership(@Param("p0") Object p0,@Param("p1") Object p1);

 /**
  * createMembership 为原TenantInvitations固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p2 原调用第3个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Insert("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}))")
 int createMembership(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /**
  * clearMemberRoles 为原TenantInvitations固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Delete("DELETE FROM access_member_role WHERE tenant_id=UUID_TO_BIN(#{p0}) AND member_id=UUID_TO_BIN(#{p1})")
 int clearMemberRoles(@Param("p0") Object p0,@Param("p1") Object p1);

 /**
  * reactivateMembership 为原TenantInvitations固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Update("UPDATE tenant_member SET enabled=TRUE,removed_at=NULL,authority_version=authority_version+1 WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1})")
 int reactivateMembership(@Param("p0") Object p0,@Param("p1") Object p1);

 /**
  * acceptInvitation 为原TenantInvitations固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Update("UPDATE tenant_invitation SET status='ACCEPTED' WHERE id=UUID_TO_BIN(#{p0})")
 int acceptInvitation(@Param("p0") Object p0);

 /**
  * revokeInvitation 为原TenantInvitations固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Update("UPDATE tenant_invitation SET status='REVOKED' WHERE id=UUID_TO_BIN(#{p0}) AND tenant_id=UUID_TO_BIN(#{p1}) AND status='PENDING'")
 int revokeInvitation(@Param("p0") Object p0,@Param("p1") Object p1);

 /**
  * lockAdministrator 为原TenantInvitations固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Select("SELECT p.account_id FROM platform_administrator p JOIN identity_account a ON a.id=p.account_id WHERE p.account_id=UUID_TO_BIN(#{p0}) AND p.enabled=TRUE AND a.enabled=TRUE FOR UPDATE")
 List<Map<String,Object>> lockAdministrator(@Param("p0") Object p0);

 /**
  * lockTenant 为原TenantInvitations固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Select("SELECT id FROM platform_tenant WHERE id=UUID_TO_BIN(#{p0}) AND enabled=TRUE FOR UPDATE")
 List<Map<String,Object>> lockTenant(@Param("p0") Object p0);

 /**
  * lockAccount 为原TenantInvitations固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Select("SELECT id FROM identity_account WHERE id=UUID_TO_BIN(#{p0}) AND enabled=TRUE FOR UPDATE")
 List<Map<String,Object>> lockAccount(@Param("p0") Object p0);

 /**
  * appendAudit 为原TenantInvitations固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p2 原调用第3个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p3 原调用第4个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Insert("INSERT INTO platform_administration_audit(actor_account_id,tenant_id,target_id,action) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),#{p3})")
 int appendAudit(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3);

 /** @param tenant 受权平台操作者已验证的租户UUID @return 最多100个邀请摘要，禁止返回Token哈希 */
 @Select("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(account_id) account,status,expires_at FROM tenant_invitation WHERE tenant_id=UUID_TO_BIN(#{tenant}) ORDER BY created_at DESC,id LIMIT 100")
 List<Map<String,Object>> summaries(@Param("tenant") String tenant);
}
