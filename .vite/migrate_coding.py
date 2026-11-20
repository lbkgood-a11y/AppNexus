from pathlib import Path
import re,subprocess
root=Path('backend/services/platform-service/adapters/persistence/src/main/java/com/appnexus/platform/persistence/businessobject/coding')
p=root/'JdbcCodingStore.java';s=subprocess.check_output(['git','show','HEAD:'+p.as_posix()]).decode('utf-8')
pat=re.compile(r'jdbc\.(queryForList|queryForObject|update)\(("""[\s\S]*?"""|"[^"\n]+"),((?:[^();]|\([^()]*\))*)\)')
entries=list(pat.finditer(s));assert len(entries)==16,len(entries)
names=['configurations','options','lock','hasIssues','insertEncoder','updateEncoder','rules','latestRuleVersion','rule','insertRule','issued','now','ensureCounter','sequence','advanceCounter','insertIssue']
methods=[]
for i,m in enumerate(entries):
 call,raw,args=m.groups();name=names[i]
 args=[x.strip() for x in args.split(',')]
 if call=='queryForObject':typ=args.pop(0).replace('.class','')
 elif call=='queryForList' and args[0]=='Long.class':typ='List<Long>';args.pop(0)
 elif call=='queryForList':typ='List<Map<String,Object>>'
 else:typ='int'
 sql=raw[3:-3].strip() if raw.startswith('"""') else raw[1:-1]
 sql=' '.join(sql.split())
 params=[];values=[]
 for j,arg in enumerate(args):
  key={'c.tenantId()':'tenant','c.actorId()':'actor','c.requestId()':'request','write(rule)':'ruleJson'}.get(arg,arg)
  javaType='long' if arg in ['ruleVersion','version','next'] else 'boolean' if arg=='enabled' else 'String'
  if key not in values:params.append('@Param("'+key+'") '+javaType+' '+key);values.append(key)
  sql=sql.replace('?', '#{'+key+'}',1)
 assert '?' not in sql,(name,sql)
 annotation='Select' if call.startswith('query') else 'Insert' if sql.startswith('INSERT') else 'Update'
 methods.append('  /** '+name+' 固定存储操作；租户、锁、版本及事务沿用编码端口契约。 */\n  @'+annotation+'("'+sql+'")\n  '+typ+' '+name+'('+','.join(params)+');')
 uniqueArgs=list(dict.fromkeys(args))
 replacement='mapper.'+name+'('+','.join(uniqueArgs)+')'
 s=s[:m.start()] if False else s
 # 替换原调用的精确文本，防止嵌套方法参数被截断。
 original=m.group(0)
 s=s.replace(original,replacement,1)
assert 'jdbc.query' not in s and 'jdbc.update' not in s
mapper='''package com.appnexus.platform.persistence.businessobject.coding;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;
import com.appnexus.platform.persistence.PlatformScoped;

/** 编码规则、绑定、流水及回执固定 SQL；调用方先通过编码用例准入，不向管理端开放 SQL。 */
@Mapper
@PlatformScoped(tables={"business_object_encoder","business_object_code_issue","business_object_code_counter","coding_rule_version"})
public interface CodingMapper {
'''+ '\n\n'.join(methods)+'\n}\n'
(root/'CodingMapper.java').write_text(mapper,encoding='utf-8')
s=s.replace('import org.springframework.jdbc.core.JdbcTemplate;\n','').replace('JdbcCodingStore','MyBatisCodingStore').replace('private final JdbcTemplate jdbc;','private final CodingMapper mapper;').replace('@param jdbc UTC 数据源 @param manager 与 jdbc 同一数据源的事务管理器，禁止独立提交流水','@param mapper 固定编码 Mapper @param manager 与 Mapper 同一数据源的事务管理器，禁止独立提交流水').replace('public MyBatisCodingStore(JdbcTemplate jdbc,PlatformTransactionManager manager){this.jdbc=jdbc;','public MyBatisCodingStore(CodingMapper mapper,PlatformTransactionManager manager){this.mapper=Objects.requireNonNull(mapper);')
(root/'MyBatisCodingStore.java').write_text(s,encoding='utf-8')
if p.exists():p.unlink()

