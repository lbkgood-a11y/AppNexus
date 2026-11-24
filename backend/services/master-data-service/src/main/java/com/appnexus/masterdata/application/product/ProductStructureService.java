package com.appnexus.masterdata.application.product;
import com.appnexus.masterdata.domain.product.AttributeRulePolicy;
import com.appnexus.masterdata.api.masterdata.MasterData;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.appnexus.masterdata.api.product.ProductStructureCommands.Specification;
import com.appnexus.masterdata.api.product.ProductStructureCommands.SpecValue;
import com.appnexus.masterdata.api.product.ProductStructureCommands.Sku;
import com.appnexus.masterdata.api.product.ProductStructureCommands.Component;
import com.appnexus.masterdata.api.product.ProductStructureCommands.State;

/** 规格、可选SKU和组合结构领域服务；父锁/CAS及追加历史同事务，不触及库存、定价或拆装。 */
public final class ProductStructureService implements com.appnexus.masterdata.api.product.ProductStructureManagement {
 /** {@inheritDoc} */
 @Override
 public ProductIdentity resolveIdentity(VerifiedTenantContext c, String productId, IdentityKind kind,
     String identityId, long expectedProductVersion) {
  require(c, "read");
  uuid(identityId);
  if (kind == null || expectedProductVersion < 0) throw invalid();
  return write(() -> {
   var product = parent(c, productId, false, null);
   long version = ((Number) product.get("version")).longValue();
   if (version != expectedProductVersion) throw changed();
   String unitId = (String) product.get("unitId");
   if (!"ENABLED".equals(product.get("status")) || product.get("archived") != null
       || "LEGACY_PENDING".equals(product.get("structure")) || unitId == null) throw invalid();
   requireMeasurementUnit(c, unitId);
   if (kind == IdentityKind.PRODUCT) {
    if (!productId.equals(identityId) || !"NONE".equals(product.get("specification_mode"))) throw invalid();
    return new ProductIdentity(productId, kind, identityId, null, unitId, version, null);
   }
   var access=products.recordPredicate(c,"read");
   var rows = kind == IdentityKind.SKU
       ? statements.skus(tenant(c),productId,access) : statements.specifications(tenant(c),productId,access);
   var identity = rows.stream()
       .filter(row -> identityId.equals(row.get("id")) && effective(row.get("effective")))
       .findFirst().orElseThrow(ProductStructureService::invalid);
   String specificationId = kind == IdentityKind.SKU ? (String) identity.get("specificationId") : identityId;
   if(kind==IdentityKind.SKU && identity.get("measurementConfig") instanceof String json){
    unitId=(String)((Map<?,?>)measurementContent.decode(json)).get("inventoryUnitId");
    requireMeasurementUnit(c,unitId);
   }
   return new ProductIdentity(productId, kind, identityId, specificationId, unitId, version,
       ((Number) identity.get("version")).longValue());
  });
 }

 /** 业务响应仅返回解码后的计量结构，数据库 JSON 文本留在持久化边界。 */
 private Map<String,Object> skuView(Map<String,Object> row){
  var view=new LinkedHashMap<>(row);
  if(view.remove("measurementConfig") instanceof String json){
   view.put("measurement",measurementContent.decode(json));
  }
  return view;
 }

 // MySQL 对逻辑表达式可能返回数值，只有明确的真值才允许用于库存。
 private static boolean effective(Object value) {
  return Boolean.TRUE.equals(value) || value instanceof Number flag && flag.intValue()==1;
 }

 /** {@inheritDoc} */
 public Object measurement(VerifiedTenantContext c,String product){
  require(c,"read");
  var parent=parent(c,product,false,null);
  var result=new LinkedHashMap<String,Object>();
  result.put("baseUnitId",parent.get("unitId"));
  result.put("version",parent.get("version"));
  result.put("configuration",measurementConfiguration(parent));
  return result;
 }

 /** {@inheritDoc} */
 public Object saveMeasurement(VerifiedTenantContext c,String product,com.appnexus.masterdata.api.product.ProductMeasurement.Change input){
  require(c,"manage_structure");
  if(input==null||input.units()==null||input.packaging()==null||input.units().size()>200||input.packaging().size()>200)throw invalid();
  return write(()->{
   var parent=parent(c,product,true,input.version());
   String base=(String)parent.get("unitId");
   if(base==null&&(!input.units().isEmpty()||!input.packaging().isEmpty()))throw invalid();
   Integer scale=base==null?null:statements.unitPrecision(tenant(c),base);
   var defaults=new HashSet<String>();
   var selections=new HashSet<List<String>>();
   var packagingUnits=new HashSet<String>();
   var codes=new HashSet<String>();
   for(var pack:input.packaging()){
    if(pack==null||pack.code()==null||!pack.code().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")||!codes.add(pack.code())
        ||pack.name()==null||pack.name().isBlank()||pack.name().length()>200||base.equals(pack.unitId()))throw invalid();
    requireMeasurementUnit(c,pack.unitId());
    var quantity=com.appnexus.masterdata.domain.product.UnitConversionRules.decimal(pack.quantity(),true);
    if(scale==null||quantity.stripTrailingZeros().scale()>scale)throw invalid();
    packagingUnits.add(pack.unitId());
   }
   for(var unit:input.units()){
    if(unit==null||unit.scene()==null||unit.method()==null
        ||!Set.of("ARRIVAL","INBOUND","OUTBOUND").contains(unit.scene())
        ||!Set.of("DIRECT","CONVERSION","PACKAGING","MEASURED").contains(unit.method()))throw invalid();
    requireMeasurementUnit(c,unit.unitId());
    if(!selections.add(List.of(unit.scene(),unit.unitId()))||(unit.defaultUnit()&&!defaults.add(unit.scene()))
        ||("DIRECT".equals(unit.method())&&!base.equals(unit.unitId()))
        ||("PACKAGING".equals(unit.method())&&!packagingUnits.contains(unit.unitId()))
        ||("CONVERSION".equals(unit.method())&&!statements.hasMeasurementConversion(tenant(c),unit.unitId(),base)))throw invalid();
   }
   var before=products.extensionSnapshot(c,product);
   String configuration=input.units().isEmpty()&&input.packaging().isEmpty()?null:
       measurementContent.encode(Map.of("units",input.units(),"packaging",input.packaging()));
   statements.saveMeasurement(tenant(c),product,configuration);
   finish(c,product,input.version(),"MEASUREMENT_UPDATE",before);
   return Map.of("version",input.version()+1);
  });
 }

