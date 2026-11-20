package com.appnexus.masterdata.application.product;
import java.util.function.Supplier;
import com.appnexus.masterdata.api.product.ProductManagement;
import com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.appnexus.contracts.workflow.*;
import com.appnexus.contracts.businessobject.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import com.appnexus.masterdata.api.masterdata.MasterData;
import java.util.*;
import static com.appnexus.contracts.businessobject.ObjectRecordMapping.*;

/** 商品审核申请接入既有策略、启动和可靠终态交付；完整正文仅留业务库，处理中冻结商品。 */
public final class ProductApprovalService implements com.appnexus.masterdata.api.product.ProductApprovalManagement {
 private final ProductApprovalRepository statements;
 private final com.appnexus.masterdata.application.RecordTransaction transaction;
 private final ProductAggregateAccess products;
 private final ProductManagement reads;
 private final WorkflowPlatform platform;
 private final Supplier<BusinessObjectExecution> execution;
 private final Supplier<WorkflowTaskAccess> tasks;
 private final ProductContent content;
 /** 商品审批完整用例装配；应用不依赖Spring、Jackson或Mapper。
  * @param transaction 同数据源完整事务，申请、父冻结版本、审批执行及审计共同回滚
  * @param products 已授权父聚合及当前完整性协作
  * @param reads 原商品读取公开用例
  * @param platform 权威审批身份、组织和功能权限契约
  * @param execution 延迟公共对象执行，不重复建立审批执行器
  * @param tasks 延迟任务准入契约，不授予记录之外的资格
  * @param statements 固定审批仓储，强制可信租户与申请归属
  * @param content 固定快照编解码，禁止多态类型解析
  */
 public ProductApprovalService(com.appnexus.masterdata.application.RecordTransaction transaction,ProductAggregateAccess products,ProductManagement reads,WorkflowPlatform platform,Supplier<BusinessObjectExecution> execution,Supplier<WorkflowTaskAccess> tasks,ProductApprovalRepository statements,ProductContent content){this.transaction=transaction;this.products=products;this.reads=reads;this.platform=platform;this.execution=execution;this.tasks=tasks;this.statements=statements;this.content=content;}
 /** 当前账号有效任职组织，用于选择审批路由，不开放自由组织ID。 */
 public Object organizations(WorkflowContext c){return platform.documentOrganizations(c);}
 /** 提交固定商品版本，完整性不足拒绝；流程配置缺失整单回滚，不伪造审批。 */
 public Object submit(VerifiedTenantContext c,WorkflowContext w,String product,long version,String organization){if(!platform.allowed(w,"masterdata.product.approval:submit")||!platform.allowed(w,"masterdata.product:read"))throw new MasterData.Rejection(403,"MASTER_DATA_DENIED");UUID.fromString(organization);if(platform.documentOrganizations(w).stream().noneMatch(o->o.id().equalsIgnoreCase(organization)))throw new MasterData.Rejection(409,"PRODUCT_APPROVAL_ORGANIZATION_INVALID");return transaction.execute(()->{products.authorizeRecord(c,product,"read",true);var lock=statements.lockProduct(w.tenantId(),product);var prior=statements.existingRequest(w.tenantId(),w.actorId(),w.requestId());if(!prior.isEmpty()){var r=prior.getFirst();if(!product.equals(r.get("productId"))||((Number)r.get("product_version")).longValue()!=version+1||!organization.equals(r.get("organization")))throw new MasterData.Rejection(409,"PRODUCT_APPROVAL_KEY_REUSED");return Map.of("id",r.get("id"),"instanceId",r.get("instanceId"),"version",version+1);}
products.ensureNotInApproval(c,product);var before=products.extensionSnapshot(c,product);if(((Number)lock.get("version")).longValue()!=version||"ENABLED".equals(lock.get("status"))||lock.get("draft_archived_at")!=null||!"COMPLETE".equals(lock.get("structure_state")))throw new MasterData.Rejection(409,"PRODUCT_APPROVAL_NOT_READY");products.requireCompleteForApproval(c,product);String id=UUID.randomUUID().toString();long frozen=version+1;statements.freezeProductVersion(w.actorId(),w.tenantId(),product);statements.createApproval(id,w.tenantId(),product,w.requestId(),frozen,w.actorId(),organization,content.encode(products.extensionSnapshot(c,product)),w.actorId(),w.actorId());var executed=execution.get().execute(new Context(w.tenantId(),w.actorId(),w.requestId(),w.personId(),w.organizationId()),new BusinessObjectExecution.Request("ProductApproval",id,"submit","ProductApproval.submit",frozen));var resolved=executed.resolution();String instance=executed.output().get("instanceId").toString();statements.bindExecution(instance,resolved.id(),w.tenantId(),id);products.extensionEvent(c,product,"APPROVAL_SUBMIT",before);return Map.of("id",id,"instanceId",instance,"version",frozen);},null);}
 /** 原商品读取权内审核历史；不将申请UUID作为数据授权。 */
 public Object list(VerifiedTenantContext c,String product){reads.read(c,product);return statements.approvalHistory(c.tenantId().toString(),product);}
 /** 审批原文只允许原申请人或当前有效任务持有人；结束任务不保留快照读取授权。 */
 public Object detail(WorkflowContext c,String id,String task){var rows=statements.approvalDetail(c.tenantId(),id);if(rows.size()!=1||!platform.active(c.tenantId(),c.actorId()))throw new MasterData.Rejection(403,"MASTER_DATA_DENIED");var row=rows.getFirst();boolean own=Objects.equals(row.get("applicant"),c.actorId())&&platform.allowed(c,"masterdata.product:read");if(!own&&!tasks.get().permits(c,task,"ProductApproval",id,((Number)row.get("productVersion")).longValue(),"productApproval","productVersion",false))throw new MasterData.Rejection(403,"MASTER_DATA_DENIED");return Map.of("state",row.get("state"),"productVersion",row.get("productVersion"),"snapshot",content.decode((String)row.get("snapshot")));}
}
