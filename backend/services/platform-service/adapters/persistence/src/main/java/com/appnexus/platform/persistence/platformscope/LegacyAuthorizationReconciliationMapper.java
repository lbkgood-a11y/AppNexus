package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.persistence.PlatformScoped;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/** 旧授权对账查询；只读有效旧授权，不提供迁移、删除或授权写入口。 */
@Mapper
@PlatformScoped(tables={"platform_administrator","identity_account","platform_tenant","tenant_member","access_member_permission","access_member_role","access_role","access_role_permission","access_permission","access_member_authorization","access_member_authorization_permission"})
public interface LegacyAuthorizationReconciliationMapper {
    @Select("SELECT EXISTS(SELECT 1 FROM platform_administrator p JOIN identity_account a ON a.id=p.account_id WHERE p.account_id=UUID_TO_BIN(#{actor}) AND p.enabled=TRUE AND a.enabled=TRUE)")
    boolean administrator(String actor);

    /** 统一分支必须满足运行时相同的主体、角色、快照、权限和有效期约束。 */
    @Select("SELECT BIN_TO_UUID(mp.tenant_id) tenant_id,BIN_TO_UUID(mp.member_id) member_id,mp.permission_code,mp.expires_at,EXISTS(SELECT 1 FROM access_member_role mr JOIN access_role r ON r.id=mr.role_id AND r.tenant_id=mr.tenant_id JOIN access_role_permission rp ON rp.role_id=r.id JOIN access_permission p ON p.id=rp.permission_id JOIN access_member_authorization g ON g.tenant_id=mr.tenant_id AND g.member_id=mr.member_id AND g.role_id=mr.role_id JOIN access_member_authorization_permission ap ON ap.tenant_id=g.tenant_id AND ap.member_id=g.member_id AND ap.role_id=g.role_id AND ap.permission_id=p.id WHERE mr.tenant_id=mp.tenant_id AND mr.member_id=mp.member_id AND r.status='ENABLED' AND r.deleted_at IS NULL AND p.enabled=TRUE AND p.code=mp.permission_code AND (g.expires_at IS NULL OR g.expires_at>CURRENT_TIMESTAMP(6))) covered FROM access_member_permission mp JOIN tenant_member m ON m.tenant_id=mp.tenant_id AND m.id=mp.member_id JOIN identity_account a ON a.id=m.account_id JOIN platform_tenant t ON t.id=m.tenant_id WHERE m.enabled=TRUE AND m.removed_at IS NULL AND a.enabled=TRUE AND t.enabled=TRUE AND (mp.expires_at IS NULL OR mp.expires_at>CURRENT_TIMESTAMP(6)) ORDER BY mp.tenant_id,mp.member_id,mp.permission_code LIMIT #{size} OFFSET #{offset}")
    List<Map<String,Object>> rows(@Param("size") int size,@Param("offset") long offset);

    @Select("SELECT COUNT(*) FROM access_member_permission mp JOIN tenant_member m ON m.tenant_id=mp.tenant_id AND m.id=mp.member_id JOIN identity_account a ON a.id=m.account_id JOIN platform_tenant t ON t.id=m.tenant_id WHERE m.enabled=TRUE AND m.removed_at IS NULL AND a.enabled=TRUE AND t.enabled=TRUE AND (mp.expires_at IS NULL OR mp.expires_at>CURRENT_TIMESTAMP(6))")
    long total();

    @Select("SELECT COUNT(*) FROM access_member_permission mp JOIN tenant_member m ON m.tenant_id=mp.tenant_id AND m.id=mp.member_id JOIN identity_account a ON a.id=m.account_id JOIN platform_tenant t ON t.id=m.tenant_id WHERE m.enabled=TRUE AND m.removed_at IS NULL AND a.enabled=TRUE AND t.enabled=TRUE AND (mp.expires_at IS NULL OR mp.expires_at>CURRENT_TIMESTAMP(6)) AND NOT EXISTS(SELECT 1 FROM access_member_role mr JOIN access_role r ON r.id=mr.role_id AND r.tenant_id=mr.tenant_id JOIN access_role_permission rp ON rp.role_id=r.id JOIN access_permission p ON p.id=rp.permission_id JOIN access_member_authorization g ON g.tenant_id=mr.tenant_id AND g.member_id=mr.member_id AND g.role_id=mr.role_id JOIN access_member_authorization_permission ap ON ap.tenant_id=g.tenant_id AND ap.member_id=g.member_id AND ap.role_id=g.role_id AND ap.permission_id=p.id WHERE mr.tenant_id=mp.tenant_id AND mr.member_id=mp.member_id AND r.status='ENABLED' AND r.deleted_at IS NULL AND p.enabled=TRUE AND p.code=mp.permission_code AND (g.expires_at IS NULL OR g.expires_at>CURRENT_TIMESTAMP(6)))")
    long uncovered();
}
