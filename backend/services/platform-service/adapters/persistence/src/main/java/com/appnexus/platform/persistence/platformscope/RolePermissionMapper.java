package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.persistence.PlatformScoped;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/** 角色权限覆盖写 Mapper；调用服务负责固定加锁顺序、管理域复核和同事务审计。 */
@Mapper
@PlatformScoped(tables={"access_role","access_permission","access_role_permission","access_permission_scope_type","access_role_data_policy","access_role_data_policy_organization","tenant_organization_unit","access_authorization_audit","platform_administrator","identity_account","tenant_member","organization_capability_grant","platform_tenant"})
public interface RolePermissionMapper {
    @Select("SELECT EXISTS(SELECT 1 FROM platform_administrator p JOIN identity_account a ON a.id=p.account_id WHERE p.account_id=UUID_TO_BIN(#{actor}) AND p.enabled=TRUE AND a.enabled=TRUE)")
    boolean platformAdministrator(String actor);
    @Select("SELECT EXISTS(SELECT 1 FROM tenant_member m JOIN identity_account a ON a.id=m.account_id JOIN platform_tenant t ON t.id=m.tenant_id JOIN organization_capability_grant g ON g.tenant_id=m.tenant_id AND g.member_id=m.id AND g.capability='GRANT' WHERE m.tenant_id=UUID_TO_BIN(#{tenant}) AND m.id=UUID_TO_BIN(#{member}) AND m.account_id=UUID_TO_BIN(#{actor}) AND m.enabled=TRUE AND a.enabled=TRUE AND t.enabled=TRUE AND g.revoked_at IS NULL AND (g.expires_at IS NULL OR g.expires_at>CURRENT_TIMESTAMP(6)))")
    boolean tenantAdministrator(@Param("actor") String actor,@Param("tenant") String tenant,@Param("member") String member);
    @Select("SELECT BIN_TO_UUID(id) id,scope_type,BIN_TO_UUID(tenant_id) tenant_id,version FROM access_role WHERE id=UUID_TO_BIN(#{role}) AND scope_owner_id=UUID_TO_BIN(#{owner}) AND deleted_at IS NULL FOR UPDATE")
    Map<String,Object> lockRole(@Param("owner") String owner,@Param("role") String role);
    @Select("SELECT BIN_TO_UUID(permission_id) FROM access_role_permission WHERE role_id=UUID_TO_BIN(#{role}) ORDER BY permission_id")
    List<String> permissions(String role);
    @Select({"<script>","SELECT BIN_TO_UUID(id) FROM access_permission WHERE scope_type=#{scope} AND enabled=TRUE AND id IN","<foreach collection='ids' item='id' open='(' separator=',' close=')'>UUID_TO_BIN(#{id})</foreach>","ORDER BY id FOR UPDATE","</script>"})
    List<String> lockPermissions(@Param("scope") String scope,@Param("ids") List<String> ids);
    @Select({"<script>","SELECT DISTINCT BIN_TO_UUID(permission_id) permission_text FROM access_permission_scope_type WHERE permission_id IN","<foreach collection='ids' item='id' open='(' separator=',' close=')'>UUID_TO_BIN(#{id})</foreach>","ORDER BY permission_text","</script>"})
    List<String> scopedPermissions(@Param("ids") List<String> ids);
    @Select("SELECT BIN_TO_UUID(dp.permission_id) permission_id,dp.scope_type,dp.resource_type,BIN_TO_UUID(o.organization_id) organization_id FROM access_role_data_policy dp LEFT JOIN access_role_data_policy_organization o ON o.role_id=dp.role_id AND o.permission_id=dp.permission_id WHERE dp.role_id=UUID_TO_BIN(#{role}) ORDER BY dp.permission_id,o.organization_id")
    List<Map<String,Object>> policies(String role);
    @Select("SELECT EXISTS(SELECT 1 FROM access_permission_scope_type WHERE permission_id=UUID_TO_BIN(#{permission}) AND scope_type=#{scopeType} AND resource_type=#{resource})")
    boolean supportsPolicy(@Param("permission") String permission,@Param("scopeType") String scopeType,@Param("resource") String resource);
    @Select({"<script>","SELECT COUNT(*) FROM tenant_organization_unit WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND enabled=TRUE AND id IN","<foreach collection='ids' item='id' open='(' separator=',' close=')'>UUID_TO_BIN(#{id})</foreach>","FOR UPDATE","</script>"})
    int validOrganizations(@Param("tenant") String tenant,@Param("ids") List<String> ids);
    @Delete("DELETE FROM access_role_permission WHERE role_id=UUID_TO_BIN(#{role})")
    int deletePermissions(String role);
    @Insert({"<script>","INSERT INTO access_role_permission(role_id,permission_id,scope_type,granted_by) VALUES","<foreach collection='ids' item='id' separator=','>(UUID_TO_BIN(#{role}),UUID_TO_BIN(#{id}),#{scope},UUID_TO_BIN(#{actor}))</foreach>","</script>"})
    int insertPermissions(@Param("role") String role,@Param("scope") String scope,@Param("actor") String actor,@Param("ids") List<String> ids);
    @Insert("INSERT INTO access_role_data_policy(role_id,permission_id,scope_type,resource_type,configured_by) VALUES(UUID_TO_BIN(#{role}),UUID_TO_BIN(#{permission}),#{scopeType},#{resource},UUID_TO_BIN(#{actor}))")
    void insertPolicy(@Param("role") String role,@Param("permission") String permission,@Param("scopeType") String scopeType,@Param("resource") String resource,@Param("actor") String actor);
    @Insert({"<script>","INSERT INTO access_role_data_policy_organization(role_id,permission_id,tenant_id,organization_id) VALUES","<foreach collection='ids' item='id' separator=','>(UUID_TO_BIN(#{role}),UUID_TO_BIN(#{permission}),UUID_TO_BIN(#{tenant}),UUID_TO_BIN(#{id}))</foreach>","</script>"})
    int insertPolicyOrganizations(@Param("role") String role,@Param("permission") String permission,@Param("tenant") String tenant,@Param("ids") List<String> ids);
    @Update("UPDATE access_role SET version=version+1,updated_by=UUID_TO_BIN(#{actor}) WHERE id=UUID_TO_BIN(#{role}) AND version=#{version}")
    int bumpVersion(@Param("role") String role,@Param("version") long version,@Param("actor") String actor);
    @Insert("INSERT INTO access_authorization_audit(scope_type,tenant_id,actor_account_id,action,target_type,target_id,reason,before_snapshot,after_snapshot) VALUES(#{scope},UUID_TO_BIN(#{tenant}),UUID_TO_BIN(#{actor}),'ROLE_PERMISSIONS_REPLACED','ROLE',UUID_TO_BIN(#{role}),#{reason},CAST(#{before} AS JSON),CAST(#{after} AS JSON))")
    void audit(@Param("scope") String scope,@Param("tenant") String tenant,@Param("actor") String actor,
               @Param("role") String role,@Param("reason") String reason,@Param("before") String before,@Param("after") String after);
}
