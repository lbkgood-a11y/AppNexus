from pathlib import Path
import re
root=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata/adapter/jdbc');p=root/'JdbcProductSnapshot.java';s=p.read_text(encoding='utf-8')
pat=re.compile(r'jdbc\.(queryForObject|queryForList|queryForMap)\("([^"]+)",((?:[^();]|\([^()]*\))*)\)')
entries=list(pat.finditer(s));assert len(entries)==11,len(entries)
names=['approvalPending','specifications','specificationValues','specificationChoices','skus','components','images','values','choices','categoryConfiguration','baseUnit'];methods=[];tables=set()
for name,m in zip(names,entries):
 call,sql,args=m.groups();args=[x.strip() for x in args.split(',')]
 if call=='queryForObject':ret=args.pop(0).replace('.class','')
 elif call=='queryForMap':ret='Map<String,Object>'
 else:ret='List<Map<String,Object>>'
 keys={'tenant(c)':'tenant','m.get("configId")':'config','m.get("unitId")':'unit'}
 for a in args:
  k=keys.get(a,a);sql=sql.replace('?', '#{__verifiedTenant}' if k=='tenant' else '#{'+k+'}',1)
 sql=sql.replace('UUID_TO_BIN(#{__verifiedTenant})','#{__verifiedTenant}')
 tables.update(re.findall(r'(?i)(?:FROM|JOIN)\s+(md_\w+)',sql));params=[]
 for a in args:
  k=keys.get(a,a);params.append('@Param("'+('__verifiedTenant' if k=='tenant' else k)+'") '+('byte[]' if k=='tenant' else 'Object' if a.startswith('m.get') else 'String')+' '+k)
 methods.append(' /** '+name+' 只读固定SQL；父记录已在同用例授权，所有关联仍强制同租户及父商品。 */\n @Select("'+sql+'")\n '+ret+' '+name+'('+','.join(params)+');')
 s=s.replace(m.group(0),'mapper.'+name+'('+','.join('tenantBytes(c)' if a=='tenant(c)' else a for a in args)+')',1)
templateSql=re.search(r'jdbc.query\("([^"]+)"',s).group(1).replace('UUID_TO_BIN(?)','#{__verifiedTenant}',1).replace('UUID_TO_BIN(?)','UUID_TO_BIN(#{config})',1)
tables.add('md_product_category_template')
methods.append(''' /** @param tenant 可信租户二进制UUID @param config 父商品已绑定的配置UUID @return 该版本的用途与模板绑定，不追随最新版本 */
 @Select("'''+templateSql+'''")
 List<Map<String,Object>> templates(@Param("__verifiedTenant") byte[] tenant,@Param("config") Object config);''')
s=re.sub(r'jdbc.query\("[^"]+",rs->\{slots.put\(rs.getString\("purpose"\),rs.getString\("versionId"\)\);\},tenant\(c\),m.get\("configId"\)\);','for(var template:mapper.templates(tenantBytes(c),m.get("configId")))slots.put((String)template.get("purpose"),template.get("versionId"));',s)
mapper='''package com.appnexus.masterdata.adapter.jdbc;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;
import com.appnexus.persistence.mybatis.TenantAggregateScoped;
/** 商品快照固定查询映射；不开放任意列名、表名、SQL或客户端事实输入。 */
@Mapper
@TenantAggregateScoped(tables={'''+','.join('"'+t+'"' for t in sorted(tables))+'''},statements={'''+','.join('"'+n+'"' for n in names+['templates'])+'''})
public interface ProductSnapshotMapper {
'''+ '\n\n'.join(methods)+'\n}\n'
(root/'ProductSnapshotMapper.java').write_text(mapper,encoding='utf-8')
s=s.replace('import org.springframework.jdbc.core.JdbcTemplate;\n','').replace('JdbcProductSnapshot','MyBatisProductSnapshot').replace('private final JdbcTemplate jdbc;','private final ProductSnapshotMapper mapper;').replace('@param jdbc 同一事务数据源','@param mapper 同一事务固定快照映射').replace('public MyBatisProductSnapshot(JdbcTemplate jdbc,','public MyBatisProductSnapshot(ProductSnapshotMapper mapper,').replace('{this.jdbc=jdbc;','{this.mapper=mapper;')
s=s.rstrip()[:-1]+''' private static byte[] tenantBytes(VerifiedTenantContext c){var id=c.tenantId();return java.nio.ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();}
}\n'''
assert 'jdbc.' not in s
(root/'MyBatisProductSnapshot.java').write_text(s,encoding='utf-8');p.unlink()
p=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata/product/ProductV1Service.java');s=p.read_text(encoding='utf-8')
s=s.replace('private final JdbcTemplate jdbc;','private final com.appnexus.masterdata.adapter.jdbc.ProductSnapshotMapper snapshotMapper;\n private final JdbcTemplate jdbc;').replace('com.appnexus.masterdata.adapter.jdbc.ProductCompletenessMapper completenessMapper){this.completenessMapper=','com.appnexus.masterdata.adapter.jdbc.ProductCompletenessMapper completenessMapper,com.appnexus.masterdata.adapter.jdbc.ProductSnapshotMapper snapshotMapper){this.snapshotMapper=snapshotMapper;this.completenessMapper=').replace('new com.appnexus.masterdata.adapter.jdbc.JdbcProductSnapshot(jdbc,','new com.appnexus.masterdata.adapter.jdbc.MyBatisProductSnapshot(snapshotMapper,')
s=s.replace('* @param completenessMapper 已授权聚合内读取完整性事实的固定映射，与记录端口同事务','* @param completenessMapper 已授权聚合内读取完整性事实的固定映射，与记录端口同事务\n  * @param snapshotMapper 父商品快照固定映射，不接收任意业务SQL')
p.write_text(s,encoding='utf-8')
p=Path('backend/services/master-data-service/src/test/java/com/appnexus/masterdata/product/ProductCatalogMySqlTest.java');s=p.read_text(encoding='utf-8').replace('mapperConfig.addMapper(com.appnexus.masterdata.adapter.jdbc.ProductCompletenessMapper.class);','mapperConfig.addMapper(com.appnexus.masterdata.adapter.jdbc.ProductCompletenessMapper.class);\n   mapperConfig.addMapper(com.appnexus.masterdata.adapter.jdbc.ProductSnapshotMapper.class);').replace('.getMapper(com.appnexus.masterdata.adapter.jdbc.ProductCompletenessMapper.class));','.getMapper(com.appnexus.masterdata.adapter.jdbc.ProductCompletenessMapper.class),new org.mybatis.spring.SqlSessionTemplate(mapperFactory.getObject()).getMapper(com.appnexus.masterdata.adapter.jdbc.ProductSnapshotMapper.class));');p.write_text(s,encoding='utf-8')
p=Path('backend/architecture-tests/src/test/java/com/appnexus/platform/ServiceLayerBoundaryTest.java');s=p.read_text(encoding='utf-8').replace('adapter.jdbc.JdbcProductSnapshot','adapter.jdbc.MyBatisProductSnapshot');p.write_text(s,encoding='utf-8')
