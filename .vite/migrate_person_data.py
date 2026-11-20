from pathlib import Path
import re
root=Path('backend/services/platform-service/organization/src/main/java/com/appnexus/platform/organization/personnel');fields=re.findall(r'PERSON\("person", "Person", List.of\(([^\n]+)\)',(root/'PersonData.java').read_text(encoding='utf-8'))[0];fields=re.findall(r'"([^"]+)"',fields);snake=lambda s:re.sub(r'([a-z0-9])([A-Z])',r'\1_\2',s).lower();columns=[snake(f) for f in fields]
where="WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND (LOCATE(#{keyword},code)&gt;0 OR LOCATE(#{keyword},name)&gt;0) <if test='enabled != null'> AND enabled=#{enabled}</if>"
insert='INSERT INTO md_person(id,tenant_id,code,name,'+','.join(columns)+',created_by,created_at,updated_by,updated_at) VALUES(UUID_TO_BIN(#{id}),UUID_TO_BIN(#{tenant}),#{input.code},#{input.name},'+','.join('#{values.'+f+'}' for f in fields)+',UUID_TO_BIN(#{actor}),CURRENT_TIMESTAMP(6),UUID_TO_BIN(#{actor}),CURRENT_TIMESTAMP(6))'
update='UPDATE md_person SET name=#{input.name},'+','.join(c+'=#{values.'+f+'}' for c,f in zip(columns,fields))+',version=version+1 WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id}) AND version=#{input.version} AND code=#{input.code}'
mapper='''package com.appnexus.platform.organization.personnel;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
import com.appnexus.platform.organization.personnel.PersonData.*;
/** 人员主档固定映射；字段构建内逐项列出，准入及字典引用继续由用例执行。 */
@Mapper
@PlatformScoped(tables={"md_person"})
public interface PersonRecordMapper {
 /** 已准入人员摘要；证件与家庭明细不进入列表。 */
 @Select({"<script>","SELECT BIN_TO_UUID(id) id,code,name,enabled,version,BIN_TO_UUID(created_by) audit_created_by,created_at,BIN_TO_UUID(updated_by) audit_updated_by,updated_at,english_name englishName,phone,email,employment_status employmentStatus FROM md_person '''+where+''' ORDER BY code,id LIMIT #{size} OFFSET #{offset}","</script>"})
 List<Map<String,Object>> list(@Param("tenant") String tenant,@Param("keyword") String keyword,@Param("enabled") Boolean enabled,@Param("size") int size,@Param("offset") int offset);
 /** 列表COUNT与数据使用相同租户及过滤条件。 */
 @Select({"<script>","SELECT COUNT(*) FROM md_person '''+where+'''","</script>"}) long count(@Param("tenant") String tenant,@Param("keyword") String keyword,@Param("enabled") Boolean enabled);
 /** 固定主档新增，账号来自可信上下文，UTC时间由数据库产生。 */
 @Insert("'''+insert+'''") int create(@Param("tenant") String tenant,@Param("id") String id,@Param("input") Input input,@Param("values") Map<String,String> values,@Param("actor") String actor);
 /** 固定主档CAS修改，编码不可变；失败整笔回滚。 */
 @Update("'''+update+'''") int update(@Param("tenant") String tenant,@Param("id") String id,@Param("input") Input input,@Param("values") Map<String,String> values);
 /** 独立启停操作及原版本校验。 */
 @Update("UPDATE md_person SET enabled=#{enabled},version=version+1 WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id}) AND version=#{version}") int status(@Param("tenant") String tenant,@Param("id") String id,@Param("enabled") boolean enabled,@Param("version") long version);
 /** 原主档值仅用于受控修改合并及字典引用校验。 */
 @Select("SELECT '''+','.join(c+' '+f for c,f in zip(columns,fields))+''' FROM md_person WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id})") List<Map<String,String>> previous(@Param("tenant") String tenant,@Param("id") String id);
 /** 修改审计与业务同事务，不覆盖创建信息。 */
 @Update("UPDATE md_person SET updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id})") int audit(@Param("tenant") String tenant,@Param("id") String id,@Param("actor") String actor);
}
''';(root/'PersonRecordMapper.java').write_text(mapper,encoding='utf-8')
p=root/'PersonDataService.java';s=p.read_text(encoding='utf-8').replace('import org.springframework.jdbc.core.JdbcTemplate;','').replace('private final JdbcTemplate jdbc;','private final PersonRecordMapper mapper;').replace('PersonDataService(JdbcTemplate jdbc,','PersonDataService(PersonRecordMapper mapper,').replace('this.jdbc=jdbc','this.mapper=mapper').replace('@param jdbc','@param mapper')
start=s.index('    String where=');end=s.index('    return new Page(rows,total);',start)
s=s[:start]+'''    long total=mapper.count(c.tenantId().toString(),keyword.trim(),enabled);
    var rows=mapper.list(c.tenantId().toString(),keyword.trim(),enabled,size,(page-1)*size).stream().map(row->{
      var attributes=new LinkedHashMap<String,String>();for(String field:List.of("englishName","phone","email","employmentStatus"))attributes.put(field,(String)row.get(field));
      return new Row((String)row.get("id"),(String)row.get("code"),(String)row.get("name"),attributes,Boolean.TRUE.equals(row.get("enabled")),((Number)row.get("version")).longValue(),new Audit((String)row.get("audit_created_by"),utc(row.get("created_at")),(String)row.get("audit_updated_by"),utc(row.get("updated_at"))));
    }).toList();
'''+s[end:]
start=s.index('        var args=new ArrayList<Object>');end=s.index('        dictionaryReferences(',start);s=s[:start]+'        mapper.create(c.tenantId().toString(),id,input,values,c.accountId().toString());\n'+s[end:]
start=s.index('      var args=new ArrayList<Object>');end=s.index('      if(count!=1)',start);s=s[:start]+'      int count=mapper.update(c.tenantId().toString(),id,input,values);\n'+s[end:]
start=s.index('jdbc.update(');end=s.index('!=1)',start);s=s[:start]+'mapper.status(c.tenantId().toString(),id,input.enabled(),input.version())'+s[end:]
# tail helpers use fixed person fields; remove SQL assembly and map results directly.
start=s.index('jdbc.query(',s.index('private Map<String,String> previousValues'));end=s.index(';',start);s=s[:start]+'mapper.previous(c.tenantId().toString(),id)'+s[end:]
start=s.index('jdbc.update(');end=s.index(';',start);s=s[:start]+'mapper.audit(c.tenantId().toString(),id,c.accountId().toString())'+s[end:]
s=s.rstrip()[:-1]+''' private static String utc(Object value){return value==null?null:((java.sql.Timestamp)value).toInstant().toString();}
}\n''';p.write_text(s,encoding='utf-8')
p=root/'PersonnelConfiguration.java';s=p.read_text(encoding='utf-8').replace('personDataService(JdbcTemplate jdbc,','personDataService(PersonRecordMapper mapper,').replace('new PersonDataService(jdbc,','new PersonDataService(mapper,');p.write_text(s,encoding='utf-8')