 /** {@inheritDoc} */
 public Object calculatePackaging(VerifiedTenantContext c,String product,String code,String amount){
  require(c,"read");
  var parent=parent(c,product,false,null);
  var quantity=com.appnexus.masterdata.domain.product.UnitConversionRules.decimal(amount,true);
  var evidence=packagingEvidence(c,product,parent,code,quantity);
  var result=new LinkedHashMap<String,Object>(com.appnexus.masterdata.domain.product.UnitConversionRules.calculate(
      quantity,evidence.factor(),evidence.targetScale()));
  result.put("baseUnitId",evidence.targetUnitId());
  result.put("version",evidence.productVersion());
  return result;
 }

 /** {@inheritDoc} */
 @Override
 public PackagingEvidence packagingEvidence(VerifiedTenantContext c, String productId, String packagingCode,
     long expectedProductVersion, String quantity) {
  require(c, "read");
  return write(() -> {
   var product=parent(c,productId,false,null);
   if (((Number) product.get("version")).longValue()!=expectedProductVersion) throw changed();
   if (!"ENABLED".equals(product.get("status")) || product.get("archived")!=null
       || "LEGACY_PENDING".equals(product.get("structure"))) throw invalid();
   var amount=com.appnexus.masterdata.domain.product.UnitConversionRules.decimal(quantity,true);
   return packagingEvidence(c,productId,product,packagingCode,amount);
  });
 }

 /** {@inheritDoc} */
 @Override
 public PackagingEvidence skuPackagingEvidence(VerifiedTenantContext c,String productId,String skuId,
     String packagingCode,long expectedProductVersion,String quantity){
  require(c,"read");
  uuid(skuId);
  return write(()->{
   var product=parent(c,productId,false,null);
   long version=((Number)product.get("version")).longValue();
   if(version!=expectedProductVersion)throw changed();
   if(!"ENABLED".equals(product.get("status"))||product.get("archived")!=null
       ||"LEGACY_PENDING".equals(product.get("structure")))throw invalid();
   var sku=statements.skus(tenant(c),productId,products.recordPredicate(c,"read")).stream()
       .filter(row->skuId.equals(row.get("id"))&&effective(row.get("effective")))
       .findFirst().orElseThrow(ProductStructureService::invalid);
   var amount=com.appnexus.masterdata.domain.product.UnitConversionRules.decimal(quantity,true);
   if(!(sku.get("measurementConfig") instanceof String json)){
    return packagingEvidence(c,productId,product,packagingCode,amount);
   }
   var configuration=(Map<?,?>)measurementContent.decode(json);
   return packagingEvidence(c,productId,version,(String)configuration.get("inventoryUnitId"),
       configuration.get("packaging"),packagingCode,amount);
  });
 }

 private PackagingEvidence packagingEvidence(VerifiedTenantContext c, String productId,
     Map<String,Object> parent, String code, BigDecimal quantity) {
  return packagingEvidence(c,productId,((Number)parent.get("version")).longValue(),
      (String)parent.get("unitId"),measurementConfiguration(parent).get("packaging"),code,quantity);
 }

 private PackagingEvidence packagingEvidence(VerifiedTenantContext c,String productId,long version,
     String base,Object rows,String code,BigDecimal quantity){
  if (!(rows instanceof List<?> packaging) || base==null) throw invalid();

  int scale=requireMeasurementUnit(c,base);
  for (Object value:packaging) {
   if (value instanceof Map<?,?> pack && Objects.equals(code,pack.get("code"))) {
    String source=(String) pack.get("unitId");
    int sourceScale=requireMeasurementUnit(c,source);
    if (quantity.stripTrailingZeros().scale()>sourceScale) throw invalid();
    var factor=com.appnexus.masterdata.domain.product.UnitConversionRules.decimal((String) pack.get("quantity"),true);
    return new PackagingEvidence(productId,code,version,
        source,base,factor,scale,quantity.multiply(factor));
   }
  }
  throw invalid();
 }

