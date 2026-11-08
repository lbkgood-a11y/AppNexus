package com.appnexus.platform.persistence.organization;
import java.util.*;
import org.apache.ibatis.annotations.*;
import com.appnexus.platform.persistence.TenantAggregateScoped;
/** 单表组织单元 SQL；固定语句形状由 TenantSqlInterceptor 再次校验。 */
@TenantAggregateScoped(tables={"tenant_organization_unit","tenant_organization_type"},statements={"findAll","create","rename","delete"})
@Mapper
public interface OrganizationUnitMapper {
    /** 只选择当前租户的行，不接受租户标识参数。 */
    @Select("SELECT BIN_TO_UUID(id) AS id, name FROM tenant_organization_unit WHERE tenant_id = #{__verifiedTenant} ORDER BY name")
    List<Map<String,Object>> findAll(@Param("__verifiedTenant") byte[] tenant);
    /** 复合主键把租户作为第一列，ID 由应用生成。 */
    @Insert("INSERT INTO tenant_organization_unit (tenant_id,id,code,type_id,name) SELECT #{__verifiedTenant},UUID_TO_BIN(#{id}),CONCAT('ORG-',LEFT(REPLACE(#{id},'-',''),12)),id,#{name} FROM tenant_organization_type WHERE tenant_id=#{__verifiedTenant} AND code='BUSINESS' AND enabled=TRUE")
    int create(@Param("__verifiedTenant") byte[] tenant, @Param("id") String id, @Param("name") String name);
    @Update("UPDATE tenant_organization_unit SET name = #{name} WHERE tenant_id = #{__verifiedTenant} AND id = UUID_TO_BIN(#{id})")
    int rename(@Param("name") String name, @Param("__verifiedTenant") byte[] tenant, @Param("id") String id);
    @Delete("DELETE FROM tenant_organization_unit WHERE tenant_id = #{__verifiedTenant} AND id = UUID_TO_BIN(#{id})")
    int delete(@Param("__verifiedTenant") byte[] tenant, @Param("id") String id);
}
