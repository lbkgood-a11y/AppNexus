package com.appnexus.masterdata.product;
import com.appnexus.masterdata.adapter.jdbc.ProductTemplateServiceSqlMapper;
import com.appnexus.masterdata.MasterData;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;

import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** 商品属性模板及不可变版本执行器；发布时一次性固定属性修订和模板项。 */
@org.springframework.stereotype.Service
public final class ProductTemplateService {
 private final ProductTemplateServiceSqlMapper statements;

  private final AccessAuthorization access; private final TransactionTemplate tx;
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
 public ProductTemplateService(AccessAuthorization access,PlatformTransactionManager manager,ProductTemplateServiceSqlMapper statements){this.statements=statements;this.access=access;this.tx=new TransactionTemplate(manager);}
 /** 返回当前租户模板定义；发布版本不随定义列表查询改变。 */
 public Object list(VerifiedTenantContext c){require(c,"read");return statements.summaries(tenant(c));}
 /** 固定模板的历史版本，发布后仍按原属性修订读取。 */
 public Object versions(VerifiedTenantContext c,String id){
  require(c,"read");uuid(id);
  return statements.statement1(tenant(c),id);
 }
 /** 固定版本模板项，带发布字段约束。 */
 public Object items(VerifiedTenantContext c,String id){
  require(c,"read");uuid(id);
  return statements.statement2(tenant(c),id);
 }
 /** 创建独立模板身份；用途创建后不可修改，SPEC不开放。 */
 public Map<String,String> create(VerifiedTenantContext c,TemplateInput i){require(c,"create");valid(i);String id=UUID.randomUUID().toString();return write(()->{statements.statement3(id,tenant(c),i.code().trim(),i.name().trim(),i.purpose(),actor(c),actor(c));return Map.of("id",id);});}
 /** 一次创建完整草稿项，父CAS保护版本号；错配修订和重复属性导致整体回滚。 */
 public Map<String,String> createVersion(VerifiedTenantContext c,String templateId,VersionInput i){require(c,"update");uuid(templateId);if(i==null||i.items()!=null&&i.items().size()>200||i.description()==null||i.description().length()>1000||i.version()<0||i.items()==null)throw invalid();String id=UUID.randomUUID().toString();return write(()->{lockTemplate(c,templateId,i.version());var purpose=statements.statement4(tenant(c),templateId);long no=statements.statement5(tenant(c),templateId);statements.statement6(id,tenant(c),templateId,no,purpose,i.description(),actor(c),actor(c));saveItems(c,id,i.items());return Map.of("id",id,"version",String.valueOf(no),"purpose",purpose);});}

 /** 编辑DRAFT全量项；单次事务替换，发布版本不能修改，版本号保持不变。 */
 public void updateVersion(VerifiedTenantContext c,String id,VersionInput input){
  require(c,"update");uuid(id);if(input==null||input.items()==null||input.items().size()>200||input.description()==null||input.description().length()>1000)throw invalid();
  write(()->{
   var rows=statements.statement7(tenant(c),id);if(rows.isEmpty())throw changed();
   lockTemplate(c,(String)rows.getFirst().get("id"),input.version());
   if(!"DRAFT".equals(statements.statement8(tenant(c),id)))throw changed();
   statements.statement9(tenant(c),id);saveItems(c,id,input.items());
   statements.statement10(input.description(),actor(c),tenant(c),id);return null;
  });
 }
 private void saveItems(VerifiedTenantContext c,String id,List<ItemInput> items){
for(var item:items){if(item==null)throw invalid();uuid(item.attributeId());uuid(item.revisionId());var revisions=statements.statement11(tenant(c),item.revisionId(),item.attributeId());if(revisions.isEmpty()||!"PUBLISHED".equals(revisions.getFirst().get("state")))throw invalid();String dataType=(String)revisions.getFirst().get("data_type");var groupRows=statements.statement12(tenant(c),item.attributeId());String groupCode=groupRows.isEmpty()?"":(String)groupRows.getFirst().get("code"),groupName=groupRows.isEmpty()?"":(String)groupRows.getFirst().get("name");if(item.searchable()&&dataType.equals("TEXT")||item.allowCustom()&&!Set.of("SINGLE_SELECT","MULTI_SELECT").contains(dataType))throw invalid();statements.statement13(UUID.randomUUID().toString(),tenant(c),id,item.attributeId(),item.revisionId(),dataType,groupCode,groupName,item.required(),item.searchable(),item.allowCustom(),item.sortOrder(),actor(c),actor(c));}
 }
 /** 发布非空草稿版本，version为父模板CAS；发布后无修改正文接口。 */
 public void publish(VerifiedTenantContext c,String versionId,long version){require(c,"publish");uuid(versionId);write(()->{var parents=statements.statement14(tenant(c),versionId);if(parents.isEmpty())throw changed();lockTemplate(c,parents.getFirst(),version);if(statements.statement15(tenant(c),versionId)==0)throw invalid();if(statements.statement16(tenant(c),versionId)>0)throw invalid();var rows=statements.versionState(tenant(c),versionId);if(rows.isEmpty()||!rows.getFirst().get("state").equals("DRAFT"))throw changed();statements.statement17(actor(c),tenant(c),versionId);return null;});}

