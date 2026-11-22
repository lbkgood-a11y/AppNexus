package com.appnexus.masterdata.application.product;
import com.appnexus.masterdata.domain.product.AttributeRulePolicy;
import com.appnexus.masterdata.api.product.ProductCatalogData;

import com.appnexus.masterdata.api.masterdata.MasterData;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.authorization.ScopedWriteGate;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import com.appnexus.masterdata.api.product.ProductAttributeData.GroupInput;
import com.appnexus.masterdata.api.product.ProductAttributeData.AttributeInput;
import com.appnexus.masterdata.api.product.ProductAttributeData.RevisionInput;
import com.appnexus.masterdata.api.product.ProductAttributeData.OptionSeed;
import com.appnexus.masterdata.api.product.ProductAttributeData.OptionInput;


/** 商品属性定义及不可变修订执行器；发布后正文不更新，所有关联带可信租户。 */
public final class ProductAttributeService implements com.appnexus.masterdata.api.product.ProductAttributeManagement {
 private final ProductAttributeRepository statements;
 private final AttributeRuleContent rules;

  private final ScopedWriteGate writes;
  private final AccessAuthorization access; private final ProductAttributeTransaction tx;
 
 
 
 

 
 /** 属性完整用例依赖装配。
  * @param access 权威功能权限公开端口
  * @param transaction 同数据源完整事务，保留原属性冲突拒绝码
  * @param statements 固定属性仓储，强制可信租户和父聚合
  * @param rules 固定规则内容解析，业务规则由领域模型执行
  */
 public ProductAttributeService(AccessAuthorization access,ProductAttributeTransaction transaction,ProductAttributeRepository statements,AttributeRuleContent rules,ScopedWriteGate writes){this.writes=writes;this.rules=rules;this.statements=statements;this.access=access;this.tx=transaction;}
 /** 按当前租户返回属性分组；读取权不授予维护能力。 */
 public Object groups(VerifiedTenantContext c){require(c,"read");return statements.groups(tenant(c));}
 /** 返回当前租户属性身份、父CAS和代表修订摘要；完整修订仍独立读取。 */
 public Object attributes(VerifiedTenantContext c){require(c,"read");return statements.attributes(tenant(c));}
 /** 固定属性的全部修订及该修订下选项；历史发布版本仍可读。 */
 public Object revisions(VerifiedTenantContext c,String id){
  require(c,"read");uuid(id);
  return statements.revisions(tenant(c),id);
 }
 /** 选项仅返回本租户本修订，不能使用未知ID穿透其他租户。 */
 public Object options(VerifiedTenantContext c,String id){
  require(c,"read");uuid(id);
  return statements.options(tenant(c),id);
 }
 /** 新建分组；唯一约束与四审计同事务。 */
 public Map<String,String> createGroup(VerifiedTenantContext c,GroupInput i){require(c,"create");if(i==null)throw invalid();common(i.code(),i.name(),i.version());String id=UUID.randomUUID().toString();return write(()->{authorizeWrite(c,"create",id);statements.createGroup(id,tenant(c),i.code().trim(),i.name().trim(),actor(c),actor(c));return Map.of("id",id);});}
 /** 新建稳定身份及可选首个草稿，选项失败时回滚整条属性。 */
 public Map<String,String> createAttribute(VerifiedTenantContext c,AttributeInput i){
  require(c,"create");
  if(i==null)throw invalid();
  common(i.code(),i.code(),i.version());
  if(Set.of("code","name","shortname","productform","categoryid","specificationmode","brand","origin","origintext","baseunitid","barcode","description","status","品牌").contains(i.code().toLowerCase(Locale.ROOT)))throw invalid();
  if(i.groupId()!=null)uuid(i.groupId());
  var initial=i.initialRevision();
  if(initial!=null){
   if(i.version()!=0||initial.version()!=0)throw invalid();
   validateNewRevision(initial);
  }
  String id=UUID.randomUUID().toString();
  return write(()->{
   authorizeWrite(c,"create",id);
   statements.createAttribute(id,tenant(c),i.code().trim(),i.groupId(),actor(c),actor(c));
   if(initial!=null)insertRevision(c,id,1L,initial,"create");
   return Map.of("id",id);
  });
 }
 /** 创建DRAFT修订及初始枚举，父属性CAS串行分配修订号。 */
 public Map<String,String> createRevision(VerifiedTenantContext c,String attributeId,RevisionInput i){
  require(c,"update");uuid(attributeId);validateNewRevision(i);
  return write(()->{
   lockAttribute(c,attributeId,i.version(),"update");
   return insertRevision(c,attributeId,statements.nextRevision(tenant(c),attributeId),i,"update");
  });
 }

