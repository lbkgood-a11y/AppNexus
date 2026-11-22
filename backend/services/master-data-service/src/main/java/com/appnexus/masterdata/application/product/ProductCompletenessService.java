package com.appnexus.masterdata.application.product;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import com.appnexus.masterdata.application.product.ProductRepository;
import java.util.*;
/** 完整性事实与状态的纯应用协调；读快照不写，refresh只在原父聚合事务中推进修改审计。 */
public final class ProductCompletenessService {
 private final ProductCompletenessFacts facts;private final ProductRepository records;
 /** @param facts 已授权聚合的固定事实输出端口 @param records 当前租户父记录端口 */
 public ProductCompletenessService(ProductCompletenessFacts facts,ProductRepository records){this.facts=facts;this.records=records;}
 /** @param c 已授权可信主体 @param row 当前商品快照 @return 原顺序完整性问题，查询无副作用 */
 public List<String> issues(VerifiedTenantContext c,Map<String,Object> row){
  var issues=new ArrayList<String>();
  if(row.get("categoryId")==null)issues.add("categoryId");if(row.get("configId")==null)issues.add("categoryConfigId");if(row.get("productForm")==null)issues.add("productForm");
  boolean unitRequired=Set.of("PHYSICAL","COMPOSITE").contains(Objects.toString(row.get("productForm"),""));
  if(row.get("configId")!=null)unitRequired|=Boolean.TRUE.equals(facts.serviceUnitRequired(c,row.get("configId")));
  if(unitRequired&&row.get("unitId")==null)issues.add("baseUnitId");
  if(row.get("configId")!=null)issues.addAll(facts.missingAttributes(c,row.get("configId"),row.get("id")).stream().map(x->"attribute:"+x).toList());
  String pid=Objects.toString(row.get("id"));
  if("STANDARD".equals(row.get("specificationMode"))&&facts.specificationCount(c,pid)==0)issues.add("specifications");
  if("COMPOSITE".equals(row.get("productForm"))){var components=facts.components(c,pid);if(components.isEmpty()||components.stream().anyMatch(x->!"ENABLED".equals(x.get("status"))||!x.get("version").equals(x.get("component_version"))))issues.add("components");}
  return issues;
 }
 /** @param c 已授权主体 @param id 已锁商品UUID @return 原完整性问题；启用商品不完整拒绝，事务回滚 */
 public List<String> refresh(VerifiedTenantContext c,String id){
  var row=records.load(c,id,false,null);var issues=issues(c,row);String state=com.appnexus.masterdata.domain.product.ProductCompletenessRules.state((String)row.get("status"),(String)row.get("structureState"),issues);
  facts.refresh(c,id,state);return issues;
 }
 private static String actor(VerifiedTenantContext c){return c.accountId().toString();}
}
