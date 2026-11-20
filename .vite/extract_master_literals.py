from pathlib import Path
import re
source=Path('.vite/extract_literal_mappers.py').read_text(encoding='utf-8');namespace={};exec(source[:source.index('targets=')],namespace);split_args=namespace['split_args']
root=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata/product')
for name in ['ProductV1Service','ProductImageService','ProductStructureService','ProductApprovalService','ProductAttributeService','ProductCatalogService','ProductTemplateService']:
 p=root/(name+'.java');s=p.read_text(encoding='utf-8');entries=[]
 for m in re.finditer(r'jdbc\.(queryForList|queryForMap|queryForObject|update)\(',s):
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
  args=split_args(s[m.end():i-1]);raw=args.pop(0)
  if not re.fullmatch(r'"(?:[^"\\]|\\.)*"(?:\s*\+\s*"(?:[^"\\]|\\.)*")*',raw):continue
  sql=''.join(re.findall(r'"((?:[^"\\]|\\.)*)"',raw));call=m.group(1);ret='int'
  if call=='queryForObject':
   if not args or not args[0].endswith('.class'):continue
   ret=args.pop(0)[:-6]
  elif call=='queryForList':
   ret='List<Map<String,Object>>'
   if args and args[0].endswith('.class'):ret='List<'+args.pop(0)[:-6]+'>'
  elif call=='queryForMap':ret='Map<String,Object>'
  if sql.count('?')!=len(args) or any('.toArray(' in a for a in args):continue
  entries.append((m.start(),i,sql,args,ret,call))
 if not entries:continue
 mapper=name+'SqlMapper';methods=[];tables=set()
 for index,(start,end,sql,args,ret,call) in enumerate(entries):
  tables.update(re.findall(r'(?i)(?:FROM|JOIN|INTO|UPDATE)\s+([a-z][a-z0-9_]+)',sql))
  for j in range(len(args)):sql=sql.replace('?', '#{p'+str(j)+'}',1)
  ann='Select' if call.startswith('query') else 'Insert' if sql.upper().startswith('INSERT') else 'Delete' if sql.upper().startswith('DELETE') else 'Update'
  methods.append(' /** 固定业务语句'+str(index+1)+'；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */\n @'+ann+'("'+sql+'")\n '+ret+' statement'+str(index+1)+'('+','.join('@Param("p'+str(j)+'") Object p'+str(j) for j in range(len(args)))+');')
 for index,(start,end,sql,args,ret,call) in reversed(list(enumerate(entries))):s=s[:start]+'statements.statement'+str(index+1)+'('+','.join(args)+')'+s[end:]
 pos=s.index('{',s.index('class '+name))+1;s=s[:pos]+'\n private final '+mapper+' statements;\n'+s[pos:]
 constructors=list(re.finditer(r'public '+name+r'\(([^)]*)\)\s*\{',s));assert len(constructors)==1,(name,len(constructors))
 m=constructors[0];s=s[:m.start()]+m.group(0).replace(')',','+mapper+' statements)',1)+'this.statements=statements;'+s[m.end():]
 p.write_text(s,encoding='utf-8')
 out=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata/adapter/jdbc')/(mapper+'.java')
 # 使用全限定类型避免业务包读取持久化细节；当前原混层Service后续仍需继续迁出。
 s=p.read_text(encoding='utf-8').replace('package com.appnexus.masterdata.product;','package com.appnexus.masterdata.product;\nimport com.appnexus.masterdata.adapter.jdbc.'+mapper+';');p.write_text(s,encoding='utf-8')
 out.write_text('''package com.appnexus.masterdata.adapter.jdbc;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** '''+name+'''固定SQL映射；不接收SQL或标识符，原用例逐次验证可信租户及操作。 */
@Mapper
@PlatformScoped(tables={'''+','.join('"'+t+'"' for t in sorted(tables))+'''})
public interface '''+mapper+''' {
'''+ '\n\n'.join(methods)+'\n}\n',encoding='utf-8')
 print(name,len(entries),'remaining',len(re.findall(r'\bjdbc\.',s)))
