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
   return new ProductIdentity(productId, kind, identityId, specificationId, unitId, version,
       ((Number) identity.get("version")).longValue());
  });
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

 private PackagingEvidence packagingEvidence(VerifiedTenantContext c, String productId,
     Map<String,Object> parent, String code, BigDecimal quantity) {
  Object rows=measurementConfiguration(parent).get("packaging");
  if (!(rows instanceof List<?> packaging) || parent.get("unitId")==null) throw invalid();
  String base=(String) parent.get("unitId");
  int scale=requireMeasurementUnit(c,base);
  for (Object value:packaging) {
   if (value instanceof Map<?,?> pack && Objects.equals(code,pack.get("code"))) {
    String source=(String) pack.get("unitId");
    int sourceScale=requireMeasurementUnit(c,source);
    if (quantity.stripTrailingZeros().scale()>sourceScale) throw invalid();
    var factor=com.appnexus.masterdata.domain.product.UnitConversionRules.decimal((String) pack.get("quantity"),true);
    return new PackagingEvidence(productId,code,((Number) parent.get("version")).longValue(),
        source,base,factor,scale,quantity.multiply(factor));
   }
  }
  throw invalid();
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
 public Object read(VerifiedTenantContext c,String id){require(c,"read");var p=parent(c,id,false,null);var access=products.recordPredicate(c,"read");var out=new LinkedHashMap<String,Object>();out.put("mode",p.get("specification_mode"));out.put("form",p.get("product_form"));out.put("version",p.get("version"));out.put("specifications",statements.specifications(tenant(c),id,access));out.put("skus",statements.skus(tenant(c),id,access));out.put("components",statements.components(tenant(c),id,access));attachSpecificationValues(c,id,access,out);attachAudit(c,out,"specifications",ProductStructureRepository.Resource.SPECIFICATION);attachAudit(c,out,"skus",ProductStructureRepository.Resource.SKU);attachAudit(c,out,"components",ProductStructureRepository.Resource.COMPONENT);return out;}
 /** 标准规格组合值按模板稳定顺序规范化；哈希仅用于唯一索引，碰撞仍拒绝并保留原串。 */
 public Object createSpecification(VerifiedTenantContext c,String product,Specification i){require(c,"manage_structure");if(i==null||i.version()==null||i.name()==null||i.name().isBlank()||i.name().length()>200||i.description()!=null&&i.description().length()>2000)throw invalid();return write(()->{var p=parent(c,product,true,i.version());String mode=(String)p.get("specification_mode"),config=(String)p.get("configId");if(!Set.of("STANDARD","CUSTOM").contains(mode)||config==null)throw invalid();var before=products.extensionSnapshot(c,product);var values=i.values()==null?List.<SpecValue>of():i.values();if(values.size()>200)throw invalid();var prepared=new ArrayList<Object[]>();var canonical=new StringBuilder();
 if(mode.equals("CUSTOM")){if(values.isEmpty()&&(i.description()==null||i.description().isBlank()))throw invalid();}
 else {var definitions=statements.standardDefinitions(tenant(c),config);if(definitions.isEmpty()||definitions.size()!=values.size())throw invalid();var supplied=new HashMap<String,String>();for(var v:values){if(v==null||v.value()==null||supplied.put(v.templateItemId(),v.value())!=null)throw invalid();}
 for(var d:definitions){String item=(String)d.get("item"),revision=(String)d.get("revision"),type=(String)d.get("type"),raw=supplied.get(item),normal=raw;Long integer=null;BigDecimal decimal=null;Boolean bool=null;String option=null;if(raw==null)throw invalid();try{switch(type){case "INTEGER"->{if(!raw.matches("-?(0|[1-9][0-9]*)"))throw invalid();integer=Long.parseLong(raw);normal=integer.toString();}case "DECIMAL"->{if(!raw.matches("-?[0-9]+([.][0-9]+)?"))throw invalid();var n=new BigDecimal(raw);if(n.scale()>((Number)d.get("scale")).intValue()||n.precision()-n.scale()>18)throw invalid();decimal=n;normal=n.stripTrailingZeros().toPlainString();}case "BOOLEAN"->{if(!Set.of("true","false").contains(raw))throw invalid();bool=Boolean.parseBoolean(raw);}case "SINGLE_SELECT"->{uuid(raw);var opts=statements.standardOption(tenant(c),revision,raw);if(opts.size()!=1)throw invalid();option=raw;normal=(String)opts.getFirst().get("option_code");}default->throw invalid();}}catch(NumberFormatException e){throw invalid();}AttributeRulePolicy.value(type,ruleContent.decode((String)d.get("rules")),Set.of("INTEGER","DECIMAL","BOOLEAN").contains(type)?raw:null,type.equals("SINGLE_SELECT")?1:0);canonical.append(item).append(':').append(revision).append(':').append(type).append(':').append(normal.length()).append(':').append(normal).append(';');prepared.add(new Object[]{item,revision,type,integer,decimal,bool,option});}}
 String id=UUID.randomUUID().toString(),signature=mode.equals("STANDARD")?canonical.toString():null;byte[] hash=null;if(signature!=null)try{hash=java.security.MessageDigest.getInstance("SHA-256").digest(signature.getBytes(StandardCharsets.UTF_8));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}statements.createSpecification(id,tenant(c),product,config,mode,i.name().trim(),i.description(),signature,hash,actor(c),actor(c));for(var v:prepared)if(statements.createStandardValue(UUID.randomUUID().toString(),tenant(c),id,config,(String)v[0],(String)v[1],(String)v[2],(Long)v[3],(BigDecimal)v[4],(Boolean)v[5],(String)v[6],actor(c),actor(c),product)!=1)throw changed();if(mode.equals("CUSTOM"))saveCustomValues(c,product,id,config,values);finish(c,product,i.version(),"SPEC_CREATE",before);return Map.of("id",id);});}
 /** 自定义SPEC使用固定修订逐项校验；必填只在建立规格记录时校验，不强迫商品预建规格或SKU。 */
 private void saveCustomValues(VerifiedTenantContext c,String product,String specification,String config,List<SpecValue> values){
  var definitions=statements.customDefinitions(tenant(c),config);
  var supplied=new HashMap<String,SpecValue>();for(var value:values){if(value==null||value.templateItemId()==null||supplied.put(value.templateItemId(),value)!=null)throw invalid();}
  for(var d:definitions){String item=(String)d.get("item"),revision=(String)d.get("revision"),type=(String)d.get("type");var value=supplied.remove(item);if(value==null){if(Boolean.TRUE.equals(d.get("requiredFlag")))throw invalid();continue;}
   String raw=value.value();List<String> options=value.optionIds()==null?List.of():value.optionIds(),customs=value.customValues()==null?List.of():value.customValues();String text=null;java.time.LocalDate date=null;Long integer=null;BigDecimal decimal=null;Boolean bool=null;String option=null;
   if(options.size()+customs.size()>50||options.stream().anyMatch(Objects::isNull)||customs.stream().anyMatch(Objects::isNull)||new HashSet<>(options).size()!=options.size()||new HashSet<>(customs).size()!=customs.size())throw invalid();
   try{switch(type){
    case "TEXT"->{if(raw==null||raw.isBlank()||raw.length()>2000)throw invalid();text=raw;}
    case "DATE"->{if(raw==null||!raw.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw invalid();date=java.time.LocalDate.parse(raw);}
    case "INTEGER"->{if(raw==null||!raw.matches("-?(0|[1-9][0-9]*)"))throw invalid();integer=Long.parseLong(raw);}
    case "DECIMAL"->{if(raw==null||!raw.matches("-?(0|[1-9][0-9]*)([.][0-9]+)?"))throw invalid();var n=new BigDecimal(raw);if(n.scale()>((Number)d.get("scale")).intValue()||n.precision()-n.scale()>18)throw invalid();decimal=n;}
    case "BOOLEAN"->{if(!Set.of("true","false").contains(Objects.toString(raw,"")))throw invalid();bool=Boolean.parseBoolean(raw);}
    case "SINGLE_SELECT","MULTI_SELECT"->{if(raw!=null||options.size()+customs.size()<1||type.equals("SINGLE_SELECT")&&options.size()+customs.size()!=1)throw invalid();}
    default->throw invalid();
   }}catch(NumberFormatException|java.time.DateTimeException e){throw invalid();}
   boolean enumeration=type.equals("SINGLE_SELECT")||type.equals("MULTI_SELECT");if(!enumeration&&(!options.isEmpty()||!customs.isEmpty()))throw invalid();
   for(String selected:options){uuid(selected);if(statements.validOptionCount(tenant(c),revision,selected)!=1)throw invalid();}
   if(!customs.isEmpty()&&!Boolean.TRUE.equals(d.get("allowCustom")))throw invalid();for(String custom:customs)if(custom.isBlank()||custom.length()>2000||custom.chars().anyMatch(Character::isISOControl))throw invalid();
   if(raw!=null&&type.equals("TEXT")&&raw.chars().anyMatch(ch->Character.isISOControl(ch)&&ch!='\n'&&ch!='\r'&&ch!='\t'))throw invalid();
   AttributeRulePolicy.value(type,ruleContent.decode((String)d.get("rules")),enumeration?null:raw,options.size()+customs.size());String valueId=UUID.randomUUID().toString();
   if(statements.createCustomValue(valueId,tenant(c),specification,config,item,revision,type,text,date,integer,decimal,bool,actor(c),actor(c),product)!=1)throw changed();
   for(String selected:options)insertChoice(c,product,valueId,revision,type,selected,null);for(String custom:customs)insertChoice(c,product,valueId,revision,type,null,custom);
  }
  if(!supplied.isEmpty())throw invalid();
 }
 private void insertChoice(VerifiedTenantContext c,String product,String value,String revision,String type,String option,String custom){if(statements.createChoice(UUID.randomUUID().toString(),tenant(c),value,revision,type,option,custom,actor(c),actor(c),product)!=1)throw changed();}
 /** 仅原商品读取权限内投影规格值和固定名称；包含历史退役规格，不追最新模板。 */
 /** 在响应中合并自定义与标准选项，避免修改仓储返回的只读列表。 */
 @SuppressWarnings("unchecked")
 private void attachSpecificationValues(VerifiedTenantContext c,String product,com.appnexus.platform.authorization.RowAccessControl.Predicate access,Map<String, Object> output) {
  String tenantId = tenant(c);
  for (var spec : (List<Map<String, Object>>) output.get("specifications")) {
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
 public Object createSku(VerifiedTenantContext c,String product,Sku i){require(c,"manage_structure");if(i==null||i.version()==null||i.code()==null||!i.code().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))throw invalid();uuid(i.specificationId());return write(()->{parent(c,product,true,i.version());var before=products.extensionSnapshot(c,product);if(statements.specificationCount(tenant(c),product,i.specificationId())!=1)throw invalid();String id=UUID.randomUUID().toString();statements.createSku(id,tenant(c),product,i.specificationId(),i.code(),actor(c),actor(c));finish(c,product,i.version(),"SKU_CREATE",before);return Map.of("id",id);});}
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
