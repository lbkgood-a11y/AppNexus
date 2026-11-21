package com.appnexus.masterdata.application.product;
import com.appnexus.masterdata.domain.product.AttributeRulePolicy;
import com.appnexus.masterdata.api.product.ProductMainData.*;
import com.appnexus.masterdata.api.product.ProductStructureData.*;


import com.appnexus.masterdata.api.masterdata.MasterData;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import com.appnexus.masterdata.api.product.ProductCommandData.Status;
import com.appnexus.masterdata.api.product.ProductCommandData.Version;
import com.appnexus.masterdata.api.product.ProductCommandData.LegacyReview;

import com.appnexus.contracts.operation.OperationPreviewProof;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 商品主档及类型化主档属性聚合；草稿仅校验已填，启用才校验完整性。
 * 所有写入先授权，商品CAS、动态值、四审计和追加历史原子提交；旧商品入口由强制对账切换迁移保护，原表仅用于历史核对。
 */
public final class ProductService implements com.appnexus.masterdata.api.product.ProductManagement, com.appnexus.masterdata.application.product.ProductAggregateAccess {
 private final ProductCommandRepository statements;
 private final ProductRepository records;
 private final ProductAggregateRepository aggregates;
 private final ProductListQueries listQueries;
 private final ProductCatalogQueries catalogQueries;
 private final ProductAuthorization productAccess;
 private final AccessAuthorization access;
 private final OperationPreviewProof previewProof;
 private final ProductExecutionTransaction transaction;
 private final ProductContent content;
 private final ProductCompletenessService completeness;
 private final AttributeRuleContent ruleContent;
 /** 商品主档完整用例装配，禁止在用例中构造技术适配器。
  * @param access 权威功能授权公开端口
  * @param previewProof 原结构预览签名契约
  * @param records 当前租户父记录与CAS输出端口
  * @param statements 固定主档及迁移回执存取
  * @param aggregates 同聚合快照、历史及属性存取
  * @param listQueries 原授权列表及COUNT查询
  * @param catalogQueries 原固定目录与引用验证
  * @param productAccess 统一数据权限执行端口，不自行决定授权
  * @param transaction 原写、只读预览及10秒迁移事务
  * @param content 保持原JSON配置与顺序的事实编码
  * @param ruleContent 固定规则解析，业务校验仍由领域执行
  * @param completeness 纯应用完整性协调，不由存储适配器决定状态
  */
 public ProductService(AccessAuthorization access,OperationPreviewProof previewProof,ProductRepository records,ProductCommandRepository statements,ProductAggregateRepository aggregates,ProductListQueries listQueries,ProductCatalogQueries catalogQueries,ProductAuthorization productAccess,ProductExecutionTransaction transaction,ProductContent content,AttributeRuleContent ruleContent,ProductCompletenessService completeness){this.completeness=completeness;this.access=access;this.previewProof=previewProof;this.records=records;this.statements=statements;this.aggregates=aggregates;this.listQueries=listQueries;this.catalogQueries=catalogQueries;this.productAccess=productAccess;this.transaction=transaction;this.content=content;this.ruleContent=ruleContent;}

 /**
  * 商品分页；编码/名称分别筛选，未归档默认可见，真实total不以固定100条代替。
  * @param name 名称字面包含，最多200字符，默认空
  * @param code 编码字面包含，最多64字符，默认空
  * @param categoryId 可选分类UUID，默认限定本级，只缩小原租户范围
  * @param form 可选PHYSICAL/SERVICE
  * @param status 可选DRAFT/ENABLED/DISABLED
  * @param archived 默认false只看未归档，true只看归档
  * @param page 页码1起，默认1，最大1000000
  * @param size 每页1至100，默认20
  */
 public Object list(VerifiedTenantContext c,String name,String code,String categoryId,String form,String status,boolean archived,int page,int size){return list(c,name,code,categoryId,form,status,archived,page,size,null,null);}
 public Object list(VerifiedTenantContext c,String name,String code,String categoryId,String form,String status,boolean archived,int page,int size,String configId,String filtersJson){
  return list(c,name,code,categoryId,form,status,archived,page,size,configId,filtersJson,false);
 }
 /** {@inheritDoc} */
 public Object list(VerifiedTenantContext c,String name,String code,String categoryId,String form,String status,boolean archived,int page,int size,String configId,String filtersJson,boolean includeDescendants){
  require(c,"read");return listQueries.list(c,name,code,categoryId,form,status,archived,page,size,configId,filtersJson,includeDescendants);
 }
 /** 保留内部调用入口的默认分页；HTTP参数由固定Controller声明。 */
 public Object list(VerifiedTenantContext c){return list(c,"","",null,null,null,false,1,20);}

