package com.appnexus.masterdata.adapter.persistence;
import com.appnexus.masterdata.application.product.ProductRepository;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import java.util.function.BiFunction;
import tools.jackson.databind.ObjectMapper;
/** 商品聚合快照固定查询适配；所有明细限定可信租户与父商品，不扩大读取权限。 */
public final class MyBatisProductSnapshot {
 private final ProductSnapshotMapper mapper;
 private final ProductRepository records;
 private final ObjectMapper json;
 private final BiFunction<VerifiedTenantContext,Map<String,Object>,List<String>> issues;
 /** @param mapper 同一事务固定快照映射 @param records 父记录端口 @param issues 既有只读完整性计算协作 @param json 宿主聚合 JSON 编解码器 */
 public MyBatisProductSnapshot(ProductSnapshotMapper mapper,ProductRepository records,BiFunction<VerifiedTenantContext,Map<String,Object>,List<String>> issues,ObjectMapper json){this.mapper=mapper;this.records=records;this.issues=issues;this.json=json;}
 /** @param c 已授权可信主体 @param id 当前租户商品UUID @param access 原读取范围，写事务中可空 @return 原字段和四审计聚合投影，不作客户端可信写入事实 */
 public Map<String,Object> load(VerifiedTenantContext c,String id,com.appnexus.platform.authorization.RowAccessControl.Predicate access){
  var m=records.load(c,id,false,access);
  m.put("measurementConfig",mapper.measurement(tenantBytes(c),id,access));
  m.put("skuAttributeTemplateVersionId",mapper.skuAttributeTemplate(tenantBytes(c),id,access));
  m.put("specificationTemplateVersionId",mapper.specificationTemplate(tenantBytes(c),id,access));
  m.put("approvalPending",mapper.approvalPending(tenantBytes(c),id,access));
  var specifications=mapper.specifications(tenantBytes(c),id,access);
  var specificationValues=new ArrayList<>(mapper.specificationValues(tenantBytes(c),id,access));
  var specificationChoices=new ArrayList<>(mapper.specificationChoices(tenantBytes(c),id,access));
  appendFixedSpecifications(specifications,specificationValues,specificationChoices);
  m.put("specifications",specifications);
  m.put("specificationValues",specificationValues);
  m.put("specificationChoices",specificationChoices);
  m.put("skus",mapper.skus(tenantBytes(c),id,access));
  m.put("components",mapper.components(tenantBytes(c),id,access));
  m.put("images",mapper.images(tenantBytes(c),id,access));
  m.put("values",mapper.values(tenantBytes(c),id,access));
  m.put("choices",mapper.choices(tenantBytes(c),id,access));
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
 /** 新规格按固定快照追加到既有审批和变更记录的字段投影。 */
 private void appendFixedSpecifications(List<Map<String,Object>> specifications,List<Map<String,Object>> values,
     List<Map<String,Object>> choices){
  for(var specification:specifications){
   if(!(specification.remove("templateValues") instanceof String content))continue;
   for(var field:json.readTree(content).path("values")){
    var value=new LinkedHashMap<String,Object>();
    value.put("specificationId",specification.get("id"));
    value.put("templateItemId",field.path("templateItemId").asText());
    value.put("attributeName",field.path("name").asText());
    value.put("dataType",field.path("dataType").asText());
    for(String key:List.of("textValue","integerValue","decimalValue","dateValue","booleanValue","unitCode")){
     if(field.hasNonNull(key))value.put(key,"booleanValue".equals(key)?field.path(key).asBoolean():field.path(key).asText());
    }
    values.add(value);
    for(var option:field.path("choices")){
     var choice=new LinkedHashMap<String,Object>();
     choice.put("specificationId",specification.get("id"));
     choice.put("templateItemId",field.path("templateItemId").asText());
     for(String key:List.of("optionId","label","customText")){
      if(option.hasNonNull(key))choice.put(key,option.path(key).asText());
     }
     choices.add(choice);
    }
   }
  }
 }

 private static byte[] tenantBytes(VerifiedTenantContext c){var id=c.tenantId();return java.nio.ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();}
}
