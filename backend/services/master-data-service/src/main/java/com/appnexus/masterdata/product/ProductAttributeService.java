package com.appnexus.masterdata.product;

import com.appnexus.masterdata.MasterData;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import tools.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** 商品属性定义及不可变修订执行器；发布后正文不更新，所有关联带可信租户。 */
@org.springframework.stereotype.Service
public final class ProductAttributeService {
 private final JdbcTemplate jdbc; private final AccessAuthorization access; private final TransactionTemplate tx;
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
 public ProductAttributeService(JdbcTemplate jdbc,AccessAuthorization access,PlatformTransactionManager manager){this.jdbc=jdbc;this.access=access;this.tx=new TransactionTemplate(manager);}
 /** 按当前租户返回属性分组；读取权不授予维护能力。 */
 public Object groups(VerifiedTenantContext c){require(c,"read");return jdbc.query("SELECT BIN_TO_UUID(id) id,code,name,enabled,version,"+AUD+" FROM md_product_attribute_group WHERE tenant_id=UUID_TO_BIN(?) ORDER BY code,id",(r,n)->Map.of("id",r.getString("id"),"code",r.getString("code"),"name",r.getString("name"),"enabled",r.getBoolean("enabled"),"version",r.getLong("version")),tenant(c));}
 /** 返回当前租户稳定属性身份与父CAS，修订独立读取。 */
 public Object attributes(VerifiedTenantContext c){require(c,"read");return jdbc.query("SELECT BIN_TO_UUID(id) id,code,BIN_TO_UUID(group_id) groupId,enabled,version,"+AUD+" FROM md_product_attribute WHERE tenant_id=UUID_TO_BIN(?) ORDER BY code,id",(r,n)->Map.of("id",r.getString("id"),"code",r.getString("code"),"groupId",Objects.toString(r.getString("groupId"),""),"enabled",r.getBoolean("enabled"),"version",r.getLong("version")),tenant(c));}
 /** 固定属性的全部修订及该修订下选项；历史发布版本仍可读。 */
 public Object revisions(VerifiedTenantContext c,String id){
  require(c,"read");uuid(id);
  return jdbc.queryForList("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(attribute_id) attributeId,revision_no revision,name,data_type dataType,BIN_TO_UUID(unit_id) unitId,numeric_scale numericScale,CAST(validation_json AS CHAR) validationJson,state,BIN_TO_UUID(created_by) createdBy,created_at createdAt,BIN_TO_UUID(updated_by) updatedBy,updated_at updatedAt FROM md_product_attribute_revision WHERE tenant_id=UUID_TO_BIN(?) AND attribute_id=UUID_TO_BIN(?) ORDER BY revision_no DESC",tenant(c),id);
 }
 /** 选项仅返回本租户本修订，不能使用未知ID穿透其他租户。 */
 public Object options(VerifiedTenantContext c,String id){
  require(c,"read");uuid(id);
  return jdbc.queryForList("SELECT BIN_TO_UUID(id) id,option_code code,label,sort_order sortOrder FROM md_product_attribute_option WHERE tenant_id=UUID_TO_BIN(?) AND revision_id=UUID_TO_BIN(?) ORDER BY sort_order,id",tenant(c),id);
 }
 /** 新建分组；唯一约束与四审计同事务。 */
 public Map<String,String> createGroup(VerifiedTenantContext c,GroupInput i){require(c,"create");if(i==null)throw invalid();common(i.code(),i.name(),i.version());String id=UUID.randomUUID().toString();return write(()->{jdbc.update("INSERT INTO md_product_attribute_group(id,tenant_id,code,name,created_by,updated_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,UUID_TO_BIN(?),UUID_TO_BIN(?))",id,tenant(c),i.code().trim(),i.name().trim(),actor(c),actor(c));return Map.of("id",id);});}
 /** 新建稳定语义身份；保留字段和跨租户分组拒绝。 */
 public Map<String,String> createAttribute(VerifiedTenantContext c,AttributeInput i){require(c,"create");if(i==null)throw invalid();common(i.code(),i.code(),i.version());if(Set.of("code","name","shortname","productform","categoryid","specificationmode","brand","origin","origintext","baseunitid","barcode","description","status","品牌").contains(i.code().toLowerCase(Locale.ROOT)))throw invalid();if(i.groupId()!=null)uuid(i.groupId());String id=UUID.randomUUID().toString();return write(()->{jdbc.update("INSERT INTO md_product_attribute(id,tenant_id,code,group_id,created_by,updated_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",id,tenant(c),i.code().trim(),i.groupId(),actor(c),actor(c));return Map.of("id",id);});}
 /** 创建DRAFT修订及初始枚举，父属性CAS串行分配修订号；任何选项错误全回滚。 */
 public Map<String,String> createRevision(VerifiedTenantContext c,String attributeId,RevisionInput i){require(c,"update");uuid(attributeId);if(i==null||i.name()==null||i.name().isBlank()||i.dataType()==null||i.version()<0)throw invalid();String id=UUID.randomUUID().toString();validateRevision(i);return write(()->{lockAttribute(c,attributeId,i.version());if(i.unitId()!=null){uuid(i.unitId());if(jdbc.queryForObject("SELECT COUNT(*) FROM md_product_unit WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND enabled=TRUE",Integer.class,tenant(c),i.unitId())!=1)throw invalid();}Long n=jdbc.queryForObject("SELECT COALESCE(MAX(revision_no),0)+1 FROM md_product_attribute_revision WHERE tenant_id=UUID_TO_BIN(?) AND attribute_id=UUID_TO_BIN(?)",Long.class,tenant(c),attributeId);jdbc.update("INSERT INTO md_product_attribute_revision(id,tenant_id,attribute_id,revision_no,name,data_type,unit_id,numeric_scale,validation_json,created_by,updated_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?,UUID_TO_BIN(?),?,?,UUID_TO_BIN(?),UUID_TO_BIN(?))",id,tenant(c),attributeId,n,i.name().trim(),i.dataType(),i.unitId(),i.numericScale(),i.validationJson(),actor(c),actor(c));if(i.options()!=null){if(!Set.of("SINGLE_SELECT","MULTI_SELECT").contains(i.dataType())&&!i.options().isEmpty()||i.options().size()>50)throw invalid();int order=0;for(var option:i.options()){if(option==null||option.code()==null||!option.code().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")||option.label()==null||option.label().isBlank()||option.label().length()>200)throw invalid();jdbc.update("INSERT INTO md_product_attribute_option(id,tenant_id,revision_id,option_code,label,sort_order,created_by,updated_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?,UUID_TO_BIN(?),UUID_TO_BIN(?))",UUID.randomUUID().toString(),tenant(c),id,option.code(),option.label(),order++,actor(c),actor(c));}}return Map.of("id",id,"revision",String.valueOf(n));});}
 /** 发布草稿，单/多选须有选项；version为当前父属性CAS，发布失败不推进审计。 */
 public void publishRevision(VerifiedTenantContext c,String id,long version){require(c,"publish");uuid(id);write(()->{var row=lockRevision(c,id,version);String type=(String)row.get("data_type");if(Set.of("SINGLE_SELECT","MULTI_SELECT").contains(type)&&jdbc.queryForObject("SELECT COUNT(*) FROM md_product_attribute_option WHERE tenant_id=UUID_TO_BIN(?) AND revision_id=UUID_TO_BIN(?)",Integer.class,tenant(c),id)==0)throw invalid();if(jdbc.update("UPDATE md_product_attribute_revision SET state='PUBLISHED',published_at=CURRENT_TIMESTAMP(6),updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND state='DRAFT'",actor(c),tenant(c),id)!=1)throw changed();return null;});}
 /** 仅未发布的枚举修订允许追加选项；与父CAS同事务，已发布拒绝。 */
 public Map<String,String> createOption(VerifiedTenantContext c,String revisionId,OptionInput i){require(c,"update");uuid(revisionId);if(i==null||i.code()==null||!i.code().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")||i.label()==null||i.label().isBlank()||i.label().length()>200)throw invalid();String id=UUID.randomUUID().toString();return write(()->{var row=lockRevision(c,revisionId,i.version());if(!Set.of("SINGLE_SELECT","MULTI_SELECT").contains(row.get("data_type")))throw invalid();jdbc.update("INSERT INTO md_product_attribute_option(id,tenant_id,revision_id,option_code,label,sort_order,created_by,updated_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?,UUID_TO_BIN(?),UUID_TO_BIN(?))",id,tenant(c),revisionId,i.code(),i.label().trim(),i.sortOrder(),actor(c),actor(c));return Map.of("id",id);});}

 /** 分组名称维护不改变已发布模板的分组快照，编码不允许修改。 */
 public void updateGroup(VerifiedTenantContext c,String id,GroupInput input){
  require(c,"update");uuid(id);if(input==null)throw invalid();common(input.code(),input.name(),input.version());
  write(()->{if(jdbc.update("UPDATE md_product_attribute_group SET name=?,version=version+1,updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND code=? AND version=?",input.name().trim(),actor(c),tenant(c),id,input.code(),input.version())!=1)throw changed();return null;});
 }
 /** 稳定属性仅调整分组，不修改语义编码、已发布修订或商品值。 */
 public void updateAttribute(VerifiedTenantContext c,String id,AttributeInput input){
  require(c,"update");uuid(id);if(input==null)throw invalid();common(input.code(),input.code(),input.version());
  if(input.groupId()!=null)uuid(input.groupId());
  write(()->{if(jdbc.update("UPDATE md_product_attribute SET group_id=UUID_TO_BIN(?),version=version+1,updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND code=? AND version=?",input.groupId(),actor(c),tenant(c),id,input.code(),input.version())!=1)throw changed();return null;});
 }
 /** 独立启停不删除修订；group参数由固定Controller选择，不由客户端指定表。 */
 public void setStatus(VerifiedTenantContext c,String id,CatalogData.Status input,boolean group){
  require(c,"set_status");uuid(id);if(input==null||input.enabled()==null||input.version()<0)throw invalid();
  String table=group?"md_product_attribute_group":"md_product_attribute";
  write(()->{if(jdbc.update("UPDATE "+table+" SET enabled=?,version=version+1,updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND version=?",input.enabled(),actor(c),tenant(c),id,input.version())!=1)throw changed();return null;});
 }
 /** 退役仅阻止新模板选用，保留旧商品解释；父CAS保护且不能退役草稿。 */
 public void retireRevision(VerifiedTenantContext c,String id,long version){
  require(c,"publish");uuid(id);write(()->{
   var rows=jdbc.queryForList("SELECT BIN_TO_UUID(attribute_id) id FROM md_product_attribute_revision WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",String.class,tenant(c),id);
   if(rows.isEmpty())throw changed();lockAttribute(c,rows.getFirst(),version);
   if(jdbc.update("UPDATE md_product_attribute_revision SET state='RETIRED',updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND state='PUBLISHED'",actor(c),tenant(c),id)!=1)throw changed();return null;
  });
 }


 /** 仅DRAFT可编辑正文/选项；父CAS串行，已发布版本无原地更新通道。 */
 public void updateRevision(VerifiedTenantContext c,String id,RevisionInput input){
  require(c,"update");uuid(id);if(input==null||input.name()==null||input.name().isBlank())throw invalid();validateRevision(input);
  write(()->{
   lockRevision(c,id,input.version());
   if(input.unitId()!=null){uuid(input.unitId());if(jdbc.queryForObject("SELECT COUNT(*) FROM md_product_unit WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND enabled=TRUE",Integer.class,tenant(c),input.unitId())!=1)throw invalid();}
   jdbc.update("UPDATE md_product_attribute_revision SET name=?,data_type=?,unit_id=UUID_TO_BIN(?),numeric_scale=?,validation_json=?,updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",input.name().trim(),input.dataType(),input.unitId(),input.numericScale(),input.validationJson(),actor(c),tenant(c),id);
   if(input.options()!=null){
    jdbc.update("DELETE FROM md_product_attribute_option WHERE tenant_id=UUID_TO_BIN(?) AND revision_id=UUID_TO_BIN(?)",tenant(c),id);
    if(!Set.of("SINGLE_SELECT","MULTI_SELECT").contains(input.dataType())&&!input.options().isEmpty()||input.options().size()>50)throw invalid();
    int order=0;for(var o:input.options()){if(o==null)throw invalid();common(o.code(),o.label(),0);jdbc.update("INSERT INTO md_product_attribute_option(id,tenant_id,revision_id,option_code,label,sort_order,created_by,updated_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?,UUID_TO_BIN(?),UUID_TO_BIN(?))",UUID.randomUUID().toString(),tenant(c),id,o.code(),o.label(),order++,actor(c),actor(c));}
   }
  return null;});
 }

 /** 属性聚合CAS同时覆盖修订和选项，避免发布后并发插入修改已发布含义。 */
 private void lockAttribute(VerifiedTenantContext c,String id,long version){
  var rows=jdbc.queryForList("SELECT version,enabled FROM md_product_attribute WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) FOR UPDATE",tenant(c),id);
  if(rows.isEmpty()||((Number)rows.getFirst().get("version")).longValue()!=version||!Boolean.TRUE.equals(rows.getFirst().get("enabled")))throw changed();
  jdbc.update("UPDATE md_product_attribute SET version=version+1,updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",actor(c),tenant(c),id);
 }
 private Map<String,Object> lockRevision(VerifiedTenantContext c,String id,long version){
  var rows=jdbc.queryForList("SELECT BIN_TO_UUID(attribute_id) attribute_id,data_type,state FROM md_product_attribute_revision WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",tenant(c),id);
  if(rows.isEmpty())throw changed();var row=rows.getFirst();lockAttribute(c,(String)row.get("attribute_id"),version);
  var current=jdbc.queryForObject("SELECT state FROM md_product_attribute_revision WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) FOR UPDATE",String.class,tenant(c),id);
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