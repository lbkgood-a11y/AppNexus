from pathlib import Path
root=Path('backend/services/platform-service/adapters/security/src/main/java/com/appnexus/platform/security')
mapper='''package com.appnexus.platform.security;
import com.appnexus.persistence.mybatis.PlatformScoped;
import com.appnexus.contracts.workflow.WorkflowPlatform.Option;
import java.util.List;
import org.apache.ibatis.annotations.*;
/** 已准入流程/打印公开桥接的最小目录固定SQL；后台及机器身份由原公共契约校验，不伪造租户请求。 */
@Mapper
@PlatformScoped(tables={"tenant_member","identity_account","tenant_organization_unit","tenant_organization_position","platform_tenant"})
public interface CapabilityDirectoryMapper {
 /** @param tenant 可信租户UUID @return 启用成员最小姓名目录，不授予人员档案读取权 */
 @Select("SELECT BIN_TO_UUID(a.id) id,a.display_name name FROM tenant_member m JOIN identity_account a ON a.id=m.account_id WHERE m.tenant_id=UUID_TO_BIN(#{tenant}) AND m.enabled=TRUE AND m.removed_at IS NULL AND a.enabled=TRUE ORDER BY a.display_name,a.id")
 List<Option> members(@Param("tenant") String tenant);
 /** @param tenant 已授权租户UUID @return 启用组织最小名称目录 */
 @Select("SELECT BIN_TO_UUID(id) id,name FROM tenant_organization_unit WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND enabled=TRUE ORDER BY name,id")
 List<Option> organizations(@Param("tenant") String tenant);
 /** @param tenant 已授权租户UUID @return 启用岗位及所属组织名称 */
 @Select("SELECT BIN_TO_UUID(p.id) id,CONCAT(u.name,' / ',p.name) name FROM tenant_organization_position p JOIN tenant_organization_unit u ON u.tenant_id=p.tenant_id AND u.id=p.organization_id WHERE p.tenant_id=UUID_TO_BIN(#{tenant}) AND p.enabled=TRUE AND u.enabled=TRUE ORDER BY u.name,p.name,p.id")
 List<Option> positions(@Param("tenant") String tenant);
 /** @param tenant 可信租户UUID @param organization 公开人员端口已验证的当前任职组织UUID @return 同租户启用组织，不扩展其他身份的组织 */
 @Select("SELECT BIN_TO_UUID(id) id,name FROM tenant_organization_unit WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{organization}) AND enabled=TRUE")
 List<Option> organization(@Param("tenant") String tenant,@Param("organization") String organization);
 /** @param tenant 打印平台已验证的机器身份绑定租户UUID @return 租户显示名称，不含成员或业务数据 */
 @Select("SELECT name FROM platform_tenant WHERE id=UUID_TO_BIN(#{tenant})")
 String tenantName(@Param("tenant") String tenant);
}
'''
(root/'CapabilityDirectoryMapper.java').write_text(mapper,encoding='utf-8')
p=root/'WorkflowPlatformBridge.java';s=p.read_text(encoding='utf-8').replace('import org.springframework.jdbc.core.JdbcTemplate;\n','').replace('private final JdbcTemplate jdbc;','private final CapabilityDirectoryMapper mapper;').replace('TenantAccess tenants, JdbcTemplate jdbc,','TenantAccess tenants, CapabilityDirectoryMapper mapper,').replace('this.jdbc = jdbc;','this.mapper = mapper;').replace('@param jdbc 组织最小目录的固定租户查询','@param mapper 组织最小目录的固定租户查询')
start=s.index('    var members = jdbc.query(');end=s.index('    return Map.of(',start);s=s[:start]+'''    var members=mapper.members(c.tenantId());
    var organizations=mapper.organizations(c.tenantId());
    var positions=mapper.positions(c.tenantId());
'''+s[end:]
start=s.index('    return jdbc.query(');end=s.index(';',start);s=s[:start]+'    return mapper.organization(c.tenantId(),c.organizationId())'+s[end:]
s=__import__('re').sub(r'jdbc.queryForObject\(\s*"SELECT enabled FROM identity_account WHERE id=UUID_TO_BIN\(\?\)",\s*Boolean.class,\s*actor\)', 'mapper.activeAccount(actor)',s)
assert 'jdbc.' not in s
p.write_text(s,encoding='utf-8')
p=root/'PrintPlatformBridge.java';s=p.read_text(encoding='utf-8').replace('private final org.springframework.jdbc.core.JdbcTemplate jdbc;','private final CapabilityDirectoryMapper mapper;').replace('org.springframework.jdbc.core.JdbcTemplate jdbc)','CapabilityDirectoryMapper mapper)').replace('this.jdbc=jdbc','this.mapper=mapper').replace('jdbc.queryForObject("SELECT name FROM platform_tenant WHERE id=UUID_TO_BIN(?)",String.class,c.tenantId())','mapper.tenantName(c.tenantId())');p.write_text(s,encoding='utf-8')
