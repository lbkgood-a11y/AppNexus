package com.appnexus.masterdata.product;
import com.appnexus.masterdata.adapter.jdbc.ProductAttributeServiceSqlMapper;

import com.appnexus.masterdata.MasterData;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import tools.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;

import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** 商品属性定义及不可变修订执行器；发布后正文不更新，所有关联带可信租户。 */
@org.springframework.stereotype.Service
public final class ProductAttributeService {
 private final ProductAttributeServiceSqlMapper statements;

  private final AccessAuthorization access; private final TransactionTemplate tx;
 private static final String AUD="BIN_TO_UUID(created_by) creator,created_at,BIN_TO_UUID(updated_by) updater,updated_at";
 /** 分组创建请求：code租户内唯一且1至64字符，name为1至200字符；version新建为0。 */
 public record GroupInput(String code,String name,long version){}
 /** 稳定属性创建：code不能占用品牌等保留字段，groupId为同租户可选分组，version新建0。 */
 public record AttributeInput(String code,String groupId,long version){}
 /**
  * 修订草稿请求；正文发布后不可改，version是稳定属性父聚合CAS。
  *
 * @param name 名称1至200字符
 * @param dataType 固定七种受支持类型
  *
 * @param unitId 数值单位UUID可空；非数值必须空
  *
 * @param numericScale INTEGER必须0，DECIMAL为0至12，其他必须空
  *
 * @param validationJson 规则白名单：文本长度、数值/日期范围、枚举数量；拒绝脚本/SQL/任意正则
  *
 * @param version 当前父属性版本，非负
 * @param options 枚举初始选项，可空；与修订同事务保存
  */
 public record RevisionInput(String name,String dataType,String unitId,Integer numericScale,String validationJson,long version,List<OptionSeed> options){
  /** 测试/内部调用不提供初始枚举；后续只能在未发布草稿下追加。 */
  public RevisionInput(String name,String dataType,String unitId,Integer numericScale,String validationJson,long version){this(name,dataType,unitId,numericScale,validationJson,version,null);}
 }
 /** 枚举选项编码1至64个ASCII字符，标签1至200字符，顺序默认由列表位置确定。 */
 public record OptionSeed(String code,String label){}