 /**
  * 商品原读取权限下的分类/单位候选；不要求建档用户获得配置维护权限。
  * @param kind 仅category/unit，不能选择任意表
  * @param keyword 字面编码/名称搜索，最多200字符
  * @param page 页码1起 @param size 每页1至100
  */
 public Object options(VerifiedTenantContext c,String kind,String keyword,int page,int size){return catalogReads().options(c,kind,keyword,page,size);}
 /** 固定分类的版本目录，不自动追最新配置。 */
 public Object configurations(VerifiedTenantContext c,String categoryId,int page,int size){return catalogReads().configurations(c,categoryId,page,size);}
 /** 固定版本字段目录，包含历史定义但不授予写权限。 */
 public Map<String,Object> configuration(VerifiedTenantContext c,String id){return catalogReads().configuration(c,id);}
 /** 组合公开读取用例与固定目录SQL，不在应用反向获取技术实现。 */
 private com.appnexus.masterdata.application.product.ProductCatalogQueryService catalogReads(){return new com.appnexus.masterdata.application.product.ProductCatalogQueryService(catalogQueries,access);}
 /** 授权详情，包括固定配置、动态值、completionIssues与可信审计，不能以列表快照替代。 */
 public Map<String,Object> read(VerifiedTenantContext c,String id){require(c,"read");uuid(id);authorizeRecord(c,id,"read",false);return snapshot(c,id);}
 /** 创建仅DRAFT；已填始终合法，缺必填不阻止草稿；返回数据库真实版本和完整性。 */
 public Map<String,Object> create(VerifiedTenantContext c,Input i){
  return mainData().create(c,i);
 }
 /** 普通修改不改变结构；启用态仍完整才允许保存，失败保留原审计与全部值。 */
 public Map<String,Object> update(VerifiedTenantContext c,String id,Update input){return mainData().update(c,id,input);}
 /** 主档技术协作者适配：复杂属性保存、快照和事件尚未全部迁出，应用仅依赖本侧端口。 */
 private com.appnexus.masterdata.application.product.ProductMainService mainData(){
  return new com.appnexus.masterdata.application.product.ProductMainService(new com.appnexus.masterdata.application.product.ProductMainPort(){
   private Map<String,Object> before;
   @Override public <T>T atomic(java.util.function.Supplier<T> work){return write(work);}
   @Override public void references(VerifiedTenantContext c,Input input){productAccess.target(c,input.organizationId(),"create",true);validateReferences(c,input.categoryId(),input.configId(),input.productForm(),input.unitId(),true);}
   @Override public void insert(VerifiedTenantContext c,String id,Input input){
    statements.createProduct(id,tenant(c),input.code().trim(),input.name().trim(),input.shortName(),input.categoryId(),input.configId(),input.productForm(),input.unitId(),input.description(),input.brand(),input.origin(),Objects.toString(input.barcode(),""),input.organizationId(),actor(c),actor(c));
   }
   @Override public Map<String,Object> lock(VerifiedTenantContext c,String id,long version){authorizeRecord(c,id,"update",true);before=locked(c,id,version);return before;}
   @Override public void unfrozen(VerifiedTenantContext c,String id){ensureNotInApproval(c,id);}
   @Override public void updateFields(VerifiedTenantContext c,String id,Update input){productAccess.target(c,input.organizationId(),"update",false);
    statements.updateMainFields(input.name().trim(),input.shortName(),input.description(),input.brand(),input.origin(),input.barcode(),input.organizationId(),tenant(c),id);
   }
   @Override public void values(VerifiedTenantContext c,String id,String config,List<Value> values){saveValues(c,id,config,values);}
   @Override public List<String> completeness(VerifiedTenantContext c,String id){return refreshCompleteness(c,id);}
   @Override public Map<String,Object> finish(VerifiedTenantContext c,String id,Long version){
    if(version!=null)bump(c,id,version);event(c,id,version==null?"CREATE":"UPDATE",version==null?null:before);return snapshot(c,id);
   }
  },access);
 }

