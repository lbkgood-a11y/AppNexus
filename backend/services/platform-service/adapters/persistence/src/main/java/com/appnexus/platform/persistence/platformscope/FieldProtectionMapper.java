package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.persistence.PlatformScoped;
import java.util.*;
import org.apache.ibatis.annotations.*;

/** 所有资源共享的字段限制查询；不包含资源专用 SQL 或原始业务数据。 */
@Mapper
@PlatformScoped(tables={"access_role_field_security","access_role","access_account_role","access_member_role","access_member_authorization","identity_account","tenant_member","platform_tenant"})
public interface FieldProtectionMapper {
    @Select("SELECT f.field_code,f.display_mode FROM access_account_role ar JOIN access_role r ON r.id=ar.role_id JOIN identity_account a ON a.id=ar.account_id JOIN access_role_field_security f ON f.role_id=r.id WHERE ar.account_id=UUID_TO_BIN(#{account}) AND ar.scope_type='PLATFORM' AND r.scope_type='PLATFORM' AND r.status='ENABLED' AND r.deleted_at IS NULL AND a.enabled=TRUE AND f.resource_type=#{resource}")
    List<Map<String,Object>> platform(@Param("account") String account,@Param("resource") String resource);
    @Select("SELECT f.field_code,f.display_mode FROM access_member_role mr JOIN tenant_member m ON m.tenant_id=mr.tenant_id AND m.id=mr.member_id JOIN identity_account a ON a.id=m.account_id JOIN platform_tenant t ON t.id=m.tenant_id JOIN access_role r ON r.id=mr.role_id AND r.tenant_id=mr.tenant_id JOIN access_member_authorization g ON g.tenant_id=mr.tenant_id AND g.member_id=mr.member_id AND g.role_id=mr.role_id JOIN access_role_field_security f ON f.role_id=r.id WHERE mr.tenant_id=UUID_TO_BIN(#{tenant}) AND mr.member_id=UUID_TO_BIN(#{member}) AND m.account_id=UUID_TO_BIN(#{account}) AND m.enabled=TRUE AND a.enabled=TRUE AND t.enabled=TRUE AND r.scope_type='TENANT' AND r.status='ENABLED' AND r.deleted_at IS NULL AND (g.expires_at IS NULL OR g.expires_at>CURRENT_TIMESTAMP(6)) AND f.resource_type=#{resource}")
    List<Map<String,Object>> tenant(@Param("account") String account,@Param("tenant") String tenant,@Param("member") String member,@Param("resource") String resource);
}
