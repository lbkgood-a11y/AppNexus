package com.appnexus.platform.persistence.platformscope;

import com.appnexus.persistence.mybatis.PlatformScoped;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/** 代码数据权限目录的受控启动投影；只登记能力，不授予角色或成员权限。 */
@Mapper
@PlatformScoped(tables={"access_permission","access_permission_scope_type","access_role_field_security"})
public interface DataAccessRegistrationMapper {
    /** @param code 唯一操作权限码 @param scope TENANT 或 PLATFORM 管理域 @return 当前启用且属于该域的权限 UUID */
    @Select("SELECT BIN_TO_UUID(id) FROM access_permission WHERE code=#{code} AND scope_type=#{scope} AND enabled=TRUE")
    List<String> permissionIds(@Param("code") String code,@Param("scope") String scope);
    /** @param permission 已验证的权限 UUID @param type 声明支持的数据范围类型 @param resource 所属主资源稳定键 @return 新增能力投影行数；不授予任何角色 */
    @Insert("INSERT IGNORE INTO access_permission_scope_type(permission_id,scope_type,resource_type) VALUES(UUID_TO_BIN(#{permission}),#{type},#{resource})")
    int registerScope(@Param("permission") String permission,@Param("type") String type,@Param("resource") String resource);
    /** @param resource 唯一声明的资源键 @return 已被策略引用的字段及模式，用于阻止不兼容声明静默删除 */
    @Select("SELECT DISTINCT field_code,display_mode FROM access_role_field_security WHERE resource_type=#{resource}")
    List<Map<String,Object>> fieldPolicies(@Param("resource") String resource);
}