 /** 三态合法转换；DRAFT不能直接停用，普通写不能通过启停接口修改结构。 */
 public Map<String,Object> updateStatus(VerifiedTenantContext c,String id,Status input){
  var service=lifecycle();
  service.authorize(c,id,input==null?null:input.version(),input==null?null:input.status(),false);
  return write(()->{authorizeRecord(c,id,"set_status",true);return service.status(c,id,input.version(),input.status());});
 }
 /** 人工归档/恢复仅DRAFT，保留编码和历史，不运行自动清理。
  * @param c 可信主体与租户 @param id 当前租户商品UUID @param input 原并发版本 @param archived 显式归档或恢复 @return 当前真实快照
  */
 public Map<String,Object> archive(VerifiedTenantContext c,String id,Version input,boolean archived){
  var service=lifecycle();service.authorize(c,id,input==null?null:input.version(),null,true);
  return write(()->{authorizeRecord(c,id,"manage_draft",true);return service.archive(c,id,input.version(),archived);});
 }
 /** 过渡输出适配；父锁和复杂快照协作者仍由原商品适配承担，禁止应用反向引用本类。 */
 private com.appnexus.masterdata.application.product.ProductLifecycleService lifecycle(){
  return new com.appnexus.masterdata.application.product.ProductLifecycleService(new com.appnexus.masterdata.application.product.ProductLifecyclePort(){
   private Map<String,Object> before;
   @Override public State lock(VerifiedTenantContext context,String id,long version){
    before=locked(context,id,version);
    return new State((String)before.get("status"),before.get("archivedAt")!=null,(String)before.get("structureState"));
   }
   @Override public void requireUnfrozen(VerifiedTenantContext context,String id){ensureNotInApproval(context,id);}
   @Override public void requireApproved(VerifiedTenantContext context,String id,long version){
    var reviews=statements.approvalStates(tenant(context),id);
    if(!reviews.isEmpty()&&(!"APPROVED".equals(reviews.getFirst().get("state"))||((Number)reviews.getFirst().get("product_version")).longValue()!=version))throw problem("PRODUCT_APPROVAL_REQUIRED");
   }
   @Override public void requireComplete(VerifiedTenantContext context,String id){
    validateReferences(context,(String)before.get("categoryId"),(String)before.get("configId"),(String)before.get("productForm"),(String)before.get("unitId"),false);
    if(!refreshCompleteness(context,id).isEmpty())throw new MasterData.Rejection(400,"PRODUCT_COMPLETION_REQUIRED");
   }
   @Override public void status(VerifiedTenantContext context,String id,String target){
    statements.setProductStatus(target,tenant(context),id);
   }
   @Override public void archive(VerifiedTenantContext context,String id,boolean archived){
    statements.archive(tenant(context),id,archived);
   }
   @Override public Map<String,Object> commit(VerifiedTenantContext context,String id,long version,String type){
    bump(context,id,version);event(context,id,type,before);return snapshot(context,id);
   }
  },access);
 }

 /** 原读取权限内的追加历史；只读、分页，不提供任意表审计入口。 */
 public Object changes(VerifiedTenantContext c,String id,int page,int size){
  require(c,"read");uuid(id);authorizeRecord(c,id,"read",false);if(page<1||page>1000000||size<1||size>100)throw invalid();row(c,id,false);
  var tenantId=c.tenantId();var tenantBytes=java.nio.ByteBuffer.allocate(16).putLong(tenantId.getMostSignificantBits()).putLong(tenantId.getLeastSignificantBits()).array();
  return aggregates.changes(c,id,page,size);
 }



