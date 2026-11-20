from pathlib import Path
import re
ns={};source=Path('.vite/extract_literal_mappers.py').read_text(encoding='utf-8');exec(source[:source.index('targets=')],ns);split_args=ns['split_args']
root=Path('backend/services/workflow-service/adapters/flowable/src/main/java/com/appnexus/workflow/adapter/flowable');p=root/'WorkflowRuntime.java';s=p.read_text(encoding='utf-8');entries=[]
for m in re.finditer(r'(?<!\w)one\(',s):
 if s[max(0,m.start()-25):m.start()].endswith(' Object> '):continue
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
 args=split_args(s[m.end():i-1]);raw=args.pop(0);lock=False
 if not raw.startswith('"'):continue
 if raw.endswith(' + (lock ? " FOR UPDATE" : "")'):raw=raw[:-len(' + (lock ? " FOR UPDATE" : "")')];lock=True
 assert re.fullmatch(r'"(?:[^"\\]|\\.)*"(?:\s*\+\s*"(?:[^"\\]|\\.)*")*',raw),raw
 sql=''.join(re.findall(r'"((?:[^"\\]|\\.)*)"',raw));assert sql.count('?')==len(args);entries.append((m.start(),i,sql,args,lock))
methods=[]
for n,(start,end,sql,args,lock) in enumerate(entries):
 for j in range(len(args)):sql=sql.replace('?', '#{p'+str(j)+'}',1)
 ann='@Select("'+sql+'")' if not lock else '@Select({"<script>","'+sql+'","<if test=\'lock\'> FOR UPDATE</if>","</script>"})'
 methods.append(' /** 固定唯一记录查询；无匹配不泄漏其他主体或租户，原用例保持参与者校验。 */\n '+ann+'\n List<Map<String,Object>> unique'+str(n)+'('+','.join('@Param("p'+str(j)+'") Object p'+str(j) for j in range(len(args)))+(',@Param("lock") boolean lock' if lock else '')+');')
for n,(start,end,sql,args,lock) in reversed(list(enumerate(entries))):s=s[:start]+'required(statements.unique'+str(n)+'('+','.join(args)+(',lock' if lock else '')+'))'+s[end:]
start=s.index('  private Map<String, Object> one(');end=s.index('\n  private WorkflowModel',start);s=s[:start]+'''  private Map<String,Object> required(List<Map<String,Object>> rows){if(rows.size()!=1)throw fail("WORKFLOW_NOT_FOUND",404);return rows.getFirst();}
'''+s[end:]
start=s.index('jdbc.queryForList(');end=s.index(';',start);s=s[:start]+'statements.publishedCandidates(c.tenantId(),bound==null?null:bound.definitionVersion())'+s[end:]
start=s.index('jdbc.queryForList(');end=s.index(';',start);s=s[:start]+'statements.actorTasks(c.tenantId(),c.actorId(),view.equals("todo"))'+s[end:]
assert not re.search(r'\bjdbc\.',s);p.write_text(s,encoding='utf-8')
methods+=[''' /** @param tenant 可信租户 @param version 已绑定不可变版本，null使用已发布指针 @return 本租户启用流程候选 */
 @Select({"<script>","SELECT v.*,d.id FROM wf_definition d JOIN wf_version v ON v.tenant_id=d.tenant_id AND v.definition_id=d.id AND <choose><when test='version == null'>v.version=d.published</when><otherwise>v.version=#{version}</otherwise></choose> WHERE d.tenant_id=#{tenant} AND d.enabled=TRUE FOR SHARE","</script>"})
 List<Map<String,Object>> publishedCandidates(@Param("tenant") String tenant,@Param("version") Integer version);''',''' /** @param tenant 可信租户 @param actor 可信实际账号 @param todo true仅待办，false仅已结束 @return 本人任务摘要，最多100条 */
 @Select({"<script>","SELECT t.*,i.object_type,i.object_id,i.object_version FROM wf_task t JOIN wf_instance i ON i.tenant_id=t.tenant_id AND i.id=t.instance_id WHERE t.tenant_id=#{tenant} AND t.actor=#{actor} AND t.state <choose><when test='todo'>='OPEN'</when><otherwise>IN('DONE','CANCELLED')</otherwise></choose> ORDER BY t.created_at DESC LIMIT 100","</script>"})
 List<Map<String,Object>> actorTasks(@Param("tenant") String tenant,@Param("actor") String actor,@Param("todo") boolean todo);''']
p=root/'WorkflowRecordMapper.java';s=p.read_text(encoding='utf-8');p.write_text(s.rstrip()[:-1]+'\n'+'\n\n'.join(methods)+'\n}\n',encoding='utf-8')