 /** 模板名称修改不回写已发布版本，编码/用途不能修改。 */
 public void update(VerifiedTenantContext c,String id,TemplateInput input){
  require(c,"update");uuid(id);valid(input);write(()->{
   if(statements.statement18(input.name().trim(),actor(c),tenant(c),id,input.code(),input.purpose(),input.version())!=1)throw changed();return null;
  });
 }
 /** 独立启停不级联旧商品，停用阻止新配置选用。 */
 public void status(VerifiedTenantContext c,String id,CatalogData.Status input){
  require(c,"set_status");uuid(id);if(input==null||input.enabled()==null||input.version()<0)throw invalid();
  write(()->{if(statements.statement19(input.enabled(),actor(c),tenant(c),id,input.version())!=1)throw changed();return null;});
 }
 /** 退役发布版本只改准入状态，商品旧固定引用永久可读。 */
 public void retire(VerifiedTenantContext c,String id,long version){
  require(c,"publish");uuid(id);write(()->{
   var rows=statements.statement20(tenant(c),id);
   if(rows.isEmpty())throw changed();lockTemplate(c,rows.getFirst(),version);
   if(statements.statement21(actor(c),tenant(c),id)!=1)throw changed();return null;
  });
 }
 /** 父模板CAS串行创建版本与发布；发布失败父审计也回滚。 */
 private void lockTemplate(VerifiedTenantContext c,String id,long version){
  if(version<0)throw invalid();
  var rows=statements.statement22(tenant(c),id);
  if(rows.isEmpty()||((Number)rows.getFirst().get("version")).longValue()!=version||!Boolean.TRUE.equals(rows.getFirst().get("enabled")))throw changed();
  statements.statement23(actor(c),tenant(c),id);
 }
 private void require(VerifiedTenantContext c,String a){if(c==null||!access.hasPermission(AccessAuthorization.Subject.tenant(c.accountId(),c.tenantId(),c.memberId()),"masterdata.producttemplate:"+a))throw new MasterData.Rejection(403,"MASTER_DATA_DENIED");}
 private <T>T write(java.util.function.Supplier<T> s){try{return tx.execute(x->s.get());}catch(DataIntegrityViolationException e){throw new MasterData.Rejection(409,"PRODUCT_TEMPLATE_CONFLICT");}}
 private static void valid(TemplateInput i){if(i==null||i.code()==null||!i.code().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")||i.name()==null||i.name().isBlank()||i.name().length()>200||i.purpose()==null||!Set.of("BASIC","SPEC","QUALITY","EXTENSION").contains(i.purpose())||i.version()<0)throw invalid();}
 private static void uuid(String s){try{UUID.fromString(s);}catch(Exception e){throw invalid();}} private static String tenant(VerifiedTenantContext c){return c.tenantId().toString();} private static String actor(VerifiedTenantContext c){return c.accountId().toString();}
 private static MasterData.Rejection invalid(){return new MasterData.Rejection(400,"PRODUCT_TEMPLATE_INPUT_INVALID");} private static MasterData.Rejection changed(){return new MasterData.Rejection(409,"PRODUCT_TEMPLATE_CHANGED");}
}