 /**
  * 只读预览不创建解析任务、临时值或幂等记录；签名绑定可信主体、源CAS、全量目标及准入摘要。
  * @return 源摘要、目标、完整性问题、退出项、签名及UTC到期点；不承诺执行必成功
  */
 public Map<String,Object> previewStructure(VerifiedTenantContext c,String id,Migration change){
  require(c,"migrate_structure");require(c,"read");uuid(id);authorizeRecord(c,id,"read",false);authorizeRecord(c,id,"migrate_structure",false);validateMigration(change);
  return transaction.readOnly(()->{
   return new com.appnexus.masterdata.application.product.ProductPreviewService(new com.appnexus.masterdata.application.product.ProductPreviewPort(){
    private Map<String,Object> before;
    @Override public Map<String,Object> source(){before=snapshot(c,id);return before;}
    @Override public void validate(Migration target){migrationSource(before,target);validateReferences(c,target.categoryId(),target.configId(),target.productForm(),target.unitId(),true,false);prepareValues(c,target.configId(),target.values());}
    @Override public List<String> issues(Migration target){return targetIssues(c,before,target);}
    @Override public List<String> dropped(Migration target){return droppedValues(before,target);}
    @Override public com.appnexus.contracts.operation.OperationPreviewProof.Proof proof(Migration target){return previewProof.issue(binding(c,id,target));}
    @Override public String digest(Migration target){return payloadDigest(target);}
   }).preview(change);
  });
 }

 /**
  * 显式迁移：先重验权限，成功重放不再检查旧CAS/token，不再次写商品/历史。
  * 首次执行在父商品锁后重查幂等结果，CAS、目标引用、值、四审计及结果同事务提交。
  * @param key Idempotency-Key，规范UUID，作用域为可信租户/账号/商品/操作；重放窗口7天
  * @throws MasterData.Rejection 异载荷同键/过期预览/源版本变化为409；错误值与未确认退出为拒绝
  */
 public Map<String,Object> migrateStructure(VerifiedTenantContext c,String id,MigrationCommand command,String key){
  require(c,"migrate_structure");require(c,"read");uuid(id);authorizeRecord(c,id,"read",false);authorizeRecord(c,id,"migrate_structure",false);uuid(key);
  if(command==null||command.change()==null)throw invalid();Migration change=command.change();validateMigration(change);
  row(c,id,false);String digest=payloadDigest(change);
  var existing=requestResult(c,id,key,digest,false);if(existing!=null)return existing;
  return transaction.migrate(()->{
    var service=new com.appnexus.masterdata.application.product.ProductStructureMigrationService(new com.appnexus.masterdata.application.product.ProductStructurePort(){
     private Map<String,Object> before;
     @Override public Map<String,Object> replay(){
      authorizeRecord(c,id,"migrate_structure",true);row(c,id,true);ensureNotInApproval(c,id);
      return requestResult(c,id,key,digest,true);
     }
     @Override public Map<String,Object> source(){before=snapshot(c,id);return before;}
     @Override public void sourceMatches(Migration target){migrationSource(before,target);}
     @Override public void validateTarget(Migration target){validateReferences(c,target.categoryId(),target.configId(),target.productForm(),target.unitId(),true);prepareValues(c,target.configId(),target.values());}
     @Override public boolean proof(Migration target){return previewProof.verify(command.previewToken(),binding(c,id,target));}
     @Override public List<String> dropped(Migration target){return droppedValues(before,target);}
     @Override public List<String> issues(Migration target){return targetIssues(c,before,target);}
     @Override public Map<String,Object> apply(Migration target){
    // MySQL没有延迟外键；先撤旧值再改父配置，再写目标值，中间态仅存在于同一事务。
    var originalAudit=valueHistory(c,id);clearValues(c,id);
    statements.changeStructureTarget(target.categoryId(),target.configId(),target.productForm(),target.unitId(),target.legacyDisposition(),tenant(c),id);
    if(target.retiredSpecificationIds()!=null)for(String spec:target.retiredSpecificationIds())statements.retireSpecification(actor(c),tenant(c),id,spec);
    if(target.specificationMode()!=null)statements.changeSpecificationMode(target.specificationMode(),tenant(c),id);
    if(!"COMPOSITE".equals(target.productForm()))statements.disableComponents(actor(c),tenant(c),id);
    saveValues(c,id,target.configId(),target.values(),originalAudit);refreshCompleteness(c,id);bump(c,id,target.sourceVersion());
    String eventId=event(c,id,"STRUCTURE",before,target.reason());
    long version=target.sourceVersion()+1,revision=target.sourceStructureRevision()+1;
    var result=new LinkedHashMap<String,Object>();result.put("productId",id);result.put("resultVersion",version);result.put("resultStructureRevision",revision);result.put("changeId",eventId);
    statements.saveMigrationResult(UUID.randomUUID().toString(),tenant(c),actor(c),id,key,digest,version,revision,eventId,content.encode(result),actor(c),actor(c));
    var completed=requestResult(c,id,key,digest,true);completed.put("replayed",false);return completed;
     }
    });
    return service.migrate(change);
   });

 }

