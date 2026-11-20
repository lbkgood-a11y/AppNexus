package com.appnexus.masterdata.product;
import com.appnexus.masterdata.adapter.jdbc.ProductApprovalServiceSqlMapper;
import com.appnexus.contracts.workflow.*;
import com.appnexus.contracts.businessobject.*;
import com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import com.appnexus.masterdata.MasterData;
import java.util.*;
import static com.appnexus.contracts.businessobject.ObjectRecordMapping.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
/** 商品审核申请接入既有策略、启动和可靠终态交付；完整正文仅留业务库，处理中冻结商品。 */
@Service @ConditionalOnProperty(name="app.workflow.engine.enabled",havingValue="true")
public final class ProductApprovalService implements Provider {
 private final ProductApprovalServiceSqlMapper statements;

 private final TransactionTemplate tx;private final ProductV1Service products;private final WorkflowPlatform platform;private final ObjectProvider<BusinessObjectExecution> execution;private final ObjectProvider<WorkflowTaskAccess> tasks;
 /** @param jdbc UTC数据源 @param manager 同数据源事务 @param products 商品授权及快照 @param platform 公开身份/组织端口 @param execution 延迟对象公共执行端口 @param tasks 延迟任务授权端口 */
 public ProductApprovalService(PlatformTransactionManager manager,ProductV1Service products,WorkflowPlatform platform,ObjectProvider<BusinessObjectExecution> execution,ObjectProvider<WorkflowTaskAccess> tasks,ProductApprovalServiceSqlMapper statements){this.statements=statements;this.tx=new TransactionTemplate(manager);this.products=products;this.platform=platform;this.execution=execution;this.tasks=tasks;}
 private static final List<Field> FIELDS=List.of(new Field("productVersion","商品版本","Product version","NUMBER","",true));
 private static final Operation SUBMIT=new Operation("submit","提交商品审核","Submit product approval","masterdata.product.approval:submit","ProductApproval.submit",new PageOperation("master-data/product","/master-data/product","商品档案","Products","POST","/api/master-data/product/{id}/approval"),List.of(),new InteractionContract("ProductApproval.submit",1,"ProductApproval","submit","提交商品审核","Submit product approval",InteractionContract.Trigger.USER_ACTION,List.of(new InteractionContract.Capability(InteractionContract.Service.WORKFLOW,InteractionContract.Completion.CALLBACK,"productApproval","approval",FIELDS,Set.of("APPROVED","REJECTED","WITHDRAWN"),"product.approval",1))));
 @Override public Descriptor descriptor(){return new Descriptor("ProductApproval","商品审核申请","Product approval","master-data",2,Category.TRANSACTION,FIELDS,List.of(SUBMIT),new DataAccess("TENANT",List.of(new DataResource("masterdata.product.approval","","商品审核申请","Product approvals",List.of(new DataField("productVersion","商品版本","Product version","TEXT",Set.of("READ_ONLY","HIDDEN"))))),Map.of("submit",Set.of("ALL","ORGANIZATION","ORGANIZATION_SUBTREE"))));}

