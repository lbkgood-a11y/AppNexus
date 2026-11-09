package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.persistence.PlatformScoped;
import java.util.*;
import org.apache.ibatis.annotations.*;

/** 主体角色分配 Mapper；服务层按主体、角色、关系、审计的固定顺序持锁。 */
@Mapper
@PlatformScoped(tables={"identity_account","tenant_member","platform_tenant","access_role","access_account_role","access_member_role","access_authorization_audit","platform_administrator","organization_capability_grant"})
public interface SubjectRoleMapper {
 /** 只移除明确撤销的角色，避免全量重建覆盖保留角色的成员范围。 */
 @Delete("DELETE FROM access_member_role WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND member_id=UUID_TO_BIN(#{member}) AND role_id=UUID_TO_BIN(#{role})")
 int deleteMemberRole(@Param("tenant") String tenant,@Param("member") String member,@Param("role") String role);
 @Select("SELECT EXISTS(SELECT 1 FROM platform_administrator p JOIN identity_account a ON a.id=p.account_id WHERE p.account_id=UUID_TO_BIN(#{actor}) AND p.enabled=TRUE AND a.enabled=TRUE)") boolean platformAdministrator(String actor);
 @Select("SELECT EXISTS(SELECT 1 FROM tenant_member m JOIN identity_account a ON a.id=m.account_id JOIN platform_tenant t ON t.id=m.tenant_id JOIN organization_capability_grant g ON g.tenant_id=m.tenant_id AND g.member_id=m.id AND g.capability='GRANT' WHERE m.tenant_id=UUID_TO_BIN(#{tenant}) AND m.id=UUID_TO_BIN(#{member}) AND m.account_id=UUID_TO_BIN(#{actor}) AND m.enabled=TRUE AND a.enabled=TRUE AND t.enabled=TRUE AND g.revoked_at IS NULL AND (g.expires_at IS NULL OR g.expires_at>CURRENT_TIMESTAMP(6)))") boolean tenantAdministrator(@Param("actor") String actor,@Param("tenant") String tenant,@Param("member") String member);
 @Select("SELECT authority_version FROM identity_account WHERE id=UUID_TO_BIN(#{id}) AND enabled=TRUE FOR UPDATE") Long lockAccount(String id);
 @Select("SELECT authority_version FROM tenant_member WHERE id=UUID_TO_BIN(#{member}) AND tenant_id=UUID_TO_BIN(#{tenant}) AND enabled=TRUE FOR UPDATE") Long lockMember(@Param("tenant") String tenant,@Param("member") String member);
 @Select("SELECT BIN_TO_UUID(role_id) FROM access_account_role WHERE account_id=UUID_TO_BIN(#{id}) ORDER BY role_id") List<String> accountRoles(String id);
 @Select("SELECT BIN_TO_UUID(role_id) FROM access_member_role WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND member_id=UUID_TO_BIN(#{member}) ORDER BY role_id") List<String> memberRoles(@Param("tenant") String tenant,@Param("member") String member);
 @Select({"<script>","SELECT BIN_TO_UUID(id) FROM access_role WHERE scope_type=#{scope} AND deleted_at IS NULL AND status='ENABLED' AND id IN","<foreach collection='ids' item='id' open='(' separator=',' close=')'>UUID_TO_BIN(#{id})</foreach>","<if test=\"tenant != null\">AND tenant_id=UUID_TO_BIN(#{tenant})</if>","ORDER BY id FOR UPDATE","</script>"}) List<String> lockRoles(@Param("scope") String scope,@Param("tenant") String tenant,@Param("ids") List<String> ids);
 @Delete("DELETE FROM access_account_role WHERE account_id=UUID_TO_BIN(#{id})") int deleteAccountRoles(String id);
 @Delete("DELETE FROM access_member_role WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND member_id=UUID_TO_BIN(#{member})") int deleteMemberRoles(@Param("tenant") String tenant,@Param("member") String member);
 @Insert({"<script>","INSERT INTO access_account_role(account_id,role_id,assigned_by) VALUES","<foreach collection='ids' item='id' separator=','>(UUID_TO_BIN(#{target}),UUID_TO_BIN(#{id}),UUID_TO_BIN(#{actor}))</foreach>","</script>"}) int insertAccountRoles(@Param("target") String target,@Param("actor") String actor,@Param("ids") List<String> ids);
 @Insert({"<script>","INSERT INTO access_member_role(tenant_id,member_id,role_id,assigned_by) VALUES","<foreach collection='ids' item='id' separator=','>(UUID_TO_BIN(#{tenant}),UUID_TO_BIN(#{target}),UUID_TO_BIN(#{id}),UUID_TO_BIN(#{actor}))</foreach>","</script>"}) int insertMemberRoles(@Param("tenant") String tenant,@Param("target") String target,@Param("actor") String actor,@Param("ids") List<String> ids);
 @Update("UPDATE identity_account SET authority_version=authority_version+1 WHERE id=UUID_TO_BIN(#{id}) AND authority_version=#{version}") int bumpAccount(@Param("id") String id,@Param("version") long version);
 @Update("UPDATE tenant_member SET authority_version=authority_version+1 WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{member}) AND authority_version=#{version}") int bumpMember(@Param("tenant") String tenant,@Param("member") String member,@Param("version") long version);
 @Insert("INSERT INTO access_authorization_audit(scope_type,tenant_id,actor_account_id,action,target_type,target_id,reason,before_snapshot,after_snapshot) VALUES(#{scope},UUID_TO_BIN(#{tenant}),UUID_TO_BIN(#{actor}),#{action},#{targetType},UUID_TO_BIN(#{target}),#{reason},CAST(#{before} AS JSON),CAST(#{after} AS JSON))") void audit(@Param("scope") String scope,@Param("tenant") String tenant,@Param("actor") String actor,@Param("action") String action,@Param("targetType") String targetType,@Param("target") String target,@Param("reason") String reason,@Param("before") String before,@Param("after") String after);
}
