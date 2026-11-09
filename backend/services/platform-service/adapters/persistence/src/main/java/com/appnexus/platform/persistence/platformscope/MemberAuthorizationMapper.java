package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.persistence.PlatformScoped;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/** 成员授权快照仓储；所有语句明确匹配租户、成员和角色，不接受客户端 SQL。 */
@Mapper
@PlatformScoped(tables={"access_member_authorization","access_member_authorization_permission","access_member_authorization_organization","access_member_role","access_role","access_role_permission","access_role_data_policy","access_role_data_policy_organization"})
public interface MemberAuthorizationMapper {
    /** 仅在新增成员角色关系时复制一次；重复初始化不得扩大已存在授权。 */
    @Insert("INSERT IGNORE INTO access_member_authorization(tenant_id,member_id,role_id,role_version) SELECT mr.tenant_id,mr.member_id,mr.role_id,r.version FROM access_member_role mr JOIN access_role r ON r.id=mr.role_id AND r.tenant_id=mr.tenant_id WHERE mr.tenant_id=UUID_TO_BIN(#{tenant}) AND mr.member_id=UUID_TO_BIN(#{member}) AND mr.role_id=UUID_TO_BIN(#{role})")
    int create(@Param("tenant") String tenant,@Param("member") String member,@Param("role") String role);
    @Insert("INSERT INTO access_member_authorization_permission(tenant_id,member_id,role_id,permission_id,scope_type,resource_type) SELECT UUID_TO_BIN(#{tenant}),UUID_TO_BIN(#{member}),rp.role_id,rp.permission_id,dp.scope_type,dp.resource_type FROM access_role_permission rp LEFT JOIN access_role_data_policy dp ON dp.role_id=rp.role_id AND dp.permission_id=rp.permission_id WHERE rp.role_id=UUID_TO_BIN(#{role})")
    void copyPermissions(@Param("tenant") String tenant,@Param("member") String member,@Param("role") String role);
    @Insert("INSERT INTO access_member_authorization_organization(tenant_id,member_id,role_id,permission_id,organization_id) SELECT tenant_id,UUID_TO_BIN(#{member}),role_id,permission_id,organization_id FROM access_role_data_policy_organization WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND role_id=UUID_TO_BIN(#{role})")
    void copyOrganizations(@Param("tenant") String tenant,@Param("member") String member,@Param("role") String role);
    @Select("SELECT BIN_TO_UUID(g.role_id) role_id,g.role_version,g.expires_at,BIN_TO_UUID(p.permission_id) permission_id,p.scope_type,p.resource_type,BIN_TO_UUID(o.organization_id) organization_id FROM access_member_authorization g LEFT JOIN access_member_authorization_permission p ON p.tenant_id=g.tenant_id AND p.member_id=g.member_id AND p.role_id=g.role_id LEFT JOIN access_member_authorization_organization o ON o.tenant_id=p.tenant_id AND o.member_id=p.member_id AND o.role_id=p.role_id AND o.permission_id=p.permission_id WHERE g.tenant_id=UUID_TO_BIN(#{tenant}) AND g.member_id=UUID_TO_BIN(#{member}) ORDER BY g.role_id,p.permission_id,o.organization_id")
    List<Map<String,Object>> list(@Param("tenant") String tenant,@Param("member") String member);
    @Delete("DELETE FROM access_member_authorization_permission WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND member_id=UUID_TO_BIN(#{member}) AND role_id=UUID_TO_BIN(#{role})")
    void clear(@Param("tenant") String tenant,@Param("member") String member,@Param("role") String role);
    @Insert("INSERT INTO access_member_authorization_permission(tenant_id,member_id,role_id,permission_id,scope_type,resource_type) VALUES(UUID_TO_BIN(#{tenant}),UUID_TO_BIN(#{member}),UUID_TO_BIN(#{role}),UUID_TO_BIN(#{permission}),#{scope},#{resource})")
    void permission(@Param("tenant") String tenant,@Param("member") String member,@Param("role") String role,@Param("permission") String permission,@Param("scope") String scope,@Param("resource") String resource);
    @Insert("INSERT INTO access_member_authorization_organization(tenant_id,member_id,role_id,permission_id,organization_id) VALUES(UUID_TO_BIN(#{tenant}),UUID_TO_BIN(#{member}),UUID_TO_BIN(#{role}),UUID_TO_BIN(#{permission}),UUID_TO_BIN(#{organization}))")
    void organization(@Param("tenant") String tenant,@Param("member") String member,@Param("role") String role,@Param("permission") String permission,@Param("organization") String organization);
    @Update("UPDATE access_member_authorization SET role_version=#{version},expires_at=#{expires} WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND member_id=UUID_TO_BIN(#{member}) AND role_id=UUID_TO_BIN(#{role})")
    void update(@Param("tenant") String tenant,@Param("member") String member,@Param("role") String role,@Param("version") long version,@Param("expires") java.sql.Timestamp expires);
}