 /** 在父锁内校验并持久化一次性计量设置，失败由创建事务整体回滚。 */
 private void initializeSkuMeasurement(VerifiedTenantContext c,String product,String sku,
     com.appnexus.masterdata.api.product.ProductMeasurement.SkuConfiguration configuration){
  if(configuration==null)return;
  if(statements.initializeSkuMeasurement(tenant(c),product,sku,encodeSkuMeasurement(c,configuration))!=1)throw changed();
 }

 /** 统一校验新增与编辑计量配置，单位和精度均由服务端目录决定。 */
 private String encodeSkuMeasurement(VerifiedTenantContext c,
     com.appnexus.masterdata.api.product.ProductMeasurement.SkuConfiguration configuration){
  String stock=configuration.inventoryUnitId();
  String pricing=configuration.pricingUnitId();
  int scale=requireMeasurementUnit(c,stock);
  requireMeasurementUnit(c,pricing);
  if(!stock.equals(pricing)&&!statements.hasMeasurementConversion(tenant(c),pricing,stock))throw invalid();
  if(configuration.packaging()==null||configuration.packaging().size()>200)throw invalid();
  var codes=new HashSet<String>();
  for(var pack:configuration.packaging()){
   if(pack==null||pack.code()==null||!pack.code().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")||!codes.add(pack.code())
       ||pack.name()==null||pack.name().isBlank()||pack.name().length()>200||stock.equals(pack.unitId()))throw invalid();
   requireMeasurementUnit(c,pack.unitId());
   var quantity=com.appnexus.masterdata.domain.product.UnitConversionRules.decimal(pack.quantity(),true);
   if(quantity.stripTrailingZeros().scale()>scale)throw invalid();
  }
  return measurementContent.encode(Map.of("inventoryUnitId",stock,"pricingUnitId",pricing,"packaging",configuration.packaging()));
 }

 /** {@inheritDoc} */
 @Override
 public Object saveSkuMeasurement(VerifiedTenantContext c,String product,String sku,
     com.appnexus.masterdata.api.product.ProductMeasurement.SkuChange input){
  require(c,"manage_structure");
  require(c,"read");
  uuid(sku);
  if(input==null||input.rowVersion()==null||input.rowVersion()<0||input.measurement()==null)throw invalid();
  return write(()->{
   var parent=parent(c,product,true,input.version());
   var row=statements.skus(tenant(c),product,products.recordPredicate(c,"read")).stream()
       .filter(candidate->sku.equals(candidate.get("id"))).findFirst().orElseThrow(ProductStructureService::changed);
   // 已登记货号可能已有库存，保留生效库存单位，避免旧余额按新单位解释。
   String stock=row.get("measurementConfig") instanceof String json
       ? (String)((Map<?,?>)measurementContent.decode(json)).get("inventoryUnitId")
       : (String)parent.get("unitId");
   if(!Objects.equals(stock,input.measurement().inventoryUnitId()))throw invalid();
   var before=products.extensionSnapshot(c,product);
   String content=encodeSkuMeasurement(c,input.measurement());
   if(statements.updateSkuMeasurement(tenant(c),product,sku,input.rowVersion(),content,actor(c))!=1)throw changed();
   finish(c,product,input.version(),"SKU_MEASUREMENT_UPDATE",before);
   return Map.of("version",input.version()+1,"rowVersion",input.rowVersion()+1);
  });
 }
 /** {@inheritDoc} */
 @Override
 public Object saveSkuBatchTemplate(VerifiedTenantContext c,String product,String sku,
     com.appnexus.masterdata.api.product.ProductStructureCommands.SkuBatchTemplate input){
  require(c,"manage_structure");
  uuid(sku);
  if(input==null||input.rowVersion()==null||input.rowVersion()<0)throw invalid();
  if(input.templateVersionId()!=null)uuid(input.templateVersionId());
  return write(()->{
   parent(c,product,true,input.version());
   var before=products.extensionSnapshot(c,product);
   if(statements.updateSkuBatchTemplate(tenant(c),product,sku,input.rowVersion(),input.templateVersionId(),actor(c))!=1){
    throw changed();
   }
   finish(c,product,input.version(),"SKU_BATCH_TEMPLATE_UPDATE",before);
   return Map.of("version",input.version()+1,"rowVersion",input.rowVersion()+1);
  });
 }

 /** {@inheritDoc} */
 @Override
 public SkuBatchRule skuBatchRule(VerifiedTenantContext c,String product,String sku,long version){
  return write(()->{
   var binding=batchBinding(c,product,sku,version);
   var fields=binding.fields().stream().map(field->new BatchField((String)field.get("itemId"),
       (String)field.get("name"),(String)field.get("data_type"),effective(field.get("requiredFlag")))).toList();
   return new SkuBatchRule(binding.version(),fields);
  });
 }

 /** {@inheritDoc} */
 @Override
 public String validateSkuBatch(VerifiedTenantContext c,String product,String sku,long version,String batchNo,
     String templateVersionId,List<com.appnexus.masterdata.api.product.ProductMainData.Value> values){
  return write(()->{
   var binding=batchBinding(c,product,sku,version);
   if(!Objects.equals(templateVersionId,binding.version()))throw changed();
   if(binding.version()==null){
    if(values!=null&&!values.isEmpty())throw invalid();
    return null;
   }
   if(batchNo==null||batchNo.isBlank())throw invalid();
   var snapshot=validatedAttributeSnapshot(c,binding.version(),binding.fields(),values);
   return measurementContent.encode(Map.of("templateVersionId",binding.version(),"fields",snapshot));
  });
 }

