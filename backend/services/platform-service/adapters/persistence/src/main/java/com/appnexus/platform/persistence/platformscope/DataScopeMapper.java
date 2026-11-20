package com.appnexus.platform.persistence.platformscope;
import com.appnexus.platform.persistence.PlatformScoped;
import java.util.*;
import org.apache.ibatis.annotations.*;
/** 数据范围解析 Mapper；只返回已通过有效主体、角色和权限连接验证的结构化策略。 */
@Mapper
@PlatformScoped(tables={"identity_account","platform_tenant","tenant_member","organization_capability_grant","access_member_role","access_member_permission","access_role","access_role_permission","access_permission","access_role_data_policy","access_role_data_policy_organization","tenant_organization_unit","access_member_authorization","access_member_authorization_permission","access_member_authorization_organization"})
public interface DataScopeMapper {
 /** 归属候选只读取可信租户启用组织，不借用组织管理权限或返回成员资料。 */
 @Select("SELECT BIN_TO_UUID(id) id,name FROM tenant_organization_unit WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND enabled=TRUE ORDER BY name,id")
 List<Map<String,Object>> organizationOptions(String tenant);

 /** 递归只沿当前租户父子关系展开；UNION DISTINCT 消除多根重叠和历史环，避免重复及无界递归。 */
 @Select({"<script>","WITH RECURSIVE allowed(id) AS (SELECT id FROM tenant_organization_unit WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id IN","<foreach collection='roots' item='root' open='(' separator=',' close=')'>UUID_TO_BIN(#{root})</foreach>","UNION DISTINCT SELECT child.id FROM tenant_organization_unit child JOIN allowed parent ON child.parent_id=parent.id WHERE child.tenant_id=UUID_TO_BIN(#{tenant})) SELECT BIN_TO_UUID(id) FROM allowed","</script>"})
 List<String> descendants(@Param("tenant") String tenant,@Param("roots") List<String> roots);
 /** 继承读取同角色当前策略，自定义只读成员策略；不能使用 COALESCE 回退，否则缺失角色策略会恢复旧范围。 */
 @Select("SELECT CASE WHEN g.inherit_data_scope THEN rd.scope_type ELSE dp.scope_type END scope_type,CAST(CASE WHEN g.inherit_data_scope THEN rd.rules_json ELSE dp.rules_json END AS CHAR) rules_json,CASE WHEN g.inherit_data_scope THEN BIN_TO_UUID(ro.organization_id) ELSE BIN_TO_UUID(o.organization_id) END organization_id FROM tenant_member m JOIN identity_account a ON a.id=m.account_id JOIN platform_tenant t ON t.id=m.tenant_id JOIN access_member_role mr ON mr.tenant_id=m.tenant_id AND mr.member_id=m.id JOIN access_role r ON r.id=mr.role_id AND r.tenant_id=m.tenant_id JOIN access_role_permission rp ON rp.role_id=r.id JOIN access_permission p ON p.id=rp.permission_id JOIN access_member_authorization g ON g.tenant_id=mr.tenant_id AND g.member_id=mr.member_id AND g.role_id=mr.role_id LEFT JOIN access_member_authorization_permission dp ON dp.tenant_id=g.tenant_id AND dp.member_id=g.member_id AND dp.role_id=g.role_id AND dp.permission_id=p.id LEFT JOIN access_member_authorization_organization o ON o.tenant_id=dp.tenant_id AND o.member_id=dp.member_id AND o.role_id=dp.role_id AND o.permission_id=dp.permission_id LEFT JOIN access_role_data_policy rd ON rd.role_id=r.id AND rd.permission_id=p.id AND g.inherit_data_scope=TRUE LEFT JOIN access_role_data_policy_organization ro ON ro.role_id=rd.role_id AND ro.permission_id=rd.permission_id WHERE (g.expires_at IS NULL OR g.expires_at>CURRENT_TIMESTAMP(6)) AND m.tenant_id=UUID_TO_BIN(#{tenant}) AND m.id=UUID_TO_BIN(#{member}) AND m.account_id=UUID_TO_BIN(#{account}) AND m.enabled=TRUE AND m.removed_at IS NULL AND a.enabled=TRUE AND t.enabled=TRUE AND r.status='ENABLED' AND r.deleted_at IS NULL AND p.enabled=TRUE AND p.code=#{code} AND p.scope_type='TENANT' AND r.scope_type='TENANT' AND (CASE WHEN g.inherit_data_scope THEN rd.resource_type ELSE dp.resource_type END)=#{resource} ORDER BY scope_type,organization_id")
 List<Map<String,Object>> policies(@Param("account") String account,@Param("tenant") String tenant,@Param("member") String member,@Param("code") String code,@Param("resource") String resource);
}
