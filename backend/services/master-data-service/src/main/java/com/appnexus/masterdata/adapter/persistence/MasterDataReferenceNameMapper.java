package com.appnexus.masterdata.adapter.persistence;

import com.appnexus.persistence.mybatis.GuardedScope;
import com.appnexus.persistence.mybatis.PlatformScoped;
import com.appnexus.platform.authorization.RowAccessControl.Predicate;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/** 仅查询主数据自有表的名称投影；行范围由与原档案读取相同的公共谓词强制绑定。 */
@Mapper
@PlatformScoped(tables={"md_partner","md_warehouse","md_product_v1","md_product_unit","md_product_specification","md_product_sku","md_storage_area","md_storage_location"})
public interface MasterDataReferenceNameMapper {
    String IDS="<foreach collection='ids' item='id' open='(' separator=',' close=')'>UUID_TO_BIN(#{id})</foreach>";

    /** @param tenant 可信租户 UUID @param ids 当前批的仓库 UUID @param access 仓库读取范围 @return 可见仓库名称 */
    @GuardedScope(tenant="tenant",predicate="access",resource="masterdata.warehouse",actions="read")
    @Select({"<script>","<bind name='rangeSql' value='@com.appnexus.persistence.mybatis.ScopedSql@bind(access.sql(), access.parameters(), &quot;access&quot;)'/>","SELECT BIN_TO_UUID(id) id,name,enabled FROM md_warehouse WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id IN",IDS,"${rangeSql}</script>"})
    List<Map<String,Object>> warehouses(@Param("tenant") String tenant,@Param("ids") List<String> ids,@Param("access") Predicate access);

    /** @param tenant 可信租户 UUID @param ids 当前批的客商 UUID @param access 客商读取范围 @return 可见客商名称 */
    @GuardedScope(tenant="tenant",predicate="access",resource="masterdata.partner",actions="read")
    @Select({"<script>","<bind name='rangeSql' value='@com.appnexus.persistence.mybatis.ScopedSql@bind(access.sql(), access.parameters(), &quot;access&quot;)'/>","SELECT BIN_TO_UUID(id) id,name,enabled FROM md_partner WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id IN",IDS,"${rangeSql}</script>"})
    List<Map<String,Object>> partners(@Param("tenant") String tenant,@Param("ids") List<String> ids,@Param("access") Predicate access);

    /** @param tenant 可信租户 UUID @param ids 位置引用 UUID @param access 根仓库读取权限 @return 可见位置名称 */
    @GuardedScope(tenant="tenant",predicate="access",resource="masterdata.warehouse",actions="read")
    @Select({"<script>","<bind name='rangeSql' value='@com.appnexus.persistence.mybatis.ScopedSql@bind(access.sql(), access.parameters(), &quot;access&quot;)'/>","SELECT BIN_TO_UUID(id_in.id) id,id_in.name FROM md_storage_area id_in WHERE id_in.tenant_id=UUID_TO_BIN(#{tenant}) AND id_in.id IN",IDS,
        "AND EXISTS (SELECT 1 FROM md_warehouse WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id_in.parent_code=code ${rangeSql})</script>"})
    List<Map<String,Object>> areas(@Param("tenant") String tenant,@Param("ids") List<String> ids,@Param("access") Predicate access);

    /** @param tenant 可信租户 UUID @param ids 位置引用 UUID @param access 根仓库读取权限 @return 可见位置名称 */
    @GuardedScope(tenant="tenant",predicate="access",resource="masterdata.warehouse",actions="read")
    @Select({"<script>","<bind name='rangeSql' value='@com.appnexus.persistence.mybatis.ScopedSql@bind(access.sql(), access.parameters(), &quot;access&quot;)'/>","SELECT BIN_TO_UUID(id_in.id) id,id_in.name FROM md_storage_location id_in WHERE id_in.tenant_id=UUID_TO_BIN(#{tenant}) AND id_in.id IN",IDS,
        "AND EXISTS (SELECT 1 FROM md_warehouse WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND EXISTS (SELECT 1 FROM md_storage_area a WHERE a.tenant_id=id_in.tenant_id AND a.code=id_in.parent_code AND a.parent_code=md_warehouse.code) ${rangeSql})</script>"})
    List<Map<String,Object>> locations(@Param("tenant") String tenant,@Param("ids") List<String> ids,@Param("access") Predicate access);


    /** @param tenant 可信租户 UUID @param ids 当前批的商品 UUID @param access 商品读取范围 @return 可见商品名称 */
    @GuardedScope(tenant="tenant",predicate="access",resource="masterdata.product",actions="read")
    @Select({"<script>","<bind name='rangeSql' value='@com.appnexus.persistence.mybatis.ScopedSql@bind(access.sql(), access.parameters(), &quot;access&quot;)'/>","SELECT BIN_TO_UUID(id) id,name FROM md_product_v1 WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id IN",IDS,"${rangeSql}</script>"})
    List<Map<String,Object>> products(@Param("tenant") String tenant,@Param("ids") List<String> ids,@Param("access") Predicate access);

    /** @param tenant 可信租户 UUID @param ids 当前批的规格 UUID @param access 父商品读取范围 @return 可见规格名称 */
    @GuardedScope(tenant="tenant",predicate="access",resource="masterdata.product",actions="read")
    @Select({"<script>","<bind name='rangeSql' value='@com.appnexus.persistence.mybatis.ScopedSql@bind(access.sql(), access.parameters(), &quot;access&quot;)'/>","SELECT BIN_TO_UUID(s.id) id,s.name FROM md_product_specification s WHERE s.tenant_id=UUID_TO_BIN(#{tenant}) AND s.id IN",IDS,
        "AND EXISTS (SELECT 1 FROM md_product_v1 WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=s.product_id ${rangeSql})</script>"})
    List<Map<String,Object>> specifications(@Param("tenant") String tenant,@Param("ids") List<String> ids,@Param("access") Predicate access);

    /** @param tenant 可信租户 UUID @param ids 当前批的 SKU UUID @param access 父商品读取范围 @return 可见 SKU 编码及所属规格名称 */
    @GuardedScope(tenant="tenant",predicate="access",resource="masterdata.product",actions="read")
    @Select({"<script>","<bind name='rangeSql' value='@com.appnexus.persistence.mybatis.ScopedSql@bind(access.sql(), access.parameters(), &quot;access&quot;)'/>","SELECT BIN_TO_UUID(k.id) id,k.code AS sku_code,s.name FROM md_product_sku k",
        "JOIN md_product_specification s ON s.tenant_id=k.tenant_id AND s.id=k.specification_id AND s.product_id=k.product_id",
        "WHERE k.tenant_id=UUID_TO_BIN(#{tenant}) AND k.id IN",IDS,
        "AND EXISTS (SELECT 1 FROM md_product_v1 WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=k.product_id ${rangeSql})</script>"})
    List<Map<String,Object>> skus(@Param("tenant") String tenant,@Param("ids") List<String> ids,@Param("access") Predicate access);

    /** @param tenant 可信租户 UUID @param ids 当前批的单位 UUID @return 具有目录读取权限的单位名称 */
    @GuardedScope(tenant="tenant",catalogPublication="masterdata.productunit:read")
    @Select({"<script>","SELECT BIN_TO_UUID(id) id,name FROM md_product_unit WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id IN",IDS,"</script>"})
    List<Map<String,Object>> units(@Param("tenant") String tenant,@Param("ids") List<String> ids);
}