 /** 普通货号与批次共用固定模板校验，持久化的显示名称来自服务端字段定义。 */
 private List<Map<String,Object>> validatedAttributeSnapshot(VerifiedTenantContext c,String template,
     List<Map<String,Object>> fields,List<com.appnexus.masterdata.api.product.ProductMainData.Value> values){
   var byId=new LinkedHashMap<String,Map<String,Object>>();
   for(var row:fields)byId.put((String)row.get("itemId"),row);
   var preparation=new ProductAttributePreparation(new ProductAttributePreparationPort(){
    @Override public List<Map<String,Object>> definitions(VerifiedTenantContext context,String config,String item){
     var row=byId.get(item);
     return row==null?List.of():List.of(row);
    }
    @Override public int optionCount(VerifiedTenantContext context,String revision,String option){
     return statements.validOptionCount(tenant(context),revision,option);
    }
    @Override public void validateRule(String type,String validation,String value,int count){
     AttributeRulePolicy.value(type,ruleContent.decode(validation),value,count);
    }
   });
   var prepared=preparation.prepare(c,template,values);
   var supplied=new HashSet<String>();
   for(var item:prepared)supplied.add(item.source().templateItemId());
   if(fields.stream().anyMatch(field->effective(field.get("requiredFlag"))
       &&!supplied.contains(field.get("itemId"))))throw invalid();
   var snapshot=new ArrayList<Map<String,Object>>();
   for(var item:prepared){
    var value=item.source();
    var field=byId.get(value.templateItemId());
    var entry=new LinkedHashMap<String,Object>();
    entry.put("templateItemId",value.templateItemId());
    entry.put("name",field.get("name"));
    entry.put("dataType",item.type());
    entry.put("value",value.value());
    entry.put("optionIds",value.optionIds()==null?List.of():value.optionIds());
    entry.put("customValues",value.customValues()==null?List.of():value.customValues());
    snapshot.add(entry);
   }
   return snapshot;
 }

 private record BatchBinding(String version,List<Map<String,Object>> fields){}

 /** 同一事务读取生效 SKU 和它采用的固定模板，防止把别的版本字段当成当前规则。 */
 private BatchBinding batchBinding(VerifiedTenantContext c,String product,String sku,long version){
  resolveIdentity(c,product,IdentityKind.SKU,sku,version);
  var predicate=products.recordPredicate(c,"read");
  var row=statements.skus(tenant(c),product,predicate).stream()
      .filter(candidate->sku.equals(candidate.get("id"))).findFirst().orElseThrow(ProductStructureService::changed);
  String template=(String)row.get("batchTemplateVersionId");
  if(template==null)return new BatchBinding(null,List.of());
  var fields=statements.skuBatchFields(tenant(c),product,sku,template,predicate);
  if(fields.isEmpty())throw changed();
  return new BatchBinding(template,fields);
 }

 /** {@inheritDoc} */
 @Override public Object saveSpecificationTemplate(VerifiedTenantContext c,String product,
     com.appnexus.masterdata.api.product.ProductStructureCommands.SpecificationTemplate input){
  require(c,"manage_structure");
  if(input==null||input.version()==null)throw invalid();
  if(input.templateVersionId()!=null)uuid(input.templateVersionId());
  return write(()->{
   var parent=parent(c,product,true,input.version());
   if(Objects.equals(parent.get("specificationTemplateVersionId"),input.templateVersionId())){
    return Map.of("version",input.version());
   }
   var before=products.extensionSnapshot(c,product);
   if(statements.updateSpecificationTemplate(tenant(c),product,input.templateVersionId())!=1)throw changed();
   if(input.templateVersionId()!=null&&"STANDARD".equals(parent.get("specification_mode"))){
    var fields=statements.specificationTemplateFields(tenant(c),product,input.templateVersionId(),products.recordPredicate(c,"read"));
    if(fields.isEmpty()||fields.stream().anyMatch(field->!Set.of("INTEGER","DECIMAL","BOOLEAN","SINGLE_SELECT")
        .contains(field.get("data_type"))))throw invalid();
   }
   finish(c,product,input.version(),"SPECIFICATION_TEMPLATE_UPDATE",before);
   return Map.of("version",input.version()+1);
  });
 }

 /** {@inheritDoc} */
 @Override public Object saveSkuAttributeTemplate(VerifiedTenantContext c,String product,
     com.appnexus.masterdata.api.product.ProductStructureCommands.SkuAttributeTemplate input){
  require(c,"manage_structure");
  if(input==null||input.version()==null)throw invalid();
  if(input.templateVersionId()!=null)uuid(input.templateVersionId());
  return write(()->{
   var parent=parent(c,product,true,input.version());
   if(Objects.equals(parent.get("skuAttributeTemplateVersionId"),input.templateVersionId())){
    return Map.of("version",input.version());
   }
   var before=products.extensionSnapshot(c,product);
   if(statements.updateSkuAttributeTemplate(tenant(c),product,input.templateVersionId())!=1)throw changed();
   finish(c,product,input.version(),"SKU_ATTRIBUTE_TEMPLATE_UPDATE",before);
   return Map.of("version",input.version()+1);
  });
 }

