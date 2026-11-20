from pathlib import Path
import re
ns={};source=Path('.vite/extract_literal_mappers.py').read_text(encoding='utf-8');exec(source[:source.index('targets=')],ns);split_args=ns['split_args']
root=Path('backend/services/platform-service/adapters/persistence/src/main/java/com/appnexus/platform/persistence/configuration')
entries=[];files={}
for name in ['BusinessValueDefaults','DictionaryStandardMigration']:
 p=root/(name+'.java');s=p.read_text(encoding='utf-8');matches=[]
 for m in re.finditer(r'jdbc\.(queryForList|queryForObject|update)\(',s):
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
  if not re.fullmatch(r'"(?:[^"\\]|\\.)*"(?:\s*\+\s*"(?:[^"\\]|\\.)*")*',raw):continue
  sql=''.join(re.findall(r'"((?:[^"\\]|\\.)*)"',raw));call=m.group(1);ret='int'
  if call=='queryForObject':ret=args.pop(0)[:-6]
  elif call=='queryForList':
   ret='List<Map<String,Object>>'
   if args and args[0].endswith('.class'):ret='List<'+args.pop(0)[:-6]+'>'
  if sql.count('?')!=len(args):continue
  n=len(entries);entries.append((sql,args,ret,call));matches.append((m.start(),i,n,args))
 for start,end,n,args in reversed(matches):s=s[:start]+'mapper.statement'+str(n)+'('+','.join(args)+')'+s[end:]
 files[p]=s
methods=[];tables={'platform_tenant'}
for n,(sql,args,ret,call) in enumerate(entries):
 tables.update(re.findall(r'(?i)(?:FROM|JOIN|INTO|UPDATE)\s+([a-z][a-z0-9_.]+)',sql))
 for j in range(len(args)):sql=sql.replace('?', '#{p'+str(j)+'}',1)
 ann='Select' if call.startswith('query') else 'Insert' if sql.startswith('INSERT') else 'Update'
 methods.append(' /** 历史数据转换固定语句'+str(n)+'；原资源、校验和及整批事务保持，不供业务HTTP调用。 */\n @'+ann+'("'+sql+'")\n '+ret+' statement'+str(n)+'('+','.join('@Param("p'+str(j)+'") Object p'+str(j) for j in range(len(args)))+');')
(root/'HistoricalDictionaryMapper.java').write_text('''package com.appnexus.platform.persistence.configuration;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** 仅历史Flyway隔离连接使用，不注册为运行期Mapper，不提供业务入口。 */
public interface HistoricalDictionaryMapper {
 @Select("SELECT BIN_TO_UUID(id) FROM platform_tenant ORDER BY id") List<String> tenants();
'''+ '\n\n'.join(methods)+'\n}\n',encoding='utf-8')
for p,s in files.items():
 s=s.replace('import org.springframework.jdbc.core.JdbcTemplate;','').replace('JdbcTemplate jdbc','HistoricalDictionaryMapper mapper').replace('(jdbc,','(mapper,').replace('@param jdbc','@param mapper');p.write_text(s,encoding='utf-8')
p=root/'WarehouseValueDefaults.java';s=p.read_text(encoding='utf-8').replace('import org.springframework.jdbc.core.JdbcTemplate;','').replace('JdbcTemplate jdbc','HistoricalDictionaryMapper mapper').replace('BusinessValueDefaults.add(jdbc,','BusinessValueDefaults.add(mapper,').replace('@param jdbc','@param mapper');p.write_text(s,encoding='utf-8')