 private Map<String,Object> requestResult(VerifiedTenantContext c,String id,String key,String digest,boolean lock){
  var rows=statements.replay(tenant(c),actor(c),id,key,lock);
  if(rows.isEmpty())return null;var record=rows.getFirst();
  if(!digest.equalsIgnoreCase((String)record.get("digest")))throw problem("PRODUCT_IDEMPOTENCY_KEY_REUSED");
  if(((Number)record.get("valid")).intValue()!=1)throw problem("PRODUCT_IDEMPOTENCY_WINDOW_EXPIRED");
  var result=new LinkedHashMap<String,Object>();result.put("productId",id);result.put("resultVersion",record.get("version"));result.put("resultStructureRevision",record.get("revision"));result.put("changeId",record.get("changeId"));result.put("replayUntil",((java.time.Instant)record.get("expires_at")).toString());result.put("replayed",true);return result;
 }
 private static void validateMigration(Migration change){
  if(change==null||change.sourceVersion()==null||change.sourceStructureRevision()==null||change.sourceVersion()<0||change.sourceStructureRevision()<0||change.values()==null||change.droppedItemIds()==null||change.values().size()>200||change.droppedItemIds().size()>200)throw invalid();
  if(change.specificationMode()!=null&&!Set.of("NONE","STANDARD","CUSTOM").contains(change.specificationMode()))throw invalid();if(change.retiredSpecificationIds()!=null){if(change.retiredSpecificationIds().size()>500)throw invalid();for(String x:change.retiredSpecificationIds())uuid(x);}
  text(change.reason(),1000,true);
  var itemIds=new HashSet<String>();for(var value:change.values()){if(value==null)throw invalid();uuid(value.templateItemId());if(!itemIds.add(value.templateItemId()))throw invalid();if(value.customValues()!=null&&(value.customValues().size()>50||value.customValues().stream().anyMatch(Objects::isNull)))throw invalid();if(value.optionIds()!=null){if(value.optionIds().size()>50||new HashSet<>(value.optionIds()).size()!=value.optionIds().size())throw invalid();for(String option:value.optionIds())uuid(option);}}
  if(change.sourceCategoryConfigId()!=null)uuid(change.sourceCategoryConfigId());
  for(String item:change.droppedItemIds())uuid(item);
  if(new HashSet<>(change.droppedItemIds()).size()!=change.droppedItemIds().size())throw invalid();
 }
 /** 父锁下事实转换为领域输入；不把 JDBC 映射或应用请求类型传入领域策略。 */
 @SuppressWarnings("unchecked")
 private void migrationSource(Map<String,Object> before,Migration change){
  // 先清空旧计量配置，再迁移基础单位，避免既有包装比例被解释为另一种单位。
  if(before.get("measurementConfig")!=null&&!Objects.equals(before.get("unitId"),change.unitId()))
   throw new MasterData.Rejection(409,"MASTER_DATA_CHANGED");
  var specs=(List<Map<String,Object>>)before.get("specifications");
  var active=specs.stream().filter(r->r.get("retiredAt")==null).map(r->(String)r.get("id")).collect(java.util.stream.Collectors.toSet());
  var source=new com.appnexus.masterdata.domain.product.ProductStructurePolicy.Source(
    ((Number)before.get("version")).longValue(),((Number)before.get("structureRevision")).longValue(),(String)before.get("configId"),(String)before.get("productForm"),(String)before.get("unitId"),(String)before.get("specificationMode"),(String)before.get("status"),before.get("archivedAt")!=null,Boolean.TRUE.equals(before.get("approvalPending")),(String)before.get("structureState"),(String)before.get("legacyDisposition"),active);
  var target=new com.appnexus.masterdata.domain.product.ProductStructurePolicy.Change(
    change.sourceVersion(),change.sourceStructureRevision(),change.sourceCategoryConfigId(),change.configId(),change.productForm(),change.unitId(),change.specificationMode(),new HashSet<>(change.retiredSpecificationIds()==null?List.of():change.retiredSpecificationIds()),change.legacyDisposition());
  com.appnexus.masterdata.domain.product.ProductStructurePolicy.requireAllowed(source,target);
 }
 private List<String> targetIssues(VerifiedTenantContext c,Map<String,Object> before,Migration change){
  var result=new ArrayList<String>();
  if(change.categoryId()==null)result.add("categoryId");if(change.configId()==null)result.add("categoryConfigId");if(change.productForm()==null)result.add("productForm");
  boolean required=Set.of("PHYSICAL","COMPOSITE").contains(Objects.toString(change.productForm(),""));
  if(change.configId()!=null)required|=Boolean.TRUE.equals(statements.serviceUnitRequired(tenant(c),change.configId()));
  if(required&&change.unitId()==null)result.add("baseUnitId");
  var supplied=change.values().stream().map(Value::templateItemId).collect(java.util.stream.Collectors.toSet());
  if(change.configId()!=null)for(String item:statements.requiredItems(tenant(c),change.configId()))if(!supplied.contains(item))result.add("attribute:"+item);
  return result;
 }
 @SuppressWarnings("unchecked")
 private static List<String> droppedValues(Map<String,Object> before,Migration change){
  var supplied=change.values().stream().map(Value::templateItemId).collect(java.util.stream.Collectors.toSet());
  return ((List<Map<String,Object>>)before.get("values")).stream().map(v->(String)v.get("templateItemId")).filter(v->!supplied.contains(v)).sorted().toList();
 }
 private String payloadDigest(Migration change){
  var canonical=new TreeMap<String,Object>();
  canonical.put("specificationMode",change.specificationMode());canonical.put("retiredSpecificationIds",change.retiredSpecificationIds()==null?List.of():change.retiredSpecificationIds().stream().sorted().toList());
  canonical.put("sourceVersion",change.sourceVersion());canonical.put("sourceStructureRevision",change.sourceStructureRevision());canonical.put("sourceCategoryConfigId",change.sourceCategoryConfigId());
  canonical.put("categoryId",change.categoryId());canonical.put("configId",change.configId());canonical.put("productForm",change.productForm());canonical.put("unitId",change.unitId());canonical.put("reason",change.reason());canonical.put("legacyDisposition",change.legacyDisposition());
  canonical.put("droppedItemIds",change.droppedItemIds().stream().sorted().toList());
  canonical.put("values",change.values().stream().sorted(Comparator.comparing(Value::templateItemId)).map(v->new Value(v.templateItemId(),v.value(),v.optionIds()==null?List.of():v.optionIds().stream().sorted().toList(),v.customValues()==null?List.of():v.customValues().stream().sorted().toList())).toList());
  return hash(content.encode(canonical));
 }
 private String binding(VerifiedTenantContext c,String id,Migration change){
  var admission=new TreeMap<String,Object>();
  if(change.categoryId()!=null)admission.put("category",statements.categoryAdmission(tenant(c),change.categoryId()));
  if(change.configId()!=null)admission.put("templates",statements.templateAdmission(tenant(c),change.configId()));
  if(change.unitId()!=null)admission.put("unit",statements.unitAdmission(tenant(c),change.unitId()));
  return hash("product-structure:v1:"+tenant(c)+":"+actor(c)+":"+id+":"+payloadDigest(change)+":"+content.encode(admission));
 }
 private static String hash(String text){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
  catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
 }
 private void clearValues(VerifiedTenantContext c,String id){
  statements.deleteChoices(tenant(c),id);
  statements.deleteValues(tenant(c),id);
 }
 private static MasterData.Rejection problem(String code){return new MasterData.Rejection(409,code);}


 
 /** 原读取权与结构迁移权双重校验，不能用普通编辑权改历史事实。 */
 public Map<String,Object> reviewLegacySpecification(VerifiedTenantContext c,String id,LegacyReview input){
  require(c,"migrate_structure");require(c,"read");uuid(id);authorizeRecord(c,id,"read",false);authorizeRecord(c,id,"migrate_structure",false);
  if(input==null||input.version()==null||input.version()<0||input.disposition()==null||!Set.of("CONFIRMED_NONE","DEFERRED_SPEC").contains(input.disposition()))throw invalid();
  text(input.reason(),1000,true);
  return write(()->{
   var before=locked(c,id,input.version());ensureNotInApproval(c,id);
   if(before.get("legacyEnabled")==null||!"LEGACY_PENDING".equals(before.get("structureState")))throw problem("PRODUCT_LEGACY_REVIEW_NOT_APPLICABLE");
   statements.reviewLegacyDisposition(input.disposition(),tenant(c),id);
   bump(c,id,input.version());event(c,id,"LEGACY_REVIEW",before,input.reason());return snapshot(c,id);
  });
 }


