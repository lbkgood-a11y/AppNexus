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
  static LeavePocService service;
  static WorkflowRuntime runtime;
  static org.flowable.engine.ProcessEngine engine;
  static WorkflowContext c(String actor) { return new WorkflowContext("019a65d0-0061-7000-8000-000000000001", actor, UUID.randomUUID().toString()); }

  @BeforeAll
  static void setup() throws Exception {
    var source = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    jdbc = new JdbcTemplate(source);
    try (var connection = source.getConnection()) {
      for (String file : List.of("V47__workflow_flowable_8.sql", "V48__workflow_application.sql", "V57__leave_poc.sql", "V59__document_configuration.sql", "V60__leave_approval_cycles.sql", "V63__document_submission_strategy.sql", "V64__leave_direct_submission.sql", "V65__document_print_binding.sql", "V66__document_page_relationships.sql", "V67__explicit_interaction_bindings.sql"))
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/" + file));
    }
    var manager = new DataSourceTransactionManager(source);
    WorkflowPlatform platform = new WorkflowPlatform() {
      public boolean allowed(WorkflowContext c, String p) { return active(c.tenantId(), c.actorId()); }
      public boolean active(String t, String a) { return t.equals("019a65d0-0061-7000-8000-000000000001") && Set.of("applicant", "leader", "hr").contains(a); }
      public List<WorkflowPlatform.Option> documentOrganizations(WorkflowContext c) {
        return active(c.tenantId(), c.actorId()) ? List.of(new WorkflowPlatform.Option(ORG, "测试组织")) : List.of();
      }
      public List<String> resolve(String t, String o, String k, String r) {
        if (!t.equals("019a65d0-0061-7000-8000-000000000001") || !ORG.equals(o)) return List.of();
        return k.equals("LEADER") ? List.of("leader") : active(t, r) ? List.of(r) : List.of();
      }
    };
    service = new LeavePocService(jdbc, manager, platform, () -> runtime, () -> runtime);
    var bridge = new WorkflowBridge();
    engine = new WorkflowEngineConfiguration().workflowProcessEngine(source, manager, bridge);
    runtime = new WorkflowEngineConfiguration().workflowRuntime(engine, source, manager, platform, List.of(service), bridge);
    var documents = new com.appnexus.document.DocumentService(jdbc, manager, new com.appnexus.contracts.document.DocumentPlatform() {
      public boolean platformAdministrator(String actor) { return actor.equals("applicant"); }
      public boolean active(com.appnexus.contracts.document.Documents.Context context) { return platform.active(context.tenantId(),context.actorId()); }
      public boolean tenantAdministrator(com.appnexus.contracts.document.Documents.Context context) { return active(context); }
      public boolean tenantExists(String tenant) { return tenant.equals("019a65d0-0061-7000-8000-000000000001"); }
    }, () -> runtime, List.of(service));
    service.bindings(() -> documents);
    runtime.documentBindings(() -> documents);
    var model = model();
    assertThat(runtime.validate(c("applicant"), model)).isEmpty();
    runtime.save(c("applicant"), "leave-poc", -1, model);
    runtime.publish(c("applicant"), "leave-poc", 0);
    var context = new com.appnexus.contracts.document.Documents.Context("019a65d0-0061-7000-8000-000000000001","applicant",UUID.randomUUID().toString());
    documents.savePageRelationships(context,"LeaveRequest",-1,List.of(new com.appnexus.contracts.document.Documents.PageRelationship("submit","LeaveRequest.submit")));
    documents.activate(context,"LeaveRequest",0,true);
    documents.grant(context,"LeaveRequest","019a65d0-0061-7000-8000-000000000001",true);
    documents.save(context,"LeaveRequest",-1,List.of(new com.appnexus.contracts.document.Documents.Binding(UUID.randomUUID().toString(),"审批","leave-poc",1,List.of(),"submit","WORKFLOW","LeaveRequest.submit",null)));
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
    var job = jdbc.queryForMap("SELECT * FROM leave_poc_delivery WHERE object_id=?", id);
    var ref = new WorkflowBusiness.Reference(submitted.get("instance_id").toString(), "leave-poc", 1, null, "APPROVAL", "APPROVED");
    var invocation = new WorkflowBusiness.Invocation(job.get("invocation_id").toString(), c("applicant"), "LeaveRequest", id, ORG, 0, "EXECUTE", "", ref, null, Map.of());
    service.execute(invocation);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM leave_poc_delivery WHERE object_id=?", Integer.class, id)).isEqualTo(1);
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
}
