package com.appnexus.platform.persistence.organization;
import java.util.*;
import org.apache.ibatis.annotations.*;
import com.appnexus.persistence.mybatis.TenantAggregateScoped;
import com.appnexus.persistence.mybatis.GuardedScope;
import com.appnexus.persistence.mybatis.ScopedSql;
import com.appnexus.platform.authorization.RowAccessControl;
/** 单表组织单元 SQL；固定语句形状由 TenantSqlInterceptor 再次校验。 */
@TenantAggregateScoped(tables={"tenant_organization_unit","tenant_organization_type"},statements={"findScoped","create","rename","renameExact","delete","deleteExact"})
@Mapper
public interface OrganizationUnitMapper {
    /** @param tenant 已验证租户 UUID @param access 已解析的组织范围 @return 同租户授权组织摘要 */
    @GuardedScope(predicate="access",resource="organization.unit",tenant="__verifiedTenant",actions="read")
    @SelectProvider(type=ScopeSql.class,method="find")
    List<Map<String,Object>> findScoped(@Param("__verifiedTenant") byte[] tenant,@Param("access") RowAccessControl.Predicate access);

    /** 组织固定列查询，公共绑定器只处理权威权限计划中的参数。 */
    final class ScopeSql {
        private ScopeSql(){}
        /** @param parameters 固定 Mapper 参数 @return 同租户且附带范围的不可变 SELECT */
        public static String find(Map<String,Object> parameters){
            var access=(RowAccessControl.Predicate)parameters.get("access");
            return "SELECT BIN_TO_UUID(id) AS id,name FROM tenant_organization_unit WHERE tenant_id=#{__verifiedTenant}"
                +ScopedSql.bind(access.sql(),access.parameters(),"access")+" ORDER BY name";
        }
    }
    /** 复合主键把租户作为第一列，ID 由应用生成。 */
    @Insert("INSERT INTO tenant_organization_unit (tenant_id,id,code,type_id,name) SELECT #{__verifiedTenant},UUID_TO_BIN(#{id}),CONCAT('ORG-',LEFT(REPLACE(#{id},'-',''),12)),id,#{name} FROM tenant_organization_type WHERE tenant_id=#{__verifiedTenant} AND code='BUSINESS' AND enabled=TRUE")
    @com.appnexus.persistence.mybatis.GuardedScope(resource="organization.unit",tenant="__verifiedTenant",recordId="id",actions="create")
    int create(@Param("__verifiedTenant") byte[] tenant, @Param("id") String id, @Param("name") String name);
    @Update("UPDATE tenant_organization_unit SET name = #{name} WHERE tenant_id = #{__verifiedTenant} AND id = UUID_TO_BIN(#{id})")
    @com.appnexus.persistence.mybatis.GuardedScope(resource="organization.unit",tenant="__verifiedTenant",recordId="id",actions="update")
    int rename(@Param("name") String name, @Param("__verifiedTenant") byte[] tenant, @Param("id") String id);
    @Update({"<script>","UPDATE tenant_organization_unit SET name=#{name} WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) AND id IN","<foreach collection='ids' item='allowed' open='(' separator=',' close=')'>UUID_TO_BIN(#{allowed})</foreach>","</script>"})
    @com.appnexus.persistence.mybatis.GuardedScope(resource="organization.unit",tenant="__verifiedTenant",recordId="id",actions="update")
    int renameExact(@Param("name") String name,@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("ids") List<String> ids);
    @Delete("DELETE FROM tenant_organization_unit WHERE tenant_id = #{__verifiedTenant} AND id = UUID_TO_BIN(#{id})")
    @com.appnexus.persistence.mybatis.GuardedScope(resource="organization.unit",tenant="__verifiedTenant",recordId="id",actions="delete")
    int delete(@Param("__verifiedTenant") byte[] tenant, @Param("id") String id);
    @Delete({"<script>","DELETE FROM tenant_organization_unit WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) AND id IN","<foreach collection='ids' item='allowed' open='(' separator=',' close=')'>UUID_TO_BIN(#{allowed})</foreach>","</script>"})
    @com.appnexus.persistence.mybatis.GuardedScope(resource="organization.unit",tenant="__verifiedTenant",recordId="id",actions="delete")
    int deleteExact(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("ids") List<String> ids);
}
