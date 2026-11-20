package com.appnexus.platform.tenant.port;
import java.util.*;
import java.time.Instant;
/** 邀请固定存取输出端口，限平台持久化适配器实现，不开放SQL或客户端身份注入。 */
public interface InvitationRecords {
 /** existingMembership固定存取，保持账号、租户和邀请原锁顺序；成员、撤旧授权和审计同事务。
  * @param tenant 可信当前或已授权目标租户UUID，禁止客户端任意覆盖
  * @param account 已有启用账号UUID，接受时必须来自真实登录主体
  * @return 原固定投影或影响行数；未匹配不得扩大主体范围
  */
 List<Map<String,Object>> existingMembership(String tenant,String account);
 /** revokePending固定存取，保持账号、租户和邀请原锁顺序；成员、撤旧授权和审计同事务。
  * @param tenant 可信当前或已授权目标租户UUID，禁止客户端任意覆盖
  * @param account 已有启用账号UUID，接受时必须来自真实登录主体
  * @return 原固定投影或影响行数；未匹配不得扩大主体范围
  */
 int revokePending(String tenant,String account);
 /** createInvitation固定存取，保持账号、租户和邀请原锁顺序；成员、撤旧授权和审计同事务。
  * @param id 原邀请或新成员UUID，不以知道ID替代资格验证
  * @param tenant 可信当前或已授权目标租户UUID，禁止客户端任意覆盖
  * @param account 已有启用账号UUID，接受时必须来自真实登录主体
  * @param digest 32字节SHA-256凭证摘要，不是凭证明文，不得写日志
  * @param expires 服务端生成UTC到期时间，原有效期24小时，不接受客户端指定
  * @param actor 可信实际操作者UUID，平台操作需事务内复核资格
  * @return 原固定投影或影响行数；未匹配不得扩大主体范围
  */
 int createInvitation(String id,String tenant,String account,byte[] digest,Instant expires,String actor);
 /** invitationOwner固定存取，保持账号、租户和邀请原锁顺序；成员、撤旧授权和审计同事务。
  * @param id 原邀请或新成员UUID，不以知道ID替代资格验证
  * @param account 已有启用账号UUID，接受时必须来自真实登录主体
  * @return 原固定投影或影响行数；未匹配不得扩大主体范围
  */
 List<Map<String,Object>> invitationOwner(String id,String account);
 /** lockPendingInvitation固定存取，保持账号、租户和邀请原锁顺序；成员、撤旧授权和审计同事务。
  * @param id 原邀请或新成员UUID，不以知道ID替代资格验证
  * @param tenant 可信当前或已授权目标租户UUID，禁止客户端任意覆盖
  * @param account 已有启用账号UUID，接受时必须来自真实登录主体
  * @return 原固定投影或影响行数；未匹配不得扩大主体范围
  */
 List<Map<String,Object>> lockPendingInvitation(String id,String tenant,String account);
 /** lockMembership固定存取，保持账号、租户和邀请原锁顺序；成员、撤旧授权和审计同事务。
  * @param tenant 可信当前或已授权目标租户UUID，禁止客户端任意覆盖
  * @param account 已有启用账号UUID，接受时必须来自真实登录主体
  * @return 原固定投影或影响行数；未匹配不得扩大主体范围
  */
 List<Map<String,Object>> lockMembership(String tenant,String account);
 /** createMembership固定存取，保持账号、租户和邀请原锁顺序；成员、撤旧授权和审计同事务。
  * @param id 原邀请或新成员UUID，不以知道ID替代资格验证
  * @param tenant 可信当前或已授权目标租户UUID，禁止客户端任意覆盖
  * @param account 已有启用账号UUID，接受时必须来自真实登录主体
  * @return 原固定投影或影响行数；未匹配不得扩大主体范围
  */
 int createMembership(String id,String tenant,String account);
 /** clearMemberRoles固定存取，保持账号、租户和邀请原锁顺序；成员、撤旧授权和审计同事务。
  * @param tenant 可信当前或已授权目标租户UUID，禁止客户端任意覆盖
  * @param member 当前租户已有成员UUID，重新加入不恢复旧授权
  * @return 原固定投影或影响行数；未匹配不得扩大主体范围
  */
 int clearMemberRoles(String tenant,String member);
 /** reactivateMembership固定存取，保持账号、租户和邀请原锁顺序；成员、撤旧授权和审计同事务。
  * @param tenant 可信当前或已授权目标租户UUID，禁止客户端任意覆盖
  * @param member 当前租户已有成员UUID，重新加入不恢复旧授权
  * @return 原固定投影或影响行数；未匹配不得扩大主体范围
  */
 int reactivateMembership(String tenant,String member);
 /** acceptInvitation固定存取，保持账号、租户和邀请原锁顺序；成员、撤旧授权和审计同事务。
  * @param id 原邀请或新成员UUID，不以知道ID替代资格验证
  * @return 原固定投影或影响行数；未匹配不得扩大主体范围
  */
 int acceptInvitation(String id);
 /** revokeInvitation固定存取，保持账号、租户和邀请原锁顺序；成员、撤旧授权和审计同事务。
  * @param id 原邀请或新成员UUID，不以知道ID替代资格验证
  * @param tenant 可信当前或已授权目标租户UUID，禁止客户端任意覆盖
  * @return 原固定投影或影响行数；未匹配不得扩大主体范围
  */
 int revokeInvitation(String id,String tenant);
 /** lockAdministrator固定存取，保持账号、租户和邀请原锁顺序；成员、撤旧授权和审计同事务。
  * @param actor 可信实际操作者UUID，平台操作需事务内复核资格
  * @return 原固定投影或影响行数；未匹配不得扩大主体范围
  */
 List<Map<String,Object>> lockAdministrator(String actor);
 /** lockTenant固定存取，保持账号、租户和邀请原锁顺序；成员、撤旧授权和审计同事务。
  * @param tenant 可信当前或已授权目标租户UUID，禁止客户端任意覆盖
  * @return 原固定投影或影响行数；未匹配不得扩大主体范围
  */
 List<Map<String,Object>> lockTenant(String tenant);
 /** lockAccount固定存取，保持账号、租户和邀请原锁顺序；成员、撤旧授权和审计同事务。
  * @param account 已有启用账号UUID，接受时必须来自真实登录主体
  * @return 原固定投影或影响行数；未匹配不得扩大主体范围
  */
 List<Map<String,Object>> lockAccount(String account);
 /** appendAudit固定存取，保持账号、租户和邀请原锁顺序；成员、撤旧授权和审计同事务。
  * @param actor 可信实际操作者UUID，平台操作需事务内复核资格
  * @param tenant 可信当前或已授权目标租户UUID，禁止客户端任意覆盖
  * @param target 当前审计目标UUID，不含Token或业务原文
  * @param action 原固定MEMBER_INVITED、INVITATION_ACCEPTED或INVITATION_REVOKED事件
  * @return 原固定投影或影响行数；未匹配不得扩大主体范围
  */
 int appendAudit(String actor,String tenant,String target,String action);
 /** summaries固定存取，保持账号、租户和邀请原锁顺序；成员、撤旧授权和审计同事务。
  * @param tenant 可信当前或已授权目标租户UUID，禁止客户端任意覆盖
  * @return 原固定投影或影响行数；未匹配不得扩大主体范围
  */
 List<Map<String,Object>> summaries(String tenant);
}
