package com.appnexus.masterdata.adapter.persistence;
import com.appnexus.masterdata.application.product.ProductRepository;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import java.util.function.BiFunction;
/** 商品聚合快照固定查询适配；所有明细限定可信租户与父商品，不扩大读取权限。 */
public final class MyBatisProductSnapshot {
 private final ProductSnapshotMapper mapper;
 private final ProductRepository records;
 private final BiFunction<VerifiedTenantContext,Map<String,Object>,List<String>> issues;
 /** @param mapper 同一事务固定快照映射 @param records 父记录端口 @param issues 既有只读完整性计算协作 */
 public MyBatisProductSnapshot(ProductSnapshotMapper mapper,ProductRepository records,BiFunction<VerifiedTenantContext,Map<String,Object>,List<String>> issues){this.mapper=mapper;this.records=records;this.issues=issues;}
 /** @param c 已授权可信主体 @param id 当前租户商品UUID @return 原字段和四审计聚合投影，不作客户端可信写入事实 */
 public Map<String,Object> load(VerifiedTenantContext c,String id){
  var m=records.load(c,id,false);
  m.put("approvalPending",mapper.approvalPending(tenantBytes(c),id));
  m.put("specifications",mapper.specifications(tenantBytes(c),id));
  m.put("specificationValues",mapper.specificationValues(tenantBytes(c),id));
  m.put("specificationChoices",mapper.specificationChoices(tenantBytes(c),id));
  m.put("skus",mapper.skus(tenantBytes(c),id));
  m.put("components",mapper.components(tenantBytes(c),id));
  m.put("images",mapper.images(tenantBytes(c),id));
  m.put("values",mapper.values(tenantBytes(c),id));
  m.put("choices",mapper.choices(tenantBytes(c),id));
  var slots=new LinkedHashMap<String,Object>();for(String purpose:List.of("BASIC","SPEC","QUALITY","EXTENSION"))slots.put(purpose,null);
  if(m.get("configId")!=null){
   m.put("categoryConfiguration",mapper.categoryConfiguration(tenantBytes(c),m.get("configId")));
   for(var template:mapper.templates(tenantBytes(c),m.get("configId")))slots.put((String)template.get("purpose"),template.get("versionId"));
  }
  m.put("templates",slots);m.put("schemaVersion",1);
  if(m.get("unitId")!=null)m.put("baseUnit",mapper.baseUnit(tenantBytes(c),m.get("unitId")));
  m.put("completionIssues",issues.apply(c,m));
  var audit=new LinkedHashMap<String,Object>();for(String k:List.of("createdBy","createdAt","updatedBy","updatedAt"))audit.put(k,m.get(k));m.put("audit",audit);return m;
 }
 private static byte[] tenantBytes(VerifiedTenantContext c){var id=c.tenantId();return java.nio.ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();}
}
