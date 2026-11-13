package com.appnexus.leave;

import static org.assertj.core.api.Assertions.*;
import com.appnexus.contracts.document.*;
import com.appnexus.contracts.document.Documents.*;
import com.appnexus.contracts.workflow.*;
import com.appnexus.document.DocumentService;
import com.appnexus.workflow.adapter.flowable.*;
import com.appnexus.workflow.core.WorkflowModel;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

/** 真实 MySQL/Flowable 验证平台授权、租户配置、精确版本提交及驳回重提；不使用本机业务数据。 */
@Tag("container") @Testcontainers
class DocumentIntegrationTest {
  @Container static final MySQLContainer MYSQL=new MySQLContainer("mysql:8.4");
  static final String TENANT="019a65d0-0060-7000-8000-000000000001", OTHER="019a65d0-0060-7000-8000-000000000002";
  static JdbcTemplate jdbc;static DocumentService documents;static LeavePocService leave;static WorkflowRuntime runtime;static org.flowable.engine.ProcessEngine engine;
  static Context dc(String actor){return new Context(TENANT,actor,UUID.randomUUID().toString());}
  static WorkflowContext wc(String actor){return new WorkflowContext(TENANT,actor,UUID.randomUUID().toString());}
  @BeforeAll static void setup() throws Exception {
    var source=new DriverManagerDataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());jdbc=new JdbcTemplate(source);
    try(var connection=source.getConnection()){for(String name:List.of("V47__workflow_flowable_8.sql","V48__workflow_application.sql","V69__workflow_data_contract_snapshots.sql","V57__leave_poc.sql","V59__document_configuration.sql","V60__leave_approval_cycles.sql","V63__document_submission_strategy.sql","V64__leave_direct_submission.sql","V65__document_print_binding.sql","V66__document_page_relationships.sql","V68__document_entry_controls.sql","V67__explicit_interaction_bindings.sql")) {
      if(name.startsWith("V67")) {
        // 只在隔离测试库构造旧协议夹具，验证一次迁移后不会残留运行时双协议。
        jdbc.update("INSERT INTO doc_configuration(tenant_id,type_code,draft,published) VALUES('migration','MigrationFixture',?,?)",
            "[{\"mode\":\"DIRECT\"}]","[{\"mode\":\"APPROVAL\"}]");
      }
      ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/"+name));
      if(name.startsWith("V67")) {
        assertThat(jdbc.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(draft,'$[0].service')) FROM doc_configuration WHERE tenant_id='migration'",String.class)).isEqualTo("BUSINESS");
        assertThat(jdbc.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(published,'$[0].service')) FROM doc_configuration WHERE tenant_id='migration'",String.class)).isEqualTo("WORKFLOW");
        assertThat(jdbc.queryForObject("SELECT JSON_CONTAINS_PATH(draft,'one','$[0].mode') FROM doc_configuration WHERE tenant_id='migration'",Integer.class)).isZero();
        jdbc.update("DELETE FROM doc_configuration WHERE tenant_id='migration'");
      }
    }}
    var tx=new DataSourceTransactionManager(source);
    WorkflowPlatform platform=new WorkflowPlatform(){
      public boolean active(String t,String a){return TENANT.equals(t)&&Set.of("applicant","leader","hr","manager").contains(a);}
      public boolean allowed(WorkflowContext c,String p){return active(c.tenantId(),c.actorId());}
      public List<Option> documentOrganizations(WorkflowContext c){return List.of(new Option(LeavePocTest.ORG,"组织"));}
      public List<String> resolve(String t,String o,String kind,String ref){return "LEADER".equals(kind)?List.of("leader"):List.of(ref);}
    };
    DocumentPlatform dp=new DocumentPlatform(){
      public boolean platformAdministrator(String a){return a.equals("platform");}
      public boolean active(Context c){return platform.active(c.tenantId(),c.actorId())||(TENANT.equals(c.tenantId())&&c.actorId().equals("partner-019a65d0-0060-7000-8000-000000000099"));}
      public boolean tenantAdministrator(Context c){return active(c)&&c.actorId().equals("manager");}
      public boolean tenantExists(String t){return Set.of(TENANT,OTHER).contains(t);}
    };
    leave=new LeavePocService(jdbc,tx,platform,()->runtime,()->runtime);
    Provider order=()->new Descriptor("Order","订单","Order","sales",1,List.of(new Field("amount","金额","Amount","NUMBER","元")),List.of(
      new Action("confirm","确认","Confirm","order:confirm","Order.confirm",null,List.of(new OperationAlias("Order.detail.confirm",new PageOperation("order/detail","/orders/detail","订单详情","Order details","POST","/api/orders/{id}/confirm"))),new com.appnexus.contracts.document.InteractionContract("Order.confirm",1,"Order","confirm","确认","Confirm",com.appnexus.contracts.document.InteractionContract.Trigger.USER_ACTION,java.util.List.of(new com.appnexus.contracts.document.InteractionContract.Capability(com.appnexus.contracts.document.InteractionContract.Service.BUSINESS,com.appnexus.contracts.document.InteractionContract.Completion.SYNCHRONOUS,"Order.confirm","",java.util.List.of(),java.util.Set.of())))),
      new Action("print","打印","Print","order:print","Order.print",null,List.of(),new com.appnexus.contracts.document.InteractionContract("Order.print",1,"Order","print","打印","Print",com.appnexus.contracts.document.InteractionContract.Trigger.USER_ACTION,java.util.List.of(new com.appnexus.contracts.document.InteractionContract.Capability(com.appnexus.contracts.document.InteractionContract.Service.PRINT,com.appnexus.contracts.document.InteractionContract.Completion.CONFIRMATION,"order.v1","",java.util.List.of(),java.util.Set.of())))),
      new Action("cancel","取消","Cancel","order:cancel","Order.cancel",null,java.util.List.of(),new com.appnexus.contracts.document.InteractionContract("Order.cancel",1,"Order","cancel","取消","Cancel",com.appnexus.contracts.document.InteractionContract.Trigger.USER_ACTION,java.util.List.of(new com.appnexus.contracts.document.InteractionContract.Capability(com.appnexus.contracts.document.InteractionContract.Service.BUSINESS,com.appnexus.contracts.document.InteractionContract.Completion.SYNCHRONOUS,"Order.cancel","",java.util.List.of(),java.util.Set.of())))),
      new Action("release","放行","Release","order:release","Order.release",null,java.util.List.of(),new com.appnexus.contracts.document.InteractionContract("Order.release",1,"Order","release","放行","Release",com.appnexus.contracts.document.InteractionContract.Trigger.USER_ACTION,java.util.List.of(new com.appnexus.contracts.document.InteractionContract.Capability(com.appnexus.contracts.document.InteractionContract.Service.WORKFLOW,com.appnexus.contracts.document.InteractionContract.Completion.CALLBACK,"orderRelease","releaseApproval",java.util.List.of(),Set.of("APPROVED","REJECTED")))))));
    documents=new DocumentService(jdbc,tx,dp,()->runtime,List.of(leave,order));leave.bindings(()->documents);
    documents.savePageRelationships(dc("platform"),"LeaveRequest",-1,List.of(new PageRelationship("submit","LeaveRequest.submit"),new PageRelationship("print","LeaveRequest.print"),new PageRelationship("print","LeaveRequest.detail.print")));
    var bridge=new WorkflowBridge();var config=new WorkflowEngineConfiguration();engine=config.workflowProcessEngine(source,tx,bridge);
    runtime=config.workflowRuntime(engine,source,tx,platform,List.of(leave),bridge);runtime.documentBindings(()->documents);
    var base=LeavePocTest.model();var model=new WorkflowModel(base.name(),base.objectType(),base.purpose(),base.mode(),base.entryCapability(),List.of(),base.nodes(),base.edges(),Map.of("APPROVED","leavePoc","REJECTED","leavePoc","WITHDRAWN","leavePoc"),List.of(),"leavePoc");
    runtime.save(wc("manager"),"leave",-1,model);runtime.publish(wc("manager"),"leave",0);
    documents.activate(dc("platform"),"LeaveRequest",0,true);documents.grant(dc("platform"),"LeaveRequest",TENANT,true);
  }
  @AfterAll static void close(){if(engine!=null)engine.close();}
  static Binding binding(String name,String operator,String value){return new Binding(UUID.randomUUID().toString(),name,"leave",1,operator==null?List.of():List.of(new Condition("days",operator,value)),"submit","WORKFLOW","LeaveRequest.submit",null);}
  static void configure(List<Binding> list){var config=documents.configuration(dc("manager"),"LeaveRequest");documents.save(dc("manager"),"LeaveRequest",((Number)config.get("revision")).longValue(),list);var saved=documents.configuration(dc("manager"),"LeaveRequest");documents.publish(dc("manager"),"LeaveRequest",((Number)saved.get("revision")).longValue());}
  /** 第二单据不实现任何中心特例：相同条件的不同动作独立匹配，独立用途的审批目录不能串用。 */
  @Test void arbitraryActionsAreIsolatedWithoutDocumentSpecificBranches() {
    assertThat((List<?>)documents.pageRelationships(dc("platform"),"Order").get("relationships")).hasSize(1);
    documents.savePageRelationships(dc("platform"),"Order",-1,List.of(new PageRelationship("confirm","Order.detail.confirm")));
    documents.activate(dc("platform"),"Order",0,true);documents.grant(dc("platform"),"Order",TENANT,true);
    List<Binding> bindings=List.of(
      new Binding(UUID.randomUUID().toString(),"确认",null,0,List.of(),"confirm","BUSINESS","Order.confirm",null),
      new Binding(UUID.randomUUID().toString(),"取消",null,0,List.of(),"cancel","BUSINESS","Order.cancel",null));
    documents.save(dc("manager"),"Order",-1,bindings);documents.publish(dc("manager"),"Order",0);
    assertThatThrownBy(()->documents.save(dc("manager"),"Order",1,List.of(new Binding(UUID.randomUUID().toString(),"错误打印",null,0,List.of(),"print","PRINT","Order.print","missing")))).hasMessage("DOCUMENT_PRINT_SCENE_INVALID");
    documents.printing(()->new com.appnexus.contracts.printing.BusinessPrinting(){
      public List<Scene> scenes(com.appnexus.contracts.printing.PrintContext context,String contract){assertThat(context.tenantId()).isEqualTo(TENANT);assertThat(contract).isEqualTo("order.v1");return List.of(new Scene("scene","order-scene","订单","order.v1"));}
      public Map<String,Object> request(com.appnexus.contracts.printing.PrintContext context,Request input){throw new UnsupportedOperationException();}
    });
    documents.save(dc("manager"),"Order",1,List.of(
      new Binding(UUID.randomUUID().toString(),"详情确认",null,0,List.of(),"confirm","BUSINESS","Order.detail.confirm",null),bindings.get(1),
      new Binding(UUID.randomUUID().toString(),"订单打印",null,0,List.of(),"print","PRINT","Order.print","order-scene")));
    documents.publish(dc("manager"),"Order",2);
    String object=UUID.randomUUID().toString();var c=dc("applicant");var facts=Map.of("amount","100");
    var confirm=documents.resolveAction(c,"Order","confirm",object,0,facts);
    var print=documents.resolveAction(dc("applicant"),"Order","print",object,0,facts);
    assertThat(print.sceneCode()).isEqualTo("order-scene");assertThat(print.dataContract()).isEqualTo("order.v1");assertThat(print.definitionId()).isNull();
    var servicePrint=documents.resolveAction(dc("partner-019a65d0-0060-7000-8000-000000000099"),"Order","print",object,0,facts);
    assertThat(servicePrint.sceneCode()).isEqualTo("order-scene");
    assertThat(confirm.actionCode()).isEqualTo("confirm");assertThat(confirm.service()).isEqualTo("BUSINESS");
    assertThat(documents.resolveAction(c,"Order","confirm",object,0,facts)).isEqualTo(confirm);
    assertThatThrownBy(()->documents.resolveAction(c,"Order","cancel",object,0,facts)).hasMessage("DOCUMENT_IDEMPOTENCY_CONFLICT");
    assertThat(documents.resolveAction(dc("applicant"),"Order","cancel",object,0,facts).actionCode()).isEqualTo("cancel");
    assertThatThrownBy(()->documents.resolveAction(dc("applicant"),"Order","unknown",object,0,facts)).hasMessage("DOCUMENT_ACTION_INVALID");
    assertThatThrownBy(()->documents.resolveAction(dc("applicant"),"Order","release",object,0,facts)).hasMessage("DOCUMENT_FLOW_NOT_MATCHED");
    // 空条件只在同一动作内冲突，不禁止其他独立动作拥有自己的默认策略。
    assertThatThrownBy(()->documents.save(dc("manager"),"Order",3,List.of(bindings.getFirst(),new Binding(UUID.randomUUID().toString(),"重复确认",null,0,List.of(new Condition("amount","GT","0")),"confirm","BUSINESS","Order.confirm",null)))).hasMessage("DOCUMENT_FLOW_MATCH_CONFLICT");
    var catalog=new com.appnexus.contracts.workflow.WorkflowDefinitionCatalog(){
      public List<Option> published(WorkflowContext context,String type,String purpose,String entry,Set<String> outcomes){
        assertThat(type).isEqualTo("Order");assertThat(purpose).isEqualTo("releaseApproval");assertThat(entry).isEqualTo("orderRelease");return List.of(new Option("release-flow",2,"订单放行"));
      }
    };
    // 重用同一登记目录验证行为契约传给专业服务，而不是使用单据级默认用途。
    var source=(javax.sql.DataSource)jdbc.getDataSource();
    var generic=new DocumentService(jdbc,new DataSourceTransactionManager(source),new DocumentPlatform(){
      public boolean platformAdministrator(String a){return true;}public boolean tenantAdministrator(Context context){return true;}public boolean active(Context context){return true;}public boolean tenantExists(String t){return true;}
    },()->catalog,List.of(()->(Descriptor)documents.catalog(dc("platform"),true).stream().map(r->r.get("descriptor")).filter(d->((Descriptor)d).typeCode().equals("Order")).findFirst().orElseThrow()));
    assertThat(generic.options(dc("manager"),"Order","release")).hasSize(1);
    generic.save(dc("manager"),"Order",3,List.of(new Binding(UUID.randomUUID().toString(),"放行审批","release-flow",2,List.of(),"release","WORKFLOW","Order.release",null)));
    generic.publish(dc("manager"),"Order",4);
    var release=generic.resolveAction(dc("applicant"),"Order","release",object,0,facts);
    assertThat(generic.verifyApproval(dc("applicant"),release.id(),"Order",object,0,facts,"releaseApproval","orderRelease")).isEqualTo(release);
    assertThatThrownBy(()->generic.verifyApproval(dc("applicant"),release.id(),"Order",object,0,facts,"approval","order")).hasMessage("DOCUMENT_APPROVAL_CONTRACT_REQUIRED");
    documents.grant(dc("platform"),"Order",TENANT,false);
  }
  @Test void entryControlsRequireAuthorityReasonAndVersion(){
    var before=documents.pageRelationships(dc("platform"),"LeaveRequest");long revision=((Number)before.get("revision")).longValue();
    assertThatThrownBy(()->documents.controlPage(dc("manager"),"LeaveRequest",revision,"LeaveRequest.print",false,"测试停用")).hasMessage("DOCUMENT_PLATFORM_DENIED");
    assertThatThrownBy(()->documents.controlPage(dc("platform"),"LeaveRequest",revision,"LeaveRequest.print",false,"")).hasMessage("DOCUMENT_CONTROL_REASON_REQUIRED");
    documents.controlPage(dc("platform"),"LeaveRequest",revision,"LeaveRequest.print",false,"测试停用");
    assertThatThrownBy(()->documents.controlPage(dc("platform"),"LeaveRequest",revision,"LeaveRequest.print",true,"陈旧版本")).hasMessage("DOCUMENT_VERSION_CONFLICT");
    documents.controlPage(dc("platform"),"LeaveRequest",revision+1,"LeaveRequest.print",true,"恢复入口");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM doc_entry_control_audit WHERE operation_key='LeaveRequest.print'",Integer.class)).isGreaterThanOrEqualTo(2);
    // 其他测试使用固定首次控制版本；该测试仅清理自己的控制和审计记录，不影响业务数据。
    jdbc.update("UPDATE doc_page_relationships SET revision=? WHERE type_code='LeaveRequest'",revision);
  }
  @Test void completeDocumentContract() {
    assertThat(leave.descriptor().actions()).contains(LeavePocService.SUBMIT_ACTION);
    assertThat(leave.actions(wc("applicant"))).first().satisfies(a->{assertThat(a.operationKey()).isEqualTo("LeaveRequest.submit");assertThat(a.allowed()).isTrue();});
    assertThatThrownBy(()->documents.resolveAction(dc("applicant"),"LeaveRequest","unknown",UUID.randomUUID().toString(),0,Map.of())).hasMessage("DOCUMENT_ACTION_INVALID");
    // 授权资格与启用开关分别表达：未启用仍可配置/查看，但不能发起运行。
    documents.activate(dc("platform"),"LeaveRequest",1,false);
    assertThat(documents.catalog(dc("manager"),false).stream().filter(item->((Descriptor)item.get("descriptor")).typeCode().equals("LeaveRequest")).toList()).hasSize(1).first().satisfies(item->assertThat(item.get("enabled")).isEqualTo(false));
    assertThat(documents.configuration(dc("manager"),"LeaveRequest").get("revision")).isEqualTo(-1);
    documents.grant(dc("platform"),"LeaveRequest",TENANT,false);
    assertThat(documents.catalog(dc("manager"),false)).isEmpty();
    documents.grant(dc("platform"),"LeaveRequest",TENANT,true);
    assertThatThrownBy(()->documents.resolveAction(dc("applicant"),"LeaveRequest","submit",UUID.randomUUID().toString(),0,Map.of("days","2","requiresHr","false"))).hasMessage("DOCUMENT_TYPE_DISABLED");
    documents.activate(dc("platform"),"LeaveRequest",2,true);
    assertThat(documents.catalog(dc("manager"),false).stream().filter(item->((Descriptor)item.get("descriptor")).typeCode().equals("LeaveRequest")).toList()).hasSize(1).first().satisfies(item->assertThat(item.get("enabled")).isEqualTo(true));
    assertThatThrownBy(()->documents.catalog(dc("applicant"),false)).isInstanceOf(Rejection.class);
    assertThatThrownBy(()->documents.configuration(new Context(OTHER,"manager",UUID.randomUUID().toString()),"LeaveRequest")).isInstanceOf(Rejection.class);
    var row=leave.create(wc("applicant"),LeavePocTest.input(3));String id=row.get("id").toString();
    assertThat(row.get("start_date")).isEqualTo("2026-11-01");
    assertThat(leave.list(wc("applicant")).stream().filter(r->id.equals(r.get("id"))).findFirst().orElseThrow().get("end_date")).isEqualTo("2026-11-03");
    assertThatThrownBy(()->leave.submit(wc("applicant"),id)).hasMessage("DOCUMENT_FLOW_NOT_BOUND");
    assertThat(leave.read(wc("applicant"),id,null).get("state")).isEqualTo("DRAFT");
    configure(List.of(binding("所有请假",null,null)));
    assertThat(documents.readiness(dc("manager"),"LeaveRequest")).anySatisfy(check->{assertThat(check.code()).isEqualTo("WORKFLOW_VERSION");assertThat(check.ready()).isTrue();});
    // 显式页面操作在保存和发布后保持一致，不依赖运行时补齐。
    var connected=documents.configuration(dc("manager"),"LeaveRequest");
    assertThat((List<Binding>)connected.get("bindings")).first().satisfies(b->assertThat(b.operationKey()).isEqualTo("LeaveRequest.submit"));
    assertThat(jdbc.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(published,'$[0].operationKey')) FROM doc_configuration WHERE tenant_id=? AND type_code='LeaveRequest'",String.class,TENANT)).isEqualTo("LeaveRequest.submit");
    assertThat(LeavePocService.SUBMIT_ACTION.pageOperation().pageKey()).isEqualTo("workflow/leave");
    assertThatThrownBy(()->configure(List.of(new Binding(UUID.randomUUID().toString(),"错绑","leave",1,List.of(),"submit","WORKFLOW","Order.submit",null)))).hasMessage("DOCUMENT_PAGE_OPERATION_INVALID");
    assertThatThrownBy(()->configure(List.of(new Binding(UUID.randomUUID().toString(),"未选择","leave",1,List.of(),"submit","WORKFLOW","",null)))).hasMessage("DOCUMENT_PAGE_OPERATION_INVALID");
    // 发布第二版后绑定仍精确使用第一版，而不是自动升级。
    long revision=jdbc.queryForObject("SELECT revision FROM wf_definition WHERE tenant_id=? AND id='leave'",Long.class,TENANT);
    runtime.publish(wc("manager"),"leave",revision);
    var submitted=leave.submit(wc("applicant"),id);String instance=submitted.get("instance_id").toString();
    assertThat(leave.availability(wc("applicant"),id)).filteredOn(a->a.actionCode().equals("submit")).first().satisfies(a->assertThat(a.reasonCode()).isEqualTo("BUSINESS_STATE_DENIED"));
    assertThat(leave.timeline(wc("applicant"),id)).anySatisfy(event->assertThat(event.get("kind")).isEqualTo("APPROVAL_STARTED"));
    assertThatThrownBy(()->leave.timeline(wc("leader"),id)).hasMessage("LEAVE_ACCESS_DENIED");
    assertThat(jdbc.queryForObject("SELECT definition_version FROM wf_instance WHERE tenant_id=? AND id=?",Integer.class,TENANT,instance)).isEqualTo(1);
    assertThat(leave.submit(wc("applicant"),id).get("instance_id")).isEqualTo(instance);
    assertThatThrownBy(()->leave.update(wc("applicant"),id,0,LeavePocTest.input(2))).hasMessage("LEAVE_BASIS_CHANGED");
    String task=runtime.tasks(wc("leader"),"todo").stream().filter(t->id.equals(t.get("object_id"))).findFirst().orElseThrow().get("id").toString();
    runtime.complete(wc("leader"),task,"REJECT",Map.of(),"不通过");
    runtime.dispatch();assertThat(leave.read(wc("applicant"),id,null).get("state")).isEqualTo("REJECTED");
    leave.update(wc("applicant"),id,0,LeavePocTest.input(2));var again=leave.submit(wc("applicant"),id);
    assertThat(again.get("instance_id")).isNotEqualTo(instance);assertThat(leave.cycles(wc("applicant"),id)).hasSize(2);
    // 重叠条件允许静态无法证明的组合，但运行时必须拒绝且回滚业务状态。
    configure(List.of(binding("一", "GT","0"),binding("二","LT","10")));
    String next=leave.create(wc("applicant"),LeavePocTest.input(2)).get("id").toString();
    assertThatThrownBy(()->leave.submit(wc("applicant"),next)).hasMessage("DOCUMENT_FLOW_MATCH_CONFLICT");
    assertThat(leave.read(wc("applicant"),next,null).get("state")).isEqualTo("DRAFT");
    // 普通业务用户可解析，但不能改配置；同键不同依据不得改选。
    configure(List.of(binding("所有",null,null)));var c=dc("applicant");
    var result=documents.resolveAction(c,"LeaveRequest","submit",next,0,Map.of("days","2","requiresHr","false"));
    assertThat(documents.resolveAction(c,"LeaveRequest","submit",next,0,Map.of("days","2","requiresHr","false"))).isEqualTo(result);
    assertThatThrownBy(()->documents.resolveAction(c,"LeaveRequest","submit",next,0,Map.of("days","3","requiresHr","false"))).hasMessage("DOCUMENT_IDEMPOTENCY_CONFLICT");
    assertThatThrownBy(()->documents.verify(dc("leader"),result.id(),"LeaveRequest",next,0,result.facts())).hasMessage("DOCUMENT_RESOLUTION_DENIED");
    // 直接提交必须显式发布，业务状态与审批结果分离，重复提交不生成流程或重复记录。
    configure(List.of(new Binding(UUID.randomUUID().toString(),"直接受理",null,0,List.of(),"submit","BUSINESS","LeaveRequest."+"submit",null)));
    String direct=leave.create(wc("applicant"),LeavePocTest.input(2)).get("id").toString();
    var accepted=leave.submit(wc("applicant"),direct);
    assertThat(accepted.get("state")).isEqualTo("ACCEPTED");assertThat(accepted.get("instance_id")).isNull();
    assertThat(leave.submit(wc("applicant"),direct).get("state")).isEqualTo("ACCEPTED");
    assertThat(leave.cycles(wc("applicant"),direct)).isEmpty();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_instance WHERE tenant_id=? AND object_id=?",Integer.class,TENANT,direct)).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM leave_poc_submission WHERE tenant_id=? AND object_id=?",Integer.class,TENANT,direct)).isEqualTo(1);
    String resolution=jdbc.queryForObject("SELECT resolution_id FROM leave_poc_submission WHERE tenant_id=? AND object_id=?",String.class,TENANT,direct);
    assertThatThrownBy(()->documents.verify(dc("applicant"),resolution,"LeaveRequest",direct,0,Map.of("days","2","requiresHr","false"))).hasMessage("DOCUMENT_APPROVAL_REQUIRED");
    assertThatThrownBy(()->configure(List.of(new Binding(UUID.randomUUID().toString(),"非法直接", "leave",1,List.of(),"submit","BUSINESS","LeaveRequest."+"submit",null)))).hasMessage("DOCUMENT_DIRECT_FLOW_INVALID");
    // 相同提交按钮可按可信天数选择直接受理或审批，未命中不能降级。
    configure(List.of(new Binding(UUID.randomUUID().toString(),"短假直接受理",null,0,List.of(new Condition("days","LE","1")),"submit","BUSINESS","LeaveRequest."+"submit",null),
      new Binding(UUID.randomUUID().toString(),"其余审批","leave",1,List.of(new Condition("days","GT","1")),"submit","WORKFLOW","LeaveRequest."+"submit",null)));
    String shortLeave=leave.create(wc("applicant"),LeavePocTest.input(1)).get("id").toString();
    assertThat(leave.submit(wc("applicant"),shortLeave).get("state")).isEqualTo("ACCEPTED");
    String longLeave=leave.create(wc("applicant"),LeavePocTest.input(4)).get("id").toString();
    assertThat(leave.submit(wc("applicant"),longLeave).get("instance_id")).isNotNull();
    documents.printing(()->new com.appnexus.contracts.printing.BusinessPrinting(){
      public List<Scene> scenes(com.appnexus.contracts.printing.PrintContext context,String contract){return List.of(new Scene("leave-scene","leave-scene","请假单",contract));}
      public Map<String,Object> request(com.appnexus.contracts.printing.PrintContext context,Request input){throw new UnsupportedOperationException();}
    });
    configure(List.of(new Binding(UUID.randomUUID().toString(),"请假打印",null,0,List.of(),"print","PRINT","LeaveRequest.detail.print","leave-scene")));
    leave.printing(()->new com.appnexus.contracts.printing.BusinessPrinting(){
      public List<Scene> scenes(com.appnexus.contracts.printing.PrintContext context,String contract){return List.of();}
      public Map<String,Object> request(com.appnexus.contracts.printing.PrintContext context,Request input){assertThat(input.sceneCode()).isEqualTo("leave-scene");assertThat(input.dataContract()).isEqualTo("leave.request.v1");assertThat(input.data()).doesNotContainKeys("reason","applicant");return Map.of("id","output");}
    });
    assertThat(leave.print(wc("applicant"),shortLeave,"LeaveRequest.print").get("id")).isEqualTo("output");
    assertThat(leave.print(wc("applicant"),shortLeave,"LeaveRequest.detail.print").get("id")).isEqualTo("output");
    assertThatThrownBy(()->leave.print(wc("leader"),shortLeave,"LeaveRequest.print")).hasMessage("LEAVE_ACCESS_DENIED");
    assertThatThrownBy(()->leave.print(wc("applicant"),shortLeave,"Other.print")).hasMessage("LEAVE_ACCESS_DENIED");
    // 平台关系本身可维护：只保留详情打印，列表入口立即拒绝，已有租户草稿仍能读取修正。
    var platformContext=dc("platform");
    assertThat(documents.pageRelationships(platformContext,"LeaveRequest").get("revision")).isEqualTo(0L);
    var detailOnly=List.of(new PageRelationship("print","LeaveRequest.detail.print"));
    assertThatThrownBy(()->documents.savePageRelationships(dc("manager"),"LeaveRequest",-1,detailOnly)).hasMessage("DOCUMENT_PLATFORM_DENIED");
    assertThatThrownBy(()->documents.savePageRelationships(platformContext,"LeaveRequest",-1,List.of(new PageRelationship("submit","LeaveRequest.print")))).hasMessage("DOCUMENT_PAGE_RELATION_INVALID");
    documents.savePageRelationships(platformContext,"LeaveRequest",0,detailOnly);
    assertThat(documents.pageRelationships(platformContext,"LeaveRequest").get("relationships")).isEqualTo(detailOnly);
    assertThatThrownBy(()->documents.savePageRelationships(platformContext,"LeaveRequest",-1,detailOnly)).hasMessage("DOCUMENT_VERSION_CONFLICT");
    assertThat(leave.actions(wc("applicant"))).filteredOn(a->a.operationKey().equals("LeaveRequest.print")).first().satisfies(a->assertThat(a.allowed()).isFalse());
    assertThat(leave.actions(wc("applicant"))).filteredOn(a->a.operationKey().equals("LeaveRequest.detail.print")).first().satisfies(a->assertThat(a.allowed()).isTrue());
    assertThatThrownBy(()->leave.print(wc("applicant"),shortLeave,"LeaveRequest.print")).hasMessage("DOCUMENT_PAGE_RELATION_DISABLED");
    assertThat(leave.print(wc("applicant"),shortLeave,"LeaveRequest.detail.print").get("id")).isEqualTo("output");
    var tenantDescriptor=(Descriptor)documents.catalog(dc("manager"),false).stream().filter(item->((Descriptor)item.get("descriptor")).typeCode().equals("LeaveRequest")).findFirst().orElseThrow().get("descriptor");
    assertThat(tenantDescriptor.actions().stream().filter(a->a.actionCode().equals("print")).findFirst().orElseThrow().pageOperation()).isNull();
    assertThat(tenantDescriptor.actions().stream().filter(a->a.actionCode().equals("print")).findFirst().orElseThrow().aliases()).hasSize(1);
    documents.savePageRelationships(platformContext,"LeaveRequest",1,List.of());
    assertThatThrownBy(()->leave.print(wc("applicant"),shortLeave,"LeaveRequest.detail.print")).hasMessage("DOCUMENT_PAGE_RELATION_DISABLED");
    assertThat(documents.configuration(dc("manager"),"LeaveRequest").get("bindings")).isNotNull();
    assertThatThrownBy(()->configure(List.of(new Binding(UUID.randomUUID().toString(),"已移除",null,0,List.of(),"print","PRINT","LeaveRequest.detail.print","leave-scene")))).hasMessage("DOCUMENT_PAGE_RELATION_DISABLED");
    documents.savePageRelationships(platformContext,"LeaveRequest",2,List.of(new PageRelationship("submit","LeaveRequest.submit"),new PageRelationship("print","LeaveRequest.print"),new PageRelationship("print","LeaveRequest.detail.print")));
  }
}
