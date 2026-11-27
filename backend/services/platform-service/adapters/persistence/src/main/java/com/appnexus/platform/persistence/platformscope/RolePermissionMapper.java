package com.appnexus.platform.persistence.platformscope;

import com.appnexus.persistence.mybatis.PlatformScoped;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/** 角色权限覆盖写 Mapper；调用服务负责固定加锁顺序、管理域复核和同事务审计。 */
@Mapper
@PlatformScoped(tables={"access_role","access_permission","access_role_permission","access_permission_scope_type","access_role_data_policy","access_role_data_policy_organization","tenant_organization_unit","access_authorization_audit","platform_administrator","identity_account","tenant_member","platform_tenant","access_role_field_security","access_effective_tenant_permission","access_member_authorization"})
public interface RolePermissionMapper {
    /** 影响预览仅统计当前有效且继承的成员；保存仍以角色版本校验，人数不是授权凭据。 */
    @Select("SELECT COUNT(*) FROM access_member_authorization g JOIN tenant_member m ON m.tenant_id=g.tenant_id AND m.id=g.member_id JOIN identity_account a ON a.id=m.account_id JOIN platform_tenant t ON t.id=m.tenant_id WHERE g.role_id=UUID_TO_BIN(#{role}) AND g.inherit_data_scope=TRUE AND (g.expires_at IS NULL OR g.expires_at>CURRENT_TIMESTAMP(6)) AND m.enabled=TRUE AND m.removed_at IS NULL AND a.enabled=TRUE AND t.enabled=TRUE")
    long inheritingMembers(String role);

 /** 字段限制与角色版本使用同一事务，目录和模式由领域用例校验。 */
 @Select("SELECT resource_type,field_code,display_mode FROM access_role_field_security WHERE role_id=UUID_TO_BIN(#{role}) ORDER BY resource_type,field_code")
 List<Map<String,Object>> fields(String role);
 @Delete("DELETE FROM access_role_field_security WHERE role_id=UUID_TO_BIN(#{role})")
 void clearFields(String role);
 /** 为已锁定并授权的角色保存字段限制，记录归属由父角色提供。 */
 @Insert("INSERT INTO access_role_field_security(role_id,resource_type,field_code,display_mode,tenant_id) SELECT id,#{resource},#{field},#{mode},tenant_id FROM access_role WHERE id=UUID_TO_BIN(#{role})")
 void field(@Param("role") String role,@Param("resource") String resource,@Param("field") String field,@Param("mode") String mode);

