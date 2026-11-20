from pathlib import Path
import re
ns={};source=Path('.vite/extract_literal_mappers.py').read_text(encoding='utf-8');exec(source[:source.index('targets=')],ns);split_args=ns['split_args']
root=Path('backend/services/workflow-service/adapters/flowable/src/main/java/com/appnexus/workflow/adapter/flowable');p=root/'WorkflowRuntime.java';s=p.read_text(encoding='utf-8');entries=[]
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
 entries.append((m.start(),i,sql,args,ret,call))
methods=[];tables=set()
for n,(start,end,sql,args,ret,call) in enumerate(entries):
 tables.update(re.findall(r'(?i)(?:FROM|JOIN|INTO|UPDATE)\s+([a-z][a-z0-9_]+)',sql))
 for j in range(len(args)):sql=sql.replace('?', '#{p'+str(j)+'}',1)
 ann='Select' if call.startswith('query') else 'Insert' if sql.upper().startswith('INSERT') else 'Delete' if sql.upper().startswith('DELETE') else 'Update'
 methods.append(' /** 固定工作流语句'+str(n+1)+'；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */\n @'+ann+'("'+sql+'")\n '+ret+' statement'+str(n+1)+'('+','.join('@Param("p'+str(j)+'") Object p'+str(j) for j in range(len(args)))+');')
for n,(start,end,sql,args,ret,call) in reversed(list(enumerate(entries))):s=s[:start]+'statements.statement'+str(n+1)+'('+','.join(args)+')'+s[end:]
(root/'WorkflowRecordMapper.java').write_text('''package com.appnexus.workflow.adapter.flowable;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** 工作流应用自有表固定映射；后台交付保持可信实例和幂等复核，不接收任意SQL。 */
@Mapper
@PlatformScoped(tables={'''+','.join('"'+t+'"' for t in sorted(tables))+'''})
public interface WorkflowRecordMapper {
'''+ '\n\n'.join(methods)+'\n}\n',encoding='utf-8')
s=s.replace('private final JdbcTemplate jdbc;','private final WorkflowRecordMapper statements;').replace('JdbcTemplate jdbc,','WorkflowRecordMapper statements,').replace('this.jdbc = jdbc;','this.statements = statements;').replace('import org.springframework.jdbc.core.JdbcTemplate;','');p.write_text(s,encoding='utf-8')
print('migrated',len(entries),'remaining',len(re.findall(r'\bjdbc\.',s)))
p=root/'WorkflowEngineConfiguration.java';s=p.read_text(encoding='utf-8');s=s.replace('new org.springframework.jdbc.core.JdbcTemplate(source),','statements,')
# 找到实际创建runtime的Bean，将Mapper参数加入该方法。
pos=s.index('new WorkflowRuntime(');before=s.rfind('public ',0,pos);brace=s.index('{',before);signature=s[before:brace];last=signature.rfind(')');s=s[:before]+signature[:last]+',WorkflowRecordMapper statements'+signature[last:]+s[brace:];p.write_text(s,encoding='utf-8')
p=Path('backend/applications/monolith-app/src/main/java/com/appnexus/platform/PlatformApplication.java');s=p.read_text(encoding='utf-8').replace('"com.appnexus.leave"}','"com.appnexus.leave","com.appnexus.workflow.adapter.flowable"}');p.write_text(s,encoding='utf-8')