 /** 两种创建用例共享草稿校验与写入；调用者持有完整属性事务。 */
 private Map<String,String> insertRevision(VerifiedTenantContext c,String attributeId,long number,RevisionInput input,String action){
  if(input.unitId()!=null){
   uuid(input.unitId());
   if(statements.enabledUnitCount(tenant(c),input.unitId())!=1)throw invalid();
  }
  String id=UUID.randomUUID().toString();
  authorizeWrite(c,action,"insert-revision",attributeId);
  statements.createRevision(id,tenant(c),attributeId,number,input.name().trim(),input.dataType(),input.unitId(),input.numericScale(),input.validationJson(),actor(c),actor(c));
  if(input.options()!=null){
   if(input.options().size()>50||!Set.of("SINGLE_SELECT","MULTI_SELECT").contains(input.dataType())&&!input.options().isEmpty())throw invalid();
   authorizeWrite(c,action,"initial-options",id);
   int order=0;
   for(var option:input.options()){
    if(option==null)throw invalid();
    common(option.code(),option.label(),0);
    statements.createInitialOption(UUID.randomUUID().toString(),tenant(c),id,option.code(),option.label().trim(),order++,actor(c),actor(c));
   }
  }
  return Map.of("id",id,"revision",String.valueOf(number));
 }