 /**
  * 暂存当前租户旧商品；只新增尚未暂存的ID，编码碰撞或已存在目标都不覆盖。
  * @param id 旧商品真实UUID；切换写入口前仍须冻结源写入并完整对账
  * @return 完整原文/审计保留的LEGACY_PENDING档案；不是新模型启用验收
  */
 public Map<String,Object> stageLegacy(VerifiedTenantContext c,String id){
  require(c,"stage_legacy");require(c,"read");productAccess.requireAll(c,"stage_legacy");uuid(id);
  return write(()->{
   var source=statements.lockLegacySource(tenant(c),id);
   if(source.isEmpty())throw changed();
   if(statements.stagedCount(tenant(c),id)!=0)throw problem("PRODUCT_LEGACY_ALREADY_STAGED");
   // 显式列出审计以绕过缺省填充，NULL原样保留；源version只作对账，不伪造旧change事件。
   statements.stageLegacyProduct(tenant(c),id);
   return snapshot(c,id);
  });
 }
 /**
  * 只读对账必须覆盖旧正文、版本与四审计，不能仅比较总数。
  * @return 每个旧商品missing/matched/sourceChanged及当前租户新增旧商品缺口；不自动覆盖已人工审查的数据
  */
 public Object reconcileLegacy(VerifiedTenantContext c,int page,int size){
  require(c,"stage_legacy");require(c,"read");productAccess.requireAll(c,"stage_legacy");if(page<1||page>1000000||size<1||size>100)throw invalid();
  var rows=statements.legacyReconciliation(tenant(c),size,(page-1)*size);
  return Map.of("rows",rows,"total",statements.legacyCount(tenant(c)));
 }

