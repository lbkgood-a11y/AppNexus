package com.appnexus.masterdata.adapter.jdbc;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import com.appnexus.masterdata.MasterData;
import com.appnexus.masterdata.application.product.ProductRecords;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
/** 完整性事实固定读取与状态持久化适配；读快照不写，refresh只在原父聚合事务中推进修改审计。 */
public final class JdbcProductCompleteness {
 private final JdbcTemplate jdbc;private final ProductRecords records;
 /** @param jdbc 父事务数据源 @param records 当前租户父记录端口 */
 public JdbcProductCompleteness(JdbcTemplate jdbc,ProductRecords records){this.jdbc=jdbc;this.records=records;}
 /** @param c 已授权可信主体 @param row 当前商品快照 @return 原顺序完整性问题，查询无副作用 */
 public List<String> issues(VerifiedTenantContext c,Map<String,Object> row){
  var issues=new ArrayList<String>();
  if(row.get("categoryId")==null)issues.add("categoryId");if(row.get("configId")==null)issues.add("categoryConfigId");if(row.get("productForm")==null)issues.add("productForm");
  boolean unitRequired=Set.of("PHYSICAL","COMPOSITE").contains(Objects.toString(row.get("productForm"),""));
  if(row.get("configId")!=null)unitRequired|=Boolean.TRUE.equals(jdbc.queryForObject("SELECT service_unit_required FROM md_product_category_config WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",Boolean.class,tenant(c),row.get("configId")));
  if(unitRequired&&row.get("unitId")==null)issues.add("baseUnitId");
  if(row.get("configId")!=null)issues.addAll(jdbc.queryForList("SELECT BIN_TO_UUID(i.id) FROM md_product_category_config_item ci JOIN md_product_template_item i ON i.tenant_id=ci.tenant_id AND i.id=ci.template_item_id WHERE ci.tenant_id=UUID_TO_BIN(?) AND ci.config_id=UUID_TO_BIN(?) AND ci.purpose<>'SPEC' AND i.required_flag=TRUE AND NOT EXISTS(SELECT 1 FROM md_product_v1_attribute_value v WHERE v.tenant_id=ci.tenant_id AND v.product_id=UUID_TO_BIN(?) AND v.template_item_id=i.id)",String.class,tenant(c),row.get("configId"),row.get("id")).stream().map(x->"attribute:"+x).toList());
  String pid=Objects.toString(row.get("id"));
  if("STANDARD".equals(row.get("specificationMode"))&&jdbc.queryForObject("SELECT COUNT(*) FROM md_product_specification WHERE tenant_id=UUID_TO_BIN(?) AND product_id=UUID_TO_BIN(?) AND retired_at IS NULL AND enabled=TRUE",Integer.class,tenant(c),pid)==0)issues.add("specifications");
  if("COMPOSITE".equals(row.get("productForm"))){var components=jdbc.queryForList("SELECT p.status,p.version,c.component_version FROM md_product_component c JOIN md_product_v1 p ON p.tenant_id=c.tenant_id AND p.id=c.component_product_id WHERE c.tenant_id=UUID_TO_BIN(?) AND c.product_id=UUID_TO_BIN(?) AND c.enabled=TRUE",tenant(c),pid);if(components.isEmpty()||components.stream().anyMatch(x->!"ENABLED".equals(x.get("status"))||!x.get("version").equals(x.get("component_version"))))issues.add("components");}
  return issues;
 }
 /** @param c 已授权主体 @param id 已锁商品UUID @return 原完整性问题；启用商品不完整拒绝，事务回滚 */
 public List<String> refresh(VerifiedTenantContext c,String id){
  var row=records.load(c,id,false);var issues=issues(c,row);String state=com.appnexus.masterdata.domain.product.ProductCompletenessRules.state((String)row.get("status"),(String)row.get("structureState"),issues);
  jdbc.update("UPDATE md_product_v1 SET structure_state=?,updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",state,actor(c),tenant(c),id);return issues;
 }
 private static String tenant(VerifiedTenantContext c){return c.tenantId().toString();}
 private static String actor(VerifiedTenantContext c){return c.accountId().toString();}
}
