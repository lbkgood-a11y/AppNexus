from pathlib import Path
import re
root=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata/adapter/jdbc');p=root/'JdbcProductRecords.java';s=p.read_text(encoding='utf-8')
sql=re.search(r'jdbc.query\("([^"]+)"\+\(lock\?" FOR UPDATE":""\)',s).group(1).replace('tenant_id=UUID_TO_BIN(?)','tenant_id=#{__verifiedTenant}').replace('id=UUID_TO_BIN(?)','id=UUID_TO_BIN(#{id})')
mapper='''package com.appnexus.masterdata.adapter.jdbc;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;
import com.appnexus.persistence.mybatis.TenantAggregateScoped;
/** 商品父记录固定Mapper；租户由可信请求覆盖，锁和CAS参与原商品聚合事务。 */
@Mapper
@TenantAggregateScoped(tables={"md_product_v1"},statements={"load","advance"})
public interface ProductRecordMapper {
  /** @param tenant 可信租户二进制UUID @param id 商品UUID @param lock 写用例是否加FOR UPDATE锁，不向客户端开放 @return 当前租户父记录；未匹配为空 */
  @Select({"<script>","'''+sql+'''","<if test='lock'> FOR UPDATE</if>","</script>"})
  List<Map<String,Object>> load(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("lock") boolean lock);
  /** @param tenant 可信租户二进制UUID @param id 已锁定的商品UUID @param actor 可信实际账号UUID @param version 当前非负版本，CAS必须匹配 @return 成功更新行数，非1整笔回滚 */
  @Update("UPDATE md_product_v1 SET version=version+1,updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) AND version=#{version}")
  int advance(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("actor") String actor,@Param("version") long version);
}
'''
(root/'ProductRecordMapper.java').write_text(mapper,encoding='utf-8')
start=s.index('  var rows=jdbc.query(');end=s.index('  if(rows.isEmpty())',start)
body='''  var rows=mapper.load(tenantBytes(c),id,lock).stream().map(r->{
   var m=new LinkedHashMap<String,Object>();
   for(String col:List.of("id","code","name","shortName","description","brand","origin","categoryId","configId","productForm","specificationMode","unitId","status","structureState","legacySpecificationOriginal","legacyDisposition","barcode","legacyUnitOriginal","createdBy","updatedBy"))m.put(col,r.get(col)==null?null:r.get(col).toString());
   for(String col:List.of("archivedAt","createdAt","updatedAt"))m.put(col,utc(r.get(col)));
   m.put("legacySourceVersion",r.get("legacySourceVersion"));m.put("legacyEnabled",r.get("legacyEnabled"));m.put("version",((Number)r.get("version")).longValue());m.put("structureRevision",((Number)r.get("structureRevision")).longValue());return m;
  }).toList();
'''
s=s[:start]+body+s[end:]
s=re.sub(r'jdbc.update\("UPDATE md_product_v1[^\n]+?,c.accountId\(\).toString\(\),c.tenantId\(\).toString\(\),id,version\)', 'mapper.advance(tenantBytes(c),id,c.accountId().toString(),version)',s)
s=s.replace('import org.springframework.jdbc.core.JdbcTemplate;\n','').replace('JdbcProductRecords','MyBatisProductRecords').replace('private final JdbcTemplate jdbc;','private final ProductRecordMapper mapper;').replace('@param jdbc 与商品扩展共用的 UTC 数据源，不创建独立事务','@param mapper 与商品扩展共用SqlSession及UTC数据源的固定Mapper，不创建独立事务').replace('public MyBatisProductRecords(JdbcTemplate jdbc){this.jdbc=jdbc;}','public MyBatisProductRecords(ProductRecordMapper mapper){this.mapper=Objects.requireNonNull(mapper);}')
s=s.rstrip()[:-1]+''' /** UTC数据库时间保持原ISO瞬时值，不采用宿主默认时区。 */
 private static String utc(Object value){if(value==null)return null;if(value instanceof java.sql.Timestamp t)return t.toInstant().toString();if(value instanceof java.time.LocalDateTime t)return t.toInstant(java.time.ZoneOffset.UTC).toString();throw new IllegalStateException("PRODUCT_TIMESTAMP_INVALID");}
 private static byte[] tenantBytes(VerifiedTenantContext c){var id=c.tenantId();return java.nio.ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();}
}\n'''
assert 'jdbc.' not in s
(root/'MyBatisProductRecords.java').write_text(s,encoding='utf-8');p.unlink()
p=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata/MasterDataConfiguration.java');s=p.read_text(encoding='utf-8').replace('productRecords(JdbcTemplate jdbc){return new com.appnexus.masterdata.adapter.jdbc.JdbcProductRecords(jdbc);}','productRecords(com.appnexus.masterdata.adapter.jdbc.ProductRecordMapper mapper){return new com.appnexus.masterdata.adapter.jdbc.MyBatisProductRecords(mapper);}')
p.write_text(s,encoding='utf-8')
p=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata/product/ProductV1Service.java');s=p.read_text(encoding='utf-8');s=re.sub(r' /\*\* @param jdbc 宿主UTC数据源[^\n]*\*/\n public ProductV1Service\(JdbcTemplate jdbc,[^\n]+\n','',s);p.write_text(s,encoding='utf-8')
p=Path('backend/services/master-data-service/src/test/java/com/appnexus/masterdata/product/ProductCatalogMySqlTest.java');s=p.read_text(encoding='utf-8').replace('mapperConfig.addMapper(com.appnexus.masterdata.adapter.jdbc.UnitConversionMapper.class);','mapperConfig.addMapper(com.appnexus.masterdata.adapter.jdbc.UnitConversionMapper.class);\n   mapperConfig.addMapper(com.appnexus.masterdata.adapter.jdbc.ProductRecordMapper.class);').replace('new ProductV1Service(jdbc,allow,manager,proofs)','new ProductV1Service(jdbc,allow,manager,proofs,new com.appnexus.masterdata.adapter.jdbc.MyBatisProductRecords(new org.mybatis.spring.SqlSessionTemplate(mapperFactory.getObject()).getMapper(com.appnexus.masterdata.adapter.jdbc.ProductRecordMapper.class)))');p.write_text(s,encoding='utf-8')
p=Path('backend/architecture-tests/src/test/java/com/appnexus/platform/ServiceLayerBoundaryTest.java');s=p.read_text(encoding='utf-8').replace('adapter.jdbc.JdbcProductRecords','adapter.jdbc.MyBatisProductRecords');p.write_text(s,encoding='utf-8')
