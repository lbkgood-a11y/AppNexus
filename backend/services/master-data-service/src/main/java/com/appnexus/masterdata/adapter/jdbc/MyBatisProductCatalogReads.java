package com.appnexus.masterdata.adapter.jdbc;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import com.appnexus.masterdata.MasterData;
import java.util.*;
/** 商品引用及固定目录SQL适配；只读租户所属分类、配置、单位与选项，锁顺序保持分类→模板→单位。 */
public final class MyBatisProductCatalogReads implements com.appnexus.masterdata.application.product.ProductCatalogReads {
 private final ProductCatalogReadMapper mapper;
 /** @param mapper 与写用例或只读预检共用事务的固定Mapper，locks=false禁止增加写锁 */
 public MyBatisProductCatalogReads(ProductCatalogReadMapper mapper){this.mapper=mapper;}
 /** {@inheritDoc} */
 public Object options(VerifiedTenantContext c,String kind,String keyword,int page,int size){
if(keyword==null||keyword.length()>200||page<1||page>1000000||size<1||size>100)throw invalid();
  if(!Set.of("category","unit").contains(kind))throw invalid();
  return Map.of("rows",mapper.options(tenantBytes(c),kind,keyword,size,(page-1)*size),"total",mapper.optionCount(tenantBytes(c),kind,keyword));
 }

 /** 按分类读取固定配置版本目录；用于显式选择版本，不自动将旧商品升级。 */
 /** {@inheritDoc} */
 public Object configurations(VerifiedTenantContext c,String categoryId,int page,int size){
uuid(categoryId);if(page<1||page>1000000||size<1||size>100)throw invalid();
  var rows=mapper.configurationVersions(tenantBytes(c),categoryId,size,(page-1)*size);
  return Map.of("rows",rows,"total",mapper.configurationCount(tenantBytes(c),categoryId));
 }
 /** 确定配置版本的动态表单元数据；历史退役定义可读，不追latest，也不扩大到字典写入权。 */
 /** {@inheritDoc} */
 public Map<String,Object> configuration(VerifiedTenantContext c,String id){
uuid(id);
  var configs=mapper.configuration(tenantBytes(c),id);
  if(configs.isEmpty())throw changed();var config=new LinkedHashMap<String,Object>(configs.getFirst());
  var fields=mapper.fields(tenantBytes(c),id);
  for(var field:fields)field.put("options",mapper.fieldOptions(tenantBytes(c),field.get("revisionId")));
  config.put("fields",fields);var slots=new LinkedHashMap<String,Object>();for(String purpose:List.of("BASIC","SPEC","QUALITY","EXTENSION"))slots.put(purpose,null);
  for(var template:mapper.templates(tenantBytes(c),id))slots.put((String)template.get("purpose"),template.get("versionId"));
  config.put("templates",slots);return config;
 }
 /** {@inheritDoc} */
 public void validate(VerifiedTenantContext c,String category,String config,String form,String unit,boolean fresh,boolean locks){
  if(category!=null){uuid(category);var rows=mapper.validateCategory(tenantBytes(c),category,locks);if(rows.isEmpty()||!Boolean.TRUE.equals(rows.getFirst().get("enabled")))throw invalid();}
  if(config!=null){uuid(config);if(category==null||mapper.validateConfiguration(tenantBytes(c),config,category)!=1)throw invalid();
   if(fresh){var versions=mapper.validateTemplateStates(tenantBytes(c),config,locks);for(var v:versions)if(!"PUBLISHED".equals(v.get("state"))||!Boolean.TRUE.equals(v.get("enabled")))throw invalid();}
  }
  if(form!=null&&!Set.of("PHYSICAL","SERVICE","COMPOSITE").contains(form))throw invalid();
  if(unit!=null){uuid(unit);var units=mapper.validateUnit(tenantBytes(c),unit,locks);if(units.size()!=1||!Boolean.TRUE.equals(units.getFirst().get("enabled")))throw invalid();}
 }
 private static void uuid(String id){try{if(!UUID.fromString(id).toString().equals(id))throw invalid();}catch(IllegalArgumentException|NullPointerException error){throw invalid();}}
 private static MasterData.Rejection invalid(){return new MasterData.Rejection(400,"PRODUCT_V1_INPUT_INVALID");}
 private static MasterData.Rejection changed(){return new MasterData.Rejection(409,"PRODUCT_V1_CHANGED");}
 private static byte[] tenantBytes(VerifiedTenantContext c){var id=c.tenantId();return java.nio.ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();}
}