 /** {@inheritDoc} */
 @Override public Object skuAttributes(VerifiedTenantContext c,String product,String sku){
  require(c,"read");
  uuid(sku);
  return write(()->{
   var parent=parent(c,product,false,null);
   var access=products.recordPredicate(c,"read");
   var row=skuAttributeBinding(c,product,sku,access);
   var template=(String)row.get("templateVersionId");
   var result=new LinkedHashMap<String,Object>();
   result.put("version",parent.get("version"));
   result.put("rowVersion",row.get("rowVersion"));
   result.put("templateVersionId",template);
   result.put("fields",template==null?List.of():skuAttributeView(
       statements.skuAttributeFields(tenant(c),product,sku,template,access),"SKU"));
   Object values=row.get("attributeValues") instanceof String json
       ? ((Map<?,?>)measurementContent.decode(json)).get("fields") : List.of();
   result.put("values",values);
   return result;
  });
 }

 /** {@inheritDoc} */
 @Override public Object saveSkuAttributes(VerifiedTenantContext c,String product,String sku,
     com.appnexus.masterdata.api.product.ProductStructureCommands.SkuAttributes input){
  require(c,"manage_structure");
  require(c,"read");
  uuid(sku);
  if(input==null||input.rowVersion()==null||input.rowVersion()<0||input.values()==null)throw invalid();
  uuid(input.templateVersionId());
  return write(()->{
   parent(c,product,true,input.version());
   var access=products.recordPredicate(c,"read");
   var row=skuAttributeBinding(c,product,sku,access);
   if(!input.templateVersionId().equals(row.get("templateVersionId")))throw changed();
   var fields=statements.skuAttributeFields(tenant(c),product,sku,input.templateVersionId(),access);
   if(fields.isEmpty())throw changed();
   var values=validatedAttributeSnapshot(c,input.templateVersionId(),fields,input.values());
   var before=products.extensionSnapshot(c,product);
   String content=measurementContent.encode(Map.of("fields",values));
   if(statements.updateSkuAttributes(tenant(c),product,sku,input.rowVersion(),input.templateVersionId(),content,actor(c))!=1){
    throw changed();
   }
   finish(c,product,input.version(),"SKU_ATTRIBUTES_UPDATE",before);
   return Map.of("version",input.version()+1,"rowVersion",input.rowVersion()+1);
  });
 }

 private Map<String,Object> skuAttributeBinding(VerifiedTenantContext c,String product,String sku,
     com.appnexus.platform.authorization.RowAccessControl.Predicate access){
  return statements.skuAttributeBinding(tenant(c),product,sku,access).stream()
      .findFirst().orElseThrow(ProductStructureService::changed);
 }

 /** 固定属性项与枚举选项共用一条范围查询，按模板顺序组装页面字段。 */
 private List<Map<String,Object>> skuAttributeView(List<Map<String,Object>> rows,String purpose){
  var fields=new LinkedHashMap<String,Map<String,Object>>();
  var options=new LinkedHashMap<String,List<Map<String,Object>>>();
  for(var row:rows){
   String id=(String)row.get("itemId");
   if(!fields.containsKey(id)){
    var field=new LinkedHashMap<String,Object>();
    for(String key:List.of("attributeId","name","groupCode","groupName","unitId","unitCode","unitName")){
     field.put(key,row.get(key));
    }
    field.put("templateItemId",id);
    field.put("revisionId",row.get("revision"));
    field.put("purpose",purpose);
    field.put("dataType",row.get("data_type"));
    field.put("numericScale",row.get("numeric_scale"));
    field.put("validationJson",row.get("validation"));
    field.put("requiredFlag",effective(row.get("requiredFlag")));
    field.put("allowCustom",effective(row.get("allow_custom")));
    field.put("searchable",false);
    options.put(id,new ArrayList<>());
    field.put("options",options.get(id));
    fields.put(id,field);
   }
   if(row.get("optionId")!=null)options.get(id).add(Map.of("id",row.get("optionId"),"code",row.get("optionCode"),
       "label",row.get("optionLabel"),"enabled",effective(row.get("optionEnabled"))));
  }
  return List.copyOf(fields.values());
 }

 private Map<?,?> measurementConfiguration(Map<String,Object> parent){
  Object raw=parent.get("measurementConfig");
  return raw instanceof String json?(Map<?,?>)measurementContent.decode(json):
      Map.of("units",List.of(),"packaging",List.of());
 }

 private int requireMeasurementUnit(VerifiedTenantContext c,String unit){
  uuid(unit);
  Integer precision=statements.unitPrecision(tenant(c),unit);
  if(precision==null)throw invalid();
  return precision;
 }
 private final ProductStructureRepository statements;
 private final AttributeRuleContent ruleContent;
 private final ProductContent measurementContent;

