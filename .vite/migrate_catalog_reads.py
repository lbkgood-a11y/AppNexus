from pathlib import Path
import re
root=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata/adapter/jdbc');p=root/'JdbcProductCatalogReads.java';s=p.read_text(encoding='utf-8')
start=s.index('  String table=switch(kind)');end=s.index('\n }',start)
s=s[:start]+'''  if(!Set.of("category","unit").contains(kind))throw invalid();
  return Map.of("rows",mapper.options(tenantBytes(c),kind,keyword,size,(page-1)*size),"total",mapper.optionCount(tenantBytes(c),kind,keyword));'''+s[end:]
pat=re.compile(r'jdbc\.(queryForList|queryForObject)\(("[^"]+")(\+\(locks\?" FOR SHARE":""\))?,((?:[^();]|\([^()]*\))*)\)')
entries=list(pat.finditer(s));assert len(entries)==9,len(entries)
names=['configurationVersions','configurationCount','configuration','fields','fieldOptions','validateCategory','validateConfiguration','validateTemplateStates','validateUnit'];methods=[];tables={'md_product_category','md_product_unit','md_product_category_template'}
keys={'tenant(c)':'tenant','field.get("revisionId")':'revision'}
types={'tenant':'byte[]','page':'int','size':'int','(page-1)*size':'int'}
for name,m in zip(names,entries):
 call,raw,dynamic,args=m.groups();args=[x.strip() for x in args.split(',')]
 if call=='queryForObject':ret=args.pop(0).replace('.class','')
 else:ret='List<Map<String,Object>>'
 sql=raw[1:-1];unique=list(dict.fromkeys(args));params=[]
 for a in args:
  k=keys.get(a,'offset' if a=='(page-1)*size' else a);sql=sql.replace('?', '#{__verifiedTenant}' if k=='tenant' else '#{'+k+'}',1)
 sql=sql.replace('UUID_TO_BIN(#{__verifiedTenant})','#{__verifiedTenant}')
 tables.update(re.findall(r'(?i)(?:FROM|JOIN)\s+(md_\w+)',sql))
 for a in unique:
  k=keys.get(a,'offset' if a=='(page-1)*size' else a);typ='byte[]' if k=='tenant' else 'int' if k in ['offset','size'] else 'Object' if a.startswith('field.get') else 'String';params.append('@Param("'+('__verifiedTenant' if k=='tenant' else k)+'") '+typ+' '+k)
 if dynamic:params.append('@Param("locks") boolean locks')
 ann='@Select({"<script>","'+sql+'","<if test=\'locks\'> FOR SHARE</if>","</script>"})' if dynamic else '@Select("'+sql+'")'
 methods.append(' /** '+name+' 固定配置及引用查询；可信租户限定，锁顺序沿用原用例。 */\n '+ann+'\n '+ret+' '+name+'('+','.join(params)+');')
 s=s.replace(m.group(0),'mapper.'+name+'('+','.join('tenantBytes(c)' if a=='tenant(c)' else a for a in unique)+(',locks' if dynamic else '')+')',1)
templateSql=re.search(r'jdbc.query\("([^"]+)"',s).group(1).replace('UUID_TO_BIN(?)','#{__verifiedTenant}',1).replace('UUID_TO_BIN(?)','UUID_TO_BIN(#{config})',1)
methods.append(''' /** @param tenant 可信租户二进制UUID @param config 明确配置版本UUID @return 该配置固定模板绑定 */
 @Select("'''+templateSql+'''")
 List<Map<String,Object>> templates(@Param("__verifiedTenant") byte[] tenant,@Param("config") String config);''')