 private Map<String,Object> row(VerifiedTenantContext c,String id,boolean lock){return records.load(c,id,lock);}
 private Map<String,Object> snapshot(VerifiedTenantContext c,String id){
  return aggregates.snapshot(c,id,this::issues);
 }
 private Map<String,Object> locked(VerifiedTenantContext c,String id,long version){if(version<0)throw invalid();var before=row(c,id,true);if(((Number)before.get("version")).longValue()!=version)throw changed();return snapshot(c,id);}
 private static void mutable(Map<String,Object> row){com.appnexus.masterdata.domain.product.ProductLifecycle.requireMutable(row.get("archivedAt")!=null,(String)row.get("structureState"));}
 /** 处理中快照冻结所有商品扩展和普通写；审批结果不会自动授予启用权限。 */
 public void ensureNotInApproval(VerifiedTenantContext c,String id){if(statements.pendingApprovalCount(tenant(c),id)>0)throw problem("PRODUCT_APPROVAL_IN_PROGRESS");}
 private void bump(VerifiedTenantContext c,String id,long version){records.advance(c,id,version);}
 /** 审核提交在父锁下重验动态必填和组成版本，不能信任上次保存的完整性标记。 */
 public void requireCompleteForApproval(VerifiedTenantContext c,String id){if(!issues(c,row(c,id,false)).isEmpty())throw new MasterData.Rejection(400,"PRODUCT_COMPLETION_REQUIRED");}
 /** 同领域图片扩展在父锁事务内捕获快照；调用方已复核独立写权限，不能作为HTTP自由查询入口。 */
 public Map<String,Object> extensionSnapshot(VerifiedTenantContext c,String id){return snapshot(c,id);}
 /** 同父聚合事务追加扩展事件，失败必须与扩展正文及CAS一起回滚。 */
 public void extensionEvent(VerifiedTenantContext c,String id,String type,Map<String,Object> before){refreshCompleteness(c,id);event(c,id,type,before);}
 private String event(VerifiedTenantContext c,String id,String type,Map<String,Object> before){return event(c,id,type,before,null);}
 private String event(VerifiedTenantContext c,String id,String type,Map<String,Object> before,String reason){return aggregates.appendHistory(c,id,type,before,reason,this::snapshot);}
 private void validateReferences(VerifiedTenantContext c,String category,String config,String form,String unit,boolean fresh){validateReferences(c,category,config,form,unit,fresh,true);}
 private void validateReferences(VerifiedTenantContext c,String category,String config,String form,String unit,boolean fresh,boolean locks){catalogQueries.validate(c,category,config,form,unit,fresh,locks);}
 private List<String> issues(VerifiedTenantContext c,Map<String,Object> row){return completeness.issues(c,row);}
 private List<String> refreshCompleteness(VerifiedTenantContext c,String id){return completeness.refresh(c,id);}
 private ProductAggregateRepository.ValueHistory valueHistory(VerifiedTenantContext c,String id){return aggregates.valueHistory(c,id);}
 private void saveValues(VerifiedTenantContext c,String id,String config,List<Value> values){saveValues(c,id,config,values,null);}
 private void saveValues(VerifiedTenantContext c,String id,String config,List<Value> values,ProductAggregateRepository.ValueHistory preserved){
  var prepared=prepareValues(c,config,values);
  aggregates.replaceValues(c,id,config,prepared,preserved);
 }