    @Select("SELECT EXISTS(SELECT 1 FROM platform_administrator p JOIN identity_account a ON a.id=p.account_id WHERE p.account_id=UUID_TO_BIN(#{actor}) AND p.enabled=TRUE AND a.enabled=TRUE)")
    boolean platformAdministrator(String actor);
    @Select("SELECT EXISTS(SELECT 1 FROM access_effective_tenant_permission e WHERE e.tenant_id=UUID_TO_BIN(#{tenant}) AND e.member_id=UUID_TO_BIN(#{member}) AND e.account_id=UUID_TO_BIN(#{actor}) AND e.permission_code='organization.permission:grant')")
    boolean tenantAdministrator(@Param("actor") String actor,@Param("tenant") String tenant,@Param("member") String member);
    @com.appnexus.persistence.mybatis.GuardedScope(tenant="owner",serviceOperation="access.role:grant",serviceKeys={"role"})
    @Select("SELECT BIN_TO_UUID(id) id,scope_type,BIN_TO_UUID(tenant_id) tenant_id,version,CAST(object_scope_defaults AS CHAR) object_scope_defaults FROM access_role WHERE id=UUID_TO_BIN(#{role}) AND scope_owner_id=UUID_TO_BIN(#{owner}) AND deleted_at IS NULL FOR UPDATE")
    Map<String,Object> lockRole(@Param("owner") String owner,@Param("role") String role);
    @Select("SELECT BIN_TO_UUID(permission_id) FROM access_role_permission WHERE role_id=UUID_TO_BIN(#{role}) ORDER BY permission_id")
    List<String> permissions(String role);
    /** 按当前角色域读取失效项详情；未知或跨域标识不泄露其他管理域的名称。 */
    @Select("SELECT code,name_zh,enabled FROM access_permission WHERE id=UUID_TO_BIN(#{id}) AND scope_type=#{scope}")
    Map<String,Object> permissionDetail(@Param("scope") String scope,@Param("id") String id);
    @Select({"<script>","SELECT BIN_TO_UUID(id) FROM access_permission WHERE scope_type=#{scope} AND enabled=TRUE AND id IN","<foreach collection='ids' item='id' open='(' separator=',' close=')'>UUID_TO_BIN(#{id})</foreach>","ORDER BY id FOR UPDATE","</script>"})
    List<String> lockPermissions(@Param("scope") String scope,@Param("ids") List<String> ids);
    @Select({"<script>","SELECT DISTINCT BIN_TO_UUID(permission_id) permission_text FROM access_permission_scope_type WHERE permission_id IN","<foreach collection='ids' item='id' open='(' separator=',' close=')'>UUID_TO_BIN(#{id})</foreach>","ORDER BY permission_text","</script>"})
    List<String> scopedPermissions(@Param("ids") List<String> ids);
    @Select("SELECT BIN_TO_UUID(dp.permission_id) permission_id,dp.scope_type,dp.resource_type,CAST(dp.rules_json AS CHAR) rules_json,BIN_TO_UUID(o.organization_id) organization_id FROM access_role_data_policy dp LEFT JOIN access_role_data_policy_organization o ON o.role_id=dp.role_id AND o.permission_id=dp.permission_id WHERE dp.role_id=UUID_TO_BIN(#{role}) ORDER BY dp.permission_id,o.organization_id")
    List<Map<String,Object>> policies(String role);
    /** 组合策略沿用原策略业务唯一键；调用方与角色版本和审计在同一事务内更新。 */
    @Update("UPDATE access_role_data_policy SET rules_json=CAST(#{rules} AS JSON),updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6) WHERE role_id=UUID_TO_BIN(#{role}) AND permission_id=UUID_TO_BIN(#{permission})")
    void rules(@Param("role") String role,@Param("permission") String permission,@Param("rules") String rules,@Param("actor") String actor);
    /** 授权主体必须是当前租户有效成员，不能使用全局账号标识。 */
    @Select({"<script>","SELECT COUNT(*) FROM tenant_member WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND enabled=TRUE AND removed_at IS NULL AND id IN", "<foreach collection='ids' item='id' open='(' separator=',' close=')'>UUID_TO_BIN(#{id})</foreach>","</script>"})
    int validMembers(@Param("tenant") String tenant,@Param("ids") List<String> ids);
    @Select("SELECT EXISTS(SELECT 1 FROM access_permission_scope_type WHERE permission_id=UUID_TO_BIN(#{permission}) AND scope_type=#{scopeType} AND resource_type=#{resource} AND scope_type IN ('ALL','RULES'))")
    boolean supportsPolicy(@Param("permission") String permission,@Param("scopeType") String scopeType,@Param("resource") String resource);
    @Delete("DELETE FROM access_role_permission WHERE role_id=UUID_TO_BIN(#{role})")
    int deletePermissions(String role);
    /** 批量保存已按管理域校验的权限，关系行继承当前事务锁定角色的租户。 */
    @Insert({"<script>","INSERT INTO access_role_permission(role_id,permission_id,scope_type,granted_by,tenant_id) VALUES","<foreach collection='ids' item='id' separator=','>(UUID_TO_BIN(#{role}),UUID_TO_BIN(#{id}),#{scope},UUID_TO_BIN(#{actor}),(SELECT tenant_id FROM access_role WHERE id=UUID_TO_BIN(#{role})))</foreach>","</script>"})
    int insertPermissions(@Param("role") String role,@Param("scope") String scope,@Param("actor") String actor,@Param("ids") List<String> ids);
    /** 保存已通过资源目录校验的策略，外键复核策略行与父角色的归属一致。 */
    @Insert("INSERT INTO access_role_data_policy(role_id,permission_id,scope_type,resource_type,configured_by,tenant_id) SELECT id,UUID_TO_BIN(#{permission}),#{scopeType},#{resource},UUID_TO_BIN(#{actor}),tenant_id FROM access_role WHERE id=UUID_TO_BIN(#{role})")
    void insertPolicy(@Param("role") String role,@Param("permission") String permission,@Param("scopeType") String scopeType,@Param("resource") String resource,@Param("actor") String actor);
    /** 角色锁已持有；默认范围与权限、审计和版本同事务更新。 */
    @com.appnexus.persistence.mybatis.GuardedScope(tenant="owner",serviceOperation="access.role:grant",serviceKeys={"role"})
    @Update("UPDATE access_role SET object_scope_defaults=CAST(#{defaults} AS JSON) WHERE id=UUID_TO_BIN(#{role}) AND scope_owner_id=UUID_TO_BIN(#{owner}) AND deleted_at IS NULL")
    void objectScopes(@Param("owner") String owner,@Param("role") String role,@Param("defaults") String defaults);
    @Update("UPDATE access_role SET version=version+1,updated_by=UUID_TO_BIN(#{actor}) WHERE id=UUID_TO_BIN(#{role}) AND version=#{version}")
    int bumpVersion(@Param("role") String role,@Param("version") long version,@Param("actor") String actor);
    @Insert("INSERT INTO access_authorization_audit(scope_type,tenant_id,actor_account_id,action,target_type,target_id,reason,before_snapshot,after_snapshot) VALUES(#{scope},UUID_TO_BIN(#{tenant}),UUID_TO_BIN(#{actor}),'ROLE_PERMISSIONS_REPLACED','ROLE',UUID_TO_BIN(#{role}),#{reason},CAST(#{before} AS JSON),CAST(#{after} AS JSON))")
    void audit(@Param("scope") String scope,@Param("tenant") String tenant,@Param("actor") String actor,
               @Param("role") String role,@Param("reason") String reason,@Param("before") String before,@Param("after") String after);
}
