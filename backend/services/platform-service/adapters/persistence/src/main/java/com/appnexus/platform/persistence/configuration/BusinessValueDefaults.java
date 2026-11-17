package com.appnexus.platform.persistence.configuration;
import com.appnexus.platform.configuration.BusinessValueCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.nio.charset.StandardCharsets;
/** 预置业务字典的数据初始化；迁移与开通租户共用同一 UTF-8 清单，只补缺失项，不覆盖租户定制或恢复停用项。 */
public final class BusinessValueDefaults {
 private BusinessValueDefaults(){}
 /** @return V89 对应不可变清单字节；后续变更必须新增迁移和资源版本 */
 public static byte[] resource(){try(var stream=BusinessValueDefaults.class.getResourceAsStream("/business-value-defaults.tsv")){return Objects.requireNonNull(stream).readAllBytes();}catch(java.io.IOException e){throw new IllegalStateException("BUSINESS_DEFAULTS_UNAVAILABLE",e);}}
 /**
  * 按不可变资源初始化缺失项，名称、稳定值、状态及创建信息已存在时不覆盖。
  * @param jdbc 当前写事务或 Flyway 连接
  * @param tenant 新租户或已存在租户 UUID
  * @param actor 可信开通账号；迁移系统执行为 null
  */
 public static void seed(JdbcTemplate jdbc,UUID tenant,UUID actor){
  int order=0;
  for(String line:new String(resource(),StandardCharsets.UTF_8).split("\n")){
   if(line.isBlank()||line.startsWith("#"))continue;var row=line.stripTrailing().split("\t",-1);
   add(jdbc,tenant,actor,row[0],row[1],row[2],row[3],++order);
  }
 }
 /** 仅在数据迁移时提炼既有自由文本，不在业务查询中写数据；无实际历史账号时保留 NULL。 */
 public static void legacy(JdbcTemplate jdbc,UUID tenant){
  extract(jdbc,tenant,"md_partner",BusinessValueCatalog.PARTNER);
  extract(jdbc,tenant,"md_product",BusinessValueCatalog.PRODUCT);
  extract(jdbc,tenant,"md_person",BusinessValueCatalog.PERSON);
  for(var row:jdbc.queryForList("SELECT section_code,content FROM md_person_detail WHERE tenant_id=UUID_TO_BIN(?)",tenant.toString())){
   var values=tools.jackson.databind.json.JsonMapper.builder().build().readValue(row.get("content").toString(),Map.class);
   for(var field:BusinessValueCatalog.PERSON.entrySet())if(values.containsKey(field.getKey()))addLegacy(jdbc,tenant,field.getValue(),Objects.toString(values.get(field.getKey()),""));
  }
  for(var field:BusinessValueCatalog.PERSON.entrySet())referenceExisting(jdbc,tenant,"md_person_detail","content->>'$."+field.getKey()+"'",field.getValue(),false);
  extract(jdbc,tenant,"md_partner_contact",Map.of("contactType","partner_contact_type"));
  extract(jdbc,tenant,"md_partner_bank_account",Map.of("currency","currency"));
 }
 private static void extract(JdbcTemplate jdbc,UUID tenant,String table,Map<String,String> bindings){
  for(var entry:bindings.entrySet()){
   String column=entry.getKey().replaceAll("([a-z0-9])([A-Z])","$1_$2").toLowerCase(Locale.ROOT);
   if(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? AND column_name=?",Integer.class,table,column)==0)continue;
   for(String value:jdbc.queryForList("SELECT DISTINCT "+column+" FROM "+table+" WHERE tenant_id=UUID_TO_BIN(?) AND "+column+"<>''",String.class,tenant.toString())){
    if(entry.getKey().equals("partnerType")){if(value.equals("BOTH"))continue;for(String role:value.split(","))addLegacy(jdbc,tenant,entry.getValue(),role);}
    else addLegacy(jdbc,tenant,entry.getValue(),value);
   }
   referenceExisting(jdbc,tenant,table,column,entry.getValue(),entry.getKey().equals("partnerType"));
  }
 }
 /** 历史引用由迁移保留，不能因旧记录尚未编辑就允许删除正在使用的字典项。 */
 private static void referenceExisting(JdbcTemplate jdbc,UUID tenant,String table,String expression,String code,boolean multiple){
  String match=multiple?"FIND_IN_SET(i.item_value,IF(b."+expression+"='BOTH','CUSTOMER,SUPPLIER',b."+expression+"))>0":"i.item_value=b."+expression;
  jdbc.update("INSERT INTO tenant_dictionary_item_reference(tenant_id,item_id,consumer,reference_key,created_by,created_at,updated_by,updated_at) SELECT b.tenant_id,i.id,?,BIN_TO_UUID(b.id),NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6) FROM "+table+" b JOIN tenant_dictionary d ON d.tenant_id=b.tenant_id AND d.code=? AND d.deleted_at IS NULL JOIN tenant_dictionary_item i ON i.tenant_id=b.tenant_id AND i.dictionary_id=d.id AND i.deleted_at IS NULL AND "+match+" WHERE b.tenant_id=UUID_TO_BIN(?) AND NOT EXISTS(SELECT 1 FROM tenant_dictionary_item_reference r WHERE r.tenant_id=b.tenant_id AND r.item_id=i.id AND r.consumer=? AND r.reference_key=BIN_TO_UUID(b.id))",table,code,tenant.toString(),table);
 }
 private static void addLegacy(JdbcTemplate jdbc,UUID tenant,String code,String value){if(!value.isBlank()&&value.length()<=100)add(jdbc,tenant,null,code,code,value,value,9000);}
 /** 稳定值及编码只在首次插入确定，所有已存在行保持原名称、状态和审计。 */
 static void add(JdbcTemplate jdbc,UUID tenant,UUID actor,String code,String name,String value,String label,int order){
  String tenantId=tenant.toString(),actorId=actor==null?null:actor.toString();String dictionary=UUID.nameUUIDFromBytes((tenantId+":"+code).getBytes(StandardCharsets.UTF_8)).toString();
  jdbc.update("INSERT INTO tenant_dictionary(id,tenant_id,code,name,description,status,sort_order,created_by,created_at,updated_by,updated_at) SELECT UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?,'ENABLED',?,UUID_TO_BIN(?),CURRENT_TIMESTAMP(6),UUID_TO_BIN(?),CURRENT_TIMESTAMP(6) WHERE NOT EXISTS(SELECT 1 FROM tenant_dictionary WHERE tenant_id=UUID_TO_BIN(?) AND code=?)",dictionary,tenantId,code,name,"业务预置；稳定值不可修改，可维护名称、排序和停用状态。",order,actorId,actorId,tenantId,code);
  var dictionaries=jdbc.queryForList("SELECT BIN_TO_UUID(id) id FROM tenant_dictionary WHERE tenant_id=UUID_TO_BIN(?) AND code=? AND deleted_at IS NULL",String.class,tenantId,code);
  if(dictionaries.isEmpty())return;dictionary=dictionaries.getFirst();
  String item=UUID.nameUUIDFromBytes((tenantId+":"+code+":"+value).getBytes(StandardCharsets.UTF_8)).toString();
  jdbc.update("INSERT INTO tenant_dictionary_item(id,tenant_id,dictionary_id,code,item_value,name,status,sort_order,created_by,created_at,updated_by,updated_at) SELECT UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?,'ENABLED',?,UUID_TO_BIN(?),CURRENT_TIMESTAMP(6),UUID_TO_BIN(?),CURRENT_TIMESTAMP(6) WHERE NOT EXISTS(SELECT 1 FROM tenant_dictionary_item WHERE tenant_id=UUID_TO_BIN(?) AND dictionary_id=UUID_TO_BIN(?) AND item_value=?)",item,tenantId,dictionary,"v_"+item.replace("-",""),value,label,order,actorId,actorId,tenantId,dictionary,value);
 }
}