 private final AccessAuthorization access;private final ProductStructureTransaction tx;private final ProductAggregateAccess products;
 /** 商品结构完整用例装配。
  * @param access 权威功能授权公开端口
  * @param transaction 同数据源完整结构事务，冲突沿用原错误码
  * @param products 父商品授权、冻结状态、快照与历史协作端口
  * @param statements 固定结构仓储，不接受数据库表名
  * @param ruleContent 固定规则解析端口，业务校验由领域执行
  */
 public ProductStructureService(AccessAuthorization access,ProductStructureTransaction transaction,ProductAggregateAccess products,ProductStructureRepository statements,AttributeRuleContent ruleContent,ProductContent measurementContent){this.measurementContent=measurementContent;this.ruleContent=ruleContent;this.statements=statements;this.access=access;this.tx=transaction;this.products=products;}
 
 
 
 
 
 /** 查询规格/SKU/组成，effective按商品与规格分层状态计算，不把SKU.enabled当可用事实。 */
 public Object read(VerifiedTenantContext c,String id){require(c,"read");var p=parent(c,id,false,null);var access=products.recordPredicate(c,"read");var out=new LinkedHashMap<String,Object>();out.put("mode",p.get("specification_mode"));out.put("form",p.get("product_form"));out.put("version",p.get("version"));attachSpecificationTemplate(c,id,p,access,out);out.put("specifications",statements.specifications(tenant(c),id,access));out.put("skus",statements.skus(tenant(c),id,access).stream().map(this::skuView).toList());out.put("components",statements.components(tenant(c),id,access));attachSpecificationValues(c,id,access,out);attachAudit(c,out,"specifications",ProductStructureRepository.Resource.SPECIFICATION);attachAudit(c,out,"skus",ProductStructureRepository.Resource.SKU);attachAudit(c,out,"components",ProductStructureRepository.Resource.COMPONENT);return out;}
 /** 已确认版本的字段独立于类目默认配置；未确认时新规格不填模板字段。 */
 private void attachSpecificationTemplate(VerifiedTenantContext c,String product,Map<String,Object> parent,
     com.appnexus.platform.authorization.RowAccessControl.Predicate access,Map<String,Object> out){
  if(parent.get("specificationTemplateVersionId") instanceof String template){
   out.put("specificationTemplateVersionId",template);
   out.put("specificationFields",skuAttributeView(statements.specificationTemplateFields(tenant(c),product,template,access),"SPEC"));
  }else{
   out.put("specificationFields",List.of());
  }
 }

 /** 已确认规格轴时，原规格标识和 SKU 关系不变，值采用独立版本快照。 */
 private Map<String,String> createBoundSpecification(VerifiedTenantContext c,String product,String config,String mode,
     String template,Specification input,List<SpecValue> values,Map<String,Object> before){
  var definitions=statements.specificationTemplateFields(tenant(c),product,template,products.recordPredicate(c,"read"));
  if(definitions.isEmpty())throw changed();
  boolean standard=mode.equals("STANDARD");
  var fieldTypes=new LinkedHashMap<String,String>();
  for(var row:definitions)fieldTypes.put((String)row.get("itemId"),(String)row.get("data_type"));
  if(standard&&(fieldTypes.size()!=values.size()||fieldTypes.values().stream()
      .anyMatch(type->!Set.of("INTEGER","DECIMAL","BOOLEAN","SINGLE_SELECT").contains(type))))throw invalid();
  if(!standard&&values.isEmpty()&&(input.description()==null||input.description().isBlank()))throw invalid();
  var supplied=new ArrayList<com.appnexus.masterdata.api.product.ProductMainData.Value>();
  for(var value:values){
   if(value==null||value.templateItemId()==null)throw invalid();
   if(standard&&"SINGLE_SELECT".equals(fieldTypes.get(value.templateItemId()))){
    if(value.value()==null||value.optionIds()!=null&&!value.optionIds().isEmpty()
        ||value.customValues()!=null&&!value.customValues().isEmpty())throw invalid();
    supplied.add(new com.appnexus.masterdata.api.product.ProductMainData.Value(
        value.templateItemId(),null,List.of(value.value()),List.of()));
   }else supplied.add(new com.appnexus.masterdata.api.product.ProductMainData.Value(
       value.templateItemId(),value.value(),value.optionIds(),value.customValues()));
  }
  var validated=validatedAttributeSnapshot(c,template,definitions,supplied);
  var options=new HashMap<String,String>();
  var units=new HashMap<String,String>();
  for(var row:definitions){
   if(row.get("optionId") instanceof String option)options.put(option,(String)row.get("optionLabel"));
   units.put((String)row.get("itemId"),(String)row.get("unitCode"));
  }
  var byItem=new HashMap<String,Map<String,Object>>();
  for(var field:validated)byItem.put((String)field.get("templateItemId"),field);
  var snapshot=new ArrayList<Map<String,Object>>();
  var canonical=new StringBuilder(template);
  for(String id:fieldTypes.keySet()){
   var field=byItem.get(id);
   if(field==null)continue;
   String type=(String)field.get("dataType"),raw=(String)field.get("value");
   var view=new LinkedHashMap<String,Object>();
   view.put("templateItemId",id);
   view.put("name",field.get("name"));
   view.put("dataType",type);
   view.put("unitCode",units.get(id));
   var choices=new ArrayList<Map<String,Object>>();
   for(Object option:(List<?>)field.get("optionIds"))choices.add(Map.of("optionId",option,"label",options.get(option)));
   for(Object text:(List<?>)field.get("customValues"))choices.add(Map.of("label",text,"customText",text));
   view.put("choices",choices);
   if(raw!=null){
    String valueField=switch(type){
     case "INTEGER"->"integerValue";
     case "DECIMAL"->"decimalValue";
     case "BOOLEAN"->"booleanValue";
     case "DATE"->"dateValue";
     default->"textValue";
    };
    view.put(valueField,"BOOLEAN".equals(type)?Boolean.valueOf(raw):raw);
   }
   if(standard){
    String value=raw==null ? String.valueOf(((List<?>)field.get("optionIds")).getFirst())
        : switch(type){
          case "INTEGER"->Long.toString(Long.parseLong(raw));
          case "DECIMAL"->new BigDecimal(raw).stripTrailingZeros().toPlainString();
          default->raw;
        };
    canonical.append(id).append(':').append(value.length()).append(':').append(value).append(';');
   }
   snapshot.add(view);
  }
  String signature=standard?canonical.toString():null;
  byte[] hash=null;
  if(signature!=null){
   try{hash=java.security.MessageDigest.getInstance("SHA-256").digest(signature.getBytes(StandardCharsets.UTF_8));}
   catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
  }
  String id=UUID.randomUUID().toString();
  statements.createSpecification(id,tenant(c),product,config,mode,input.name().trim(),input.description(),signature,hash,actor(c),actor(c));
  if(statements.initializeSpecificationValues(tenant(c),product,id,template,measurementContent.encode(Map.of("values",snapshot)))!=1)throw changed();
  finish(c,product,input.version(),"SPEC_CREATE",before);
  return Map.of("id",id);
 }

