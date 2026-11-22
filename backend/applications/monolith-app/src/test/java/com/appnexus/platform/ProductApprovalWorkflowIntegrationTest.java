package com.appnexus.platform;
import com.appnexus.workflow.application.WorkflowRuntime;
import com.appnexus.masterdata.api.product.ProductCatalogData;
import com.appnexus.masterdata.application.product.ProductService;
import com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.appnexus.workflow.core.WorkflowModel.*;
import com.appnexus.masterdata.api.masterdata.MasterData;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.appnexus.masterdata.application.product.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import com.appnexus.contracts.workflow.*;
import com.appnexus.contracts.businessobject.*;
import com.appnexus.workflow.core.WorkflowModel;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
/** 真实MySQL、Flowable、业务策略和可靠终态交付；组织选人端口采用隔离测试身份，不改共享账号。 */
@SpringBootTest(properties={"app.workflow.engine.enabled=true","app.workflow.worker.enabled=false"}) @ActiveProfiles("local")
class ProductApprovalWorkflowIntegrationTest extends WorkflowDatabaseTestSupport {
 @Autowired JdbcTemplate jdbc;
 @Autowired ProductService products;
 @Autowired ProductCatalogService catalog;
 @Autowired ProductApprovalService approvals;
 @Autowired com.appnexus.platform.businessobject.BusinessObjectManagement bindings;
 @Autowired com.appnexus.workflow.application.WorkflowRuntime runtime;
 @Autowired com.appnexus.platform.persistence.organization.OrganizationAdministratorBootstrap bootstrap;
 @MockitoBean WorkflowPlatform platform;
 @Autowired com.appnexus.platform.authorization.ScopedWriteGate workflowWrites;
 @MockitoBean BusinessObjectPlatform businessPlatform;
 /** 审批批准不自动启用；当前有效任务可读依据，任务结束失效；交付后显式启用。 */
 @Test void realFlowFreezesProductAndReliablyDeliversFinalApproval(){
  UUID tenant=UUID.randomUUID(),account=UUID.randomUUID(),member=UUID.randomUUID();String reviewer=UUID.randomUUID().toString();
  jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'商品真实审批隔离测试')",tenant.toString());jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'审核申请人','unused')",account.toString(),"approval-"+account);jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",member.toString(),tenant.toString(),account.toString());bootstrap.initialize(tenant,member);String org=ProductOrganizationFixture.create(jdbc,tenant);
  doAnswer(call->{
   WorkflowContext context=call.getArgument(0);
   String permission=call.getArgument(1);
   String[] keys=(String[])call.getRawArguments()[2];
   workflowWrites.authorizeServiceOperation(UUID.fromString(context.tenantId()),permission,List.of(keys),
       ()->platform.allowed(context,permission));
   return null;
  }).when(platform).authorizeRecordWrite(any(WorkflowContext.class),eq("workflow:start"),any(String[].class));
  doAnswer(call->{
   WorkflowContext context=call.getArgument(0);
   String task=call.getArgument(1);
   workflowWrites.authorizeServiceOperation(UUID.fromString(context.tenantId()),"workflow.task:complete",
       List.of(task,context.actorId()),()->platform.active(context.tenantId(),context.actorId()));
   return null;
  }).when(platform).authorizeTaskWrite(any(WorkflowContext.class),anyString());
  when(platform.projectData(any(),anyString(),anyMap())).thenAnswer(call->call.getArgument(2));when(platform.active(anyString(),anyString())).thenReturn(true);when(platform.allowed(any(),anyString())).thenReturn(true);when(platform.documentOrganizations(any())).thenReturn(List.of(new WorkflowPlatform.Option(org,"验收组织")));when(platform.resolve(anyString(),anyString(),anyString(),anyString())).thenReturn(List.of(reviewer));when(platform.candidate(any(),anyString(),anyString(),anyString())).thenAnswer(i->reviewer.equals(((WorkflowContext)i.getArgument(0)).actorId()));
  when(businessPlatform.operationAllowed(any(),anyString(),anyString())).thenReturn(true);when(businessPlatform.authorizeRecord(any(),any(),anyString(),any())).thenAnswer(call->call.getArgument(3));when(businessPlatform.platformAdministrator(anyString())).thenReturn(true);when(businessPlatform.tenantAdministrator(any())).thenReturn(true);when(businessPlatform.active(any())).thenReturn(true);when(businessPlatform.tenantExists(anyString())).thenReturn(true);
  var c=new VerifiedTenantContext(account,tenant,member,1);
  var requestContext=new org.springframework.mock.web.MockHttpServletRequest();
  requestContext.setAttribute(VerifiedTenantContext.class.getName(),c);
  RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(requestContext));
  var w=new WorkflowContext(tenant.toString(),account.toString(),UUID.randomUUID().toString(),"test-person",org);var dc=new Context(tenant.toString(),account.toString(),UUID.randomUUID().toString());
  var category=catalog.createCategory(c,new ProductCatalogData.CategoryInput("APPROVAL","审批测试",null,0));var slots=new LinkedHashMap<String,String>();for(String key:List.of("BASIC","SPEC","QUALITY","EXTENSION"))slots.put(key,null);var config=catalog.publish(c,category.id(),new ProductCatalogData.Publish(slots,false,0));String product=(String)products.create(c,new com.appnexus.masterdata.api.product.ProductMainData.Input("APPROVE","完整服务商品",null,category.id(),config.id(),"SERVICE",null,null,null,null,null,"",org)).get("id");
  bindings.savePageRelationships(dc,"ProductApproval",-1,List.of(new PageRelationship("submit","ProductApproval.submit")));bindings.activate(dc,"ProductApproval",0,true);bindings.grant(dc,"ProductApproval",tenant.toString(),true);
  var nodes=List.of(node("start",Kind.START,null),node("review",Kind.REVIEW,reviewer),node("end",Kind.END,null));var model=new WorkflowModel("商品审核验收","ProductApproval","approval",Mode.APPROVAL,"productApproval",List.of(),nodes,List.of(new Edge("start","review",null,null,false),new Edge("review","end",null,null,false)),Map.of("APPROVED","productApproval","REJECTED","productApproval","WITHDRAWN","productApproval"),List.of(),"productApproval");String definition=UUID.randomUUID().toString();runtime.save(w,definition,-1,model);assertThat(runtime.validate(w,model)).isEmpty();runtime.publish(new WorkflowContext(w.tenantId(),w.actorId(),UUID.randomUUID().toString(),w.personId(),w.organizationId()),definition,0);
  bindings.save(dc,"ProductApproval",-1,List.of(new Binding(UUID.randomUUID().toString(),"默认商品审核",definition,1,List.of(),"submit","WORKFLOW","ProductApproval.submit",null)));bindings.publish(dc,"ProductApproval",0);
  @SuppressWarnings("unchecked") var submitted=(Map<String,Object>)approvals.submit(c,new WorkflowContext(w.tenantId(),w.actorId(),UUID.randomUUID().toString(),w.personId(),w.organizationId()),product,0,org);String instance=(String)submitted.get("instanceId"),request=(String)submitted.get("id");
  assertThatThrownBy(()->products.update(c,product,new com.appnexus.masterdata.api.product.ProductMainData.Update("冻结修改",null,null,null,null,List.of(),1))).hasMessage("PRODUCT_APPROVAL_IN_PROGRESS");String task=jdbc.queryForObject("SELECT id FROM wf_task WHERE instance_id=?",String.class,instance);var reviewerContext=new WorkflowContext(tenant.toString(),reviewer,UUID.randomUUID().toString(),"reviewer-person",org);
  var reviewerSubject=new VerifiedTenantContext(UUID.fromString(reviewer),tenant,UUID.randomUUID(),1);
  assertThat(approvals.detail(reviewerSubject,reviewerContext,request,task).toString()).contains("完整服务商品");runtime.complete(reviewerContext,task,"APPROVE",Map.of(),"已核对商品档案");assertThat(jdbc.queryForObject("SELECT state FROM md_product_approval WHERE id=UUID_TO_BIN(?)",String.class,request)).isEqualTo("SUBMITTED");assertThat(runtime.dispatch()).isTrue();assertThat(jdbc.queryForObject("SELECT state FROM md_product_approval WHERE id=UUID_TO_BIN(?)",String.class,request)).isEqualTo("APPROVED");assertThat(products.read(c,product).get("status")).isEqualTo("DRAFT");assertThatThrownBy(()->approvals.detail(reviewerSubject,reviewerContext,request,task)).isInstanceOf(com.appnexus.masterdata.api.masterdata.MasterData.Rejection.class);products.updateStatus(c,product,new com.appnexus.masterdata.api.product.ProductCommandData.Status("ENABLED",1));
 }
 @org.junit.jupiter.api.AfterEach void clearRequestContext(){
  RequestContextHolder.resetRequestAttributes();
 }
 private static Node node(String id,Kind kind,String reviewer){return new Node(id,id,kind,kind==Kind.REVIEW?List.of(new Person("MEMBER",reviewer)):List.of(),Completion.ALL,false,null,Set.of(),Map.of(),2,3600,null,true);}
}
