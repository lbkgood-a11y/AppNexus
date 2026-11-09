package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.persistence.PlatformScoped;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 权限目录持久化 Mapper；只读取受控迁移发布的定义，不提供运行时任意写入口。 */
@Mapper
@PlatformScoped(tables={"access_permission","access_permission_scope_type","access_role","access_role_permission","access_account_role","access_member_role","access_member_permission","identity_account","platform_tenant","tenant_member","access_member_authorization","access_member_authorization_permission"})
public interface PermissionMapper {
    /** 按管理域读取权限目录；是否包含停用项由管理用例显式决定。 */
    @Select("SELECT BIN_TO_UUID(id) id,scope_type,code,resource,action,name_zh,name_en,description,enabled,system_managed,version FROM access_permission WHERE scope_type=#{scope} AND (enabled=TRUE OR #{includeDisabled}=TRUE) ORDER BY resource,action,code")
    List<Map<String,Object>> list(@Param("scope") String scope, @Param("includeDisabled") boolean includeDisabled);

    /** 只判断同管理域内启用权限是否存在；未知权限必须失败关闭。 */
    @Select("SELECT EXISTS(SELECT 1 FROM access_permission WHERE scope_type=#{scope} AND code=#{code} AND enabled=TRUE)")
    boolean enabled(@Param("scope") String scope, @Param("code") String code);

    /** 平台权限要求账号、角色、权限及全部关系同时有效；软删除角色不能继续授权。 */
    @Select("SELECT EXISTS(SELECT 1 FROM access_account_role ar JOIN identity_account a ON a.id=ar.account_id JOIN access_role r ON r.id=ar.role_id JOIN access_role_permission rp ON rp.role_id=r.id JOIN access_permission p ON p.id=rp.permission_id WHERE ar.account_id=UUID_TO_BIN(#{account}) AND ar.scope_type='PLATFORM' AND a.enabled=TRUE AND r.scope_type='PLATFORM' AND r.status='ENABLED' AND r.deleted_at IS NULL AND p.scope_type='PLATFORM' AND p.code=#{code} AND p.enabled=TRUE)")
    boolean hasPlatformPermission(@Param("account") String account, @Param("code") String code);

    /** 租户权限逐次复核账号、租户、成员、角色及权限状态，并在所有连接上固定同一 tenant_id。 */
    @Select("SELECT EXISTS(SELECT 1 FROM access_member_role mr JOIN tenant_member m ON m.id=mr.member_id AND m.tenant_id=mr.tenant_id JOIN identity_account a ON a.id=m.account_id JOIN platform_tenant t ON t.id=mr.tenant_id JOIN access_role r ON r.id=mr.role_id AND r.tenant_id=mr.tenant_id JOIN access_role_permission rp ON rp.role_id=r.id JOIN access_permission p ON p.id=rp.permission_id JOIN access_member_authorization g ON g.tenant_id=mr.tenant_id AND g.member_id=mr.member_id AND g.role_id=mr.role_id JOIN access_member_authorization_permission ap ON ap.tenant_id=g.tenant_id AND ap.member_id=g.member_id AND ap.role_id=g.role_id AND ap.permission_id=p.id WHERE (g.expires_at IS NULL OR g.expires_at>CURRENT_TIMESTAMP(6)) AND mr.tenant_id=UUID_TO_BIN(#{tenant}) AND mr.member_id=UUID_TO_BIN(#{member}) AND m.account_id=UUID_TO_BIN(#{account}) AND m.enabled=TRUE AND a.enabled=TRUE AND t.enabled=TRUE AND r.scope_type='TENANT' AND r.status='ENABLED' AND r.deleted_at IS NULL AND p.scope_type='TENANT' AND p.code=#{code} AND p.enabled=TRUE)")
    boolean hasTenantPermission(@Param("account") String account, @Param("tenant") String tenant,
                                @Param("member") String member, @Param("code") String code);
    /** 只返回启用权限已登记的数据策略能力，避免管理端展示后端无法执行的范围类型。 */
    @Select("SELECT BIN_TO_UUID(p.id) permission_id,p.code,s.scope_type,s.resource_type FROM access_permission p JOIN access_permission_scope_type s ON s.permission_id=p.id WHERE p.scope_type=#{scope} AND p.enabled=TRUE ORDER BY p.code,s.scope_type")
    List<Map<String,Object>> dataPolicyMetadata(String scope);
}
