package com.appnexus.masterdata.adapter.persistence;
import com.appnexus.masterdata.adapter.persistence.*;

import com.appnexus.masterdata.application.product.ProductRepository;
import com.appnexus.masterdata.api.masterdata.MasterData;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;

/** 商品父记录 MySQL 适配器；固定表与绑定租户参数，锁与 CAS 共享调用方事务。 */
public final class MyBatisProductRepository implements ProductRepository {
 private final ProductRecordMapper mapper;
 /** @param mapper 与商品扩展共用SqlSession及UTC数据源的固定Mapper，不创建独立事务 */
 public MyBatisProductRepository(ProductRecordMapper mapper){this.mapper=Objects.requireNonNull(mapper);}
 @Override public Map<String,Object> load(VerifiedTenantContext c,String id,boolean lock){
  var rows=mapper.load(tenantBytes(c),id,lock).stream().map(r->{
   var m=new LinkedHashMap<String,Object>();
   for(String col:List.of("organizationId","id","code","name","shortName","description","brand","origin","categoryId","configId","productForm","specificationMode","unitId","status","structureState","legacySpecificationOriginal","legacyDisposition","barcode","legacyUnitOriginal","createdBy","updatedBy"))m.put(col,r.get(col)==null?null:r.get(col).toString());
   for(String col:List.of("archivedAt","createdAt","updatedAt"))m.put(col,utc(r.get(col)));
   m.put("legacySourceVersion",r.get("legacySourceVersion"));m.put("legacyEnabled",r.get("legacyEnabled"));m.put("version",((Number)r.get("version")).longValue());m.put("structureRevision",((Number)r.get("structureRevision")).longValue());return m;
  }).toList();
  if(rows.isEmpty())throw new MasterData.Rejection(409,"PRODUCT_V1_CHANGED");return rows.getFirst();
 }
 @Override public void advance(VerifiedTenantContext c,String id,long version){
  if(mapper.advance(tenantBytes(c),id,c.accountId().toString(),version)!=1)throw new MasterData.Rejection(409,"PRODUCT_V1_CHANGED");
 }
 /** UTC数据库时间保持原ISO瞬时值，不采用宿主默认时区。 */
 private static String utc(Object value){if(value==null)return null;if(value instanceof java.sql.Timestamp t)return t.toInstant().toString();if(value instanceof java.time.LocalDateTime t)return t.toInstant(java.time.ZoneOffset.UTC).toString();throw new IllegalStateException("PRODUCT_TIMESTAMP_INVALID");}
 private static byte[] tenantBytes(VerifiedTenantContext c){var id=c.tenantId();return java.nio.ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();}
}
