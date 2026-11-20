from pathlib import Path
root=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata/adapter/jdbc')
mapper='''package com.appnexus.masterdata.adapter.jdbc;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;
import com.appnexus.persistence.mybatis.TenantAggregateScoped;
/** 商品追加历史固定Mapper；业务快照授权、父CAS和历史在同一事务完成，不保存预检Token。 */
@Mapper
@TenantAggregateScoped(tables={"md_product_v1_change"},statements={"append","list","count"})
public interface ProductHistoryMapper {
 /** @param tenant 可信租户二进制UUID @param id 服务端生成历史UUID @param product 已授权商品UUID @param version 更新后的父版本 @param event 固定业务事件码 @param before 创建时可空的原JSON快照 @param after 已授权结果JSON快照 @param actor 可信实际账号UUID @return 追加行数，失败与父修改共同回滚 */
 @Insert("INSERT INTO md_product_v1_change(id,tenant_id,product_id,to_version,event_type,before_snapshot,after_snapshot,created_by,updated_by) VALUES(UUID_TO_BIN(#{id}),#{__verifiedTenant},UUID_TO_BIN(#{product}),#{version},#{event},#{before},#{after},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))")
 int append(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("product") String product,@Param("version") Object version,@Param("event") String event,@Param("before") String before,@Param("after") String after,@Param("actor") String actor);
 /** @param tenant 可信租户二进制UUID @param product 已通过原对象读取权限的商品UUID @param size 每页1至100条 @param offset 从0开始偏移 @return 同租户商品历史，按版本倒序 */
 @Select("SELECT BIN_TO_UUID(id) id,to_version version,event_type eventType,CAST(before_snapshot AS CHAR) beforeSnapshot,CAST(after_snapshot AS CHAR) afterSnapshot,schema_version schemaVersion,created_at createdAt,BIN_TO_UUID(created_by) createdBy FROM md_product_v1_change WHERE tenant_id=#{__verifiedTenant} AND product_id=UUID_TO_BIN(#{product}) ORDER BY to_version DESC LIMIT #{size} OFFSET #{offset}")
 List<Map<String,Object>> list(@Param("__verifiedTenant") byte[] tenant,@Param("product") String product,@Param("size") int size,@Param("offset") int offset);
 /** @param tenant 可信租户二进制UUID @param product 与分页相同的商品UUID @return 与列表相同租户及父记录条件的总数 */
 @Select("SELECT COUNT(*) FROM md_product_v1_change WHERE tenant_id=#{__verifiedTenant} AND product_id=UUID_TO_BIN(#{product})")
 long count(@Param("__verifiedTenant") byte[] tenant,@Param("product") String product);
}
'''
(root/'ProductHistoryMapper.java').write_text(mapper,encoding='utf-8')
p=root/'JdbcProductHistory.java';s=p.read_text(encoding='utf-8').replace('import org.springframework.jdbc.core.JdbcTemplate;\n','').replace('JdbcProductHistory','MyBatisProductHistory').replace('private final JdbcTemplate jdbc;','private final ProductHistoryMapper mapper;').replace('@param jdbc 原聚合事务数据源','@param mapper 原聚合事务固定历史映射').replace('public MyBatisProductHistory(JdbcTemplate jdbc,','public MyBatisProductHistory(ProductHistoryMapper mapper,').replace('{this.jdbc=jdbc;','{this.mapper=mapper;')
start=s.index('  jdbc.update(');end=s.index(';return changeId;',start)
s=s[:start]+'  mapper.append(tenantBytes(c),changeId,id,after.get("version"),type,before==null?null:json.writeValueAsString(before),json.writeValueAsString(after),actor(c))'+s[end:]
s=s.rstrip()[:-1]+''' private static byte[] tenantBytes(VerifiedTenantContext c){var id=c.tenantId();return java.nio.ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();}
}\n'''
(root/'MyBatisProductHistory.java').write_text(s,encoding='utf-8');p.unlink()
p=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata/product/ProductV1Service.java');s=p.read_text(encoding='utf-8')
s=s.replace('private final JdbcTemplate jdbc;','private final com.appnexus.masterdata.adapter.jdbc.ProductHistoryMapper historyMapper;\n private final JdbcTemplate jdbc;').replace('com.appnexus.masterdata.adapter.jdbc.ProductSnapshotMapper snapshotMapper){this.snapshotMapper=','com.appnexus.masterdata.adapter.jdbc.ProductSnapshotMapper snapshotMapper,com.appnexus.masterdata.adapter.jdbc.ProductHistoryMapper historyMapper){this.historyMapper=historyMapper;this.snapshotMapper=').replace('new com.appnexus.masterdata.adapter.jdbc.JdbcProductHistory(jdbc,','new com.appnexus.masterdata.adapter.jdbc.MyBatisProductHistory(historyMapper,')
s=s.replace('* @param snapshotMapper 父商品快照固定映射，不接收任意业务SQL','* @param snapshotMapper 父商品快照固定映射，不接收任意业务SQL\n  * @param historyMapper 商品追加历史固定映射，与父修改共同回滚')
start=s.index('  var rows=jdbc.queryForList("SELECT BIN_TO_UUID(id) id,to_version');end=s.index('\n }',start)
s=s[:start]+'''  var tenantId=c.tenantId();var tenantBytes=java.nio.ByteBuffer.allocate(16).putLong(tenantId.getMostSignificantBits()).putLong(tenantId.getLeastSignificantBits()).array();
  return Map.of("rows",historyMapper.list(tenantBytes,id,size,(page-1)*size),"total",historyMapper.count(tenantBytes,id));'''+s[end:]
p.write_text(s,encoding='utf-8')
p=Path('backend/services/master-data-service/src/test/java/com/appnexus/masterdata/product/ProductCatalogMySqlTest.java');s=p.read_text(encoding='utf-8').replace('mapperConfig.addMapper(com.appnexus.masterdata.adapter.jdbc.ProductSnapshotMapper.class);','mapperConfig.addMapper(com.appnexus.masterdata.adapter.jdbc.ProductSnapshotMapper.class);\n   mapperConfig.addMapper(com.appnexus.masterdata.adapter.jdbc.ProductHistoryMapper.class);').replace('.getMapper(com.appnexus.masterdata.adapter.jdbc.ProductSnapshotMapper.class));','.getMapper(com.appnexus.masterdata.adapter.jdbc.ProductSnapshotMapper.class),new org.mybatis.spring.SqlSessionTemplate(mapperFactory.getObject()).getMapper(com.appnexus.masterdata.adapter.jdbc.ProductHistoryMapper.class));');p.write_text(s,encoding='utf-8')
p=Path('backend/architecture-tests/src/test/java/com/appnexus/platform/ServiceLayerBoundaryTest.java');s=p.read_text(encoding='utf-8').replace('adapter.jdbc.JdbcProductHistory','adapter.jdbc.MyBatisProductHistory');p.write_text(s,encoding='utf-8')
