package com.appnexus.masterdata.application.product;
import com.appnexus.masterdata.api.product.ProductCatalogData;
import com.appnexus.masterdata.api.masterdata.MasterData;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.authorization.ScopedWriteGate;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import com.appnexus.masterdata.api.product.ProductTemplateData.TemplateInput;
import com.appnexus.masterdata.api.product.ProductTemplateData.ItemInput;
import com.appnexus.masterdata.api.product.ProductTemplateData.VersionInput;


/** 商品属性模板及不可变版本执行器；发布时一次性固定属性修订和模板项。 */
public final class ProductTemplateService implements com.appnexus.masterdata.api.product.ProductTemplateManagement {
 private final ProductTemplateRepository statements;

  private final ScopedWriteGate writes;
  private final AccessAuthorization access; private final ProductTemplateTransaction tx;
 
 
 
 /** 人员或商品完整用例依赖装配；所有输出参与原业务安全与事务约束。
  * @param access 租户授权公开端口，每次调用检查独立操作
  * @param transaction REQUIRED模板完整事务，完整性冲突沿用稳定拒绝码
  * @param statements 固定模板仓储，不接收SQL、表名或外部租户
  */
 public ProductTemplateService(AccessAuthorization access,ProductTemplateTransaction transaction,ProductTemplateRepository statements,ScopedWriteGate writes){this.writes=writes;this.statements=statements;this.access=access;this.tx=transaction;}
 /** 返回当前租户模板定义；发布版本不随定义列表查询改变。 */
 public Object list(VerifiedTenantContext c){require(c,"read");return statements.summaries(tenant(c));}
 /** 固定模板的历史版本，发布后仍按原属性修订读取。 */
 public Object versions(VerifiedTenantContext c,String id){
  require(c,"read");uuid(id);
  return statements.versions(tenant(c),id);
 }
 /** 固定版本模板项，带发布字段约束。 */
 public Object items(VerifiedTenantContext c,String id){
  require(c,"read");uuid(id);
  return statements.items(tenant(c),id);
 }
 /** 创建独立模板身份；用途创建后不可修改，SPEC不开放。 */
 public Map<String,String> create(VerifiedTenantContext c,TemplateInput i){require(c,"create");valid(i);String id=UUID.randomUUID().toString();return write(()->{authorizeWrite(c,"create",id);statements.createTemplate(id,tenant(c),i.code().trim(),i.name().trim(),i.purpose(),actor(c),actor(c));return Map.of("id",id);});}
 /** 一次创建完整草稿项，父CAS保护版本号；错配修订和重复属性导致整体回滚。 */
 public Map<String,String> createVersion(VerifiedTenantContext c,String templateId,VersionInput i){require(c,"update");uuid(templateId);if(i==null||i.items()!=null&&i.items().size()>200||i.description()==null||i.description().length()>1000||i.version()<0||i.items()==null)throw invalid();String id=UUID.randomUUID().toString();return write(()->{lockTemplate(c,templateId,i.version(),"update");var purpose=statements.purpose(tenant(c),templateId);long no=statements.nextVersion(tenant(c),templateId);authorizeWrite(c,"update",templateId);statements.createVersion(id,tenant(c),templateId,no,purpose,i.description(),actor(c),actor(c));authorizeWrite(c,"update",id);saveItems(c,id,i.items());return Map.of("id",id,"version",String.valueOf(no),"purpose",purpose);});}

 /** 编辑DRAFT全量项；单次事务替换，发布版本不能修改，版本号保持不变。 */
 public void updateVersion(VerifiedTenantContext c,String id,VersionInput input){
  require(c,"update");uuid(id);if(input==null||input.items()==null||input.items().size()>200||input.description()==null||input.description().length()>1000)throw invalid();
  write(()->{
   var rows=statements.versionOwnerAndState(tenant(c),id);if(rows.isEmpty())throw changed();
   lockTemplate(c,(String)rows.getFirst().get("id"),input.version(),"update");
   if(!"DRAFT".equals(statements.lockVersionState(tenant(c),id)))throw changed();
   authorizeWrite(c,"update",id);
   statements.deleteVersionItems(tenant(c),id);saveItems(c,id,input.items());
   statements.updateDescription(input.description(),actor(c),tenant(c),id);return null;
  });
 }
 private void saveItems(VerifiedTenantContext c,String id,List<ItemInput> items){
for(var item:items){if(item==null)throw invalid();uuid(item.attributeId());uuid(item.revisionId());var revisions=statements.attributeRevision(tenant(c),item.revisionId(),item.attributeId());if(revisions.isEmpty()||!"PUBLISHED".equals(revisions.getFirst().get("state")))throw invalid();String dataType=(String)revisions.getFirst().get("data_type");var groupRows=statements.attributeGroup(tenant(c),item.attributeId());String groupCode=groupRows.isEmpty()?"":(String)groupRows.getFirst().get("code"),groupName=groupRows.isEmpty()?"":(String)groupRows.getFirst().get("name");if(item.searchable()&&dataType.equals("TEXT")||item.allowCustom()&&!Set.of("SINGLE_SELECT","MULTI_SELECT").contains(dataType))throw invalid();statements.createItem(UUID.randomUUID().toString(),tenant(c),id,item.attributeId(),item.revisionId(),dataType,groupCode,groupName,item.required(),item.searchable(),item.allowCustom(),item.sortOrder(),actor(c),actor(c));}
 }
 /** 发布非空草稿版本，version为父模板CAS；发布后无修改正文接口。 */
 public void publish(VerifiedTenantContext c,String versionId,long version){require(c,"publish");uuid(versionId);write(()->{var parents=statements.versionOwners(tenant(c),versionId);if(parents.isEmpty())throw changed();lockTemplate(c,parents.getFirst(),version,"publish");if(statements.itemCount(tenant(c),versionId)==0)throw invalid();if(statements.unavailableItemCount(tenant(c),versionId)>0)throw invalid();var rows=statements.versionState(tenant(c),versionId);if(rows.isEmpty()||!rows.getFirst().get("state").equals("DRAFT"))throw changed();writes.authorizeCatalogPublication(access,AccessAuthorization.Subject.tenant(c.accountId(),c.tenantId(),c.memberId()),"masterdata.producttemplate:publish");statements.publishVersion(actor(c),tenant(c),versionId);return null;});}

