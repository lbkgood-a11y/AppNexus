package com.appnexus.platform.persistence.configuration;
import com.appnexus.platform.configuration.BusinessValueCatalog;

import java.util.*;
import java.nio.charset.StandardCharsets;
/** 预置业务字典的数据初始化；迁移与开通租户共用同一 UTF-8 清单，只补缺失项，不覆盖租户定制或恢复停用项。 */
public final class BusinessValueDefaults {
 private BusinessValueDefaults(){}
 /** @return V89 对应不可变清单字节；后续变更必须新增迁移和资源版本 */
 public static byte[] resource(){try(var stream=BusinessValueDefaults.class.getResourceAsStream("/business-value-defaults.tsv")){return Objects.requireNonNull(stream).readAllBytes();}catch(java.io.IOException e){throw new IllegalStateException("BUSINESS_DEFAULTS_UNAVAILABLE",e);}}
 /**
  * 按不可变资源初始化缺失项，名称、稳定值、状态及创建信息已存在时不覆盖。
  * @param mapper 当前写事务或 Flyway 连接
  * @param tenant 新租户或已存在租户 UUID
  * @param actor 可信开通账号；迁移系统执行为 null
  */
 public static void seed(HistoricalDictionaryMapper mapper,UUID tenant,UUID actor){
  int order=0;
  for(String line:new String(resource(),StandardCharsets.UTF_8).split("\n")){
   if(line.isBlank()||line.startsWith("#"))continue;var row=line.stripTrailing().split("\t",-1);
   add(mapper,tenant,actor,row[0],row[1],row[2],row[3],++order);
  }
 }
 /** 仅在数据迁移时提炼既有自由文本，不在业务查询中写数据；无实际历史账号时保留 NULL。 */
 public static void legacy(HistoricalDictionaryMapper mapper,UUID tenant){
  extract(mapper,tenant,"md_partner",BusinessValueCatalog.PARTNER);
  extract(mapper,tenant,"md_product",BusinessValueCatalog.PRODUCT);
  extract(mapper,tenant,"md_person",BusinessValueCatalog.PERSON);
  for(var row:mapper.statement0(tenant.toString())){
   var values=tools.jackson.databind.json.JsonMapper.builder().build().readValue(row.get("content").toString(),Map.class);
   for(var field:BusinessValueCatalog.PERSON.entrySet())if(values.containsKey(field.getKey()))addLegacy(mapper,tenant,field.getValue(),Objects.toString(values.get(field.getKey()),""));
  }
  for(var field:BusinessValueCatalog.PERSON.entrySet())referenceExisting(mapper,tenant,"md_person_detail","content->>'$."+field.getKey()+"'",field.getValue(),false);
  extract(mapper,tenant,"md_partner_contact",Map.of("contactType","partner_contact_type"));
  extract(mapper,tenant,"md_partner_bank_account",Map.of("currency","currency"));
 }
 private static void extract(HistoricalDictionaryMapper mapper,UUID tenant,String table,Map<String,String> bindings){
  for(var entry:bindings.entrySet()){
   String column=entry.getKey().replaceAll("([a-z0-9])([A-Z])","$1_$2").toLowerCase(Locale.ROOT);
   if(mapper.statement1(table,column)==0)continue;
   for(String value:mapper.values(tenant.toString(),table,column)){
    if(entry.getKey().equals("partnerType")){if(value.equals("BOTH"))continue;for(String role:value.split(","))addLegacy(mapper,tenant,entry.getValue(),role);}
    else addLegacy(mapper,tenant,entry.getValue(),value);
   }
   referenceExisting(mapper,tenant,table,column,entry.getValue(),entry.getKey().equals("partnerType"));
  }
 }
 /** 历史引用由迁移保留，不能因旧记录尚未编辑就允许删除正在使用的字典项。 */
 private static void referenceExisting(HistoricalDictionaryMapper mapper,UUID tenant,String table,String expression,String code,boolean multiple){
  mapper.references(tenant.toString(),table,expression,code,multiple);
 }
 private static void addLegacy(HistoricalDictionaryMapper mapper,UUID tenant,String code,String value){if(!value.isBlank()&&value.length()<=100)add(mapper,tenant,null,code,code,value,value,9000);}
 /** 稳定值及编码只在首次插入确定，所有已存在行保持原名称、状态和审计。 */
 static void add(HistoricalDictionaryMapper mapper,UUID tenant,UUID actor,String code,String name,String value,String label,int order){
  String tenantId=tenant.toString(),actorId=actor==null?null:actor.toString();String dictionary=UUID.nameUUIDFromBytes((tenantId+":"+code).getBytes(StandardCharsets.UTF_8)).toString();
  mapper.statement2(dictionary,tenantId,code,name,"业务预置；稳定值不可修改，可维护名称、排序和停用状态。",order,actorId,actorId,tenantId,code);
  var dictionaries=mapper.statement3(tenantId,code);
  if(dictionaries.isEmpty())return;dictionary=dictionaries.getFirst();
  String item=UUID.nameUUIDFromBytes((tenantId+":"+code+":"+value).getBytes(StandardCharsets.UTF_8)).toString();
  mapper.statement4(item,tenantId,dictionary,"v_"+item.replace("-",""),value,label,order,actorId,actorId,tenantId,dictionary,value);
 }
}
