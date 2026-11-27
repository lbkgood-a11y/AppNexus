package com.appnexus.masterdata.adapter.persistence.organization;

import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 已获统一删除许可的组织用例调用，在同一事务协调租户锁与人员引用检查。 */
@Mapper
@PlatformScoped(tables={"platform_tenant","tenant_person_organization","tenant_person_position","tenant_organization_position"})
interface OrganizationDeletionGuardMapper {
    /** 按租户主键锁定，与租户停用及组织授权变更使用同一数据库行。 */
    @Select("SELECT enabled FROM platform_tenant WHERE id=UUID_TO_BIN(#{tenant}) FOR UPDATE")
    Boolean lockTenant(String tenant);
    /** 组织删除保护检查人员归属及岗位任职，不能删除仍被人员使用的组织。 */
    @Select("SELECT EXISTS(SELECT 1 FROM tenant_person_organization WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND organization_id=UUID_TO_BIN(#{organization})) OR EXISTS(SELECT 1 FROM tenant_person_position a JOIN tenant_organization_position p ON p.tenant_id=a.tenant_id AND p.id=a.position_id WHERE a.tenant_id=UUID_TO_BIN(#{tenant}) AND p.organization_id=UUID_TO_BIN(#{organization}))")
    boolean organizationAssigned(@Param("tenant") String tenant,@Param("organization") String organization);
}