 /** 模板名称修改不回写已发布版本，编码/用途不能修改。 */
 public void update(VerifiedTenantContext c,String id,TemplateInput input){
  require(c,"update");uuid(id);valid(input);write(()->{
   authorizeWrite(c,"update",id);
   if(statements.updateTemplate(input.name().trim(),actor(c),tenant(c),id,input.code(),input.purpose(),input.version())!=1)throw changed();return null;
  });
 }
 /** 独立启停不级联旧商品，停用阻止新配置选用。 */
 public void status(VerifiedTenantContext c,String id,ProductCatalogData.Status input){
  require(c,"set_status");uuid(id);if(input==null||input.enabled()==null||input.version()<0)throw invalid();
  write(()->{authorizeWrite(c,"set_status",id);if(statements.setTemplateStatus(input.enabled(),actor(c),tenant(c),id,input.version())!=1)throw changed();return null;});
 }
 /** 退役发布版本只改准入状态，商品旧固定引用永久可读。 */
 public void retire(VerifiedTenantContext c,String id,long version){
  require(c,"publish");uuid(id);write(()->{
   var rows=statements.retirementOwners(tenant(c),id);
   if(rows.isEmpty())throw changed();lockTemplate(c,rows.getFirst(),version,"publish");
   authorizeWrite(c,"publish",id);
   if(statements.retireVersion(actor(c),tenant(c),id)!=1)throw changed();return null;
  });
 }
 /** 父模板CAS串行创建版本与发布；发布失败父审计也回滚。 */
 private void lockTemplate(VerifiedTenantContext c,String id,long version,String action){
  if(version<0)throw invalid();
  var rows=statements.lockTemplate(tenant(c),id);
  if(rows.isEmpty()||((Number)rows.getFirst().get("version")).longValue()!=version||!Boolean.TRUE.equals(rows.getFirst().get("enabled")))throw changed();
  authorizeWrite(c,action,"advance-template-version",id);
  statements.advanceTemplateVersion(actor(c),tenant(c),id);
 }
 /** 绑定同事务目录维护许可；共享父版本操作逐次复核实际业务权限。 */
 private void authorizeWrite(VerifiedTenantContext c,String action,String id){
  authorizeWrite(c,action,action,id);
 }
 private void authorizeWrite(VerifiedTenantContext c,String action,String operation,String id){
  var subject=AccessAuthorization.Subject.tenant(c.accountId(),c.tenantId(),c.memberId());
  try{
   writes.authorizeServiceOperation(c.tenantId(),"masterdata.producttemplate:"+operation,List.of(id),
       ()->access.hasPermission(subject,"masterdata.producttemplate:"+action));
  }catch(SecurityException denied){
   throw new MasterData.Rejection(403,"MASTER_DATA_DENIED");
  }
 }
 private void require(VerifiedTenantContext c,String a){if(c==null||!access.hasPermission(AccessAuthorization.Subject.tenant(c.accountId(),c.tenantId(),c.memberId()),"masterdata.producttemplate:"+a))throw new MasterData.Rejection(403,"MASTER_DATA_DENIED");}
 private <T>T write(java.util.function.Supplier<T> work){return tx.write(work);}
 private static void valid(TemplateInput i){if(i==null||i.code()==null||!i.code().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")||i.name()==null||i.name().isBlank()||i.name().length()>200||i.purpose()==null||!Set.of("BASIC","SPEC","QUALITY","EXTENSION").contains(i.purpose())||i.version()<0)throw invalid();}
 private static void uuid(String s){try{UUID.fromString(s);}catch(Exception e){throw invalid();}} private static String tenant(VerifiedTenantContext c){return c.tenantId().toString();} private static String actor(VerifiedTenantContext c){return c.accountId().toString();}
 private static MasterData.Rejection invalid(){return new MasterData.Rejection(400,"PRODUCT_TEMPLATE_INPUT_INVALID");} private static MasterData.Rejection changed(){return new MasterData.Rejection(409,"PRODUCT_TEMPLATE_CHANGED");}
}