 private BusinessObjectRuntime runtime;
 /** Spring装配与测试共用固定对象运行器，不再手写审批取数及回执SQL。 */
 @org.springframework.beans.factory.annotation.Autowired
 public void runtimeFactory(ObjectRecordRuntimeFactory factory){runtime=factory.create(this,recordMapping(),(c,id,operation,service)->{});}
 @Override public BusinessObjectRuntime runtime(){return Objects.requireNonNull(runtime,"OBJECT_RUNTIME_NOT_ASSEMBLED");}
 /** 商品审核使用二进制UUID与现有冻结版本，不改变商品正文所有权。 */
 public static ObjectRecordMapping recordMapping(){
   var binary=Encoding.BINARY_UUID;return new ObjectRecordMapping("md_product_approval",new Column("tenant_id",binary),new Column("id",binary),new Column("organization_id",binary),new Column("applicant_id",binary),
      "product_version","state","instance_id","delivery_id",null,Map.of("productVersion",new Value(new Column("product_version",Encoding.TEXT),Transform.VALUE,0)),
      Map.of("submit",new Transition(Set.of("SUBMITTED"),null,false)),null,true);
 }
 /** 当前账号有效任职组织，用于选择审批路由，不开放自由组织ID。 */
 public Object organizations(WorkflowContext c){return platform.documentOrganizations(c);}
 /** 提交固定商品版本，完整性不足拒绝；流程配置缺失整单回滚，不伪造审批。 */
 public Object submit(VerifiedTenantContext c,WorkflowContext w,String product,long version,String organization){if(!platform.allowed(w,"masterdata.product.approval:submit")||!platform.allowed(w,"masterdata.product:read"))throw new MasterData.Rejection(403,"MASTER_DATA_DENIED");UUID.fromString(organization);if(platform.documentOrganizations(w).stream().noneMatch(o->o.id().equalsIgnoreCase(organization)))throw new MasterData.Rejection(409,"PRODUCT_APPROVAL_ORGANIZATION_INVALID");return tx.execute(s->{products.authorizeRecord(c,product,"read",true);var lock=statements.statement1(w.tenantId(),product);var prior=statements.statement2(w.tenantId(),w.actorId(),w.requestId());if(!prior.isEmpty()){var r=prior.getFirst();if(!product.equals(r.get("productId"))||((Number)r.get("product_version")).longValue()!=version+1||!organization.equals(r.get("organization")))throw new MasterData.Rejection(409,"PRODUCT_APPROVAL_KEY_REUSED");return Map.of("id",r.get("id"),"instanceId",r.get("instanceId"),"version",version+1);}
products.ensureNotInApproval(c,product);var before=products.extensionSnapshot(c,product);if(((Number)lock.get("version")).longValue()!=version||"ENABLED".equals(lock.get("status"))||lock.get("draft_archived_at")!=null||!"COMPLETE".equals(lock.get("structure_state")))throw new MasterData.Rejection(409,"PRODUCT_APPROVAL_NOT_READY");products.requireCompleteForApproval(c,product);String id=UUID.randomUUID().toString();long frozen=version+1;statements.statement3(w.actorId(),w.tenantId(),product);statements.statement4(id,w.tenantId(),product,w.requestId(),frozen,w.actorId(),organization,new tools.jackson.databind.ObjectMapper().writeValueAsString(products.extensionSnapshot(c,product)),w.actorId(),w.actorId());var executed=execution.getObject().execute(new Context(w.tenantId(),w.actorId(),w.requestId(),w.personId(),w.organizationId()),new BusinessObjectExecution.Request("ProductApproval",id,"submit","ProductApproval.submit",frozen));var resolved=executed.resolution();String instance=executed.output().get("instanceId").toString();statements.statement5(instance,resolved.id(),w.tenantId(),id);products.extensionEvent(c,product,"APPROVAL_SUBMIT",before);return Map.of("id",id,"instanceId",instance,"version",frozen);});}
 /** 原商品读取权内审核历史；不将申请UUID作为数据授权。 */
 public Object list(VerifiedTenantContext c,String product){products.read(c,product);return statements.statement6(c.tenantId().toString(),product);}
 /** 审批原文只允许原申请人或当前有效任务持有人；结束任务不保留快照读取授权。 */
 public Object detail(WorkflowContext c,String id,String task){var rows=statements.statement7(c.tenantId(),id);if(rows.size()!=1||!platform.active(c.tenantId(),c.actorId()))throw new MasterData.Rejection(403,"MASTER_DATA_DENIED");var row=rows.getFirst();boolean own=Objects.equals(row.get("applicant"),c.actorId())&&platform.allowed(c,"masterdata.product:read");if(!own&&!tasks.getObject().permits(c,task,"ProductApproval",id,((Number)row.get("productVersion")).longValue(),"productApproval","productVersion",false))throw new MasterData.Rejection(403,"MASTER_DATA_DENIED");return Map.of("state",row.get("state"),"productVersion",row.get("productVersion"),"snapshot",new tools.jackson.databind.ObjectMapper().readTree((String)row.get("snapshot")));}
}