s=re.sub(r'jdbc.query\("[^"]+",rs->\{slots.put\(rs.getString\("purpose"\),rs.getString\("versionId"\)\);\},tenant\(c\),id\);','for(var template:mapper.templates(tenantBytes(c),id))slots.put((String)template.get("purpose"),template.get("versionId"));',s)
extra='''
 /** @param tenant 可信租户二进制UUID @param kind 已验证的category/unit @param keyword 最多200字字面包含 @param size 每页1–100条 @param offset 从0开始偏移 @return 同租户启用目录候选，不提供维护权限 */
 @Select({"<script>","SELECT BIN_TO_UUID(id) id,code,name",
 "<choose><when test=\"kind == 'category'\">,BIN_TO_UUID(current_config_id) configId FROM md_product_category</when><otherwise>,dimension_code dimensionCode,precision_scale precisionScale FROM md_product_unit</otherwise></choose>",
 "WHERE tenant_id=#{__verifiedTenant} AND enabled=TRUE AND (LOCATE(#{keyword},code)&gt;0 OR LOCATE(#{keyword},name)&gt;0) ORDER BY code,id LIMIT #{size} OFFSET #{offset}","</script>"})
 List<Map<String,Object>> options(@Param("__verifiedTenant") byte[] tenant,@Param("kind") String kind,@Param("keyword") String keyword,@Param("size") int size,@Param("offset") int offset);
 /** @param tenant 与候选列表同一可信租户 @param kind 固定category/unit @param keyword 与列表相同的过滤 @return 相同条件下总数 */
 @Select({"<script>","SELECT COUNT(*) FROM <choose><when test=\"kind == 'category'\">md_product_category</when><otherwise>md_product_unit</otherwise></choose>",
 "WHERE tenant_id=#{__verifiedTenant} AND enabled=TRUE AND (LOCATE(#{keyword},code)&gt;0 OR LOCATE(#{keyword},name)&gt;0)","</script>"})
 long optionCount(@Param("__verifiedTenant") byte[] tenant,@Param("kind") String kind,@Param("keyword") String keyword);
'''.replace('test="kind', 'test=\\"kind').replace("'category'\">", "'category'\\\">")
mapper='''package com.appnexus.masterdata.adapter.jdbc;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;
import com.appnexus.persistence.mybatis.TenantAggregateScoped;
/** 商品引用目录固定Mapper；上层先验证消费资格，所有多表关系限定同一可信租户。 */
@Mapper
@TenantAggregateScoped(requireTenantMatch=true,tables={'''+','.join('"'+t+'"' for t in sorted(tables))+'''},statements={'''+','.join('"'+n+'"' for n in names+['templates','options','optionCount'])+'''})
public interface ProductCatalogReadMapper {
'''+ '\n\n'.join(methods)+extra+'\n}\n'
(root/'ProductCatalogReadMapper.java').write_text(mapper,encoding='utf-8')
s=s.replace('import org.springframework.jdbc.core.JdbcTemplate;\n','').replace('JdbcProductCatalogReads','MyBatisProductCatalogReads').replace('private final JdbcTemplate jdbc;','private final ProductCatalogReadMapper mapper;').replace('@param jdbc 与写用例或只读预检共用数据源','@param mapper 与写用例或只读预检共用事务的固定Mapper').replace('public MyBatisProductCatalogReads(JdbcTemplate jdbc){this.jdbc=jdbc;}','public MyBatisProductCatalogReads(ProductCatalogReadMapper mapper){this.mapper=mapper;}')
s=s.rstrip()[:-1]+''' private static byte[] tenantBytes(VerifiedTenantContext c){var id=c.tenantId();return java.nio.ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();}
}\n'''
assert 'jdbc.' not in s
(root/'MyBatisProductCatalogReads.java').write_text(s,encoding='utf-8');p.unlink()
p=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata/product/ProductV1Service.java');s=p.read_text(encoding='utf-8').replace('private final JdbcTemplate jdbc;','private final com.appnexus.masterdata.adapter.jdbc.ProductCatalogReadMapper catalogMapper;\n private final JdbcTemplate jdbc;').replace('com.appnexus.masterdata.adapter.jdbc.ProductListMapper listMapper){this.listMapper=','com.appnexus.masterdata.adapter.jdbc.ProductListMapper listMapper,com.appnexus.masterdata.adapter.jdbc.ProductCatalogReadMapper catalogMapper){this.catalogMapper=catalogMapper;this.listMapper=').replace('new com.appnexus.masterdata.adapter.jdbc.JdbcProductCatalogReads(jdbc)','new com.appnexus.masterdata.adapter.jdbc.MyBatisProductCatalogReads(catalogMapper)').replace('* @param listMapper 列表、总数及属性筛选固定映射，不接受SQL片段','* @param listMapper 列表、总数及属性筛选固定映射，不接受SQL片段\n  * @param catalogMapper 已授权商品操作的引用目录与锁定预检映射');p.write_text(s,encoding='utf-8')
p=Path('backend/services/master-data-service/src/test/java/com/appnexus/masterdata/product/ProductCatalogMySqlTest.java');s=p.read_text(encoding='utf-8').replace('mapperConfig.addMapper(com.appnexus.masterdata.adapter.jdbc.ProductListMapper.class);','mapperConfig.addMapper(com.appnexus.masterdata.adapter.jdbc.ProductListMapper.class);\n   mapperConfig.addMapper(com.appnexus.masterdata.adapter.jdbc.ProductCatalogReadMapper.class);').replace('.getMapper(com.appnexus.masterdata.adapter.jdbc.ProductListMapper.class));','.getMapper(com.appnexus.masterdata.adapter.jdbc.ProductListMapper.class),new org.mybatis.spring.SqlSessionTemplate(mapperFactory.getObject()).getMapper(com.appnexus.masterdata.adapter.jdbc.ProductCatalogReadMapper.class));');p.write_text(s,encoding='utf-8')
p=Path('backend/architecture-tests/src/test/java/com/appnexus/platform/ServiceLayerBoundaryTest.java');s=p.read_text(encoding='utf-8').replace('adapter.jdbc.JdbcProductCatalogReads','adapter.jdbc.MyBatisProductCatalogReads');p.write_text(s,encoding='utf-8')
