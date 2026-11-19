package com.appnexus.masterdata.adapter.jdbc;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import com.appnexus.masterdata.MasterData;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
/** 商品引用及固定目录SQL适配；只读租户所属分类、配置、单位与选项，锁顺序保持分类→模板→单位。 */
public final class JdbcProductCatalogReads implements com.appnexus.masterdata.application.product.ProductCatalogReads {
 private final JdbcTemplate jdbc;
 /** @param jdbc 与写用例或只读预检共用数据源，locks=false禁止增加写锁 */
 public JdbcProductCatalogReads(JdbcTemplate jdbc){this.jdbc=jdbc;}
 /** {@inheritDoc} */
 public Object options(VerifiedTenantContext c,String kind,String keyword,int page,int size){
if(keyword==null||keyword.length()>200||page<1||page>1000000||size<1||size>100)throw invalid();
  String table=switch(kind){case "category"->"md_product_category";case "unit"->"md_product_unit";default->throw invalid();};
  String where=" WHERE tenant_id=UUID_TO_BIN(?) AND enabled=TRUE AND (LOCATE(?,code)>0 OR LOCATE(?,name)>0)";
  long total=jdbc.queryForObject("SELECT COUNT(*) FROM "+table+where,Long.class,tenant(c),keyword,keyword);
  String extra=kind.equals("category")?",BIN_TO_UUID(current_config_id) configId":",dimension_code dimensionCode,precision_scale precisionScale";
  return Map.of("rows",jdbc.queryForList("SELECT BIN_TO_UUID(id) id,code,name"+extra+" FROM "+table+where+" ORDER BY code,id LIMIT ? OFFSET ?",tenant(c),keyword,keyword,size,(page-1)*size),"total",total);
 }

 /** 按分类读取固定配置版本目录；用于显式选择版本，不自动将旧商品升级。 */
 /** {@inheritDoc} */
 public Object configurations(VerifiedTenantContext c,String categoryId,int page,int size){
uuid(categoryId);if(page<1||page>1000000||size<1||size>100)throw invalid();
  var rows=jdbc.queryForList("SELECT BIN_TO_UUID(id) id,revision_no revision,display_path_snapshot path FROM md_product_category_config WHERE tenant_id=UUID_TO_BIN(?) AND category_id=UUID_TO_BIN(?) ORDER BY revision_no DESC LIMIT ? OFFSET ?",tenant(c),categoryId,size,(page-1)*size);
  return Map.of("rows",rows,"total",jdbc.queryForObject("SELECT COUNT(*) FROM md_product_category_config WHERE tenant_id=UUID_TO_BIN(?) AND category_id=UUID_TO_BIN(?)",Long.class,tenant(c),categoryId));
 }
 /** 确定配置版本的动态表单元数据；历史退役定义可读，不追latest，也不扩大到字典写入权。 */
 /** {@inheritDoc} */
 public Map<String,Object> configuration(VerifiedTenantContext c,String id){
uuid(id);
  var configs=jdbc.queryForList("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(category_id) categoryId,revision_no revision,display_path_snapshot path,service_unit_required serviceUnitRequired FROM md_product_category_config WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",tenant(c),id);
  if(configs.isEmpty())throw changed();var config=new LinkedHashMap<String,Object>(configs.getFirst());
  var fields=jdbc.queryForList("SELECT BIN_TO_UUID(i.id) templateItemId,BIN_TO_UUID(i.attribute_id) attributeId,BIN_TO_UUID(i.attribute_revision_id) revisionId,ci.purpose,r.name,r.data_type dataType,r.numeric_scale numericScale,CAST(r.validation_json AS CHAR) validationJson,i.group_code groupCode,i.group_name groupName,i.required_flag requiredFlag,i.searchable,i.allow_custom allowCustom,BIN_TO_UUID(r.unit_id) unitId,u.code unitCode,u.name unitName FROM md_product_category_config_item ci JOIN md_product_template_item i ON i.tenant_id=ci.tenant_id AND i.id=ci.template_item_id JOIN md_product_attribute_revision r ON r.tenant_id=i.tenant_id AND r.id=i.attribute_revision_id LEFT JOIN md_product_unit u ON u.tenant_id=r.tenant_id AND u.id=r.unit_id WHERE ci.tenant_id=UUID_TO_BIN(?) AND ci.config_id=UUID_TO_BIN(?) ORDER BY ci.purpose,i.sort_order,i.id",tenant(c),id);
  for(var field:fields)field.put("options",jdbc.queryForList("SELECT BIN_TO_UUID(id) id,option_code code,label,enabled FROM md_product_attribute_option WHERE tenant_id=UUID_TO_BIN(?) AND revision_id=UUID_TO_BIN(?) ORDER BY sort_order,id",tenant(c),field.get("revisionId")));
  config.put("fields",fields);var slots=new LinkedHashMap<String,Object>();for(String purpose:List.of("BASIC","SPEC","QUALITY","EXTENSION"))slots.put(purpose,null);
  jdbc.query("SELECT purpose,BIN_TO_UUID(template_version_id) versionId FROM md_product_category_template WHERE tenant_id=UUID_TO_BIN(?) AND config_id=UUID_TO_BIN(?)",rs->{slots.put(rs.getString("purpose"),rs.getString("versionId"));},tenant(c),id);
  config.put("templates",slots);return config;
 }
 /** {@inheritDoc} */
 public void validate(VerifiedTenantContext c,String category,String config,String form,String unit,boolean fresh,boolean locks){
  if(category!=null){uuid(category);var rows=jdbc.queryForList("SELECT enabled FROM md_product_category WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)"+(locks?" FOR SHARE":""),tenant(c),category);if(rows.isEmpty()||!Boolean.TRUE.equals(rows.getFirst().get("enabled")))throw invalid();}
  if(config!=null){uuid(config);if(category==null||jdbc.queryForObject("SELECT COUNT(*) FROM md_product_category_config WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND category_id=UUID_TO_BIN(?)",Integer.class,tenant(c),config,category)!=1)throw invalid();
   if(fresh){var versions=jdbc.queryForList("SELECT v.state,t.enabled FROM md_product_category_template b JOIN md_product_template_version v ON v.tenant_id=b.tenant_id AND v.id=b.template_version_id JOIN md_product_template t ON t.tenant_id=v.tenant_id AND t.id=v.template_id WHERE b.tenant_id=UUID_TO_BIN(?) AND b.config_id=UUID_TO_BIN(?) ORDER BY v.id"+(locks?" FOR SHARE":""),tenant(c),config);for(var v:versions)if(!"PUBLISHED".equals(v.get("state"))||!Boolean.TRUE.equals(v.get("enabled")))throw invalid();}
  }
  if(form!=null&&!Set.of("PHYSICAL","SERVICE","COMPOSITE").contains(form))throw invalid();
  if(unit!=null){uuid(unit);var units=jdbc.queryForList("SELECT enabled FROM md_product_unit WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)"+(locks?" FOR SHARE":""),tenant(c),unit);if(units.size()!=1||!Boolean.TRUE.equals(units.getFirst().get("enabled")))throw invalid();}
 }
 private static String tenant(VerifiedTenantContext c){return c.tenantId().toString();}
 private static void uuid(String id){try{if(!UUID.fromString(id).toString().equals(id))throw invalid();}catch(IllegalArgumentException|NullPointerException error){throw invalid();}}
 private static MasterData.Rejection invalid(){return new MasterData.Rejection(400,"PRODUCT_V1_INPUT_INVALID");}
 private static MasterData.Rejection changed(){return new MasterData.Rejection(409,"PRODUCT_V1_CHANGED");}
}
