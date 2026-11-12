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
    try(var connection=source.getConnection()){for(String name:List.of("V47__workflow_flowable_8.sql","V48__workflow_application.sql","V57__leave_poc.sql","V59__document_configuration.sql","V60__leave_approval_cycles.sql","V63__document_submission_strategy.sql","V64__leave_direct_submission.sql")) ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/"+name));}
    var tx=new DataSourceTransactionManager(source);
    WorkflowPlatform platform=new WorkflowPlatform(){
      public boolean active(String t,String a){return TENANT.equals(t)&&Set.of("applicant","leader","hr","manager").contains(a);}
      public boolean allowed(WorkflowContext c,String p){return active(c.tenantId(),c.actorId());}
      public List<Option> documentOrganizations(WorkflowContext c){return List.of(new Option(LeavePocTest.ORG,"组织"));}
      public List<String> resolve(String t,String o,String kind,String ref){return "LEADER".equals(kind)?List.of("leader"):List.of(ref);}
    };
    DocumentPlatform dp=new DocumentPlatform(){
      public boolean platformAdministrator(String a){return a.equals("platform");}
      public boolean active(Context c){return platform.active(c.tenantId(),c.actorId());}
      public boolean tenantAdministrator(Context c){return active(c)&&c.actorId().equals("manager");}
      public boolean tenantExists(String t){return Set.of(TENANT,OTHER).contains(t);}
    };
    leave=new LeavePocService(jdbc,tx,platform,()->runtime,()->runtime);
    documents=new DocumentService(jdbc,tx,dp,()->runtime,List.of(leave));leave.bindings(()->documents);
    var bridge=new WorkflowBridge();var config=new WorkflowEngineConfiguration();engine=config.workflowProcessEngine(source,tx,bridge);
    runtime=config.workflowRuntime(engine,source,tx,platform,List.of(leave),bridge);runtime.documentBindings(()->documents);
    var base=LeavePocTest.model();var model=new WorkflowModel(base.name(),base.objectType(),base.purpose(),base.mode(),base.entryCapability(),List.of(),base.nodes(),base.edges(),Map.of("APPROVED","leavePoc","REJECTED","leavePoc","WITHDRAWN","leavePoc"),List.of(),"leavePoc");
    runtime.save(wc("manager"),"leave",-1,model);runtime.publish(wc("manager"),"leave",0);
    documents.activate(dc("platform"),"LeaveRequest",0,true);documents.grant(dc("platform"),"LeaveRequest",TENANT,true);
  }
  @AfterAll static void close(){if(engine!=null)engine.close();}
  static Binding binding(String name,String operator,String value){return new Binding(UUID.randomUUID().toString(),name,"leave",1,operator==null?List.of():List.of(new Condition("days",operator,value)));}
  static void configure(List<Binding> list){var config=documents.configuration(dc("manager"),"LeaveRequest");documents.save(dc("manager"),"LeaveRequest",((Number)config.get("revision")).longValue(),list);var saved=documents.configuration(dc("manager"),"LeaveRequest");documents.publish(dc("manager"),"LeaveRequest",((Number)saved.get("revision")).longValue());}
  @Test void completeDocumentContract() {
    assertThat(leave.descriptor().actions()).contains(LeavePocService.SUBMIT_ACTION);
    assertThat(leave.actions(wc("applicant"))).first().satisfies(a->{assertThat(a.operationKey()).isEqualTo("LeaveRequest.submit");assertThat(a.allowed()).isTrue();});
    assertThatThrownBy(()->documents.resolveAction(dc("applicant"),"LeaveRequest","unknown",UUID.randomUUID().toString(),0,Map.of())).hasMessage("DOCUMENT_ACTION_INVALID");
    // 授权资格与启用开关分别表达：未启用仍可配置/查看，但不能发起运行。
    documents.activate(dc("platform"),"LeaveRequest",1,false);
    assertThat(documents.catalog(dc("manager"),false)).hasSize(1).first().satisfies(item->assertThat(item.get("enabled")).isEqualTo(false));
    assertThat(documents.configuration(dc("manager"),"LeaveRequest").get("revision")).isEqualTo(-1);
    documents.grant(dc("platform"),"LeaveRequest",TENANT,false);
    assertThat(documents.catalog(dc("manager"),false)).isEmpty();
    documents.grant(dc("platform"),"LeaveRequest",TENANT,true);
    assertThatThrownBy(()->documents.resolve(dc("applicant"),"LeaveRequest",UUID.randomUUID().toString(),0,Map.of("days","2","requiresHr","false"))).hasMessage("DOCUMENT_TYPE_DISABLED");
    documents.activate(dc("platform"),"LeaveRequest",2,true);
    assertThat(documents.catalog(dc("manager"),false)).hasSize(1).first().satisfies(item->assertThat(item.get("enabled")).isEqualTo(true));
    assertThatThrownBy(()->documents.catalog(dc("applicant"),false)).isInstanceOf(Rejection.class);
    assertThatThrownBy(()->documents.configuration(new Context(OTHER,"manager",UUID.randomUUID().toString()),"LeaveRequest")).isInstanceOf(Rejection.class);
    var row=leave.create(wc("applicant"),LeavePocTest.input(3));String id=row.get("id").toString();
    assertThatThrownBy(()->leave.submit(wc("applicant"),id)).hasMessage("DOCUMENT_FLOW_NOT_BOUND");
    assertThat(leave.read(wc("applicant"),id,null).get("state")).isEqualTo("DRAFT");
    configure(List.of(binding("所有请假",null,null)));
    // 发布第二版后绑定仍精确使用第一版，而不是自动升级。
    long revision=jdbc.queryForObject("SELECT revision FROM wf_definition WHERE tenant_id=? AND id='leave'",Long.class,TENANT);
    runtime.publish(wc("manager"),"leave",revision);
    var submitted=leave.submit(wc("applicant"),id);String instance=submitted.get("instance_id").toString();
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
    var result=documents.resolve(c,"LeaveRequest",next,0,Map.of("days","2","requiresHr","false"));
    assertThat(documents.resolve(c,"LeaveRequest",next,0,Map.of("days","2","requiresHr","false"))).isEqualTo(result);
    assertThatThrownBy(()->documents.resolve(c,"LeaveRequest",next,0,Map.of("days","3","requiresHr","false"))).hasMessage("DOCUMENT_IDEMPOTENCY_CONFLICT");
    assertThatThrownBy(()->documents.verify(dc("leader"),result.id(),"LeaveRequest",next,0,result.facts())).hasMessage("DOCUMENT_RESOLUTION_DENIED");
    // 直接提交必须显式发布，业务状态与审批结果分离，重复提交不生成流程或重复记录。
    configure(List.of(new Binding(UUID.randomUUID().toString(),"直接受理",null,0,List.of(),"submit","DIRECT")));
    String direct=leave.create(wc("applicant"),LeavePocTest.input(2)).get("id").toString();
    var accepted=leave.submit(wc("applicant"),direct);
    assertThat(accepted.get("state")).isEqualTo("ACCEPTED");assertThat(accepted.get("instance_id")).isNull();
    assertThat(leave.submit(wc("applicant"),direct).get("state")).isEqualTo("ACCEPTED");
    assertThat(leave.cycles(wc("applicant"),direct)).isEmpty();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_instance WHERE tenant_id=? AND object_id=?",Integer.class,TENANT,direct)).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM leave_poc_submission WHERE tenant_id=? AND object_id=?",Integer.class,TENANT,direct)).isEqualTo(1);
    String resolution=jdbc.queryForObject("SELECT resolution_id FROM leave_poc_submission WHERE tenant_id=? AND object_id=?",String.class,TENANT,direct);
    assertThatThrownBy(()->documents.verify(dc("applicant"),resolution,"LeaveRequest",direct,0,Map.of("days","2","requiresHr","false"))).hasMessage("DOCUMENT_APPROVAL_REQUIRED");
    assertThatThrownBy(()->configure(List.of(new Binding(UUID.randomUUID().toString(),"非法直接", "leave",1,List.of(),"submit","DIRECT")))).hasMessage("DOCUMENT_DIRECT_FLOW_INVALID");
    // 相同提交按钮可按可信天数选择直接受理或审批，未命中不能降级。
    configure(List.of(new Binding(UUID.randomUUID().toString(),"短假直接受理",null,0,List.of(new Condition("days","LE","1")),"submit","DIRECT"),
      new Binding(UUID.randomUUID().toString(),"其余审批","leave",1,List.of(new Condition("days","GT","1")),"submit","APPROVAL")));
    String shortLeave=leave.create(wc("applicant"),LeavePocTest.input(1)).get("id").toString();
    assertThat(leave.submit(wc("applicant"),shortLeave).get("state")).isEqualTo("ACCEPTED");
    String longLeave=leave.create(wc("applicant"),LeavePocTest.input(4)).get("id").toString();
    assertThat(leave.submit(wc("applicant"),longLeave).get("instance_id")).isNotNull();
  }
}
