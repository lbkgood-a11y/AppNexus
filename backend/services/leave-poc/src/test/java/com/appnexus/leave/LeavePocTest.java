package com.appnexus.leave;



import static org.assertj.core.api.Assertions.*;
import static com.appnexus.workflow.core.WorkflowModel.*;
import com.appnexus.contracts.workflow.*;
import com.appnexus.workflow.adapter.flowable.*;
import com.appnexus.workflow.core.WorkflowModel;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

/** 隔离 MySQL 与真实 Flowable 验证请假依据、审批路由和业务结果交付；不连接本机业务数据库。 */
@Tag("container")
@Testcontainers
class LeavePocTest {
  @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
  static final String ORG = "019a65d0-0057-7000-8000-000000000001";
  static JdbcTemplate jdbc;
  static boolean leaderMissing;
  static boolean organizationUnavailable;
  static LeavePocService service;
  static WorkflowRuntime runtime;
  static WorkflowBusiness approval;
  static org.flowable.engine.ProcessEngine engine;
  static WorkflowContext c(String actor) { return new WorkflowContext("019a65d0-0061-7000-8000-000000000001", actor, UUID.randomUUID().toString(),actor+"-person",ORG); }

  @BeforeAll
  static void setup() throws Exception {
    var source = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    jdbc = new JdbcTemplate(source);
    try (var connection = source.getConnection()) {
      for (String file : List.of("V47__workflow_flowable_8.sql", "V48__workflow_application.sql","V69__workflow_data_contract_snapshots.sql", "V57__leave_poc.sql", "V59__document_configuration.sql", "V60__leave_approval_cycles.sql", "V63__document_submission_strategy.sql", "V64__leave_direct_submission.sql", "V65__document_print_binding.sql", "V66__document_page_relationships.sql","V68__document_entry_controls.sql", "V67__explicit_interaction_bindings.sql","V78__leave_personnel_identity.sql","V79__workflow_personnel_audit.sql"))
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/" + file));
    }
    // 切片集成库只包含业务相关表；执行 V73 中这些表对应的全部改名和 JSON 转换。
    var migration=new ClassPathResource("db/migration/V73__business_object_operation_naming.sql");
    String sql=new String(migration.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
    for(String statement:sql.replaceAll("(?m)^--.*$","").split(";")){
      var table=java.util.regex.Pattern.compile("(?:TABLE|UPDATE)\\s+`?([a-z_]+)`?",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(statement);
      if(table.find()&&jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?",Integer.class,table.group(1))>0)jdbc.execute(statement);
    }
    try(var connection=source.getConnection()){ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V129__object_approval_receipts.sql"));}
    var manager = new DataSourceTransactionManager(source);
    WorkflowPlatform platform = new WorkflowPlatform() {
      public Map<String,Object> projectData(WorkflowContext context,String resource,Map<String,Object> values){return Map.copyOf(values);}
      /** 测试平台显式核验节点选人；正式平台的人员任职判定由集成测试覆盖。 */
      public boolean candidate(WorkflowContext c,String org,String kind,String reference){return resolve(c.tenantId(),org,kind,reference).contains(c.actorId());}
      public boolean allowed(WorkflowContext c, String p) { return active(c.tenantId(), c.actorId()); }
      public boolean active(String t, String a) { return t.equals("019a65d0-0061-7000-8000-000000000001") && Set.of("applicant", "leader", "hr").contains(a); }
      public List<WorkflowPlatform.Option> documentOrganizations(WorkflowContext c) {
        return !organizationUnavailable&&active(c.tenantId(), c.actorId()) ? List.of(new WorkflowPlatform.Option(ORG, "测试组织")) : List.of();
      }
      public List<String> resolve(String t, String o, String k, String r) {
        if (!t.equals("019a65d0-0061-7000-8000-000000000001") || !ORG.equals(o)) return List.of();
        return k.equals("LEADER") ? (leaderMissing?List.of():List.of("leader")) : active(t, r) ? List.of(r) : List.of();
      }
    };
    service = new LeavePocService(jdbc, manager, platform, () -> runtime);
    service.runtimeFactory(new com.appnexus.platform.persistence.businessobject.MappedObjectRuntimeFactory(jdbc));
    var bridge = new WorkflowBridge();
    engine = new WorkflowEngineConfiguration().workflowProcessEngine(source, manager, bridge);
    approval=new ObjectWorkflowAdapter(service,LeavePocService.SUBMIT_OPERATION,platform,()->runtime,manager);
    runtime = new WorkflowEngineConfiguration().workflowRuntime(engine, source, manager, platform, List.of(approval), bridge);
    var objectPlatform = new com.appnexus.contracts.businessobject.BusinessObjectPlatform() {
      public boolean operationAllowed(com.appnexus.contracts.businessobject.BusinessObjects.Context c,String operation,String permission){return platform.allowed(new WorkflowContext(c.tenantId(),c.actorId(),c.requestId()),permission);}
      public com.appnexus.contracts.businessobject.BusinessObjectRuntime.Snapshot authorizeRecord(com.appnexus.contracts.businessobject.BusinessObjects.Context c,com.appnexus.contracts.businessobject.BusinessObjects.Descriptor d,String operation,com.appnexus.contracts.businessobject.BusinessObjectRuntime.Snapshot snapshot){return snapshot;}
      public boolean platformAdministrator(String actor) { return actor.equals("applicant"); }
      public boolean active(com.appnexus.contracts.businessobject.BusinessObjects.Context context) { return platform.active(context.tenantId(),context.actorId()); }
      public boolean tenantAdministrator(com.appnexus.contracts.businessobject.BusinessObjects.Context context) { return active(context); }
      public boolean tenantExists(String tenant) { return tenant.equals("019a65d0-0061-7000-8000-000000000001"); }
    };
    var documents = com.appnexus.platform.persistence.businessobject.BusinessObjectPersistence.create(configurationMapper(),manager,objectPlatform,()->runtime,List.of(service));
    service.bindings(() -> documents);
    service.execution(()->com.appnexus.platform.businessobject.BusinessObjectUseCases.execution(List.of(service),documents,new com.appnexus.platform.persistence.businessobject.configuration.SpringConfigurationTransaction(manager),objectPlatform,()->runtime,()->null));
    runtime.documentBindings(() -> documents);
    var model = model();
    assertThat(runtime.validate(c("applicant"), model)).isEmpty();
    runtime.save(c("applicant"), "leave-poc", -1, model);
    runtime.publish(c("applicant"), "leave-poc", 0);
    var context = new com.appnexus.contracts.businessobject.BusinessObjects.Context("019a65d0-0061-7000-8000-000000000001","applicant",UUID.randomUUID().toString());
    documents.savePageRelationships(context,"LeaveRequest",-1,List.of(new com.appnexus.contracts.businessobject.BusinessObjects.PageRelationship("submit","LeaveRequest.submit")));
    documents.activate(context,"LeaveRequest",0,true);
    documents.grant(context,"LeaveRequest","019a65d0-0061-7000-8000-000000000001",true);
    documents.save(context,"LeaveRequest",-1,List.of(new com.appnexus.contracts.businessobject.BusinessObjects.Binding(UUID.randomUUID().toString(),"审批","leave-poc",1,List.of(),"submit","WORKFLOW","LeaveRequest.submit",null)));
    documents.publish(context,"LeaveRequest",0);

  }

  @AfterAll static void close() { if (engine != null) engine.close(); }

  static Node node(String id, Kind kind, String person) {
    return new Node(id, id, kind, person == null ? List.of() : List.of(new Person(person.equals("leader") ? "LEADER" : "MEMBER", person)),
        Completion.ALL, false, kind == Kind.REVIEW ? "leavePoc" : null, Set.of(), Map.of(), 2, 86400, null, true);
  }
  static Edge edge(String from, String to) { return new Edge(from, to, null, null, false); }
  static WorkflowModel model() {
    return new WorkflowModel("请假 POC", "LeaveRequest", "approval", Mode.APPROVAL, "leavePoc", List.of(),
        List.of(node("start", Kind.START, null), node("leader", Kind.REVIEW, "leader"), node("choice", Kind.CHOICE, null),
            node("hr", Kind.REVIEW, "hr"), node("merge", Kind.MERGE, null), node("end", Kind.END, null)),
        List.of(edge("start", "leader"), edge("leader", "choice"), new Edge("choice", "hr", "days", "3", false, "GT"),
            new Edge("choice", "merge", null, null, true), edge("hr", "merge"), edge("merge", "end")),
        Map.of("APPROVED", "leavePoc", "REJECTED", "leavePoc", "WITHDRAWN", "leavePoc"), List.of(), "leavePoc");
  }
  static LeavePocService.Input input(int days) {
    return new LeavePocService.Input(ORG, "PERSONAL", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, days), "私人原因");
  }
  static Map<String, Object> create(int days) { return service.create(c("applicant"), input(days)); }
  static String task(String actor, String id) {
    return runtime.tasks(c(actor), "todo").stream().filter(t -> id.equals(t.get("object_id"))).findFirst().orElseThrow().get("id").toString();
  }

  @Test void threeDaysEndsAfterSupervisorAndDeliversOnce() {
    var row = create(3); String id = row.get("id").toString();
    var submitted = service.submit(c("applicant"), id);
    assertThat(service.submit(c("applicant"), id).get("instance_id")).isEqualTo(submitted.get("instance_id"));
    String task = task("leader", id);
    assertThat(service.read(c("leader"), id, task).get("reason")).isEqualTo("私人原因");
    assertThatThrownBy(() -> service.read(c("hr"), id, task)).isInstanceOf(SecurityException.class);
    runtime.complete(c("leader"), task, "APPROVE", Map.of());
    assertThatThrownBy(() -> service.read(c("leader"), id, task)).isInstanceOf(SecurityException.class);
    assertThat(runtime.tasks(c("hr"), "todo")).noneMatch(t -> id.equals(t.get("object_id")));
    assertThat(service.read(c("applicant"), id, null).get("state")).isEqualTo("SUBMITTED");
    assertThat(runtime.dispatch()).isTrue();
    assertThat(service.read(c("applicant"), id, null).get("state")).isEqualTo("APPROVED");
    var job = jdbc.queryForMap("SELECT delivery_id invocation_id,instance_id,result FROM leave_poc_approval_cycle WHERE object_id=? AND delivery_id IS NOT NULL", id);
    var ref = new WorkflowBusiness.Reference(submitted.get("instance_id").toString(), "leave-poc", 1, null, "APPROVAL", "APPROVED");
    var invocation = new WorkflowBusiness.Invocation(job.get("invocation_id").toString(), c("applicant"), "LeaveRequest", id, ORG, 0, "EXECUTE", "", ref, null, Map.of());
    approval.execute(invocation);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM leave_poc_approval_cycle WHERE object_id=? AND delivery_id IS NOT NULL", Integer.class, id)).isEqualTo(1);
  }

  @Test void fourDaysRequiresHrAndRejectionWritesBack() {
    String id = create(4).get("id").toString(); service.submit(c("applicant"), id);
    runtime.complete(c("leader"), task("leader", id), "APPROVE", Map.of());
    assertThat(service.read(c("applicant"), id, null).get("state")).isEqualTo("SUBMITTED");
    runtime.complete(c("hr"), task("hr", id), "REJECT", Map.of());
    assertThat(runtime.dispatch()).isTrue();
    assertThat(service.read(c("applicant"), id, null).get("state")).isEqualTo("REJECTED");
  }

  @Test void idempotencyIsolationAndUntrustedConditionsAreRejected() {
    var c = c("applicant"); var row = service.create(c, input(1));
    assertThat(service.create(c, input(1)).get("id")).isEqualTo(row.get("id"));
    assertThatThrownBy(() -> service.create(c, input(2))).isInstanceOf(IllegalArgumentException.class);
    String id = row.get("id").toString();
    assertThatThrownBy(() -> service.read(new WorkflowContext("other", "applicant", UUID.randomUUID().toString()), id, null)).isInstanceOf(SecurityException.class);
    assertThatThrownBy(() -> service.submit(c("hr"), id)).isInstanceOf(SecurityException.class);
    assertThatThrownBy(() -> LeavePocService.days(new LeavePocService.Input(ORG, "PERSONAL", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 1), "原因"))).isInstanceOf(IllegalArgumentException.class);
  }

  @Test void withdrawalIsBusinessCheckedAndDeliveredWithoutApproving() {
    String id = create(4).get("id").toString();
    String instance = service.submit(c("applicant"),id).get("instance_id").toString();
    assertThatThrownBy(() -> runtime.withdraw(c("hr"), instance, "POC 校验")).isInstanceOf(RuntimeException.class);
    runtime.withdraw(c("applicant"), instance, "POC 本人撤回");
    assertThat(runtime.dispatch()).isTrue();
    assertThat(service.read(c("applicant"), id, null).get("state")).isEqualTo("WITHDRAWN");
    assertThat(runtime.tasks(c("leader"), "todo")).noneMatch(t -> id.equals(t.get("object_id")));
  }

  /** 新模型创建时必须与已选人员组织一致；提交仍复核实时归属，撤销归属不能靠旧草稿继续审批。 */
  @Test void selectedOrganizationIsCheckedAtCreationAndRecheckedAtApproval(){
    var base=input(2);
    var forged=new LeavePocService.Input(UUID.randomUUID().toString(),base.leaveType(),base.startDate(),base.endDate(),base.reason());
    assertThatThrownBy(()->service.create(c("applicant"),forged)).hasMessage("PERSON_CONTEXT_REQUIRED");
    String id=service.create(c("applicant"),base).get("id").toString();
    assertThat(service.update(c("applicant"),id,0,base).get("state")).isEqualTo("DRAFT");
    organizationUnavailable=true;
    try{
      assertThatThrownBy(()->service.submit(c("applicant"),id)).hasMessage("LEAVE_ORGANIZATION_UNAVAILABLE");
      assertThat(service.read(c("applicant"),id,null).get("state")).isEqualTo("DRAFT");
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_instance WHERE object_id=?",Integer.class,id)).isZero();
    }finally{organizationUnavailable=false;}
  }

  @Test void missingLeaderAllowsDraftButApprovalRollsBack(){
    leaderMissing=true;
    try{
      String id=service.create(c("applicant"),input(2)).get("id").toString();
      assertThat(service.update(c("applicant"),id,0,input(3)).get("state")).isEqualTo("DRAFT");
      assertThatThrownBy(()->service.submit(c("applicant"),id)).hasMessage("LEAVE_ORGANIZATION_LEADER_REQUIRED");
      assertThat(service.read(c("applicant"),id,null).get("state")).isEqualTo("DRAFT");
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_instance WHERE object_id=?",Integer.class,id)).isZero();
    }finally{leaderMissing=false;}
  }

  @Test void businessCatalogAndTypedConditionsAreAuthoritative() {
    var config = runtime.configuration(c("applicant"));
    assertThat(config.get("documents").toString()).contains("LeaveRequest", "请假天数");
    var base = model();
    var edges = new ArrayList<>(base.edges());
    edges.set(2, new Edge("choice", "hr", "requiresHr", "3", false, "GT"));
    var invalid = new WorkflowModel(base.name(), base.objectType(), base.purpose(), base.mode(), base.entryCapability(),
        base.matches(), base.nodes(), edges, base.terminalActions());
    assertThat(runtime.validate(c("applicant"), invalid)).contains("NUMERIC_FIELD_REQUIRED:choice");
    var noResult = new WorkflowModel(base.name(), base.objectType(), base.purpose(), base.mode(), base.entryCapability(),
        base.matches(), base.nodes(), base.edges(), Map.of());
    assertThat(runtime.validate(c("applicant"), noResult)).contains("BUSINESS_OUTCOME_REQUIRED:APPROVED");
  }
  /** 多线程真实连接争抢同一冻结记录，同一回执只应用一次，竞争终态不得互相覆盖。 */
  @Test void concurrentTerminalDeliveryIsIdempotentAndConflictsRollback() throws Exception {
    String id=create(2).get("id").toString();var submitted=service.submit(c("applicant"),id);
    String instance=submitted.get("instance_id").toString();String delivery=UUID.randomUUID().toString();
    var invocation=new WorkflowBusiness.Invocation(delivery,c("applicant"),"LeaveRequest",id,ORG,0,"EXECUTE","",new WorkflowBusiness.Reference(instance,"leave-poc",1,null,"APPROVAL","APPROVED"),null,Map.of("result","APPROVED"));
    var gate=new java.util.concurrent.CountDownLatch(1);
    try(var workers=java.util.concurrent.Executors.newFixedThreadPool(8)){
      var futures=new ArrayList<java.util.concurrent.Future<?>>();
      for(int index=0;index<8;index++)futures.add(workers.submit(()->{gate.await();approval.execute(invocation);return null;}));
      gate.countDown();for(var future:futures)future.get(30,java.util.concurrent.TimeUnit.SECONDS);
    }
    assertThat(service.read(c("applicant"),id,null).get("state")).isEqualTo("APPROVED");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM leave_poc_approval_cycle WHERE object_id=? AND delivery_id=?",Integer.class,id,delivery)).isEqualTo(1);
    var conflict=new WorkflowBusiness.Invocation(UUID.randomUUID().toString(),c("applicant"),"LeaveRequest",id,ORG,0,"EXECUTE","",new WorkflowBusiness.Reference(instance,"leave-poc",1,null,"APPROVAL","REJECTED"),null,Map.of("result","REJECTED"));
    assertThatThrownBy(()->approval.execute(conflict)).isInstanceOf(SecurityException.class);
    assertThat(jdbc.queryForObject("SELECT last_delivery_id FROM leave_poc_request WHERE id=?",String.class,id)).isEqualTo(delivery);
  }
  /** 结果回执写失败时连同状态写入一起回滚，重试不能看到半完成记录。 */
  @Test void missingCycleRollsBackTerminalState(){
    String id=create(2).get("id").toString();String instance=service.submit(c("applicant"),id).get("instance_id").toString();
    jdbc.update("DELETE FROM leave_poc_approval_cycle WHERE object_id=?",id);
    var invocation=new WorkflowBusiness.Invocation(UUID.randomUUID().toString(),c("applicant"),"LeaveRequest",id,ORG,0,"EXECUTE","",new WorkflowBusiness.Reference(instance,"leave-poc",1,null,"APPROVAL","APPROVED"),null,Map.of());
    assertThatThrownBy(()->approval.execute(invocation)).isInstanceOf(com.appnexus.contracts.businessobject.BusinessObjects.Rejection.class);
    assertThat(service.read(c("applicant"),id,null).get("state")).isEqualTo("SUBMITTED");
    assertThat(jdbc.queryForObject("SELECT last_delivery_id FROM leave_poc_request WHERE id=?",String.class,id)).isNull();
  }

  /** 两个不同终态并发争用同一业务版本，只有一个事务可应用，不允许后提交覆盖。 */
  @Test void competingTerminalResultsHaveExactlyOneWinner() throws Exception {
    String id=create(2).get("id").toString();String instance=service.submit(c("applicant"),id).get("instance_id").toString();
    var gate=new java.util.concurrent.CountDownLatch(1);var winners=new java.util.concurrent.atomic.AtomicInteger();
    try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)){
      var futures=new ArrayList<java.util.concurrent.Future<?>>();
      for(String outcome:List.of("APPROVED","REJECTED"))futures.add(workers.submit(()->{
        gate.await();
        var invocation=new WorkflowBusiness.Invocation(UUID.randomUUID().toString(),c("applicant"),"LeaveRequest",id,ORG,0,"EXECUTE","",new WorkflowBusiness.Reference(instance,"leave-poc",1,null,"APPROVAL",outcome),null,Map.of("result",outcome));
        try{approval.execute(invocation);winners.incrementAndGet();}catch(SecurityException expected){assertThat(expected.getMessage()).isEqualTo("BUSINESS_OBJECT_APPROVAL_RESULT_CONFLICT");}
        return null;
      }));
      gate.countDown();for(var future:futures)future.get(30,java.util.concurrent.TimeUnit.SECONDS);
    }
    assertThat(winners.get()).isEqualTo(1);
    var state=service.read(c("applicant"),id,null).get("state");
    assertThat(state).isIn("APPROVED","REJECTED");
    assertThat(jdbc.queryForObject("SELECT result FROM leave_poc_approval_cycle WHERE object_id=?",String.class,id)).isEqualTo(state);
  }


  /** 隔离测试使用真实 MyBatis/Spring 事务；不使用直连 SqlSession 造成独立提交。 */
  private static com.appnexus.platform.persistence.businessobject.configuration.ConfigurationMapper configurationMapper() {
    try {
      var factory = new org.mybatis.spring.SqlSessionFactoryBean();
      factory.setDataSource(java.util.Objects.requireNonNull(jdbc.getDataSource()));
      var configuration = new org.apache.ibatis.session.Configuration();
      configuration.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);
      configuration.setCacheEnabled(false);
      configuration.setCallSettersOnNulls(true);
      configuration.addMapper(com.appnexus.platform.persistence.businessobject.configuration.ConfigurationMapper.class);
      factory.setConfiguration(configuration);
      return new org.mybatis.spring.SqlSessionTemplate(factory.getObject())
          .getMapper(com.appnexus.platform.persistence.businessobject.configuration.ConfigurationMapper.class);
    } catch (Exception error) { throw new IllegalStateException("Cannot assemble configuration mapper", error); }
  }
}
