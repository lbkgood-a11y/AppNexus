package com.appnexus.masterdata.product;
import com.appnexus.masterdata.MasterData;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** 商品属性模板及不可变版本执行器；发布时一次性固定属性修订和模板项。 */
@org.springframework.stereotype.Service
public final class ProductTemplateService {
 private final JdbcTemplate jdbc; private final AccessAuthorization access; private final TransactionTemplate tx;
 /** 模板创建：code为租户内唯一编码，name为1至200字符，purpose仅BASIC/QUALITY/EXTENSION，version新建0。 */
 public record TemplateInput(String code,String name,String purpose,long version){}
 /** 模板项固定attributeId/revisionId，required为启用必填；searchable仅支持已实现类型，自定义输入尚未接入时必须false。 */
 public record ItemInput(String attributeId,String revisionId,String groupCode,String groupName,boolean required,boolean searchable,boolean allowCustom,int sortOrder){}
 /** 版本草稿：description最多1000字符，items固定已发布属性修订；version为当前模板父CAS。 */
 public record VersionInput(String description,List<ItemInput> items,long version){}
 /**
 * @param jdbc 宿主UTC数据源
 * @param access 租户授权公开端口
 * @param manager 同源事务管理器 */
 public ProductTemplateService(JdbcTemplate jdbc,AccessAuthorization access,PlatformTransactionManager manager){this.jdbc=jdbc;this.access=access;this.tx=new TransactionTemplate(manager);}
 /** 返回当前租户模板定义；发布版本不随定义列表查询改变。 */
 public Object list(VerifiedTenantContext c){require(c,"read");return jdbc.query("SELECT BIN_TO_UUID(id) id,code,name,purpose,enabled,version FROM md_product_template WHERE tenant_id=UUID_TO_BIN(?) ORDER BY code,id",(r,n)->Map.of("id",r.getString("id"),"code",r.getString("code"),"name",r.getString("name"),"purpose",r.getString("purpose"),"enabled",r.getBoolean("enabled"),"version",r.getLong("version")),tenant(c));}
 /** 固定模板的历史版本，发布后仍按原属性修订读取。 */
 public Object versions(VerifiedTenantContext c,String id){
  require(c,"read");uuid(id);
  return jdbc.queryForList("SELECT BIN_TO_UUID(id) id,version_no versionNo,state,description,purpose,BIN_TO_UUID(created_by) createdBy,created_at createdAt,BIN_TO_UUID(updated_by) updatedBy,updated_at updatedAt FROM md_product_template_version WHERE tenant_id=UUID_TO_BIN(?) AND template_id=UUID_TO_BIN(?) ORDER BY version_no DESC",tenant(c),id);
 }
 /** 固定版本模板项，带发布字段约束。 */
 public Object items(VerifiedTenantContext c,String id){
  require(c,"read");uuid(id);
  return jdbc.queryForList("SELECT BIN_TO_UUID(i.id) id,BIN_TO_UUID(i.attribute_id) attributeId,BIN_TO_UUID(i.attribute_revision_id) revisionId,r.name,i.data_type dataType,i.required_flag requiredFlag,i.searchable,i.allow_custom allowCustom,i.group_code groupCode,i.group_name groupName FROM md_product_template_item i JOIN md_product_attribute_revision r ON r.tenant_id=i.tenant_id AND r.id=i.attribute_revision_id WHERE i.tenant_id=UUID_TO_BIN(?) AND i.template_version_id=UUID_TO_BIN(?) ORDER BY i.sort_order,i.id",tenant(c),id);
 }
 /** 创建独立模板身份；用途创建后不可修改，SPEC不开放。 */
 public Map<String,String> create(VerifiedTenantContext c,TemplateInput i){require(c,"create");valid(i);String id=UUID.randomUUID().toString();return write(()->{jdbc.update("INSERT INTO md_product_template(id,tenant_id,code,name,purpose,created_by,updated_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?,UUID_TO_BIN(?),UUID_TO_BIN(?))",id,tenant(c),i.code().trim(),i.name().trim(),i.purpose(),actor(c),actor(c));return Map.of("id",id);});}
 /** 一次创建完整草稿项，父CAS保护版本号；错配修订和重复属性导致整体回滚。 */
 public Map<String,String> createVersion(VerifiedTenantContext c,String templateId,VersionInput i){require(c,"update");uuid(templateId);if(i==null||i.description()==null||i.description().length()>1000||i.version()<0||i.items()==null)throw invalid();String id=UUID.randomUUID().toString();return write(()->{lockTemplate(c,templateId,i.version());var purpose=jdbc.queryForObject("SELECT purpose FROM md_product_template WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",String.class,tenant(c),templateId);long no=jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM md_product_template_version WHERE tenant_id=UUID_TO_BIN(?) AND template_id=UUID_TO_BIN(?)",Long.class,tenant(c),templateId);jdbc.update("INSERT INTO md_product_template_version(id,tenant_id,template_id,version_no,purpose,description,created_by,updated_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?,UUID_TO_BIN(?),UUID_TO_BIN(?))",id,tenant(c),templateId,no,purpose,i.description(),actor(c),actor(c));for(var item:i.items()){uuid(item.attributeId());uuid(item.revisionId());var revisions=jdbc.queryForList("SELECT data_type,state FROM md_product_attribute_revision WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND attribute_id=UUID_TO_BIN(?)",tenant(c),item.revisionId(),item.attributeId());if(revisions.isEmpty()||!"PUBLISHED".equals(revisions.getFirst().get("state")))throw invalid();String dataType=(String)revisions.getFirst().get("data_type");if(item.searchable()&&dataType.equals("TEXT")||item.allowCustom())throw invalid();jdbc.update("INSERT INTO md_product_template_item(id,tenant_id,template_version_id,attribute_id,attribute_revision_id,data_type,group_code,group_name,required_flag,searchable,allow_custom,sort_order,created_by,updated_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?,?,?,?,?,UUID_TO_BIN(?),UUID_TO_BIN(?))",UUID.randomUUID().toString(),tenant(c),id,item.attributeId(),item.revisionId(),dataType,Objects.toString(item.groupCode(),""),Objects.toString(item.groupName(),""),item.required(),item.searchable(),item.allowCustom(),item.sortOrder(),actor(c),actor(c));}return Map.of("id",id,"version",String.valueOf(no),"purpose",purpose);});}
 /** 发布非空草稿版本，version为父模板CAS；发布后无修改正文接口。 */
 public void publish(VerifiedTenantContext c,String versionId,long version){require(c,"publish");uuid(versionId);write(()->{var parents=jdbc.queryForList("SELECT BIN_TO_UUID(template_id) id FROM md_product_template_version WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",String.class,tenant(c),versionId);if(parents.isEmpty())throw changed();lockTemplate(c,parents.getFirst(),version);if(jdbc.queryForObject("SELECT COUNT(*) FROM md_product_template_item WHERE tenant_id=UUID_TO_BIN(?) AND template_version_id=UUID_TO_BIN(?)",Integer.class,tenant(c),versionId)==0)throw invalid();var rows=jdbc.query("SELECT tv.state,t.purpose FROM md_product_template_version tv JOIN md_product_template t ON t.tenant_id=tv.tenant_id AND t.id=tv.template_id WHERE tv.tenant_id=UUID_TO_BIN(?) AND tv.id=UUID_TO_BIN(?)",(r,n)->Map.of("state",r.getString("state"),"purpose",r.getString("purpose")),tenant(c),versionId);if(rows.isEmpty()||!rows.getFirst().get("state").equals("DRAFT"))throw changed();jdbc.update("UPDATE md_product_template_version SET state='PUBLISHED',published_at=CURRENT_TIMESTAMP(6),updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",actor(c),tenant(c),versionId);return null;});}
 /** 父模板CAS串行创建版本与发布；发布失败父审计也回滚。 */
 private void lockTemplate(VerifiedTenantContext c,String id,long version){
  if(version<0)throw invalid();
  var rows=jdbc.queryForList("SELECT version,enabled FROM md_product_template WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) FOR UPDATE",tenant(c),id);
  if(rows.isEmpty()||((Number)rows.getFirst().get("version")).longValue()!=version||!Boolean.TRUE.equals(rows.getFirst().get("enabled")))throw changed();
  jdbc.update("UPDATE md_product_template SET version=version+1,updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",actor(c),tenant(c),id);
 }
 private void require(VerifiedTenantContext c,String a){if(c==null||!access.hasPermission(AccessAuthorization.Subject.tenant(c.accountId(),c.tenantId(),c.memberId()),"masterdata.producttemplate:"+a))throw new MasterData.Rejection(403,"MASTER_DATA_DENIED");}
 private <T>T write(java.util.function.Supplier<T> s){try{return tx.execute(x->s.get());}catch(DataIntegrityViolationException e){throw new MasterData.Rejection(409,"PRODUCT_TEMPLATE_CONFLICT");}}
 private static void valid(TemplateInput i){if(i==null||i.code()==null||!i.code().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")||i.name()==null||i.name().isBlank()||!Set.of("BASIC","QUALITY","EXTENSION").contains(i.purpose())||i.version()<0)throw invalid();}
 private static void uuid(String s){try{UUID.fromString(s);}catch(Exception e){throw invalid();}} private static String tenant(VerifiedTenantContext c){return c.tenantId().toString();} private static String actor(VerifiedTenantContext c){return c.accountId().toString();}
 private static MasterData.Rejection invalid(){return new MasterData.Rejection(400,"PRODUCT_TEMPLATE_INPUT_INVALID");} private static MasterData.Rejection changed(){return new MasterData.Rejection(409,"PRODUCT_TEMPLATE_CHANGED");}
}