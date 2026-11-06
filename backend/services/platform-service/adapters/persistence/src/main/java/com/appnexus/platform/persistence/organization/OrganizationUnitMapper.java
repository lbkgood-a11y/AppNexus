package com.appnexus.platform.persistence.organization;
import java.util.*;
import org.apache.ibatis.annotations.*;
import com.appnexus.platform.persistence.TenantScoped;
/** 单表组织单元 SQL；固定语句形状由 TenantSqlInterceptor 再次校验。 */
@TenantScoped(table="tenant_organization_unit")
@Mapper
public interface OrganizationUnitMapper {
    /** 只选择当前租户的行，不接受租户标识参数。 */
    @Select("SELECT BIN_TO_UUID(id) AS id, name FROM tenant_organization_unit WHERE tenant_id = #{__verifiedTenant} ORDER BY name")
    List<Map<String,Object>> findAll(@Param("__verifiedTenant") byte[] tenant);
    /** 复合主键把租户作为第一列，ID 由应用生成。 */
    @Insert("INSERT INTO tenant_organization_unit (tenant_id, id, name) VALUES (#{__verifiedTenant}, UUID_TO_BIN(#{id}), #{name})")
    int create(@Param("__verifiedTenant") byte[] tenant, @Param("id") String id, @Param("name") String name);
    @Update("UPDATE tenant_organization_unit SET name = #{name} WHERE tenant_id = #{__verifiedTenant} AND id = UUID_TO_BIN(#{id})")
    int rename(@Param("name") String name, @Param("__verifiedTenant") byte[] tenant, @Param("id") String id);
    @Delete("DELETE FROM tenant_organization_unit WHERE tenant_id = #{__verifiedTenant} AND id = UUID_TO_BIN(#{id})")
    int delete(@Param("__verifiedTenant") byte[] tenant, @Param("id") String id);
}
