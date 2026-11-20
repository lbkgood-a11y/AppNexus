from pathlib import Path
import re
source=Path('.vite/extract_literal_mappers.py').read_text(encoding='utf-8');ns={};exec(source[:source.index('targets=')],ns);split_args=ns['split_args']
root=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata/adapter/jdbc');p=root/'JdbcProductAttributes.java';s=p.read_text(encoding='utf-8');entries=[]
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
 args=split_args(s[m.end():i-1]);raw=args.pop(0);assert re.fullmatch(r'"[^\n]+"',raw);sql=raw[1:-1];call=m.group(1);ret='int'
 if call=='queryForObject':ret=args.pop(0)[:-6]
 elif call=='queryForList':ret='List<Map<String,Object>>'
 assert sql.count('?')==len(args);entries.append((m.start(),i,sql,args,ret,call))
methods=[];tables=set()
for n,(start,end,sql,args,ret,call) in enumerate(entries):
 tables.update(re.findall(r'(?i)(?:FROM|JOIN|INTO|UPDATE)\s+([a-z][a-z0-9_]+)',sql))
 for j in range(len(args)):sql=sql.replace('?', '#{p'+str(j)+'}',1)
 ann='Select' if call.startswith('query') else 'Delete' if sql.startswith('DELETE') else 'Insert'
 methods.append(' /** 固定属性语句'+str(n+1)+'；父锁、值校验、原创建审计及整笔回滚契约保持。 */\n @'+ann+'("'+sql+'")\n '+ret+' statement'+str(n+1)+'('+','.join('@Param("p'+str(j)+'") Object p'+str(j) for j in range(len(args)))+');')
for n,(start,end,sql,args,ret,call) in reversed(list(enumerate(entries))):s=s[:start]+'mapper.statement'+str(n+1)+'('+','.join(args)+')'+s[end:]
(root/'ProductAttributeValueMapper.java').write_text('''package com.appnexus.masterdata.adapter.jdbc;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** 已授权商品聚合属性固定SQL，不向外提供任意SQL或技术表接口。 */
@Mapper
@PlatformScoped(tables={'''+','.join('"'+t+'"' for t in sorted(tables))+'''})
public interface ProductAttributeValueMapper {
'''+ '\n\n'.join(methods)+'\n}\n',encoding='utf-8')
s=s.replace('import org.springframework.jdbc.core.JdbcTemplate;','').replace('JdbcProductAttributes','MyBatisProductAttributes').replace('private final JdbcTemplate jdbc;','private final ProductAttributeValueMapper mapper;').replace('MyBatisProductAttributes(JdbcTemplate jdbc){this.jdbc=jdbc;}','MyBatisProductAttributes(ProductAttributeValueMapper mapper){this.mapper=mapper;}').replace('@param jdbc','@param mapper');(root/'MyBatisProductAttributes.java').write_text(s,encoding='utf-8');p.unlink()
p=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata/product/ProductV1Service.java');s=p.read_text(encoding='utf-8').replace('private final JdbcTemplate jdbc;','private final com.appnexus.masterdata.adapter.jdbc.ProductAttributeValueMapper attributeValues;\n private final JdbcTemplate jdbc;').replace('ProductV1ServiceSqlMapper statements){this.statements=','ProductV1ServiceSqlMapper statements,com.appnexus.masterdata.adapter.jdbc.ProductAttributeValueMapper attributeValues){this.attributeValues=attributeValues;this.statements=').replace('JdbcProductAttributes','MyBatisProductAttributes').replace('new com.appnexus.masterdata.adapter.jdbc.MyBatisProductAttributes(jdbc)','new com.appnexus.masterdata.adapter.jdbc.MyBatisProductAttributes(attributeValues)');p.write_text(s,encoding='utf-8')
p=Path('backend/architecture-tests/src/test/java/com/appnexus/platform/ServiceLayerBoundaryTest.java');s=p.read_text(encoding='utf-8').replace('JdbcProductAttributes','MyBatisProductAttributes');p.write_text(s,encoding='utf-8')
