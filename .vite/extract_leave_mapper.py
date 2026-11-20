from pathlib import Path
import re,runpy
# 复用参数解析函数定义，不执行其他文件迁移。
source=Path('.vite/extract_literal_mappers.py').read_text(encoding='utf-8');namespace={};exec(source[:source.index('targets=')],namespace);split_args=namespace['split_args']
root=Path('backend/services/leave-poc/src/main/java/com/appnexus/leave');p=root/'LeavePocService.java';s=p.read_text(encoding='utf-8');entries=[]
for m in re.finditer(r'jdbc\.(queryForList|queryForMap|update)\(',s):
 depth=1;i=m.end();quote=None
 while depth:
  c=s[i]
  if quote:
   if c=='\\':i+=2;continue
   if c==quote:quote=None
  elif c=='"':quote=c
  elif c=='(':depth+=1
  elif c==')':depth-=1
  i+=1
 args=split_args(s[m.end():i-1]);raw=args.pop(0);lock=False
 if raw.endswith(' + (lock ? " FOR UPDATE" : "")'):raw=raw[:-len(' + (lock ? " FOR UPDATE" : "")')];lock=True
 if not re.fullmatch(r'"(?:[^"\\]|\\.)*"(?:\s*\+\s*"(?:[^"\\]|\\.)*")*',raw):raise ValueError(raw)
 sql=''.join(re.findall(r'"((?:[^"\\]|\\.)*)"',raw));assert sql.count('?')==len(args),(sql,args)
 entries.append((m.start(),i,sql,args,m.group(1),lock))
methods=[];tables=set()
for index,(start,end,sql,args,call,lock) in enumerate(entries):
 tables.update(re.findall(r'(?i)(?:FROM|JOIN|INTO|UPDATE)\s+([a-z][a-z0-9_]+)',sql))
 for j in range(len(args)):sql=sql.replace('?', '#{p'+str(j)+'}',1)
 annotation='Select' if call.startswith('query') else 'Insert' if sql.upper().startswith('INSERT') else 'Update';ret='List<Map<String,Object>>' if call=='queryForList' else 'Map<String,Object>' if call=='queryForMap' else 'int'
 params=','.join('@Param("p'+str(j)+'") Object p'+str(j) for j in range(len(args)))+(',@Param("lock") boolean lock' if lock else '')
 ann='@'+annotation+'("'+sql+'")' if not lock else '@Select({"<script>","'+sql+'","<if test=\'lock\'> FOR UPDATE</if>","</script>"})'
 methods.append(' /** 固定请假存储语句'+str(index+1)+'；租户、本人归属、状态及任务准入由原用例复核，回执和状态保持同事务。 */\n '+ann+'\n '+ret+' statement'+str(index+1)+'('+params+');')
for index,(start,end,sql,args,call,lock) in reversed(list(enumerate(entries))):s=s[:start]+'statements.statement'+str(index+1)+'('+','.join(args)+(',lock' if lock else '')+')'+s[end:]
s=s.replace('import org.springframework.jdbc.core.JdbcTemplate;','').replace('private final JdbcTemplate jdbc;','private final LeaveRecordMapper statements;').replace('JdbcTemplate jdbc,','LeaveRecordMapper statements,').replace('this.jdbc = jdbc;','this.statements = statements;').replace('this.jdbc=jdbc;','this.statements=statements;').replace('this(jdbc,','this(statements,').replace('@param jdbc','@param statements')
assert not re.search(r'\bjdbc\b',s)
p.write_text(s,encoding='utf-8')
(root/'LeaveRecordMapper.java').write_text('''package com.appnexus.leave;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** 请假固定SQL映射；非HTTP审批终态也使用原可信实例与幂等校验，不伪造请求上下文。 */
@Mapper
@PlatformScoped(tables={'''+','.join('"'+t+'"' for t in sorted(tables))+'''})
public interface LeaveRecordMapper {
'''+ '\n\n'.join(methods)+'\n}\n',encoding='utf-8')
p=root/'LeavePocConfiguration.java';s=p.read_text(encoding='utf-8').replace('import org.springframework.jdbc.core.JdbcTemplate;','').replace('leavePocService(JdbcTemplate jdbc,','leavePocService(LeaveRecordMapper statements,').replace('new LeavePocService(jdbc,','new LeavePocService(statements,');p.write_text(s,encoding='utf-8')
p=Path('backend/applications/monolith-app/src/main/java/com/appnexus/platform/PlatformApplication.java');s=p.read_text(encoding='utf-8').replace('"com.appnexus.platform.security"}','"com.appnexus.platform.security","com.appnexus.leave"}');p.write_text(s,encoding='utf-8')
