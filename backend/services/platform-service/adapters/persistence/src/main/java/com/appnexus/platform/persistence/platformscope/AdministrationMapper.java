package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.persistence.PlatformScoped;
import java.util.*;
import org.apache.ibatis.annotations.*;

/** 专用平台目录仓储；仅由平台管理用例调用，所有列表限制摘要字段和分页，不读取租户业务内容。 */
@Mapper
@PlatformScoped(tables={"identity_account","platform_tenant","tenant_member","platform_administrator","platform_administrator_bootstrap","platform_administration_audit","platform_tenant_creation","organization_capability_grant","organization_grant_audit","access_role","access_permission","access_role_permission","access_account_role","access_role_field_security","access_member_role","access_member_permission","tenant_organization_assignment","tenant_organization_leader","access_effective_tenant_permission"})
public interface AdministrationMapper {
    /** 每次操作从权威源复核有效平台管理员，不能信任会话中的角色展示缓存。 */
    @Select("SELECT EXISTS(SELECT 1 FROM platform_administrator p JOIN identity_account a ON a.id=p.account_id WHERE p.account_id=UUID_TO_BIN(#{actor}) AND p.enabled=TRUE AND a.enabled=TRUE)")
    boolean administrator(String actor);
    /** 事务内固定管理员行锁，将平台写操作串行化，避免停用与成员写入竞态。 */
    @Select("SELECT BIN_TO_UUID(p.account_id) FROM platform_administrator p JOIN identity_account a ON a.id=p.account_id WHERE p.account_id=UUID_TO_BIN(#{actor}) AND p.enabled=TRUE AND a.enabled=TRUE FOR UPDATE")
    String lockAdministrator(String actor);
    /** 单例 claim 在初始化事务内与授权、审计一起提交。 */
    @Insert("INSERT IGNORE INTO platform_administrator_bootstrap(slot,account_id) VALUES(1,UUID_TO_BIN(#{account}))")
    int claim(String account);
    @Select("SELECT BIN_TO_UUID(account_id) FROM platform_administrator_bootstrap WHERE slot=1 FOR UPDATE")
    String initializedAccount();
    @Select("SELECT BIN_TO_UUID(id) FROM identity_account WHERE username=#{username} AND enabled=TRUE FOR UPDATE")
    String accountByUsername(String username);
    @Select("SELECT BIN_TO_UUID(id) FROM identity_account WHERE id=UUID_TO_BIN(#{account}) AND enabled=TRUE FOR UPDATE")
    String lockAccount(String account);
    @Insert("INSERT INTO platform_administrator(account_id) VALUES(UUID_TO_BIN(#{account}))")
    void initialAdministrator(String account);
    /** 幂等建立平台超级管理员系统角色，运行时初始化与 V24 使用同一固定标识。 */
    @Insert("INSERT IGNORE INTO access_role(id,scope_type,role_type,system_key,code,name,description,status,sort_order) VALUES(UUID_TO_BIN('019a65d0-0000-7000-9000-000000000001'),'PLATFORM','SYSTEM','PLATFORM_SUPER_ADMIN','sys_platform_super_admin','平台超级管理员','平台受控初始化角色；拥有全部已发布平台权限。','ENABLED',0)")
    int ensurePlatformSystemRole();
    @Insert("INSERT IGNORE INTO access_role_permission(role_id,permission_id,scope_type,granted_by) SELECT UUID_TO_BIN('019a65d0-0000-7000-9000-000000000001'),id,'PLATFORM',UUID_TO_BIN(#{account}) FROM access_permission WHERE scope_type='PLATFORM' AND enabled=TRUE")
    int ensurePlatformSystemPermissions(String account);
    @Insert("INSERT IGNORE INTO access_account_role(account_id,role_id,scope_type,assigned_by) VALUES(UUID_TO_BIN(#{account}),UUID_TO_BIN('019a65d0-0000-7000-9000-000000000001'),'PLATFORM',UUID_TO_BIN(#{account}))")
    int ensurePlatformSystemAssignment(String account);
    /** 查询词作为 LIKE 参数绑定，前端不提供 SQL、排序列或表名。 */
    @Select("SELECT BIN_TO_UUID(id) id,username,display_name,email,email_verified,phone_number,phone_verified,enabled FROM identity_account WHERE username LIKE CONCAT('%',#{query},'%') OR display_name LIKE CONCAT('%',#{query},'%') ORDER BY username,id LIMIT #{size} OFFSET #{offset}")
    List<Map<String,Object>> users(@Param("query") String query,@Param("size") int size,@Param("offset") long offset);
    @Select("SELECT COUNT(*) FROM identity_account WHERE username LIKE CONCAT('%',#{query},'%') OR display_name LIKE CONCAT('%',#{query},'%')")
    long userCount(String query);
    @Insert("INSERT INTO identity_account(id,username,display_name,email,phone_number,password_hash) VALUES(UUID_TO_BIN(#{id}),#{username},#{name},#{email},#{phone},#{hash})")
    void createUser(@Param("id") String id,@Param("username") String username,@Param("name") String name,@Param("email") String email,@Param("phone") String phone,@Param("hash") String hash);
    /** 修改联系方式必须重置验证状态，避免新值继承旧值的信任。 */
    @Update("UPDATE identity_account SET display_name=#{name},email_verified=IF(email<=>#{email},email_verified,FALSE),email=#{email},phone_verified=IF(phone_number<=>#{phone},phone_verified,FALSE),phone_number=#{phone} WHERE id=UUID_TO_BIN(#{id})")
    int updateUser(@Param("id") String id,@Param("name") String name,@Param("email") String email,@Param("phone") String phone);
    @Select("SELECT BIN_TO_UUID(id) id,username,display_name,email,email_verified,phone_number,phone_verified,enabled FROM identity_account WHERE id=UUID_TO_BIN(#{id})")
    Map<String,Object> user(String id);
    /** 状态变化增加凭据版本，恢复账号也不能重用停用前的会话。 */
    @Update("UPDATE identity_account SET enabled=#{enabled},version=version+1 WHERE id=UUID_TO_BIN(#{id}) AND enabled<>#{enabled}")
    int userState(@Param("id") String id,@Param("enabled") boolean enabled);
    @Select("SELECT EXISTS(SELECT 1 FROM platform_administrator WHERE account_id=UUID_TO_BIN(#{id}))")
    boolean protectedAccount(String id);
    /** 组织管理者账号必须走专门运维治理，防止从全局账号停用入口绕过管理权限保护。 */
    @Select("SELECT EXISTS(SELECT 1 FROM access_effective_tenant_permission e WHERE e.account_id=UUID_TO_BIN(#{id}) AND e.permission_code='organization.permission:grant')")
    boolean organizationAdministrator(String id);
    @Select("SELECT BIN_TO_UUID(id) id,name,enabled FROM platform_tenant WHERE name LIKE CONCAT('%',#{query},'%') ORDER BY name,id LIMIT #{size} OFFSET #{offset}")
    List<Map<String,Object>> tenants(@Param("query") String query,@Param("size") int size,@Param("offset") long offset);
    @Select("SELECT COUNT(*) FROM platform_tenant WHERE name LIKE CONCAT('%',#{query},'%')")
    long tenantCount(String query);
    @Insert("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(#{id}),#{name})")
    void createTenant(@Param("id") String id,@Param("name") String name);
    @Select("SELECT BIN_TO_UUID(id) id,name,enabled FROM platform_tenant WHERE id=UUID_TO_BIN(#{tenant})")
    Map<String,Object> tenant(String tenant);
    @Select("SELECT enabled FROM platform_tenant WHERE id=UUID_TO_BIN(#{tenant}) FOR UPDATE")
    Boolean lockTenant(String tenant);
    @Update("UPDATE platform_tenant SET name=#{name} WHERE id=UUID_TO_BIN(#{tenant}) AND name<>#{name}")
    int updateTenant(@Param("tenant") String tenant,@Param("name") String name);
    @Update("UPDATE platform_tenant SET enabled=#{enabled} WHERE id=UUID_TO_BIN(#{tenant})")
    int tenantState(@Param("tenant") String tenant,@Param("enabled") boolean enabled);
    @Select("SELECT BIN_TO_UUID(tenant_id) tenant_id,BIN_TO_UUID(actor_account_id) actor,BIN_TO_UUID(administrator_account_id) administrator,tenant_name FROM platform_tenant_creation WHERE request_id=UUID_TO_BIN(#{request})")
    Map<String,Object> creation(String request);
    @Insert("INSERT INTO platform_tenant_creation(request_id,tenant_id,actor_account_id,administrator_account_id,tenant_name) VALUES(UUID_TO_BIN(#{request}),UUID_TO_BIN(#{tenant}),UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{administrator}),#{name})")
    void rememberCreation(@Param("request") String request,@Param("tenant") String tenant,@Param("actor") String actor,@Param("administrator") String administrator,@Param("name") String name);
    @Select("SELECT BIN_TO_UUID(m.id) member_id,BIN_TO_UUID(a.id) account_id,a.username,a.display_name,m.enabled,m.removed_at FROM tenant_member m JOIN identity_account a ON a.id=m.account_id WHERE m.tenant_id=UUID_TO_BIN(#{tenant}) AND (a.username LIKE CONCAT('%',#{query},'%') OR a.display_name LIKE CONCAT('%',#{query},'%')) ORDER BY a.username,m.id LIMIT #{size} OFFSET #{offset}")
    List<Map<String,Object>> members(@Param("tenant") String tenant,@Param("query") String query,@Param("size") int size,@Param("offset") long offset);
    @Select("SELECT COUNT(*) FROM tenant_member m JOIN identity_account a ON a.id=m.account_id WHERE m.tenant_id=UUID_TO_BIN(#{tenant}) AND (a.username LIKE CONCAT('%',#{query},'%') OR a.display_name LIKE CONCAT('%',#{query},'%'))")
    long memberCount(@Param("tenant") String tenant,@Param("query") String query);
    @Insert("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(#{id}),UUID_TO_BIN(#{tenant}),UUID_TO_BIN(#{account}))")
    void addMember(@Param("id") String id,@Param("tenant") String tenant,@Param("account") String account);
    @Select("SELECT BIN_TO_UUID(id) member_id,enabled,removed_at FROM tenant_member WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND account_id=UUID_TO_BIN(#{account}) FOR UPDATE")
    Map<String,Object> lockMemberByAccount(@Param("tenant") String tenant,@Param("account") String account);
    /** 重新加入仅恢复成员身份；历史角色、直授权和组织能力已在移除事务中撤销。 */
    @Update("UPDATE tenant_member SET enabled=TRUE,removed_at=NULL,authority_version=authority_version+1 WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{member}) AND removed_at IS NOT NULL")
    int rejoinMember(@Param("tenant") String tenant,@Param("member") String member);
    @Select("SELECT enabled FROM tenant_member WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{member}) FOR UPDATE")
    Boolean lockMember(@Param("tenant") String tenant,@Param("member") String member);
    @Select("SELECT removed_at IS NOT NULL FROM tenant_member WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{member})")
    Boolean memberRemoved(@Param("tenant") String tenant,@Param("member") String member);
    @Update("UPDATE tenant_member SET enabled=#{enabled} WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{member})")
    int memberState(@Param("tenant") String tenant,@Param("member") String member,@Param("enabled") boolean enabled);
    /** 管理者成员禁止从普通停用入口变更，避免绕过 GRANT 运维治理。 */
    @Select("SELECT EXISTS(SELECT 1 FROM access_effective_tenant_permission e WHERE e.tenant_id=UUID_TO_BIN(#{tenant}) AND e.member_id=UUID_TO_BIN(#{member}) AND e.permission_code='organization.permission:grant')")
    boolean manages(@Param("tenant") String tenant,@Param("member") String member);
    @Select("SELECT COUNT(DISTINCT e.member_id) FROM access_effective_tenant_permission e WHERE e.tenant_id=UUID_TO_BIN(#{tenant}) AND e.permission_code='organization.permission:grant'")
    int activeManagerCount(String tenant);
    @Update("UPDATE tenant_member SET enabled=FALSE,removed_at=CURRENT_TIMESTAMP(6),authority_version=authority_version+1 WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{member}) AND removed_at IS NULL")
    int removeMember(@Param("tenant") String tenant,@Param("member") String member);
    /** 成员失效时移除负责人引用，避免选人继续命中已离岗成员。 */
    @Delete("DELETE l FROM tenant_organization_leader l JOIN tenant_organization_assignment a ON a.tenant_id=l.tenant_id AND a.id=l.assignment_id WHERE a.tenant_id=UUID_TO_BIN(#{tenant}) AND a.member_id=UUID_TO_BIN(#{member})")
    int removeMemberLeaders(@Param("tenant") String tenant,@Param("member") String member);
    /** 结束当前及未来任职并推进版本；历史任职保留，不物理删除。 */
    @Update("UPDATE tenant_organization_assignment SET enabled=FALSE,ends_at=CASE WHEN starts_at<CURRENT_TIMESTAMP(6) THEN CURRENT_TIMESTAMP(6) ELSE starts_at END,version=version+1 WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND member_id=UUID_TO_BIN(#{member}) AND enabled=TRUE AND (ends_at IS NULL OR ends_at>CURRENT_TIMESTAMP(6))")
    int endMemberAssignments(@Param("tenant") String tenant,@Param("member") String member);
    /** 成员停用审计不伪造租户内操作者；平台停用动作记录在独立平台审计中。 */
    @Update("UPDATE organization_capability_grant SET revoked_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND member_id=UUID_TO_BIN(#{member}) AND revoked_at IS NULL")
    void revokeMember(@Param("tenant") String tenant,@Param("member") String member);
    /** 停用时删除角色关系并级联当前授权快照；恢复成员不能恢复已撤销授权，历史仍由审计保留。 */
    @Delete("DELETE FROM access_member_role WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND member_id=UUID_TO_BIN(#{member})")
    void revokeMemberRoles(@Param("tenant") String tenant,@Param("member") String member);
    /** 旧成员直授权同事务撤销，防止恢复成员后从兼容分支重新放行。 */
    @Delete("DELETE FROM access_member_permission WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND member_id=UUID_TO_BIN(#{member})")
    void revokeMemberPermissions(@Param("tenant") String tenant,@Param("member") String member);
    /** 停用撤权推进授权版本，旧工作台草稿不能覆盖停用后的授权状态。 */
    @Update("UPDATE tenant_member SET authority_version=authority_version+1 WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{member})")
    void bumpMemberAuthority(@Param("tenant") String tenant,@Param("member") String member);
    @Insert("INSERT INTO platform_administration_audit(actor_account_id,action,target_id,tenant_id) VALUES(UUID_TO_BIN(#{actor}),#{action},UUID_TO_BIN(#{target}),UUID_TO_BIN(#{tenant}))")
    void audit(@Param("actor") String actor,@Param("action") String action,@Param("target") String target,@Param("tenant") String tenant);
}
