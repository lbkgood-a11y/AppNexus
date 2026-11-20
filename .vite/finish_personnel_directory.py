from pathlib import Path
root=Path('backend/services/platform-service/adapters/persistence/src/main/java/com/appnexus/platform/persistence/organization')
p=root/'PersonnelDirectoryMapper.java';s=p.read_text(encoding='utf-8');s=s.rstrip()[:-1]+'''
 /** @param tenant 可信租户 @param organization 已校验组织UUID @param kind POSITION或LEADER @param reference 岗位UUID，仅POSITION使用 @return 当前有效任职人员UUID，不授予档案权限 */
 @Select({"<script>","SELECT DISTINCT BIN_TO_UUID(a.person_id) FROM tenant_person_position a JOIN tenant_organization_position p ON p.tenant_id=a.tenant_id AND p.id=a.position_id JOIN tenant_organization_unit u ON u.tenant_id=p.tenant_id AND u.id=p.organization_id",
 "<if test=\"kind == 'LEADER'\">JOIN tenant_person_leader l ON l.tenant_id=a.tenant_id AND l.assignment_id=a.id AND l.organization_id=u.id</if>",
 "WHERE a.tenant_id=UUID_TO_BIN(#{tenant}) AND u.id=UUID_TO_BIN(#{organization}) AND a.enabled=TRUE AND p.enabled=TRUE AND u.enabled=TRUE AND a.starts_at&lt;=CURRENT_TIMESTAMP(6) AND (a.ends_at IS NULL OR a.ends_at&gt;CURRENT_TIMESTAMP(6))",
 "<if test=\"kind == 'POSITION'\"> AND p.id=UUID_TO_BIN(#{reference})</if>","</script>"})
 List<String> resolve(@Param("tenant") String tenant,@Param("organization") String organization,@Param("kind") String kind,@Param("reference") String reference);
}
'''.replace('test="kind', 'test=\\"kind').replace("'LEADER'\">","'LEADER'\\\">").replace("'POSITION'\">","'POSITION'\\\">")
s=s.replace('@PlatformScoped(tables={','@PlatformScoped(tables={"tenant_person_leader",');p.write_text(s,encoding='utf-8')
p=root/'MyBatisPersonnelDirectory.java';s=p.read_text(encoding='utf-8');start=s.index('    String sql="SELECT DISTINCT');end=s.index('\n  }',start);s=s[:start]+'''    if(kind.equals("POSITION"))UUID.fromString(reference);
    return mapper.resolve(tenant.toString(),organization.toString(),kind,reference);'''+s[end:]
s=s.replace('import org.springframework.jdbc.core.JdbcTemplate;','').replace('@param jdbc','@param mapper');p.write_text(s,encoding='utf-8')
