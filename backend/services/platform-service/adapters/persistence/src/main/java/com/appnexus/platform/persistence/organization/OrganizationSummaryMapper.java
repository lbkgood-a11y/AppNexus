package com.appnexus.platform.persistence.organization;

import com.appnexus.persistence.mybatis.TenantAggregateScoped;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/** 组织统计固定联表 SQL；租户绑定由拦截器从可信请求覆盖，范围在分组前执行。 */
@Mapper
@TenantAggregateScoped(tables={"tenant_organization_unit","tenant_organization_type"},statements={"summarize"})
public interface OrganizationSummaryMapper {
    /**
     * 汇总已授权组织；空组织集合不会解除范围限制。
     * @param tenant 可信租户二进制 UUID，拦截器再次验证并覆盖
     * @param all 是否为已解析的 ALL 范围；不能来自客户端
     * @param ids 已解析的组织 UUID 集合，非 ALL 时必需且非空
     * @return 固定业务分组和计数，不含未授权组织
     */
    @Select({"<script>",
        "SELECT t.code,t.name,u.enabled,COUNT(*) total FROM tenant_organization_unit u",
        "JOIN tenant_organization_type t ON t.id=u.type_id AND t.tenant_id=u.tenant_id",
        "WHERE u.tenant_id=#{__verifiedTenant} AND t.tenant_id=#{__verifiedTenant}",
        "<if test='!all'><choose><when test='ids != null and !ids.isEmpty()'> AND u.id IN",
        "<foreach collection='ids' item='id' open='(' separator=',' close=')'>UUID_TO_BIN(#{id})</foreach>",
        "</when><otherwise> AND 1=0</otherwise></choose></if>",
        "GROUP BY t.code,t.name,u.enabled ORDER BY t.code,u.enabled","</script>"})
    List<Map<String,Object>> summarize(@Param("__verifiedTenant") byte[] tenant,@Param("all") boolean all,@Param("ids") List<String> ids);
}
