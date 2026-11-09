package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.persistence.PlatformScoped;
import java.util.*;
import org.apache.ibatis.annotations.*;

/** 有效权限解释 Mapper；返回原始角色分支，由服务层按权限码聚合而不参与实际放行。 */
@Mapper
@PlatformScoped(tables={"access_account_role","access_member_role","access_role","access_role_permission","access_permission","access_permission_scope_type","access_role_data_policy","access_role_data_policy_organization"})
public interface EffectiveAuthorizationMapper {
 @Select("SELECT p.code,BIN_TO_UUID(r.id) role_id,r.name role_name,r.status role_status,r.deleted_at,p.enabled permission_enabled,EXISTS(SELECT 1 FROM access_permission_scope_type st WHERE st.permission_id=p.id) requires_scope,dp.scope_type,BIN_TO_UUID(o.organization_id) organization_id FROM access_account_role ar JOIN access_role r ON r.id=ar.role_id JOIN access_role_permission rp ON rp.role_id=r.id JOIN access_permission p ON p.id=rp.permission_id LEFT JOIN access_role_data_policy dp ON dp.role_id=r.id AND dp.permission_id=p.id LEFT JOIN access_role_data_policy_organization o ON o.role_id=dp.role_id AND o.permission_id=dp.permission_id WHERE ar.account_id=UUID_TO_BIN(#{account}) ORDER BY p.code,r.id,o.organization_id")
 List<Map<String,Object>> platform(String account);
 @Select("SELECT p.code,BIN_TO_UUID(r.id) role_id,r.name role_name,r.status role_status,r.deleted_at,p.enabled permission_enabled,EXISTS(SELECT 1 FROM access_permission_scope_type st WHERE st.permission_id=p.id) requires_scope,dp.scope_type,BIN_TO_UUID(o.organization_id) organization_id FROM access_member_role mr JOIN access_role r ON r.id=mr.role_id AND r.tenant_id=mr.tenant_id JOIN access_role_permission rp ON rp.role_id=r.id JOIN access_permission p ON p.id=rp.permission_id LEFT JOIN access_role_data_policy dp ON dp.role_id=r.id AND dp.permission_id=p.id LEFT JOIN access_role_data_policy_organization o ON o.role_id=dp.role_id AND o.permission_id=dp.permission_id AND o.tenant_id=mr.tenant_id WHERE mr.tenant_id=UUID_TO_BIN(#{tenant}) AND mr.member_id=UUID_TO_BIN(#{member}) ORDER BY p.code,r.id,o.organization_id")
 List<Map<String,Object>> tenant(@Param("tenant") String tenant,@Param("member") String member);
}
