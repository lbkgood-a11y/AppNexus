from pathlib import Path
import re
ns={};source=Path('.vite/extract_literal_mappers.py').read_text(encoding='utf-8');exec(source[:source.index('targets=')],ns);split_args=ns['split_args']
root=Path('backend/services/platform-service/adapters/persistence/src/main/java/com/appnexus/platform/persistence/organization');p=root/'JdbcPersonnelDirectory.java';s=p.read_text(encoding='utf-8')
users=re.search(r'private static final String USERS="([^"]+)";',s).group(1)
s=s.replace('USERS+" ORDER BY member_id"','"'+users+' ORDER BY member_id"').replace('USERS+" AND m.id=UUID_TO_BIN(?)"','"'+users+' AND m.id=UUID_TO_BIN(?)"')
entries=[]
for m in re.finditer(r'jdbc\.(query|queryForObject|update)\(',s):
 depth=1;i=m.end();q=None
 while depth:
  c=s[i]
  if q:
   if c=='\\':i+=2;continue
   if c==q:q=None
  elif c=='"':q=c
  elif c=='(':depth+=1
  elif c==')':depth-=1
  i+=1
 args=split_args(s[m.end():i-1]);raw=args.pop(0)
 if not re.fullmatch(r'"[^\n]+"',raw):continue
 sql=raw[1:-1];call=m.group(1);callback=None;ret='int'
 if call=='query':callback=args.pop(0);ret='List<Map<String,Object>>'
 if call=='queryForObject':ret=args.pop(0)[:-6]
 assert sql.count('?')==len(args),(sql,args)
 entries.append((m.start(),i,sql,args,ret,call,callback))
methods=[];tables=set()
for n,(start,end,sql,args,ret,call,cb) in enumerate(entries):
 tables.update(re.findall(r'(?i)(?:FROM|JOIN|INTO|UPDATE)\s+([a-z][a-z0-9_]+)',sql))
 for j in range(len(args)):sql=sql.replace('?', '#{p'+str(j)+'}',1)
 ann='Select' if call.startswith('query') else 'Insert' if sql.startswith('INSERT') else 'Delete' if sql.startswith('DELETE') else 'Update'
 methods.append(' /** 固定人员关系SQL；操作、租户、人员及组织有效性由公开端口复核，不复制授权规则。 */\n @'+ann+'("'+sql+'")\n '+ret+' statement'+str(n)+'('+','.join('@Param("p'+str(j)+'") Object p'+str(j) for j in range(len(args)))+');')
for n,(start,end,sql,args,ret,call,cb) in reversed(list(enumerate(entries))):
 replacement='mapper.statement'+str(n)+'('+','.join(args)+')'
 if cb:
  if 'new Organization(' in cb:expr='new Organization(text(row,"id"),text(row,"name"),flag(row,"enabled"))'
  elif 'new Position(' in cb:expr='new Position(text(row,"id"),text(row,"organization_id"),text(row,"name"),flag(row,"enabled"))'
  elif 'new User(' in cb:expr='new User(text(row,"member_id"),text(row,"account_id"),text(row,"display_name"))'
  elif 'new Affiliation(' in cb:
   body=cb[cb.index('new Affiliation('):]
   body=re.sub(r'rs.getString\("([^"]+)"\)',r'text(row,"\1")',body)
   body=re.sub(r'rs.getBoolean\("([^"]+)"\)',r'flag(row,"\1")',body)
   body=body.replace('rs.getTimestamp("starts_at").toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()','day(row.get("starts_at"))').replace('rs.getTimestamp("ends_at")==null?"":rs.getTimestamp("ends_at").toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()','day(row.get("ends_at"))')
   expr=body
  elif 'rs.getString(1)' in cb:expr='text(row,"display_name")'
  else:expr='text(row,"member_id")'
  replacement+='.stream().map(row->'+expr+').toList()'
 s=s[:start]+replacement+s[end:]
mapper='''package com.appnexus.platform.persistence.organization;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** 人员目录固定SQL映射；不接收任意SQL或客户端表名。 */
@Mapper
@PlatformScoped(tables={'''+','.join('"'+t+'"' for t in sorted(tables))+'''})
public interface PersonnelDirectoryMapper {
'''+ '\n\n'.join(methods)+'\n}\n'
(root/'PersonnelDirectoryMapper.java').write_text(mapper,encoding='utf-8')
s=s.replace('JdbcPersonnelDirectory','MyBatisPersonnelDirectory').replace('private final JdbcTemplate jdbc;','private final PersonnelDirectoryMapper mapper;').replace('MyBatisPersonnelDirectory(JdbcTemplate jdbc){this.jdbc=jdbc;}','MyBatisPersonnelDirectory(PersonnelDirectoryMapper mapper){this.mapper=mapper;}')
s=s.rstrip()[:-1]+''' private static String text(Map<String,Object> row,String key){return Objects.toString(row.get(key),"");}
 private static boolean flag(Map<String,Object> row,String key){Object value=row.get(key);return Boolean.TRUE.equals(value)||value instanceof Number number&&number.intValue()!=0;}
 private static String day(Object value){if(value==null)return "";if(value instanceof java.sql.Timestamp time)return time.toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate().toString();return value.toString().substring(0,10);}
}\n'''
(root/'MyBatisPersonnelDirectory.java').write_text(s,encoding='utf-8');p.unlink()
