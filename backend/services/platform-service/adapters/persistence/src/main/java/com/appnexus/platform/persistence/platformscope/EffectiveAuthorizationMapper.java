package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.persistence.PlatformScoped;
import java.util.*;
import org.apache.ibatis.annotations.*;

/** 有效权限解释 Mapper；返回原始角色分支，由服务层按权限码聚合而不参与实际放行。 */
@Mapper
@PlatformScoped(tables={"access_account_role","access_member_role","access_role","access_role_permission","access_permission","access_permission_scope_type","access_role_data_policy","access_role_data_policy_organization","access_member_authorization","access_member_authorization_permission","access_member_authorization_organization","identity_account","tenant_member","platform_tenant"})
public interface EffectiveAuthorizationMapper {
 @Select("SELECT p.code,BIN_TO_UUID(r.id) role_id,r.name role_name,r.status role_status,r.deleted_at,p.enabled permission_enabled,EXISTS(SELECT 1 FROM access_permission_scope_type st WHERE st.permission_id=p.id) requires_scope,dp.scope_type,BIN_TO_UUID(o.organization_id) organization_id FROM access_account_role ar JOIN access_role r ON r.id=ar.role_id JOIN access_role_permission rp ON rp.role_id=r.id JOIN access_permission p ON p.id=rp.permission_id LEFT JOIN access_role_data_policy dp ON dp.role_id=r.id AND dp.permission_id=p.id LEFT JOIN access_role_data_policy_organization o ON o.role_id=dp.role_id AND o.permission_id=dp.permission_id WHERE ar.account_id=UUID_TO_BIN(#{account}) ORDER BY p.code,r.id,o.organization_id")
 List<Map<String,Object>> platform(String account);
 @Select("SELECT p.code,BIN_TO_UUID(r.id) role_id,r.name role_name,r.status role_status,r.deleted_at,p.enabled permission_enabled,EXISTS(SELECT 1 FROM access_permission_scope_type st WHERE st.permission_id=p.id) requires_scope,ap.scope_type,ap.resource_type,BIN_TO_UUID(o.organization_id) organization_id,(g.expires_at IS NOT NULL AND g.expires_at<=CURRENT_TIMESTAMP(6)) expired,(rp.permission_id IS NOT NULL) current_permission,(m.enabled AND a.enabled AND t.enabled) subject_enabled FROM access_member_role mr JOIN tenant_member m ON m.tenant_id=mr.tenant_id AND m.id=mr.member_id JOIN identity_account a ON a.id=m.account_id JOIN platform_tenant t ON t.id=m.tenant_id JOIN access_role r ON r.id=mr.role_id AND r.tenant_id=mr.tenant_id JOIN access_member_authorization g ON g.tenant_id=mr.tenant_id AND g.member_id=mr.member_id AND g.role_id=mr.role_id JOIN access_member_authorization_permission ap ON ap.tenant_id=g.tenant_id AND ap.member_id=g.member_id AND ap.role_id=g.role_id JOIN access_permission p ON p.id=ap.permission_id LEFT JOIN access_role_permission rp ON rp.role_id=r.id AND rp.permission_id=ap.permission_id LEFT JOIN access_member_authorization_organization o ON o.tenant_id=ap.tenant_id AND o.member_id=ap.member_id AND o.role_id=ap.role_id AND o.permission_id=ap.permission_id WHERE mr.tenant_id=UUID_TO_BIN(#{tenant}) AND mr.member_id=UUID_TO_BIN(#{member}) ORDER BY p.code,r.id,o.organization_id")
 List<Map<String,Object>> tenant(@Param("tenant") String tenant,@Param("member") String member);
}
