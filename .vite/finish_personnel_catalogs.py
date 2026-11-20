from pathlib import Path
root=Path('backend/services/platform-service/organization/src/main/java/com/appnexus/platform/organization/personnel')
(root/'PersonnelIdentityMapper.java').write_text('''package com.appnexus.platform.organization.personnel;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** 人员公开身份目录固定映射；调用者仍须分别验证操作和被代表身份，不授予档案原文权限。 */
@Mapper
@PlatformScoped(tables={"md_person","md_person_account"})
public interface PersonnelIdentityMapper {
 /** @param tenant 可信租户UUID @return 当前启用在职人员最小姓名摘要 */
 @Select("SELECT BIN_TO_UUID(id) id,name FROM md_person WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND enabled=TRUE AND employment_status IN ('ACTIVE','PROBATION') ORDER BY name,id") List<Map<String,Object>> active(@Param("tenant") String tenant);
 /** @param tenant 可信租户 @param person 原操作已校验人员UUID @param actor 实际账号UUID @return 更新行数；失败整笔回滚 */
 @Update("UPDATE md_person SET version=version+1,updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{person}) AND enabled=TRUE") int touch(@Param("tenant") String tenant,@Param("person") String person,@Param("actor") String actor);
 /** @param tenant 可信租户 @param member 已启用租户成员UUID @return 该成员关联的有效人员，不合并为业务授权 */
 @Select("SELECT BIN_TO_UUID(p.id) id,p.name FROM md_person_account a JOIN md_person p ON p.tenant_id=a.tenant_id AND p.id=a.person_id WHERE a.tenant_id=UUID_TO_BIN(#{tenant}) AND a.member_id=UUID_TO_BIN(#{member}) AND p.enabled=TRUE AND p.employment_status IN ('ACTIVE','PROBATION') ORDER BY p.name,p.id") List<Map<String,Object>> people(@Param("tenant") String tenant,@Param("member") String member);
 /** @param tenant 可信租户 @param person 当前租户启用人员 @return 关联成员UUID，后续目录复核账号有效性 */
 @Select("SELECT BIN_TO_UUID(a.member_id) FROM md_person_account a JOIN md_person p ON p.tenant_id=a.tenant_id AND p.id=a.person_id WHERE a.tenant_id=UUID_TO_BIN(#{tenant}) AND p.id=UUID_TO_BIN(#{person}) AND p.enabled=TRUE AND p.employment_status IN ('ACTIVE','PROBATION')") List<String> members(@Param("tenant") String tenant,@Param("person") String person);
}
''',encoding='utf-8')
p=root/'PersonnelCatalogAdapter.java';s=p.read_text(encoding='utf-8').replace('import org.springframework.jdbc.core.JdbcTemplate;','').replace('private final JdbcTemplate jdbc;','private final PersonnelIdentityMapper mapper;').replace('PersonnelCatalogAdapter(JdbcTemplate jdbc){this.jdbc=jdbc;}','PersonnelCatalogAdapter(PersonnelIdentityMapper mapper){this.mapper=mapper;}').replace('@param jdbc','@param mapper')
start=s.index('jdbc.query(');end=s.index(';}',start);s=s[:start]+'mapper.active(tenant.toString()).stream().map(row->new Person(UUID.fromString((String)row.get("id")),(String)row.get("name"))).toList()'+s[end:]
start=s.index('jdbc.update(');end=s.index('!=1)',start);s=s[:start]+'mapper.touch(tenant.toString(),person.toString(),actor.toString())'+s[end:];p.write_text(s,encoding='utf-8')
p=root/'PersonnelExecutionAdapter.java';s=p.read_text(encoding='utf-8').replace('private final JdbcTemplate jdbc;','private final PersonnelIdentityMapper identity;').replace('PersonnelExecutionAdapter(JdbcTemplate jdbc,','PersonnelExecutionAdapter(PersonnelIdentityMapper identity,').replace('this.jdbc=jdbc;','this.identity=identity;').replace('import org.springframework.jdbc.core.JdbcTemplate;','')
start=s.index('jdbc.query(');end=s.index(';',start);s=s[:start]+'identity.people(tenant.toString(),member.toString()).stream().map(row->Map.entry((String)row.get("id"),(String)row.get("name"))).toList()'+s[end:]
start=s.index('jdbc.query(');end=s.index(';',start);s=s[:start]+'identity.members(tenant.toString(),person)'+s[end:];p.write_text(s,encoding='utf-8')
