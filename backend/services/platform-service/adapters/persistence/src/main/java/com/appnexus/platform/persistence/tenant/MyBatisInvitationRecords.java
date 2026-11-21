package com.appnexus.platform.persistence.tenant;
import java.util.*;
import java.time.Instant;
import com.appnexus.platform.tenant.port.InvitationRecords;
import org.springframework.stereotype.Component;
/** 邀请固定SQL及UTC时间转换适配，不决定业务授权。 */
@Component
public final class MyBatisInvitationRecords implements InvitationRecords, com.appnexus.platform.authorization.DataAccessDeclaration.Executor {
 /** @return 受固定租户和目标账号凭证条件约束的邀请资源，密钥摘要不进入字段目录 */
 @Override public java.util.Set<String> enforcedResources(){return java.util.Set.of("tenant.invitation");}
 private final TenantInvitationMapper mapper;
 /** @param mapper 原邀请固定SQL映射，与原事务共用连接 */
 public MyBatisInvitationRecords(TenantInvitationMapper mapper){this.mapper=mapper;}
 /** {@inheritDoc} */
 public List<Map<String,Object>> existingMembership(String tenant,String account){return stable(mapper.existingMembership(tenant,account));}
 /** {@inheritDoc} */
 public int revokePending(String tenant,String account){return mapper.revokePending(tenant,account);}
 /** {@inheritDoc} */
 public int createInvitation(String id,String tenant,String account,byte[] digest,Instant expires,String actor){return mapper.createInvitation(id,tenant,account,digest,expires,actor);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> invitationOwner(String id,String account){return stable(mapper.invitationOwner(id,account));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> lockPendingInvitation(String id,String tenant,String account){return stable(mapper.lockPendingInvitation(id,tenant,account));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> lockMembership(String tenant,String account){return stable(mapper.lockMembership(tenant,account));}
 /** {@inheritDoc} */
 public int createMembership(String id,String tenant,String account){return mapper.createMembership(id,tenant,account);}
 /** {@inheritDoc} */
 public int clearMemberRoles(String tenant,String member){return mapper.clearMemberRoles(tenant,member);}
 /** {@inheritDoc} */
 public int reactivateMembership(String tenant,String member){return mapper.reactivateMembership(tenant,member);}
 /** {@inheritDoc} */
 public int acceptInvitation(String id){return mapper.acceptInvitation(id);}
 /** {@inheritDoc} */
 public int revokeInvitation(String id,String tenant){return mapper.revokeInvitation(id,tenant);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> lockAdministrator(String actor){return stable(mapper.lockAdministrator(actor));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> lockTenant(String tenant){return stable(mapper.lockTenant(tenant));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> lockAccount(String account){return stable(mapper.lockAccount(account));}
 /** {@inheritDoc} */
 public int appendAudit(String actor,String tenant,String target,String action){return mapper.appendAudit(actor,tenant,target,action);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> summaries(String tenant){return stable(mapper.summaries(tenant));}
 @SuppressWarnings("unchecked") private <T>T stable(T value){if(value instanceof java.sql.Timestamp at)return (T)at.toInstant();if(value instanceof Map<?,?> map){var out=new LinkedHashMap<Object,Object>();map.forEach((key,item)->out.put(key,stable(item)));return (T)out;}if(value instanceof List<?> list)return (T)list.stream().map(this::stable).toList();return value;}
}
