package com.appnexus.masterdata.adapter.jdbc;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import com.appnexus.masterdata.MasterData;
import com.appnexus.masterdata.application.product.ProductRecords;
import java.util.*;
/** 完整性事实固定读取与状态持久化适配；读快照不写，refresh只在原父聚合事务中推进修改审计。 */
public final class MyBatisProductCompleteness {
 private final ProductCompletenessMapper mapper;private final ProductRecords records;
 /** @param mapper 父事务固定SQL映射 @param records 当前租户父记录端口 */
 public MyBatisProductCompleteness(ProductCompletenessMapper mapper,ProductRecords records){this.mapper=mapper;this.records=records;}
 /** @param c 已授权可信主体 @param row 当前商品快照 @return 原顺序完整性问题，查询无副作用 */
 public List<String> issues(VerifiedTenantContext c,Map<String,Object> row){
  var issues=new ArrayList<String>();
  if(row.get("categoryId")==null)issues.add("categoryId");if(row.get("configId")==null)issues.add("categoryConfigId");if(row.get("productForm")==null)issues.add("productForm");
  boolean unitRequired=Set.of("PHYSICAL","COMPOSITE").contains(Objects.toString(row.get("productForm"),""));
  if(row.get("configId")!=null)unitRequired|=Boolean.TRUE.equals(mapper.serviceUnitRequired(tenantBytes(c),row.get("configId")));
  if(unitRequired&&row.get("unitId")==null)issues.add("baseUnitId");
  if(row.get("configId")!=null)issues.addAll(mapper.missingAttributes(tenantBytes(c),row.get("configId"),row.get("id")).stream().map(x->"attribute:"+x).toList());
  String pid=Objects.toString(row.get("id"));
  if("STANDARD".equals(row.get("specificationMode"))&&mapper.specificationCount(tenantBytes(c),pid)==0)issues.add("specifications");
  if("COMPOSITE".equals(row.get("productForm"))){var components=mapper.components(tenantBytes(c),pid);if(components.isEmpty()||components.stream().anyMatch(x->!"ENABLED".equals(x.get("status"))||!x.get("version").equals(x.get("component_version"))))issues.add("components");}
  return issues;
 }
 /** @param c 已授权主体 @param id 已锁商品UUID @return 原完整性问题；启用商品不完整拒绝，事务回滚 */
 public List<String> refresh(VerifiedTenantContext c,String id){
  var row=records.load(c,id,false);var issues=issues(c,row);String state=com.appnexus.masterdata.domain.product.ProductCompletenessRules.state((String)row.get("status"),(String)row.get("structureState"),issues);
  mapper.refresh(state,actor(c),tenantBytes(c),id);return issues;
 }
 private static String actor(VerifiedTenantContext c){return c.accountId().toString();}
 private static byte[] tenantBytes(VerifiedTenantContext c){var id=c.tenantId();return java.nio.ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();}
}
