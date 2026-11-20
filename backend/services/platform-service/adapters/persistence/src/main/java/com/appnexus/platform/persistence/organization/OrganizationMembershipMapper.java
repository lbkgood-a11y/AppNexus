package com.appnexus.platform.persistence.organization;

import com.appnexus.persistence.mybatis.PlatformScoped;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** 授权基础设施的权威组织身份查询；允许后台调用，租户及成员来自公开授权端口，不接受页面自报身份。 */
@Mapper
@PlatformScoped(tables={"md_person_account","md_person","tenant_person_organization","tenant_organization_unit","tenant_member"})
public interface OrganizationMembershipMapper {
    /**
     * 读取启用成员关联人员的有效组织；所有关系通过同租户连接，未知身份返回空集合。
     * @param tenantId 可信租户 UUID，不可缺省
     * @param memberId 本租户成员 UUID，不可使用全局账号 UUID 替代
     * @return UTC 当天有效且启用的组织 UUID；缺省日期不限制，起止日均包含
     */
    @Select("""
        SELECT DISTINCT BIN_TO_UUID(o.id)
        FROM md_person_account pa
        JOIN md_person p ON p.tenant_id=pa.tenant_id AND p.id=pa.person_id AND p.enabled=TRUE
        JOIN tenant_person_organization po ON po.tenant_id=p.tenant_id AND po.person_id=p.id
        JOIN tenant_organization_unit o ON o.tenant_id=po.tenant_id AND o.id=po.organization_id AND o.enabled=TRUE
        JOIN tenant_member m ON m.tenant_id=pa.tenant_id AND m.id=pa.member_id AND m.enabled=TRUE AND m.removed_at IS NULL
        WHERE pa.tenant_id=UUID_TO_BIN(#{tenantId}) AND pa.member_id=UUID_TO_BIN(#{memberId}) AND po.enabled=TRUE
          AND (po.starts_on IS NULL OR po.starts_on<=UTC_DATE())
          AND (po.ends_on IS NULL OR po.ends_on>=UTC_DATE())
        """)
    List<String> activeOrganizations(@Param("tenantId") String tenantId,@Param("memberId") String memberId);
}
