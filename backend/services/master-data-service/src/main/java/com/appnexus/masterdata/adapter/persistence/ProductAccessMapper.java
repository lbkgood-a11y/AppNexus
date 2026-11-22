package com.appnexus.masterdata.adapter.persistence;

import com.appnexus.persistence.mybatis.TenantAggregateScoped;
import com.appnexus.platform.authorization.RowAccessControl.Predicate;
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
     * @param access 公共执行器生成的本次操作谓词；缺失拒绝
     * @param lock 写用例在同事务锁定已授权父行；不能由客户端解除
     * @return 已授权主行的组织事实，无权或不存在均为空集合
     */
    @com.appnexus.persistence.mybatis.GuardedScope(predicate="access",resource="masterdata.product",tenant="__verifiedTenant")
    @Select({"<script>","SELECT BIN_TO_UUID(organization_id) organizationId,id FROM md_product_v1 WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id})",
        "<bind name='accessSql' value='@com.appnexus.persistence.mybatis.ScopedSql@bind(access.sql(), access.parameters(), &quot;access&quot;)'/> ${accessSql}",
        "<if test='lock'> FOR UPDATE</if>","</script>"})
    List<Map<String,Object>> authorized(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("access") Predicate access,@Param("lock") boolean lock);
}