 /** 草稿追加枚举：code为修订内唯一编码，label为1至200字符，sortOrder为显示顺序，version是父属性CAS。 */
 public record OptionInput(String code,String label,int sortOrder,long version){}
 /**
 * @param jdbc 宿主UTC数据源
 * @param access 公开租户授权端口
 * @param manager 同一数据源事务管理器 */
 public ProductAttributeService(AccessAuthorization access,PlatformTransactionManager manager,ProductAttributeServiceSqlMapper statements){this.statements=statements;this.access=access;this.tx=new TransactionTemplate(manager);}
 /** 按当前租户返回属性分组；读取权不授予维护能力。 */
 public Object groups(VerifiedTenantContext c){require(c,"read");return statements.groups(tenant(c));}
 /** 返回当前租户稳定属性身份与父CAS，修订独立读取。 */
 public Object attributes(VerifiedTenantContext c){require(c,"read");return statements.attributes(tenant(c));}
 /** 固定属性的全部修订及该修订下选项；历史发布版本仍可读。 */
 public Object revisions(VerifiedTenantContext c,String id){
  require(c,"read");uuid(id);
  return statements.statement1(tenant(c),id);
 }
 /** 选项仅返回本租户本修订，不能使用未知ID穿透其他租户。 */
 public Object options(VerifiedTenantContext c,String id){
  require(c,"read");uuid(id);
  return statements.statement2(tenant(c),id);
 }
 /** 新建分组；唯一约束与四审计同事务。 */
 public Map<String,String> createGroup(VerifiedTenantContext c,GroupInput i){require(c,"create");if(i==null)throw invalid();common(i.code(),i.name(),i.version());String id=UUID.randomUUID().toString();return write(()->{statements.statement3(id,tenant(c),i.code().trim(),i.name().trim(),actor(c),actor(c));return Map.of("id",id);});}
 /** 新建稳定语义身份；保留字段和跨租户分组拒绝。 */
 public Map<String,String> createAttribute(VerifiedTenantContext c,AttributeInput i){require(c,"create");if(i==null)throw invalid();common(i.code(),i.code(),i.version());if(Set.of("code","name","shortname","productform","categoryid","specificationmode","brand","origin","origintext","baseunitid","barcode","description","status","品牌").contains(i.code().toLowerCase(Locale.ROOT)))throw invalid();if(i.groupId()!=null)uuid(i.groupId());String id=UUID.randomUUID().toString();return write(()->{statements.statement4(id,tenant(c),i.code().trim(),i.groupId(),actor(c),actor(c));return Map.of("id",id);});}
 /** 创建DRAFT修订及初始枚举，父属性CAS串行分配修订号；任何选项错误全回滚。 */
 public Map<String,String> createRevision(VerifiedTenantContext c,String attributeId,RevisionInput i){require(c,"update");uuid(attributeId);if(i==null||i.name()==null||i.name().isBlank()||i.dataType()==null||i.version()<0)throw invalid();String id=UUID.randomUUID().toString();validateRevision(i);return write(()->{lockAttribute(c,attributeId,i.version());if(i.unitId()!=null){uuid(i.unitId());if(statements.statement5(tenant(c),i.unitId())!=1)throw invalid();}Long n=statements.statement6(tenant(c),attributeId);statements.statement7(id,tenant(c),attributeId,n,i.name().trim(),i.dataType(),i.unitId(),i.numericScale(),i.validationJson(),actor(c),actor(c));if(i.options()!=null){if(!Set.of("SINGLE_SELECT","MULTI_SELECT").contains(i.dataType())&&!i.options().isEmpty()||i.options().size()>50)throw invalid();int order=0;for(var option:i.options()){if(option==null||option.code()==null||!option.code().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")||option.label()==null||option.label().isBlank()||option.label().length()>200)throw invalid();statements.statement8(UUID.randomUUID().toString(),tenant(c),id,option.code(),option.label(),order++,actor(c),actor(c));}}return Map.of("id",id,"revision",String.valueOf(n));});}
 /** 发布草稿，单/多选须有选项；version为当前父属性CAS，发布失败不推进审计。 */
 public void publishRevision(VerifiedTenantContext c,String id,long version){require(c,"publish");uuid(id);write(()->{var row=lockRevision(c,id,version);String type=(String)row.get("data_type");if(Set.of("SINGLE_SELECT","MULTI_SELECT").contains(type)&&statements.statement9(tenant(c),id)==0)throw invalid();if(statements.statement10(actor(c),tenant(c),id)!=1)throw changed();return null;});}
 /** 仅未发布的枚举修订允许追加选项；与父CAS同事务，已发布拒绝。 */
 public Map<String,String> createOption(VerifiedTenantContext c,String revisionId,OptionInput i){require(c,"update");uuid(revisionId);if(i==null||i.code()==null||!i.code().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")||i.label()==null||i.label().isBlank()||i.label().length()>200)throw invalid();String id=UUID.randomUUID().toString();return write(()->{var row=lockRevision(c,revisionId,i.version());if(!Set.of("SINGLE_SELECT","MULTI_SELECT").contains(row.get("data_type")))throw invalid();statements.statement11(id,tenant(c),revisionId,i.code(),i.label().trim(),i.sortOrder(),actor(c),actor(c));return Map.of("id",id);});}

 /** 分组名称维护不改变已发布模板的分组快照，编码不允许修改。 */
 public void updateGroup(VerifiedTenantContext c,String id,GroupInput input){
  require(c,"update");uuid(id);if(input==null)throw invalid();common(input.code(),input.name(),input.version());
  write(()->{if(statements.statement12(input.name().trim(),actor(c),tenant(c),id,input.code(),input.version())!=1)throw changed();return null;});
 }
 /** 稳定属性仅调整分组，不修改语义编码、已发布修订或商品值。 */
 public void updateAttribute(VerifiedTenantContext c,String id,AttributeInput input){
  require(c,"update");uuid(id);if(input==null)throw invalid();common(input.code(),input.code(),input.version());
  if(input.groupId()!=null)uuid(input.groupId());
  write(()->{if(statements.statement13(input.groupId(),actor(c),tenant(c),id,input.code(),input.version())!=1)throw changed();return null;});
 }
 /** 独立启停不删除修订；group参数由固定Controller选择，不由客户端指定表。 */
 public void setStatus(VerifiedTenantContext c,String id,CatalogData.Status input,boolean group){
  require(c,"set_status");uuid(id);if(input==null||input.enabled()==null||input.version()<0)throw invalid();
  String table=group?"md_product_attribute_group":"md_product_attribute";
  write(()->{if(statements.status(table.equals("md_product_attribute_group"),tenant(c),id,input.enabled(),actor(c),input.version())!=1)throw changed();return null;});
 }
 /** 退役仅阻止新模板选用，保留旧商品解释；父CAS保护且不能退役草稿。 */
 public void retireRevision(VerifiedTenantContext c,String id,long version){
  require(c,"publish");uuid(id);write(()->{
   var rows=statements.statement14(tenant(c),id);
   if(rows.isEmpty())throw changed();lockAttribute(c,rows.getFirst(),version);
   if(statements.statement15(actor(c),tenant(c),id)!=1)throw changed();return null;
  });
 }


 /** 仅DRAFT可编辑正文/选项；父CAS串行，已发布版本无原地更新通道。 */
 public void updateRevision(VerifiedTenantContext c,String id,RevisionInput input){
  require(c,"update");uuid(id);if(input==null||input.name()==null||input.name().isBlank())throw invalid();validateRevision(input);
  write(()->{
   lockRevision(c,id,input.version());
   if(input.unitId()!=null){uuid(input.unitId());if(statements.statement16(tenant(c),input.unitId())!=1)throw invalid();}
   statements.statement17(input.name().trim(),input.dataType(),input.unitId(),input.numericScale(),input.validationJson(),actor(c),tenant(c),id);
   if(input.options()!=null){
    statements.statement18(tenant(c),id);
    if(!Set.of("SINGLE_SELECT","MULTI_SELECT").contains(input.dataType())&&!input.options().isEmpty()||input.options().size()>50)throw invalid();
    int order=0;for(var o:input.options()){if(o==null)throw invalid();common(o.code(),o.label(),0);statements.statement19(UUID.randomUUID().toString(),tenant(c),id,o.code(),o.label(),order++,actor(c),actor(c));}
   }
  return null;});
 }

 /** 属性聚合CAS同时覆盖修订和选项，避免发布后并发插入修改已发布含义。 */
 private void lockAttribute(VerifiedTenantContext c,String id,long version){
  var rows=statements.statement20(tenant(c),id);
  if(rows.isEmpty()||((Number)rows.getFirst().get("version")).longValue()!=version||!Boolean.TRUE.equals(rows.getFirst().get("enabled")))throw changed();
  statements.statement21(actor(c),tenant(c),id);
 }
 private Map<String,Object> lockRevision(VerifiedTenantContext c,String id,long version){
  var rows=statements.statement22(tenant(c),id);
  if(rows.isEmpty())throw changed();var row=rows.getFirst();lockAttribute(c,(String)row.get("attribute_id"),version);
  var current=statements.statement23(tenant(c),id);
  if(!"DRAFT".equals(current))throw changed();return row;
 }
 private static void validateRevision(RevisionInput i){
  if(i.name().trim().length()>200||!Set.of("TEXT","INTEGER","DECIMAL","BOOLEAN","SINGLE_SELECT","MULTI_SELECT","DATE").contains(i.dataType()))throw invalid();
  boolean numeric=Set.of("INTEGER","DECIMAL").contains(i.dataType());
  if(numeric&&(i.numericScale()==null||i.numericScale()<0||i.numericScale()>12)||!numeric&&(i.unitId()!=null||i.numericScale()!=null)||"INTEGER".equals(i.dataType())&&i.numericScale()!=0)throw invalid();
  ProductAttributeRules.definition(i.dataType(),i.validationJson());
 }
 private void require(VerifiedTenantContext c,String a){if(c==null||!access.hasPermission(AccessAuthorization.Subject.tenant(c.accountId(),c.tenantId(),c.memberId()),"masterdata.productattribute:"+a))throw new MasterData.Rejection(403,"MASTER_DATA_DENIED");}
 private <T>T write(java.util.function.Supplier<T> s){try{return tx.execute(x->s.get());}catch(DataIntegrityViolationException e){throw new MasterData.Rejection(409,"PRODUCT_ATTRIBUTE_CONFLICT");}}
 private static void common(String c,String n,long v){if(c==null||!c.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")||n==null||n.isBlank()||n.trim().length()>200||n.chars().anyMatch(Character::isISOControl)||v<0)throw invalid();}
 private static void uuid(String s){try{UUID.fromString(s);}catch(Exception e){throw invalid();}}
 private static String actor(VerifiedTenantContext c){return c.accountId().toString();} private static String tenant(VerifiedTenantContext c){return c.tenantId().toString();}
 private static MasterData.Rejection invalid(){return new MasterData.Rejection(400,"PRODUCT_ATTRIBUTE_INPUT_INVALID");}
 private static MasterData.Rejection changed(){return new MasterData.Rejection(409,"PRODUCT_ATTRIBUTE_CHANGED");}
}