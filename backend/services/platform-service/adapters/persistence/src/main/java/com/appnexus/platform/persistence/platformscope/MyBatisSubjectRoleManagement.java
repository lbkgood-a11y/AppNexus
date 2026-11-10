package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.authorization.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 主体角色全量覆盖实现；有效主体锁、角色域复核、版本推进与审计在同一 MySQL 事务完成。 */
@Service
public class MyBatisSubjectRoleManagement implements SubjectRoleManagement {
 private final SubjectRoleMapper mapper;
 private final MemberAuthorizationMapper snapshots;
 private final AuthorityEvents events;
 public MyBatisSubjectRoleManagement(SubjectRoleMapper mapper,MemberAuthorizationMapper snapshots,AuthorityEvents events){this.mapper=mapper;this.snapshots=snapshots;this.events=events;}
 @Override @Transactional public Assignment accountRoles(UUID actor,UUID target){platform(actor);Long version=mapper.lockAccount(text(target));if(version==null)notFound();return assignment(mapper.accountRoles(text(target)),version);}
 @Override @Transactional public Assignment replaceAccountRoles(UUID actor,UUID target,long expected,Set<UUID> ids,String reason){platform(actor);return replaceAccount(actor,target,expected,normalize(ids),reason);}
 @Override @Transactional public Assignment memberRoles(RoleManagement.Context c,UUID target){var x=tenant(c);Long version=mapper.lockMember(text(x.tenantId()),text(target));if(version==null)notFound();return assignment(mapper.memberRoles(text(x.tenantId()),text(target)),version);}
 @Override @Transactional public Assignment replaceMemberRoles(RoleManagement.Context c,UUID target,long expected,Set<UUID> ids,String reason){var x=tenant(c);required(reason);validVersion(expected);var roles=normalize(ids);Long current=mapper.lockMember(text(x.tenantId()),text(target));if(current==null)notFound();if(current!=expected)conflict();validateRoles("TENANT",text(x.tenantId()),roles);var before=mapper.memberRoles(text(x.tenantId()),text(target));if(equal(before,roles))return assignment(before,current);// 保留未变角色的独立快照；先删除撤销分支，再仅为新增角色复制一次默认策略。
for(String id:before)if(!roles.contains(UUID.fromString(id)))mapper.deleteMemberRole(text(x.tenantId()),text(target),id);
for(UUID id:roles)if(!before.contains(id.toString())){mapper.insertMemberRoles(text(x.tenantId()),text(target),text(x.actorAccountId()),List.of(id.toString()));if(snapshots.create(text(x.tenantId()),text(target),id.toString())==1){snapshots.copyPermissions(text(x.tenantId()),text(target),id.toString());snapshots.copyOrganizations(text(x.tenantId()),text(target),id.toString());}}if(mapper.bumpMember(text(x.tenantId()),text(target),current)!=1)conflict();events.invalidated(x.tenantId(),target);mapper.audit("TENANT",text(x.tenantId()),text(x.actorAccountId()),"MEMBER_ROLES_REPLACED","MEMBER",text(target),reason.trim(),json(before),json(strings(roles)));return new Assignment(Set.copyOf(roles),current+1);}
 private Assignment replaceAccount(UUID actor,UUID target,long expected,List<UUID> roles,String reason){required(reason);validVersion(expected);Long current=mapper.lockAccount(text(target));if(current==null)notFound();if(current!=expected)conflict();validateRoles("PLATFORM",null,roles);var before=mapper.accountRoles(text(target));if(equal(before,roles))return assignment(before,current);mapper.deleteAccountRoles(text(target));if(!roles.isEmpty())mapper.insertAccountRoles(text(target),text(actor),strings(roles));if(mapper.bumpAccount(text(target),current)!=1)conflict();events.invalidated(null,target);mapper.audit("PLATFORM",null,text(actor),"ACCOUNT_ROLES_REPLACED","ACCOUNT",text(target),reason.trim(),json(before),json(strings(roles)));return new Assignment(Set.copyOf(roles),current+1);}
 private void validateRoles(String scope,String tenant,List<UUID> roles){if(!roles.isEmpty()&&mapper.lockRoles(scope,tenant,strings(roles)).size()!=roles.size())throw error("ROLE_SCOPE_MISMATCH","角色不存在、已停用或不属于目标管理域");}
 private void platform(UUID actor){Objects.requireNonNull(actor);if(!mapper.platformAdministrator(text(actor)))throw new SecurityException("Subject role authority required");}
 private RoleManagement.Context tenant(RoleManagement.Context c){Objects.requireNonNull(c);Objects.requireNonNull(c.actorAccountId());Objects.requireNonNull(c.tenantId());if(c.actorMemberId()==null){platform(c.actorAccountId());return c;}if(!mapper.tenantAdministrator(text(c.actorAccountId()),text(c.tenantId()),text(c.actorMemberId())))throw new SecurityException("Subject role authority required");return c;}
 private List<UUID> normalize(Set<UUID> ids){Objects.requireNonNull(ids);if(ids.size()>100)throw new IllegalArgumentException("单主体角色数量不能超过 100");if(ids.stream().anyMatch(Objects::isNull))throw new IllegalArgumentException("角色 ID 不能为空");return ids.stream().sorted().toList();}
 private Assignment assignment(List<String> ids,long version){return new Assignment(ids.stream().map(UUID::fromString).collect(java.util.stream.Collectors.toUnmodifiableSet()),version);}
 private boolean equal(List<String> before,List<UUID> after){return before.stream().map(UUID::fromString).collect(java.util.stream.Collectors.toSet()).equals(Set.copyOf(after));}
 private List<String> strings(Collection<UUID> ids){return ids.stream().map(UUID::toString).toList();}
 private String json(Collection<String> ids){return ids.stream().map(id->"\""+id+"\"").collect(java.util.stream.Collectors.joining(",","[","]"));}
 private String text(UUID id){return id==null?null:id.toString();} private void validVersion(long v){if(v<0)throw new IllegalArgumentException("授权版本无效");} private void required(String r){if(r==null||r.isBlank()||r.trim().length()>500)throw new IllegalArgumentException("原因须为 1–500 字符");}
 private void notFound(){throw error("SUBJECT_NOT_FOUND","目标主体不存在");} private void conflict(){throw error("AUTHORITY_VERSION_CONFLICT","主体角色已被其他管理员修改");} private RoleManagementException error(String c,String m){return new RoleManagementException(c,m);}
}
