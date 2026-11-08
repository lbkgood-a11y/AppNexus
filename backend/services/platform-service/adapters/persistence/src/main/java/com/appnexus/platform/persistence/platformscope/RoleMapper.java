package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.persistence.PlatformScoped;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/** 角色持久化 Mapper；每条角色 SQL 都以归一化管理域标识限定范围。 */
@Mapper
@PlatformScoped(tables={"access_role","access_role_audit","platform_administrator","identity_account","tenant_member","organization_capability_grant","platform_tenant"})
public interface RoleMapper {
    /** 平台入口每次复核账号权威状态，前端路由角色不作为授权依据。 */
    @Select("SELECT EXISTS(SELECT 1 FROM platform_administrator p JOIN identity_account a ON a.id=p.account_id WHERE p.account_id=UUID_TO_BIN(#{actor}) AND p.enabled=TRUE AND a.enabled=TRUE)")
    boolean platformAdministrator(String actor);
    /** 写事务锁定平台管理员，避免并发撤权后继续修改。 */
    @Select("SELECT BIN_TO_UUID(p.account_id) FROM platform_administrator p JOIN identity_account a ON a.id=p.account_id WHERE p.account_id=UUID_TO_BIN(#{actor}) AND p.enabled=TRUE AND a.enabled=TRUE FOR UPDATE")
    String lockPlatformAdministrator(String actor);
    /** 租户自助入口要求账号、成员、租户均有效且成员具有不可委派的现有 GRANT 管理能力。 */
    @Select("SELECT EXISTS(SELECT 1 FROM tenant_member m JOIN identity_account a ON a.id=m.account_id JOIN platform_tenant t ON t.id=m.tenant_id JOIN organization_capability_grant g ON g.tenant_id=m.tenant_id AND g.member_id=m.id AND g.capability='GRANT' WHERE m.tenant_id=UUID_TO_BIN(#{tenant}) AND m.id=UUID_TO_BIN(#{member}) AND m.account_id=UUID_TO_BIN(#{actor}) AND m.enabled=TRUE AND a.enabled=TRUE AND t.enabled=TRUE AND g.revoked_at IS NULL AND (g.expires_at IS NULL OR g.expires_at>CURRENT_TIMESTAMP(6)))")
    boolean tenantAdministrator(@Param("actor") String actor,@Param("tenant") String tenant,@Param("member") String member);
    /** 写事务锁定成员及其管理授权，和撤权流程串行化。 */
    @Select("SELECT BIN_TO_UUID(m.id) FROM tenant_member m JOIN identity_account a ON a.id=m.account_id JOIN platform_tenant t ON t.id=m.tenant_id JOIN organization_capability_grant g ON g.tenant_id=m.tenant_id AND g.member_id=m.id AND g.capability='GRANT' WHERE m.tenant_id=UUID_TO_BIN(#{tenant}) AND m.id=UUID_TO_BIN(#{member}) AND m.account_id=UUID_TO_BIN(#{actor}) AND m.enabled=TRUE AND a.enabled=TRUE AND t.enabled=TRUE AND g.revoked_at IS NULL AND (g.expires_at IS NULL OR g.expires_at>CURRENT_TIMESTAMP(6)) FOR UPDATE")
    String lockTenantAdministrator(@Param("actor") String actor,@Param("tenant") String tenant,@Param("member") String member);
    /** 平台代管前锁定有效目标租户，禁止为不存在或停用租户修改角色。 */
    @Select("SELECT BIN_TO_UUID(id) FROM platform_tenant WHERE id=UUID_TO_BIN(#{tenant}) AND enabled=TRUE FOR UPDATE")
    String lockTenant(String tenant);