 private void validateNewRevision(RevisionInput input){
  if(input==null||input.name()==null||input.name().isBlank()||input.dataType()==null||input.version()<0)throw invalid();
  validateRevision(input);
 }
 /** 发布草稿，单/多选须有选项；version为当前父属性CAS，发布失败不推进审计。 */
 public void publishRevision(VerifiedTenantContext c,String id,long version){require(c,"publish");uuid(id);write(()->{var row=lockRevision(c,id,version,"publish");String type=(String)row.get("data_type");if(Set.of("SINGLE_SELECT","MULTI_SELECT").contains(type)&&statements.optionCount(tenant(c),id)==0)throw invalid();writes.authorizeCatalogPublication(access,AccessAuthorization.Subject.tenant(c.accountId(),c.tenantId(),c.memberId()),"masterdata.productattribute:publish");if(statements.publishRevision(actor(c),tenant(c),id)!=1)throw changed();return null;});}
 /** 仅未发布的枚举修订允许追加选项；与父CAS同事务，已发布拒绝。 */
 public Map<String,String> createOption(VerifiedTenantContext c,String revisionId,OptionInput i){require(c,"update");uuid(revisionId);if(i==null||i.code()==null||!i.code().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")||i.label()==null||i.label().isBlank()||i.label().length()>200)throw invalid();String id=UUID.randomUUID().toString();return write(()->{var row=lockRevision(c,revisionId,i.version(),"update");if(!Set.of("SINGLE_SELECT","MULTI_SELECT").contains(row.get("data_type")))throw invalid();authorizeWrite(c,"update",revisionId);statements.createOption(id,tenant(c),revisionId,i.code(),i.label().trim(),i.sortOrder(),actor(c),actor(c));return Map.of("id",id);});}

 /** 分组名称维护不改变已发布模板的分组快照，编码不允许修改。 */
 public void updateGroup(VerifiedTenantContext c,String id,GroupInput input){
  require(c,"update");uuid(id);if(input==null)throw invalid();common(input.code(),input.name(),input.version());
  write(()->{authorizeWrite(c,"update",id);if(statements.updateGroup(input.name().trim(),actor(c),tenant(c),id,input.code(),input.version())!=1)throw changed();return null;});
 }
 /** 稳定属性仅调整分组，不修改语义编码、已发布修订或商品值。 */
 public void updateAttribute(VerifiedTenantContext c,String id,AttributeInput input){
  require(c,"update");uuid(id);if(input==null||input.initialRevision()!=null)throw invalid();common(input.code(),input.code(),input.version());
  if(input.groupId()!=null)uuid(input.groupId());
  write(()->{authorizeWrite(c,"update",id);if(statements.updateAttribute(input.groupId(),actor(c),tenant(c),id,input.code(),input.version())!=1)throw changed();return null;});
 }
 /** 独立启停不删除修订；group参数由固定Controller选择，不由客户端指定表。 */
 public void setStatus(VerifiedTenantContext c,String id,ProductCatalogData.Status input,boolean group){
  require(c,"set_status");uuid(id);if(input==null||input.enabled()==null||input.version()<0)throw invalid();
  
  write(()->{authorizeWrite(c,"set_status",id);if(statements.status(group,tenant(c),id,input.enabled(),actor(c),input.version())!=1)throw changed();return null;});
 }
 /** 退役仅阻止新模板选用，保留旧商品解释；父CAS保护且不能退役草稿。 */
 public void retireRevision(VerifiedTenantContext c,String id,long version){
  require(c,"publish");uuid(id);write(()->{
   var rows=statements.revisionOwners(tenant(c),id);
   if(rows.isEmpty())throw changed();lockAttribute(c,rows.getFirst(),version,"publish");
   authorizeWrite(c,"publish",id);
   if(statements.retireRevision(actor(c),tenant(c),id)!=1)throw changed();return null;
  });
 }


 /** 仅DRAFT可编辑正文/选项；父CAS串行，已发布版本无原地更新通道。 */
 public void updateRevision(VerifiedTenantContext c,String id,RevisionInput input){
  require(c,"update");uuid(id);if(input==null||input.name()==null||input.name().isBlank())throw invalid();validateRevision(input);
  write(()->{
   lockRevision(c,id,input.version(),"update");
   authorizeWrite(c,"update",id);
   if(input.unitId()!=null){uuid(input.unitId());if(statements.validUnitCount(tenant(c),input.unitId())!=1)throw invalid();}
   statements.updateRevision(input.name().trim(),input.dataType(),input.unitId(),input.numericScale(),input.validationJson(),actor(c),tenant(c),id);
   if(input.options()!=null){
    statements.deleteOptions(tenant(c),id);
    if(!Set.of("SINGLE_SELECT","MULTI_SELECT").contains(input.dataType())&&!input.options().isEmpty()||input.options().size()>50)throw invalid();
    int order=0;for(var o:input.options()){if(o==null)throw invalid();common(o.code(),o.label(),0);statements.replaceOption(UUID.randomUUID().toString(),tenant(c),id,o.code(),o.label(),order++,actor(c),actor(c));}
   }
  return null;});
 }

 /** 属性聚合CAS同时覆盖修订和选项，避免发布后并发插入修改已发布含义。 */
 private void lockAttribute(VerifiedTenantContext c,String id,long version,String action){
  var rows=statements.lockAttribute(tenant(c),id);
  if(rows.isEmpty()||((Number)rows.getFirst().get("version")).longValue()!=version||!Boolean.TRUE.equals(rows.getFirst().get("enabled")))throw changed();
  authorizeWrite(c,action,"advance-attribute-version",id);
  statements.advanceAttributeVersion(actor(c),tenant(c),id);
 }
 private Map<String,Object> lockRevision(VerifiedTenantContext c,String id,long version,String action){
  var rows=statements.revisionParent(tenant(c),id);
  if(rows.isEmpty())throw changed();var row=rows.getFirst();lockAttribute(c,(String)row.get("attribute_id"),version,action);
  var current=statements.lockRevisionState(tenant(c),id);
  if(!"DRAFT".equals(current))throw changed();return row;
 }
 private void validateRevision(RevisionInput i){
  if(i.name().trim().length()>200||!Set.of("TEXT","INTEGER","DECIMAL","BOOLEAN","SINGLE_SELECT","MULTI_SELECT","DATE").contains(i.dataType()))throw invalid();
  boolean numeric=Set.of("INTEGER","DECIMAL").contains(i.dataType());
  if(numeric&&(i.numericScale()==null||i.numericScale()<0||i.numericScale()>12)||!numeric&&(i.unitId()!=null||i.numericScale()!=null)||"INTEGER".equals(i.dataType())&&i.numericScale()!=0)throw invalid();
  AttributeRulePolicy.definition(i.dataType(),rules.decode(i.validationJson()));
 }
 /** 事务许可绑定原目录维护操作与固定记录键，直接 Mapper 调用不能复用目录查询权限。 */
 private void authorizeWrite(VerifiedTenantContext c,String action,String id){
  authorizeWrite(c,action,action,id);
 }
 /** 共享目录语句使用固定内部操作键，签发时仍校验发起用例的既有权限码。 */
 private void authorizeWrite(VerifiedTenantContext c,String action,String operation,String id){
  String permission="masterdata.productattribute:"+action;
  var subject=AccessAuthorization.Subject.tenant(c.accountId(),c.tenantId(),c.memberId());
  try{
   writes.authorizeServiceOperation(c.tenantId(),"masterdata.productattribute:"+operation,List.of(id),()->access.hasPermission(subject,permission));
  }catch(SecurityException denied){
   throw new MasterData.Rejection(403,"MASTER_DATA_DENIED");
  }
 }
 private void require(VerifiedTenantContext c,String a){if(c==null||!access.hasPermission(AccessAuthorization.Subject.tenant(c.accountId(),c.tenantId(),c.memberId()),"masterdata.productattribute:"+a))throw new MasterData.Rejection(403,"MASTER_DATA_DENIED");}
 private <T>T write(java.util.function.Supplier<T> work){return tx.write(work);}
 private static void common(String c,String n,long v){if(c==null||!c.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")||n==null||n.isBlank()||n.trim().length()>200||n.chars().anyMatch(Character::isISOControl)||v<0)throw invalid();}
 private static void uuid(String s){try{UUID.fromString(s);}catch(Exception e){throw invalid();}}
 private static String actor(VerifiedTenantContext c){return c.accountId().toString();} private static String tenant(VerifiedTenantContext c){return c.tenantId().toString();}
 private static MasterData.Rejection invalid(){return new MasterData.Rejection(400,"PRODUCT_ATTRIBUTE_INPUT_INVALID");}
 private static MasterData.Rejection changed(){return new MasterData.Rejection(409,"PRODUCT_ATTRIBUTE_CHANGED");}
}