 /** 新规格只使用 SPU 确认的规格轴；无模板的自定义规格可仅使用说明。 */
 public Object createSpecification(VerifiedTenantContext c,String product,Specification input){
  require(c,"manage_structure");
  if(input==null||input.version()==null||input.name()==null||input.name().isBlank()
      ||input.name().length()>200||input.description()!=null&&input.description().length()>2000)throw invalid();
  return write(()->{
   var parent=parent(c,product,true,input.version());
   String mode=(String)parent.get("specification_mode"),config=(String)parent.get("configId");
   if(!Set.of("STANDARD","CUSTOM").contains(mode)||config==null)throw invalid();
   var values=input.values()==null?List.<SpecValue>of():input.values();
   if(values.size()>200)throw invalid();
   var before=products.extensionSnapshot(c,product);
   if(parent.get("specificationTemplateVersionId") instanceof String template){
    return createBoundSpecification(c,product,config,mode,template,input,values,before);
   }
   if(!"CUSTOM".equals(mode)||!values.isEmpty()||input.description()==null||input.description().isBlank())throw invalid();
   String id=UUID.randomUUID().toString();
   statements.createSpecification(id,tenant(c),product,config,mode,input.name().trim(),input.description(),null,null,actor(c),actor(c));
   finish(c,product,input.version(),"SPEC_CREATE",before);
   return Map.of("id",id);
  });
 }
 @SuppressWarnings("unchecked")
 private void attachSpecificationValues(VerifiedTenantContext c,String product,com.appnexus.platform.authorization.RowAccessControl.Predicate access,Map<String, Object> output) {
  String tenantId = tenant(c);
  for (var spec : (List<Map<String, Object>>) output.get("specifications")) {
   if(spec.remove("templateValues") instanceof String json){
    spec.put("values",((Map<?,?>)measurementContent.decode(json)).get("values"));
    continue;
   }
   var values = statements.specificationValues(tenantId,spec.get("id").toString(),product,access);
   for (var value : values) {
    var choices = new ArrayList<>(statements.customChoices(tenantId,value.get("id").toString(),product,access));
    if (value.get("optionId") != null) {
     choices.addAll(statements.standardChoices(tenantId, value.get("optionId").toString()));
    }
    value.put("choices", choices);
   }
   spec.put("values", values);
  }
 }
 /** 只有显式动作才创建SKU；父子归属复合外键兜底，SKU身份创建后不可修改。 */
 public Object createSku(VerifiedTenantContext c,String product,Sku input){
  require(c,"manage_structure");
  if(input==null||input.version()==null||input.code()==null
      ||!input.code().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))throw invalid();
  uuid(input.specificationId());
  return write(()->{
   parent(c,product,true,input.version());
   var before=products.extensionSnapshot(c,product);
   if(statements.specificationCount(tenant(c),product,input.specificationId())!=1)throw invalid();
   String id=UUID.randomUUID().toString();
   statements.createSku(id,tenant(c),product,input.specificationId(),input.code(),actor(c),actor(c));
   initializeSkuMeasurement(c,product,id,input.measurement());
   finish(c,product,input.version(),"SKU_CREATE",before);
   return Map.of("id",id);
  });
 }
 /** 组合写入共用租户图锁，防止并发互加形成环；数量按组成基础单位精度，不自动换算。 */
 public Object addComponent(VerifiedTenantContext c,String product,Component i){require(c,"manage_structure");if(i==null||i.version()==null||i.componentVersion()==null||i.quantity()==null||!i.quantity().matches("(0|[1-9][0-9]{0,17})([.][0-9]{1,12})?"))throw invalid();uuid(i.componentId());var quantity=new BigDecimal(i.quantity());if(quantity.signum()<=0||product.equals(i.componentId()))throw invalid();return write(()->{graphLock(c);var p=parent(c,product,true,i.version());if(!"COMPOSITE".equals(p.get("product_form")))throw invalid();var before=products.extensionSnapshot(c,product);var child=parent(c,i.componentId(),false,null);if(!"ENABLED".equals(child.get("status"))||!i.componentVersion().equals(((Number)child.get("version")).longValue())||child.get("unitId")==null)throw invalid();Integer scale=statements.unitPrecision(tenant(c),child.get("unitId").toString());if(scale==null||quantity.scale()>scale)throw invalid();var seen=new HashSet<String>();var queue=new ArrayDeque<String>();queue.add(i.componentId());while(!queue.isEmpty()){String node=queue.remove();if(node.equals(product)||seen.size()>10000)throw invalid();if(!seen.add(node))continue;queue.addAll(statements.componentChildren(tenant(c),node));}var existing=statements.existingComponent(tenant(c),product,i.componentId());if(!existing.isEmpty()){if(Boolean.TRUE.equals(existing.getFirst().get("enabled")))throw invalid();String reused=(String)existing.getFirst().get("id");if(statements.updateComponent(quantity,i.componentVersion(),actor(c),tenant(c),reused,product)!=1)throw changed();finish(c,product,i.version(),"COMPONENT_REPLACE",before);return Map.of("id",reused);}String id=UUID.randomUUID().toString();statements.createComponent(id,tenant(c),product,i.componentId(),quantity,i.componentVersion(),actor(c),actor(c));finish(c,product,i.version(),"COMPONENT_ADD",before);return Map.of("id",id);});}
 /** 子对象启停按父CAS与子CAS原子修改；不能恢复退役规格，不能跨商品操作SKU。 */
 public Object state(VerifiedTenantContext c,String product,String kind,String id,State i){require(c,"manage_structure");uuid(id);if(i==null||i.version()==null||i.rowVersion()==null||i.enabled()==null)throw invalid();if(!Set.of("specification","sku","component").contains(kind))throw invalid();return write(()->{if(kind.equals("component"))graphLock(c);parent(c,product,true,i.version());if(kind.equals("component")&&i.enabled())throw invalid();var before=products.extensionSnapshot(c,product);int n=statements.state(kind,i.enabled(),actor(c),tenant(c),product,id,i.rowVersion());if(n!=1)throw changed();finish(c,product,i.version(),"STRUCTURE_STATE",before);return Map.of("ok",true);});}
 /** 已授权父商品内批量投影四审计，不接受外部表名，查询使用可信租户与已返回行ID。 */
 @SuppressWarnings("unchecked") private void attachAudit(VerifiedTenantContext c,Map<String,Object> output,String key,ProductStructureRepository.Resource resource){for(var row:(List<Map<String,Object>>)output.get(key)){var audit=statements.audit(resource,tenant(c),(String)row.get("id")).stream().map(raw->{var result=new LinkedHashMap<String,Object>(raw);for(String field:List.of("createdAt","updatedAt")){Object value=result.get(field);if(value instanceof java.time.Instant time)result.put(field,time.toString());}return result;}).toList();if(!audit.isEmpty())row.put("audit",audit.getFirst());}}
 private void graphLock(VerifiedTenantContext c){statements.ensureGraphGuard(tenant(c),actor(c),actor(c));statements.lockGraphGuard(tenant(c));}
 private Map<String,Object> parent(VerifiedTenantContext c,String id,boolean lock,Long version){products.authorizeRecord(c,id,lock?"manage_structure":"read",lock);uuid(id);var rows=statements.parent(tenant(c),id,lock,products.recordPredicate(c,lock?"manage_structure":"read"));if(rows.size()!=1)throw changed();var p=rows.getFirst();if(lock)products.ensureNotInApproval(c,id);if(lock&&(version==null||version<0||((Number)p.get("version")).longValue()!=version||"ENABLED".equals(p.get("status"))||p.get("archived")!=null||"LEGACY_PENDING".equals(p.get("structure"))))throw changed();return p;}
 private void finish(VerifiedTenantContext c,String id,long version,String event,Map<String,Object> before){if(statements.advanceParentVersion(actor(c),tenant(c),id,version)!=1)throw changed();products.extensionEvent(c,id,event,before);}
 private void require(VerifiedTenantContext c,String action){if(c==null||!access.hasPermission(AccessAuthorization.Subject.tenant(c.accountId(),c.tenantId(),c.memberId()),"masterdata.product:"+action))throw new MasterData.Rejection(403,"MASTER_DATA_DENIED");}
 private <T>T write(java.util.function.Supplier<T> action){return tx.write(action);}
 private static void uuid(String id){try{if(!UUID.fromString(id).toString().equals(id))throw invalid();}catch(IllegalArgumentException|NullPointerException e){throw invalid();}}
 private static String tenant(VerifiedTenantContext c){return c.tenantId().toString();}private static String actor(VerifiedTenantContext c){return c.accountId().toString();}
 private static MasterData.Rejection invalid(){return new MasterData.Rejection(400,"PRODUCT_STRUCTURE_INVALID");}private static MasterData.Rejection changed(){return new MasterData.Rejection(409,"MASTER_DATA_CHANGED");}
}