    @Select("SELECT BIN_TO_UUID(id) id,scope_type,BIN_TO_UUID(tenant_id) tenant_id,role_type,system_key,code,name,description,status,sort_order,version,updated_at FROM access_role WHERE scope_owner_id=UUID_TO_BIN(#{owner}) AND deleted_at IS NULL AND (#{query}='' OR code LIKE CONCAT('%',#{query},'%') OR name LIKE CONCAT('%',#{query},'%')) AND (#{status} IS NULL OR status=#{status}) AND (#{roleType} IS NULL OR role_type=#{roleType}) ORDER BY sort_order,created_at DESC,id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Map<String,Object>> list(@Param("owner") String owner,@Param("query") String query,@Param("status") String status,@Param("roleType") String roleType,@Param("limit") int limit,@Param("offset") int offset);
    @Select("SELECT COUNT(*) FROM access_role WHERE scope_owner_id=UUID_TO_BIN(#{owner}) AND deleted_at IS NULL AND (#{query}='' OR code LIKE CONCAT('%',#{query},'%') OR name LIKE CONCAT('%',#{query},'%')) AND (#{status} IS NULL OR status=#{status}) AND (#{roleType} IS NULL OR role_type=#{roleType})")
    long count(@Param("owner") String owner,@Param("query") String query,@Param("status") String status,@Param("roleType") String roleType);
    @Select("SELECT BIN_TO_UUID(id) id,scope_type,BIN_TO_UUID(tenant_id) tenant_id,role_type,system_key,code,name,description,status,sort_order,version,updated_at FROM access_role WHERE id=UUID_TO_BIN(#{id}) AND scope_owner_id=UUID_TO_BIN(#{owner}) AND deleted_at IS NULL")
    Map<String,Object> find(@Param("owner") String owner,@Param("id") String id);
    @Select("SELECT BIN_TO_UUID(id) id,scope_type,BIN_TO_UUID(tenant_id) tenant_id,role_type,system_key,code,name,description,status,sort_order,version,updated_at FROM access_role WHERE id=UUID_TO_BIN(#{id}) AND scope_owner_id=UUID_TO_BIN(#{owner}) AND deleted_at IS NULL FOR UPDATE")
    Map<String,Object> lock(@Param("owner") String owner,@Param("id") String id);
    @Insert("INSERT INTO access_role(id,scope_type,tenant_id,role_type,code,name,description,status,sort_order,created_by,updated_by) VALUES(UUID_TO_BIN(#{id}),#{scope},UUID_TO_BIN(#{tenant}),'CUSTOM',#{code},#{name},#{description},'ENABLED',#{sortOrder},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))")
    void create(@Param("id") String id,@Param("scope") String scope,@Param("tenant") String tenant,@Param("code") String code,@Param("name") String name,@Param("description") String description,@Param("sortOrder") int sortOrder,@Param("actor") String actor);
    @Update("UPDATE access_role SET name=#{name},description=#{description},sort_order=#{sortOrder},updated_by=UUID_TO_BIN(#{actor}),version=version+1 WHERE id=UUID_TO_BIN(#{id}) AND scope_owner_id=UUID_TO_BIN(#{owner}) AND deleted_at IS NULL AND version=#{version}")
    int update(@Param("owner") String owner,@Param("id") String id,@Param("version") long version,@Param("name") String name,@Param("description") String description,@Param("sortOrder") int sortOrder,@Param("actor") String actor);
    @Update("UPDATE access_role SET status=#{status},updated_by=UUID_TO_BIN(#{actor}),version=version+1 WHERE id=UUID_TO_BIN(#{id}) AND scope_owner_id=UUID_TO_BIN(#{owner}) AND deleted_at IS NULL AND version=#{version}")
    int status(@Param("owner") String owner,@Param("id") String id,@Param("version") long version,@Param("status") String status,@Param("actor") String actor);
    @Update("UPDATE access_role SET deleted_at=CURRENT_TIMESTAMP(6),deleted_by=UUID_TO_BIN(#{actor}),delete_reason=#{reason},updated_by=UUID_TO_BIN(#{actor}),version=version+1 WHERE id=UUID_TO_BIN(#{id}) AND scope_owner_id=UUID_TO_BIN(#{owner}) AND deleted_at IS NULL AND version=#{version}")
    int delete(@Param("owner") String owner,@Param("id") String id,@Param("version") long version,@Param("reason") String reason,@Param("actor") String actor);
    /** 审计与角色变更处于同一事务；快照后续扩展时不得包含凭据或会话数据。 */
    @Insert("INSERT INTO access_role_audit(role_id,scope_type,tenant_id,actor_account_id,action,reason) VALUES(UUID_TO_BIN(#{role}),#{scope},UUID_TO_BIN(#{tenant}),UUID_TO_BIN(#{actor}),#{action},#{reason})")
    void audit(@Param("role") String role,@Param("scope") String scope,@Param("tenant") String tenant,@Param("actor") String actor,@Param("action") String action,@Param("reason") String reason);
}
