"""迁移固定字面量SQL调用；保留动态语句供人工逐条迁移，不创建任意SQL执行入口。"""
from pathlib import Path
import re
def split_args(text):
 result=[];start=0;depth=0;quote=None;i=0
 while i<len(text):
  ch=text[i]
  if quote:
   if ch=='\\':i+=2;continue
   if ch==quote:quote=None
  elif ch in '\"\'':quote=ch
  elif ch in '([{':depth+=1
  elif ch in ')]}':depth-=1
  elif ch==',' and depth==0:result.append(text[start:i].strip());start=i+1
  i+=1
 result.append(text[start:].strip());return result
targets=['TenantInvitations','IntegrationClients','PolicyTemplates','ReliableEvents','PartnerReferenceController','PartnerCallFilter','TaskManagementController']
root=Path('backend/services/platform-service/adapters/security/src/main/java/com/appnexus/platform/security')
for name in targets:
 p=root/(name+'.java');s=p.read_text(encoding='utf-8');matches=[]
 for m in re.finditer(r'jdbc\.(queryForList|queryForObject|update)\(',s):
  start=m.end();depth=1;i=start;quote=None
  while i<len(s) and depth:
   ch=s[i]
   if quote:
    if ch=='\\':i+=2;continue
    if ch==quote:quote=None
   elif ch in '\"\'':quote=ch
   elif ch=='(':depth+=1
   elif ch==')':depth-=1
   i+=1
  args=split_args(s[start:i-1]);literal=args.pop(0)
  if not re.fullmatch(r'"(?:[^"\\]|\\.)*"',literal):continue
  sql=literal[1:-1];call=m.group(1);ret='int'
  if call=='queryForObject':
   if not args or not args[0].endswith('.class'):continue
   ret=args.pop(0)[:-6]
  elif call=='queryForList':
   ret='List<Map<String,Object>>'
   if args and args[0].endswith('.class'):ret='List<'+args.pop(0)[:-6]+'>'
  if len(args)!=sql.count('?') or any('.toArray(' in a for a in args):continue
  matches.append((m.start(),i,sql,args,ret,call))
 if not matches:continue
 mapper=name+'SqlMapper';tables=set();methods=[]
 for index,(start,end,sql,args,ret,call) in enumerate(matches):
  tables.update(re.findall(r'(?i)(?:FROM|JOIN|INTO|UPDATE)\s+([a-z][a-z0-9_]+)',sql));method='statement'+str(index+1)
  # 精确参数位置保持原调用，不合并null或表达式，不接收SQL文本。
  for j in range(len(args)):sql=sql.replace('?', '#{p'+str(j)+'}',1)
  annotation='Select' if call.startswith('query') else 'Insert' if sql.upper().startswith('INSERT') else 'Delete' if sql.upper().startswith('DELETE') else 'Update'
  params=','.join('@Param("p'+str(j)+'") Object p'+str(j) for j in range(len(args)))
  doc=' /**\n  * '+method+' 为原'+name+'固定SQL，准入、事务、锁及业务规则仍由原用例执行。\n'+''.join('  * @param p'+str(j)+' 原调用第'+str(j+1)+'个绑定值，来自原已校验业务参数；不可作为SQL标识符\n' for j in range(len(args)))+'  * @return 原查询结果或写入行数；失败传播并回滚原业务事务\n  */'
  methods.append(doc+'\n @'+annotation+'("'+sql+'")\n '+ret+' '+method+'('+params+');')
 for index,(start,end,sql,args,ret,call) in reversed(list(enumerate(matches))):s=s[:start]+'statements.statement'+str(index+1)+'('+','.join(args)+')'+s[end:]
 declaration='''package com.appnexus.platform.security;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** '''+name+'''固定SQL映射；没有接收SQL、表名或列名的通用执行入口。 */
@Mapper
@PlatformScoped(tables={'''+','.join('"'+t+'"' for t in sorted(tables))+'''})
public interface '''+mapper+''' {
'''+ '\n\n'.join(methods)+'\n}\n'
 (root/(mapper+'.java')).write_text(declaration,encoding='utf-8')
 # 暂保留其他JDBC/框架原生调用；注入固定Mapper，不通过容器回查。
 declarationPosition=s.index('{',s.index('class '+name))+1
 s=s[:declarationPosition]+'\n    private final '+mapper+' statements;\n'+s[declarationPosition:]
 constructors=list(re.finditer(r'public '+name+r'\(([^)]*)\)\s*\{',s))
 assert len(constructors)==1,(name,len(constructors))
 m=constructors[0];s=s[:m.start()]+m.group(0).replace(')',','+mapper+' statements)',1)+'this.statements=statements;'+s[m.end():]
 if not re.search(r'\bjdbc\.',s.replace('import org.springframework.jdbc.core.JdbcTemplate;','')):
  s=s.replace('import org.springframework.jdbc.core.JdbcTemplate;','');s=re.sub(r'(?:private\s+)?final JdbcTemplate jdbc;','',s).replace('this.jdbc=jdbc;','').replace('JdbcTemplate jdbc,','').replace(',JdbcTemplate jdbc)',' )')
 p.write_text(s,encoding='utf-8');print(name,len(matches),'remaining jdbc calls',len(re.findall(r'\bjdbc\.',s)))
