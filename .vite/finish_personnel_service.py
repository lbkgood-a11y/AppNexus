from pathlib import Path
import re
root=Path('backend/services/platform-service/organization/src/main/java/com/appnexus/platform/organization/personnel');p=root/'PersonnelService.java';s=p.read_text(encoding='utf-8')
fields=re.findall(r'"([^"]+)"',re.search(r'PERSON\("person", "Person", List.of\(([^\n]+)\)',(root/'PersonData.java').read_text(encoding='utf-8')).group(1));snake=lambda x:re.sub(r'([a-z0-9])([A-Z])',r'\1_\2',x).lower()
mapper=root/'PersonnelServiceSqlMapper.java';ms=mapper.read_text(encoding='utf-8');methods=[]
queries=[('profile','SELECT BIN_TO_UUID(id) id,code,name,enabled,version,'+','.join(snake(f)+' '+f for f in fields)+' FROM md_person WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id})'),('audit','SELECT BIN_TO_UUID(created_by) created_by,created_at,BIN_TO_UUID(updated_by) updated_by,updated_at FROM md_person WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id})'),('details',"SELECT BIN_TO_UUID(id) id,section_code,CAST(content AS CHAR) content FROM md_person_detail WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND person_id=UUID_TO_BIN(#{id}) AND section_code NOT IN ('organizations','positions') ORDER BY section_code,id"),('assignments','SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(member_id) member_text FROM md_person_account WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND person_id=UUID_TO_BIN(#{id}) ORDER BY created_at,id')]
for name,sql in queries:methods.append(' /** @param tenant 可信租户UUID @param id 已授权人员UUID @return 固定字段与同人员明细；不扩展独立读取资格 */\n @Select("'+sql+'") List<Map<String,Object>> '+name+'(@Param("tenant") String tenant,@Param("id") String id);')
mapper.write_text(ms.rstrip()[:-1]+'\n'+'\n'.join(methods)+'\n}\n',encoding='utf-8')
start=s.index('    var people=jdbc.query(');end=s.index('    return new Profile(',start)
s=s[:start]+'''    var people=statements.profile(c.tenantId().toString(),id).stream().map(row->{var values=new LinkedHashMap<String,String>();for(String field:Kind.PERSON.fields)values.put(field,(String)row.get(field));return new Row((String)row.get("id"),(String)row.get("code"),(String)row.get("name"),values,Boolean.TRUE.equals(row.get("enabled")),((Number)row.get("version")).longValue());}).toList();
    if(people.isEmpty())throw new Rejection(404,"PERSON_NOT_FOUND");
    var rawAudit=statements.audit(c.tenantId().toString(),id).getFirst();String creator=(String)rawAudit.get("created_by"),updater=(String)rawAudit.get("updated_by");
    var audit=new Audit(creator,directory.accountName(c.tenantId(),creator),utc(rawAudit.get("created_at")),updater,directory.accountName(c.tenantId(),updater),utc(rawAudit.get("updated_at")));
    var details=new ArrayList<>(statements.details(c.tenantId().toString(),id).stream().map(row->new Detail((String)row.get("id"),(String)row.get("section_code"),decode((String)row.get("content")))).toList());
    for(var affiliation:directory.affiliations(c.tenantId(),uuid(id)))details.add(new Detail(affiliation.id(),affiliation.section(),affiliation.values()));
    var catalog=directory.catalog(c.tenantId());
    var assignments=statements.assignments(c.tenantId().toString(),id).stream().map(row->{String member=(String)row.get("member_text");var user=catalog.users().stream().filter(value->value.memberId().equalsIgnoreCase(member)).findFirst();return new Assignment((String)row.get("id"),member,user.map(PersonnelDirectory.User::name).orElse(""),user.isPresent());}).toList();
'''+s[end:]
s=s.replace('import org.springframework.jdbc.core.JdbcTemplate;','').replace('private final JdbcTemplate jdbc;','').replace('JdbcTemplate jdbc,','').replace('this.jdbc=jdbc;','');s=s.rstrip()[:-1]+''' private static String utc(Object value){return value==null?null:((java.sql.Timestamp)value).toInstant().toString();}
}\n''';p.write_text(s,encoding='utf-8')
p=root/'PersonnelConfiguration.java';s=p.read_text(encoding='utf-8').replace('personnelService(JdbcTemplate jdbc,','personnelService(PersonnelServiceSqlMapper statements,').replace('new PersonnelService(jdbc,access,directory,manager,dictionaries)','new PersonnelService(access,directory,manager,dictionaries,statements)').replace('import org.springframework.jdbc.core.JdbcTemplate;','');p.write_text(s,encoding='utf-8')