 /** 类型化校验只读复用，预览不插入临时值或使用写后回滚模拟校验。 */
 private List<com.appnexus.masterdata.application.product.ProductAttributePreparation.PreparedValue> prepareValues(VerifiedTenantContext c,String config,List<Value> values){
  return new com.appnexus.masterdata.application.product.ProductAttributePreparation(new com.appnexus.masterdata.application.product.ProductAttributePreparationPort(){
   @Override public List<Map<String,Object>> definitions(VerifiedTenantContext context,String configuration,String item){
   var definitions=statements.attributeDefinition(tenant(context),configuration,item);
    return definitions;
   }
   @Override public int optionCount(VerifiedTenantContext context,String revision,String option){return statements.validOptionCount(tenant(context),revision,option);}
   @Override public void validateRule(String type,String validation,String value,int count){AttributeRulePolicy.value(type,ruleContent.decode(validation),value,count);}
  }).prepare(c,config,values);
 }

 private <T>T write(java.util.function.Supplier<T> work){return transaction.write(work);}
 private void require(VerifiedTenantContext c,String action){if(c==null||c.accountId()==null||!access.hasPermission(AccessAuthorization.Subject.tenant(c.accountId(),c.tenantId(),c.memberId()),"masterdata.product:"+action))throw new MasterData.Rejection(403,"MASTER_DATA_DENIED");}
 private static void validate(Input i){if(i==null||i.code()==null||!i.code().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))throw invalid();text(i.name(),200,true);text(i.shortName(),100,false);text(i.brand(),200,false);text(i.origin(),200,false);text(i.description(),20000,false);text(i.barcode(),500,false);}
 private static void text(String value,int max,boolean required){if(value==null){if(required)throw invalid();return;}if(required&&value.isBlank()||value.length()>max||value.chars().anyMatch(x->Character.isISOControl(x)&&x!='\n'&&x!='\r'&&x!='\t'))throw invalid();}
 private static void uuid(String id){try{if(!UUID.fromString(id).toString().equals(id))throw invalid();}catch(IllegalArgumentException|NullPointerException e){throw invalid();}}
 private static String actor(VerifiedTenantContext c){return c.accountId().toString();}
 private static String tenant(VerifiedTenantContext c){return c.tenantId().toString();}
 private static MasterData.Rejection invalid(){return new MasterData.Rejection(400,"PRODUCT_V1_INPUT_INVALID");}
 private static MasterData.Rejection changed(){return new MasterData.Rejection(409,"PRODUCT_V1_CHANGED");}

 /** 所有商品扩展读取正文前使用当前真实操作，写用例在原事务锁定授权父行。 */
 public void authorizeRecord(VerifiedTenantContext c,String id,String action,boolean lock){productAccess.authorize(c,id,action,lock);}
 /** @param c 可信主体 @param action create/update独立准入 @return 处于对应范围内的启用组织候选 */
 public Object organizations(VerifiedTenantContext c,String action){return productAccess.organizations(c,action);}
}
