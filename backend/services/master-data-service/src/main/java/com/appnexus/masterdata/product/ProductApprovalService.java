package com.appnexus.masterdata.product;
import com.appnexus.contracts.workflow.*;
import com.appnexus.contracts.businessobject.*;
import com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import com.appnexus.masterdata.MasterData;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
/** 商品审核申请接入既有策略、启动和可靠终态交付；完整正文仅留业务库，处理中冻结商品。 */
@Service @ConditionalOnProperty(name="app.workflow.engine.enabled",havingValue="true")
public final class ProductApprovalService implements Provider,BusinessObjectRuntime {
 private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final ProductV1Service products;private final WorkflowPlatform platform;private final ObjectProvider<BusinessObjectExecution> execution;private final ObjectProvider<WorkflowTaskAccess> tasks;
 /** @param jdbc UTC数据源 @param manager 同数据源事务 @param products 商品授权及快照 @param platform 公开身份/组织端口 @param execution 延迟对象公共执行端口 @param tasks 延迟任务授权端口 */
 public ProductApprovalService(JdbcTemplate jdbc,PlatformTransactionManager manager,ProductV1Service products,WorkflowPlatform platform,ObjectProvider<BusinessObjectExecution> execution,ObjectProvider<WorkflowTaskAccess> tasks){this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);this.products=products;this.platform=platform;this.execution=execution;this.tasks=tasks;}
 private static final List<Field> FIELDS=List.of(new Field("productVersion","商品版本","Product version","NUMBER","",true));
 private static final Operation SUBMIT=new Operation("submit","提交商品审核","Submit product approval","masterdata.product.approval:submit","ProductApproval.submit",new PageOperation("master-data/product","/master-data/product","商品档案","Products","POST","/api/master-data/product/{id}/approval"),List.of(),new InteractionContract("ProductApproval.submit",1,"ProductApproval","submit","提交商品审核","Submit product approval",InteractionContract.Trigger.USER_ACTION,List.of(new InteractionContract.Capability(InteractionContract.Service.WORKFLOW,InteractionContract.Completion.CALLBACK,"productApproval","approval",FIELDS,Set.of("APPROVED","REJECTED","WITHDRAWN"),"product.approval",1))));
 @Override public Descriptor descriptor(){return new Descriptor("ProductApproval","商品审核申请","Product approval","master-data",2,Category.TRANSACTION,FIELDS,List.of(SUBMIT),new DataAccess("TENANT",List.of(new DataResource("masterdata.product.approval","","商品审核申请","Product approvals",List.of(new DataField("productVersion","商品版本","Product version","TEXT",Set.of("READ_ONLY","HIDDEN"))))),Map.of("submit",Set.of("ALL","ORGANIZATION","ORGANIZATION_SUBTREE"))));}

 /** 对象级授权取数供所有通用能力复用，不返回商品完整快照。 */
 @Override public Snapshot read(Context c,String type,String id,String operation,long version,Set<String> fields){
  if(!"ProductApproval".equals(type)||!"submit".equals(operation)||!Set.of("productVersion").containsAll(fields))throw new SecurityException("PRODUCT_APPROVAL_DENIED");
  var w=new WorkflowContext(c.tenantId(),c.actorId(),c.requestId(),c.personId(),c.organizationId());
  if(!platform.active(c.tenantId(),c.actorId())||!platform.allowed(w,SUBMIT.permissionCode())||!platform.allowed(w,"masterdata.product:read"))throw new SecurityException("PRODUCT_APPROVAL_DENIED");
  var row=jdbc.queryForMap("SELECT product_version,BIN_TO_UUID(applicant_id) applicant,BIN_TO_UUID(organization_id) organization,state FROM md_product_approval WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) FOR UPDATE",c.tenantId(),id);
  if(!Objects.equals(row.get("applicant"),c.actorId()))throw new SecurityException("PRODUCT_APPROVAL_DENIED");
  if(((Number)row.get("product_version")).longValue()!=version)throw new BusinessObjects.Rejection(409,"BUSINESS_OBJECT_VERSION_CONFLICT");
  if(!"SUBMITTED".equals(row.get("state")))throw new IllegalArgumentException("PRODUCT_APPROVAL_STATE_CHANGED");
  return new Snapshot(version,row.get("organization").toString(),Map.of("productVersion",row.get("product_version")));
 }
 /** 商品审核只声明工作流能力，不能通过公共入口伪造直接受理。 */
 @Override public BusinessObjectRuntime.Result execute(Context c,String type,String id,String operation,long version,Map<String,?> values){throw new SecurityException("PRODUCT_APPROVAL_WORKFLOW_REQUIRED");}
 /** 当前账号有效任职组织，用于选择审批路由，不开放自由组织ID。 */
 public Object organizations(WorkflowContext c){return platform.documentOrganizations(c);}
 /** 提交固定商品版本，完整性不足拒绝；流程配置缺失整单回滚，不伪造审批。 */
 public Object submit(VerifiedTenantContext c,WorkflowContext w,String product,long version,String organization){if(!platform.allowed(w,"masterdata.product.approval:submit")||!platform.allowed(w,"masterdata.product:read"))throw new MasterData.Rejection(403,"MASTER_DATA_DENIED");UUID.fromString(organization);if(platform.documentOrganizations(w).stream().noneMatch(o->o.id().equalsIgnoreCase(organization)))throw new MasterData.Rejection(409,"PRODUCT_APPROVAL_ORGANIZATION_INVALID");return tx.execute(s->{var lock=jdbc.queryForMap("SELECT status,version,structure_state,draft_archived_at FROM md_product_v1 WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) FOR UPDATE",w.tenantId(),product);var prior=jdbc.queryForList("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(product_id) productId,product_version,instance_id instanceId,BIN_TO_UUID(organization_id) organization FROM md_product_approval WHERE tenant_id=UUID_TO_BIN(?) AND applicant_id=UUID_TO_BIN(?) AND request_key=UUID_TO_BIN(?)",w.tenantId(),w.actorId(),w.requestId());if(!prior.isEmpty()){var r=prior.getFirst();if(!product.equals(r.get("productId"))||((Number)r.get("product_version")).longValue()!=version+1||!organization.equals(r.get("organization")))throw new MasterData.Rejection(409,"PRODUCT_APPROVAL_KEY_REUSED");return Map.of("id",r.get("id"),"instanceId",r.get("instanceId"),"version",version+1);}
products.ensureNotInApproval(c,product);var before=products.extensionSnapshot(c,product);if(((Number)lock.get("version")).longValue()!=version||"ENABLED".equals(lock.get("status"))||lock.get("draft_archived_at")!=null||!"COMPLETE".equals(lock.get("structure_state")))throw new MasterData.Rejection(409,"PRODUCT_APPROVAL_NOT_READY");products.requireCompleteForApproval(c,product);String id=UUID.randomUUID().toString();long frozen=version+1;jdbc.update("UPDATE md_product_v1 SET version=version+1,updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",w.actorId(),w.tenantId(),product);jdbc.update("INSERT INTO md_product_approval(id,tenant_id,product_id,request_key,product_version,applicant_id,organization_id,basis_snapshot,created_by,updated_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,UUID_TO_BIN(?),UUID_TO_BIN(?),?,UUID_TO_BIN(?),UUID_TO_BIN(?))",id,w.tenantId(),product,w.requestId(),frozen,w.actorId(),organization,new tools.jackson.databind.ObjectMapper().writeValueAsString(products.extensionSnapshot(c,product)),w.actorId(),w.actorId());var executed=execution.getObject().execute(new Context(w.tenantId(),w.actorId(),w.requestId(),w.personId(),w.organizationId()),new BusinessObjectExecution.Request("ProductApproval",id,"submit","ProductApproval.submit",frozen));var resolved=executed.resolution();String instance=executed.output().get("instanceId").toString();jdbc.update("UPDATE md_product_approval SET instance_id=?,resolution_id=? WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",instance,resolved.id(),w.tenantId(),id);products.extensionEvent(c,product,"APPROVAL_SUBMIT",before);return Map.of("id",id,"instanceId",instance,"version",frozen);});}
 /** 原商品读取权内审核历史；不将申请UUID作为数据授权。 */
 public Object list(VerifiedTenantContext c,String product){products.read(c,product);return jdbc.queryForList("SELECT BIN_TO_UUID(id) id,product_version productVersion,state,instance_id instanceId,created_at createdAt,updated_at updatedAt FROM md_product_approval WHERE tenant_id=UUID_TO_BIN(?) AND product_id=UUID_TO_BIN(?) ORDER BY created_at DESC",c.tenantId().toString(),product);}
 /** 冻结记录通过对象运行端口公开，工作流引擎不能查询商品表。 */
 @Override public ApprovalRecord approvalRecord(Context c,String id,boolean lock){
  var row=jdbc.queryForMap("SELECT product_version,state,instance_id,delivery_id,BIN_TO_UUID(applicant_id) applicant,BIN_TO_UUID(organization_id) organization FROM md_product_approval WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)"+(lock?" FOR UPDATE":""),c.tenantId(),id);
  return new ApprovalRecord(((Number)row.get("product_version")).longValue(),row.get("organization").toString(),row.get("applicant").toString(),row.get("state").toString(),Objects.toString(row.get("instance_id"),null),Objects.toString(row.get("delivery_id"),null),Map.of("productVersion",row.get("product_version")));
 }
 /** 商品审核结果及审计在公共桥接事务中落地，状态不等同于自动启用商品。 */
 @Override public void completeApproval(Context c,String id,long version,String invocation,String instance,String result){
  int changed=jdbc.update("UPDATE md_product_approval SET state=?,delivery_id=?,updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND product_version=? AND instance_id=? AND state='SUBMITTED'",result,invocation,c.actorId(),c.tenantId(),id,version,instance);
  if(changed!=1)throw new IllegalArgumentException("BUSINESS_OBJECT_APPROVAL_STATE_CONFLICT");
 }
 /** 审批原文只允许原申请人或当前有效任务持有人；结束任务不保留快照读取授权。 */
 public Object detail(WorkflowContext c,String id,String task){var rows=jdbc.queryForList("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(product_id) productId,product_version productVersion,BIN_TO_UUID(applicant_id) applicant,BIN_TO_UUID(organization_id) organization,state,CAST(basis_snapshot AS CHAR) snapshot,instance_id instanceId FROM md_product_approval WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",c.tenantId(),id);if(rows.size()!=1||!platform.active(c.tenantId(),c.actorId()))throw new MasterData.Rejection(403,"MASTER_DATA_DENIED");var row=rows.getFirst();boolean own=Objects.equals(row.get("applicant"),c.actorId())&&platform.allowed(c,"masterdata.product:read");if(!own&&!tasks.getObject().permits(c,task,"ProductApproval",id,((Number)row.get("productVersion")).longValue(),"productApproval","productVersion",false))throw new MasterData.Rejection(403,"MASTER_DATA_DENIED");return Map.of("state",row.get("state"),"productVersion",row.get("productVersion"),"snapshot",new tools.jackson.databind.ObjectMapper().readTree((String)row.get("snapshot")));}
}
