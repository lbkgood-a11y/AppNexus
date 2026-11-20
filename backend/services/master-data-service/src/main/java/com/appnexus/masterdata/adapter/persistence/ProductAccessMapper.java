package com.appnexus.masterdata.adapter.persistence;

import com.appnexus.persistence.mybatis.TenantAggregateScoped;
import com.appnexus.platform.authorization.RowAccessControl.OrganizationRange;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/** 商品主行范围的固定执行映射；只返回归属事实，拒绝后不读取业务正文。 */
@Mapper
@TenantAggregateScoped(requireTenantMatch=true,tables={"md_product_v1"},statements={"authorized"})
public interface ProductAccessMapper {
    /**
     * @param tenant 可信租户二进制UUID，必须与安全链一致
     * @param id 已校验商品UUID
     * @param range 公共执行器生成的本次操作范围；非ALL空集合拒绝，历史NULL仅ALL可读
     * @param lock 写用例在同事务锁定已授权父行；不能由客户端解除
     * @return 已授权主行的组织事实，无权或不存在均为空集合
     */
    @Select({"<script>","SELECT BIN_TO_UUID(organization_id) organizationId,id FROM md_product_v1 WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id})",
        "<if test='!range.all'><choose><when test='!range.ids.isEmpty()'> AND organization_id IN <foreach collection='range.ids' item='organization' open='(' separator=',' close=')'>UUID_TO_BIN(#{organization})</foreach></when><otherwise> AND 1=0</otherwise></choose></if>",
        "<if test='lock'> FOR UPDATE</if>","</script>"})
    List<Map<String,Object>> authorized(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("range") OrganizationRange range,@Param("lock") boolean lock);
}
