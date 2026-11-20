from pathlib import Path
import re
root=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata/adapter/jdbc');p=root/'JdbcProductCompleteness.java';s=p.read_text(encoding='utf-8')
pat=re.compile(r'jdbc\.(queryForObject|queryForList|update)\("([^"]+)",((?:[^();]|\([^()]*\))*)\)')
entries=list(pat.finditer(s));assert len(entries)==5,len(entries)
names=['serviceUnitRequired','missingAttributes','specificationCount','components','refresh'];methods=[];tables=set()
for name,m in zip(names,entries):
 call,sql,args=m.groups();args=[x.strip() for x in args.split(',')]
 if call=='queryForObject':ret=args.pop(0).replace('.class','')
 elif call=='queryForList' and args[0]=='String.class':ret='List<String>';args.pop(0)
 elif call=='queryForList':ret='List<Map<String,Object>>'
 else:ret='int'
 unique=list(dict.fromkeys(args));keys={'tenant(c)':'tenant','actor(c)':'actor','row.get("configId")':'config','row.get("id")':'product'}
 params=[]
 for a in args:
  k=keys.get(a,a);sql=sql.replace('?', '#{__verifiedTenant}' if k=='tenant' else '#{'+k+'}',1)
 sql=sql.replace('UUID_TO_BIN(#{__verifiedTenant})','#{__verifiedTenant}')
 tables.update(re.findall(r'(?i)(?:FROM|JOIN|UPDATE)\s+(md_\w+)',sql))
 for a in unique:
  k=keys.get(a,a);params.append('@Param("'+('__verifiedTenant' if k=='tenant' else k)+'") '+('byte[]' if k=='tenant' else 'Object' if a.startswith('row.get') else 'String')+' '+k)
 ann='Select' if call.startswith('query') else 'Update'
 methods.append(' /** '+name+' 固定查询或状态更新；在调用方商品聚合授权和事务内执行，不扩大字段权限。 */\n @'+ann+'("'+sql+'")\n '+ret+' '+name+'('+','.join(params)+');')
 s=s.replace(m.group(0),'mapper.'+name+'('+','.join('tenantBytes(c)' if a=='tenant(c)' else a for a in unique)+')',1)
mapper='''package com.appnexus.masterdata.adapter.jdbc;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;
import com.appnexus.persistence.mybatis.TenantAggregateScoped;
/** 商品完整性事实固定Mapper；只访问主数据拥有的聚合与配置表，不提供动态SQL。 */
@Mapper
@TenantAggregateScoped(tables={'''+','.join('"'+t+'"' for t in sorted(tables))+'''},statements={'''+','.join('"'+n+'"' for n in names)+'''})
public interface ProductCompletenessMapper {
'''+ '\n\n'.join(methods)+'\n}\n'
(root/'ProductCompletenessMapper.java').write_text(mapper,encoding='utf-8')
s=s.replace('import org.springframework.jdbc.core.JdbcTemplate;\n','').replace('JdbcProductCompleteness','MyBatisProductCompleteness').replace('private final JdbcTemplate jdbc;','private final ProductCompletenessMapper mapper;').replace('@param jdbc 父事务数据源','@param mapper 父事务固定SQL映射').replace('public MyBatisProductCompleteness(JdbcTemplate jdbc,ProductRecords records){this.jdbc=jdbc;','public MyBatisProductCompleteness(ProductCompletenessMapper mapper,ProductRecords records){this.mapper=mapper;')
s=s.rstrip()[:-1]+''' private static byte[] tenantBytes(VerifiedTenantContext c){var id=c.tenantId();return java.nio.ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();}
}\n'''
(root/'MyBatisProductCompleteness.java').write_text(s,encoding='utf-8');p.unlink()
p=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata/product/ProductV1Service.java');s=p.read_text(encoding='utf-8')
s=s.replace('private final JdbcTemplate jdbc;','private final com.appnexus.masterdata.adapter.jdbc.ProductCompletenessMapper completenessMapper;\n private final JdbcTemplate jdbc;')
s=s.replace('com.appnexus.masterdata.application.product.ProductRecords records){this.jdbc=jdbc;','com.appnexus.masterdata.application.product.ProductRecords records,com.appnexus.masterdata.adapter.jdbc.ProductCompletenessMapper completenessMapper){this.completenessMapper=completenessMapper;this.jdbc=jdbc;').replace('new com.appnexus.masterdata.adapter.jdbc.JdbcProductCompleteness(jdbc,records)','new com.appnexus.masterdata.adapter.jdbc.MyBatisProductCompleteness(completenessMapper,records)')
s=s.replace('* @param records 父记录输出端口，负责租户、行锁、CAS 与数据库审计时间','* @param records 父记录输出端口，负责租户、行锁、CAS 与数据库审计时间\n  * @param completenessMapper 已授权聚合内读取完整性事实的固定映射，与记录端口同事务')
p.write_text(s,encoding='utf-8')
p=Path('backend/services/master-data-service/src/test/java/com/appnexus/masterdata/product/ProductCatalogMySqlTest.java');s=p.read_text(encoding='utf-8').replace('mapperConfig.addMapper(com.appnexus.masterdata.adapter.jdbc.ProductRecordMapper.class);','mapperConfig.addMapper(com.appnexus.masterdata.adapter.jdbc.ProductRecordMapper.class);\n   mapperConfig.addMapper(com.appnexus.masterdata.adapter.jdbc.ProductCompletenessMapper.class);')
s=s.replace('.getMapper(com.appnexus.masterdata.adapter.jdbc.ProductRecordMapper.class)));','.getMapper(com.appnexus.masterdata.adapter.jdbc.ProductRecordMapper.class)),new org.mybatis.spring.SqlSessionTemplate(mapperFactory.getObject()).getMapper(com.appnexus.masterdata.adapter.jdbc.ProductCompletenessMapper.class));')
p.write_text(s,encoding='utf-8')
p=Path('backend/architecture-tests/src/test/java/com/appnexus/platform/ServiceLayerBoundaryTest.java');s=p.read_text(encoding='utf-8').replace('adapter.jdbc.JdbcProductCompleteness','adapter.jdbc.MyBatisProductCompleteness');p.write_text(s,encoding='utf-8')
