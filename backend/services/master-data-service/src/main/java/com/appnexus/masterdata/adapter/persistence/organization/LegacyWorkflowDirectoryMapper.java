package com.appnexus.masterdata.adapter.persistence.organization;

import com.appnexus.contracts.workflow.WorkflowPlatform.Option;
import com.appnexus.persistence.mybatis.PlatformScoped;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 兼容流程配置和单据归属查询，固定租户及启停范围。 */
@Mapper
@PlatformScoped(tables={"tenant_organization_unit","tenant_organization_position"})
interface LegacyWorkflowDirectoryMapper {
 /** @param tenant 已授权租户UUID @return 启用组织最小名称目录 */
 @Select("SELECT BIN_TO_UUID(id) id,name FROM tenant_organization_unit WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND enabled=TRUE ORDER BY name,id")
 List<Option> organizations(@Param("tenant") String tenant);
 /** @param tenant 已授权租户UUID @return 启用岗位及所属组织名称 */
 @Select("SELECT BIN_TO_UUID(p.id) id,CONCAT(u.name,' / ',p.name) name FROM tenant_organization_position p JOIN tenant_organization_unit u ON u.tenant_id=p.tenant_id AND u.id=p.organization_id WHERE p.tenant_id=UUID_TO_BIN(#{tenant}) AND p.enabled=TRUE AND u.enabled=TRUE ORDER BY u.name,p.name,p.id")
 List<Option> positions(@Param("tenant") String tenant);
 /** @param tenant 可信租户UUID @param organization 公开人员端口已验证的当前任职组织UUID @return 同租户启用组织，不扩展其他身份的组织 */
 @Select("SELECT BIN_TO_UUID(id) id,name FROM tenant_organization_unit WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{organization}) AND enabled=TRUE")
 List<Option> organization(@Param("tenant") String tenant,@Param("organization") String organization);
}
