package com.appnexus.masterdata.adapter.jdbc;
import com.appnexus.masterdata.application.product.ProductRecords;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.util.function.BiFunction;
/** 商品聚合快照固定查询适配；所有明细限定可信租户与父商品，不扩大读取权限。 */
public final class JdbcProductSnapshot {
 private final JdbcTemplate jdbc;
 private final ProductRecords records;
 private final BiFunction<VerifiedTenantContext,Map<String,Object>,List<String>> issues;
 /** @param jdbc 同一事务数据源 @param records 父记录端口 @param issues 既有只读完整性计算协作 */
 public JdbcProductSnapshot(JdbcTemplate jdbc,ProductRecords records,BiFunction<VerifiedTenantContext,Map<String,Object>,List<String>> issues){this.jdbc=jdbc;this.records=records;this.issues=issues;}
 /** @param c 已授权可信主体 @param id 当前租户商品UUID @return 原字段和四审计聚合投影，不作客户端可信写入事实 */
 public Map<String,Object> load(VerifiedTenantContext c,String id){
  var m=records.load(c,id,false);
  m.put("approvalPending",jdbc.queryForObject("SELECT COUNT(*)>0 FROM md_product_approval WHERE tenant_id=UUID_TO_BIN(?) AND product_id=UUID_TO_BIN(?) AND state='SUBMITTED'",Boolean.class,tenant(c),id));
  m.put("specifications",jdbc.queryForList("SELECT BIN_TO_UUID(id) id,mode,name,enabled,retired_at retiredAt,combination_canonical combination FROM md_product_specification WHERE tenant_id=UUID_TO_BIN(?) AND product_id=UUID_TO_BIN(?) ORDER BY id",tenant(c),id));
  m.put("specificationValues",jdbc.queryForList("SELECT BIN_TO_UUID(v.specification_id) specificationId,BIN_TO_UUID(v.template_item_id) templateItemId,r.name attributeName,v.value_type dataType,u.code unitCode,v.text_value textValue,CAST(v.date_value AS CHAR) dateValue,CAST(v.integer_value AS CHAR) integerValue,CAST(v.decimal_value AS CHAR) decimalValue,v.boolean_value booleanValue,o.label optionLabel FROM md_product_spec_value v JOIN md_product_specification s ON s.tenant_id=v.tenant_id AND s.id=v.specification_id JOIN md_product_attribute_revision r ON r.tenant_id=v.tenant_id AND r.id=v.attribute_revision_id LEFT JOIN md_product_unit u ON u.tenant_id=r.tenant_id AND u.id=r.unit_id LEFT JOIN md_product_attribute_option o ON o.tenant_id=v.tenant_id AND o.id=v.option_id WHERE s.tenant_id=UUID_TO_BIN(?) AND s.product_id=UUID_TO_BIN(?) ORDER BY v.id",tenant(c),id));
  m.put("specificationChoices",jdbc.queryForList("SELECT BIN_TO_UUID(v.specification_id) specificationId,BIN_TO_UUID(v.template_item_id) templateItemId,COALESCE(o.label,ch.custom_text) label,ch.custom_text customText FROM md_product_spec_choice ch JOIN md_product_spec_value v ON v.tenant_id=ch.tenant_id AND v.id=ch.value_id JOIN md_product_specification s ON s.tenant_id=v.tenant_id AND s.id=v.specification_id LEFT JOIN md_product_attribute_option o ON o.tenant_id=ch.tenant_id AND o.id=ch.option_id WHERE s.tenant_id=UUID_TO_BIN(?) AND s.product_id=UUID_TO_BIN(?) ORDER BY ch.id",tenant(c),id));
  m.put("skus",jdbc.queryForList("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(specification_id) specificationId,code,enabled,version FROM md_product_sku WHERE tenant_id=UUID_TO_BIN(?) AND product_id=UUID_TO_BIN(?) ORDER BY id",tenant(c),id));
  m.put("components",jdbc.queryForList("SELECT BIN_TO_UUID(c.component_product_id) componentId,p.code componentCode,p.name componentName,CAST(c.quantity AS CHAR) quantity,c.component_version componentVersion,c.enabled FROM md_product_component c JOIN md_product_v1 p ON p.tenant_id=c.tenant_id AND p.id=c.component_product_id WHERE c.tenant_id=UUID_TO_BIN(?) AND c.product_id=UUID_TO_BIN(?) ORDER BY c.id",tenant(c),id));
  m.put("images",jdbc.queryForList("SELECT BIN_TO_UUID(id) id,HEX(content_hash) contentHash,width_px width,height_px height FROM md_product_image WHERE tenant_id=UUID_TO_BIN(?) AND product_id=UUID_TO_BIN(?) AND deleted_at IS NULL ORDER BY id",tenant(c),id));
  m.put("values",jdbc.queryForList("SELECT BIN_TO_UUID(v.template_item_id) templateItemId,BIN_TO_UUID(v.attribute_revision_id) revisionId,r.name attributeName,r.data_type dataType,v.text_value textValue,CAST(v.integer_value AS CHAR) integerValue,CAST(v.decimal_value AS CHAR) decimalValue,v.boolean_value booleanValue,CAST(v.date_value AS CHAR) dateValue FROM md_product_v1_attribute_value v JOIN md_product_attribute_revision r ON r.tenant_id=v.tenant_id AND r.id=v.attribute_revision_id WHERE v.tenant_id=UUID_TO_BIN(?) AND v.product_id=UUID_TO_BIN(?) ORDER BY v.id",tenant(c),id));
  m.put("choices",jdbc.queryForList("SELECT BIN_TO_UUID(v.template_item_id) templateItemId,BIN_TO_UUID(o.id) optionId,o.option_code optionCode,COALESCE(o.label,ch.custom_text) label,ch.custom_text customText FROM md_product_v1_attribute_choice ch JOIN md_product_v1_attribute_value v ON v.tenant_id=ch.tenant_id AND v.id=ch.value_id LEFT JOIN md_product_attribute_option o ON o.tenant_id=ch.tenant_id AND o.id=ch.option_id WHERE ch.tenant_id=UUID_TO_BIN(?) AND v.product_id=UUID_TO_BIN(?)",tenant(c),id));
  var slots=new LinkedHashMap<String,Object>();for(String purpose:List.of("BASIC","SPEC","QUALITY","EXTENSION"))slots.put(purpose,null);
  if(m.get("configId")!=null){
   m.put("categoryConfiguration",jdbc.queryForMap("SELECT revision_no revision,display_path_snapshot path,service_unit_required serviceUnitRequired FROM md_product_category_config WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",tenant(c),m.get("configId")));
   jdbc.query("SELECT purpose,BIN_TO_UUID(template_version_id) versionId FROM md_product_category_template WHERE tenant_id=UUID_TO_BIN(?) AND config_id=UUID_TO_BIN(?)",rs->{slots.put(rs.getString("purpose"),rs.getString("versionId"));},tenant(c),m.get("configId"));
  }
  m.put("templates",slots);m.put("schemaVersion",1);
  if(m.get("unitId")!=null)m.put("baseUnit",jdbc.queryForMap("SELECT code,name,dimension_code dimensionCode,precision_scale precisionScale FROM md_product_unit WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",tenant(c),m.get("unitId")));
  m.put("completionIssues",issues.apply(c,m));
  var audit=new LinkedHashMap<String,Object>();for(String k:List.of("createdBy","createdAt","updatedBy","updatedAt"))audit.put(k,m.get(k));m.put("audit",audit);return m;
 }
 private static String tenant(VerifiedTenantContext c){return c.tenantId().toString();}
}
