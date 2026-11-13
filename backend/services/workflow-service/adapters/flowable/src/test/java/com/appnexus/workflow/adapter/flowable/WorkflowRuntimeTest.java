package com.appnexus.workflow.adapter.flowable;

import static com.appnexus.workflow.core.WorkflowModel.*;
import static org.assertj.core.api.Assertions.*;

import com.appnexus.contracts.workflow.*;
import com.appnexus.workflow.core.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

/** 真实迁移与业务闭环验证；业务替身仅在测试中注册，不向生产提供模拟成功能力。 */
@Tag("container")
@Testcontainers
class WorkflowRuntimeTest {
  @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
  static WorkflowRuntime runtime;
  static JdbcTemplate jdbc;
  static org.flowable.engine.ProcessEngine engine;
  static final Map<String, String> workItems = new HashMap<>();
  static String basis = "standard";
  static final Map<String, Integer> attempts = new HashMap<>();
  static final Set<String> inactive = new HashSet<>();

  static WorkflowContext c(String actor) {
    return new WorkflowContext("tenant-a", actor, UUID.randomUUID().toString());
  }

  @BeforeAll
  static void setup() throws Exception {
    var source =
        new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    jdbc = new JdbcTemplate(source);
    try (var connection = source.getConnection()) {
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("db/migration/V47__workflow_flowable_8.sql"));
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("db/migration/V48__workflow_application.sql"));
      ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V69__workflow_data_contract_snapshots.sql"));
    }
    var bridge = new WorkflowBridge();
    var manager = new DataSourceTransactionManager(source);
    engine = new WorkflowEngineConfiguration().workflowProcessEngine(source, manager, bridge);
    WorkflowPlatform platform =
        new WorkflowPlatform() {
          public boolean allowed(WorkflowContext c, String p) {
            return active(c.tenantId(), c.actorId());
          }

          public boolean active(String t, String a) {
            return t.equals("tenant-a") && !inactive.contains(a);
          }

          public List<String> resolve(String t, String o, String k, String r) {
            return inactive.contains(r) ? List.of() : List.of(r);
          }
        };
    WorkflowBusiness business =
        new WorkflowBusiness() {
          public Capability capability() {
            return new Capability(
                "testBusiness",
                "测试业务",
                "test:use",
                "",
                Set.of("score", "result"),
                Set.of("score", "category"));
          }

          public Map<String, String> snapshot(Invocation i) {
            return Map.of("category", basis);
          }

          public Result execute(Invocation i) {
            workItems.put(i.taskId() == null ? i.invocationId() : i.taskId(), i.workItemKey());
            return new Result(false, Map.of("score", i.values().getOrDefault("score", "5")));
          }
        };
    WorkflowBusiness async =
        new WorkflowBusiness() {
          public Capability capability() {
            return new Capability("testAsync", "测试异步", "test:use", "", Set.of(), Set.of("score"));
          }

          public Result execute(Invocation i) {
            return new Result(true, Map.of());
          }
        };
    WorkflowBusiness flaky =
        new WorkflowBusiness() {
          public Capability capability() {
            return new Capability("flaky", "测试重试", "test:use", "", Set.of(), Set.of());
          }

          public Result execute(Invocation i) {
            if (attempts.merge(i.invocationId(), 1, Integer::sum) == 1)
              throw new IllegalStateException("test-unavailable");
            return new Result(false, Map.of());
          }
        };
    runtime = new WorkflowRuntime(engine, jdbc, manager, platform, List.of(business, async, flaky));
    bridge.bind(runtime);
  }

  /** 每个用例清理上一用例尚未消费的测试交付，避免全局扫描器领取到其他用例的作业。 */
  @BeforeEach
  void isolateJobs() {
    jdbc.update("UPDATE wf_job SET state='CANCELLED' WHERE state<>'DONE'");
  }

  @AfterAll
  static void close() {
    if (engine != null) engine.close();
  }

  static Node n(String id, Kind kind, String cap) {
    return new Node(
        id,
        id,
        kind,
        Set.of(Kind.REVIEW, Kind.HANDLE).contains(kind)
            ? List.of(new Person("MEMBER", "reviewer"))
            : List.of(),
        Completion.ALL,
        false,
        cap,
        kind == Kind.HANDLE ? Set.of("score") : Set.of(),
        Map.of(),
        2,
        3600,
        null,
        true);
  }

  static Edge e(String a, String b) {
    return new Edge(a, b, null, null, false);
  }

  static WorkflowModel m(
      String purpose, Mode mode, List<Node> nodes, List<Edge> edges, Map<String, String> terminal) {
    return new WorkflowModel(
        "测试流程", "TestDocument", purpose, mode, "testBusiness", List.of(), nodes, edges, terminal);
  }

  static void publish(WorkflowModel m) {
    String id = UUID.randomUUID().toString();
    runtime.save(c("author"), id, -1, m);
    assertThat(runtime.validate(c("author"), m)).isEmpty();
    runtime.publish(c("author"), id, 0);
  }

  static String start(String purpose) {
    return String.valueOf(
        ((Map<?, ?>)
                runtime.start(
                    c("author"),
                    "TestDocument",
                    UUID.randomUUID().toString(),
                    1,
                    "organization",
                    purpose,
                    "testBusiness"))
            .get("id"));
  }

  @Test
  void reviewAndReliableTerminalDelivery() {
    publish(
        m(
            "Review",
            Mode.APPROVAL,
            List.of(
                n("start", Kind.START, null),
                n("review", Kind.REVIEW, null),
                n("end", Kind.END, null)),
            List.of(e("start", "review"), e("review", "end")),
            Map.of("APPROVED", "testBusiness")));
    String id = start("Review");
    String task =
        jdbc.queryForObject("SELECT id FROM wf_task WHERE instance_id=?", String.class, id);
    var context = c("reviewer");
    var result = runtime.complete(context, task, "APPROVE", Map.of(), "审核依据已核对");
    assertThat(runtime.complete(context, task, "APPROVE", Map.of(), "审核依据已核对")).isEqualTo(result);
    assertThat(jdbc.queryForObject("SELECT state FROM wf_instance WHERE id=?", String.class, id))
        .isEqualTo("ENDED");
    assertThat(jdbc.queryForObject("SELECT comment FROM wf_task WHERE id=?", String.class, task))
        .isEqualTo("审核依据已核对");
    assertThat(runtime.dispatch()).isTrue();
    assertThat(
            jdbc.queryForObject("SELECT state FROM wf_job WHERE instance_id=?", String.class, id))
        .isEqualTo("DONE");
    assertThatThrownBy(() -> runtime.complete(context, task, "REJECT", Map.of()))
        .hasMessage("IDEMPOTENCY_CONFLICT");
  }

  @Test
  void handleSaveAndAsyncCallback() {
    publish(
        m(
            "Handle",
            Mode.BUSINESS,
            List.of(
                n("start", Kind.START, null),
                n("handle", Kind.HANDLE, "testBusiness"),
                n("wait", Kind.WAIT, "testAsync"),
                n("end", Kind.END, null)),
            List.of(e("start", "handle"), e("handle", "wait"), e("wait", "end")),
            Map.of()));
    String id = start("Handle");
    String task =
        jdbc.queryForObject("SELECT id FROM wf_task WHERE instance_id=?", String.class, id);
    runtime.complete(c("reviewer"), task, "SAVE", Map.of("score", "6"));
    assertThat(jdbc.queryForObject("SELECT state FROM wf_task WHERE id=?", String.class, task))
        .isEqualTo("OPEN");
    assertThatThrownBy(() -> runtime.complete(c("reviewer"), task, "SUBMIT", Map.of()))
        .hasMessage("REQUIRED_INPUT");
    runtime.complete(c("reviewer"), task, "SUBMIT", Map.of("score", "7"));
    runtime.dispatch();
    var job = jdbc.queryForMap("SELECT * FROM wf_job WHERE instance_id=?", id);
    assertThat(job.get("state")).isEqualTo("WAITING");
    runtime.callback("tenant-a", String.valueOf(job.get("id")), "testAsync", Map.of("score", "7"));
    runtime.callback("tenant-a", String.valueOf(job.get("id")), "testAsync", Map.of("score", "7"));
    assertThat(jdbc.queryForObject("SELECT state FROM wf_instance WHERE id=?", String.class, id))
        .isEqualTo("ENDED");
  }

  @Test
  void tenantOwnershipAndDisabledMembers() {
    publish(
        m(
            "Isolation",
            Mode.APPROVAL,
            List.of(
                n("start", Kind.START, null),
                n("review", Kind.REVIEW, null),
                n("end", Kind.END, null)),
            List.of(e("start", "review"), e("review", "end")),
            Map.of()));
    String id = start("Isolation"),
        task = jdbc.queryForObject("SELECT id FROM wf_task WHERE instance_id=?", String.class, id);
    assertThatThrownBy(() -> runtime.complete(c("author"), task, "APPROVE", Map.of()))
        .hasMessage("TASK_NOT_ACTIVE");
    assertThatThrownBy(
            () ->
                runtime.detail(
                    new WorkflowContext("tenant-b", "reviewer", UUID.randomUUID().toString()), id))
        .hasMessage("MEMBER_NOT_ACTIVE");
    inactive.add("reviewer");
    try {
      assertThatThrownBy(() -> runtime.complete(c("reviewer"), task, "APPROVE", Map.of()))
          .hasMessage("MEMBER_NOT_ACTIVE");
    } finally {
      inactive.clear();
    }
  }

  @Test
  void unavailableCapabilityRejected() {
    var model =
        m(
            "Broken",
            Mode.BUSINESS,
            List.of(
                n("start", Kind.START, null),
                n("action", Kind.ACTION, "unknown"),
                n("end", Kind.END, null)),
            List.of(e("start", "action"), e("action", "end")),
            Map.of());
    assertThat(runtime.validate(c("author"), model))
        .anyMatch(code -> code.startsWith("CAPABILITY_UNAVAILABLE"));
  }

  @Test
  void parallelActionsMustBothCompleteBeforeJoin() {
    var fork =
        new Node(
            "fork",
            "fork",
            Kind.FORK,
            List.of(),
            Completion.ALL,
            false,
            null,
            Set.of(),
            Map.of(),
            1,
            3600,
            "join",
            true);
    publish(
        m(
            "Parallel",
            Mode.BUSINESS,
            List.of(
                n("start", Kind.START, null),
                fork,
                n("a", Kind.ACTION, "testBusiness"),
                n("b", Kind.ACTION, "testBusiness"),
                n("join", Kind.JOIN, null),
                n("end", Kind.END, null)),
            List.of(
                e("start", "fork"),
                e("fork", "a"),
                e("fork", "b"),
                e("a", "join"),
                e("b", "join"),
                e("join", "end")),
            Map.of()));
    String id = start("Parallel");
    runtime.dispatch();
    assertThat(jdbc.queryForObject("SELECT state FROM wf_instance WHERE id=?", String.class, id))
        .isEqualTo("RUNNING");
    runtime.dispatch();
    assertThat(jdbc.queryForObject("SELECT state FROM wf_instance WHERE id=?", String.class, id))
        .isEqualTo("ENDED");
  }

  @Test
  void conditionalBranchUsesTrustedSnapshot() {
    publish(
        m(
            "Choice",
            Mode.BUSINESS,
            List.of(
                n("start", Kind.START, null),
                n("choice", Kind.CHOICE, null),
                n("action", Kind.ACTION, "testBusiness"),
                n("end", Kind.END, null)),
            List.of(
                e("start", "choice"),
                new Edge("choice", "action", "category", "standard", false),
                new Edge("choice", "end", null, null, true),
                e("action", "end")),
            Map.of()));
    String id = start("Choice");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM wf_job WHERE instance_id=?", Integer.class, id))
        .isEqualTo(1);
    runtime.dispatch();
    assertThat(jdbc.queryForObject("SELECT state FROM wf_instance WHERE id=?", String.class, id))
        .isEqualTo("ENDED");
  }

  @Test
  void duplicateActiveDocumentAndConflictingMatchAreRejected() {
    var model =
        m(
            "Unique",
            Mode.APPROVAL,
            List.of(
                n("start", Kind.START, null),
                n("review", Kind.REVIEW, null),
                n("end", Kind.END, null)),
            List.of(e("start", "review"), e("review", "end")),
            Map.of());
    publish(model);
    String object = UUID.randomUUID().toString();
    runtime.start(c("author"), "TestDocument", object, 1, "organization", "Unique", "testBusiness");
    assertThatThrownBy(
            () ->
                runtime.start(
                    c("author"),
                    "TestDocument",
                    object,
                    1,
                    "organization",
                    "Unique",
                    "testBusiness"))
        .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    publish(model);
    assertThatThrownBy(() -> start("Unique")).hasMessage("FLOW_MATCH_CONFLICT");
  }

  @Test
  void emptyAssigneesMustNotAutoComplete() {
    var self =
        new Node(
            "review",
            "review",
            Kind.REVIEW,
            List.of(new Person("MEMBER", "author")),
            Completion.ALL,
            false,
            null,
            Set.of(),
            Map.of(),
            1,
            3600,
            null,
            true);
    publish(
        m(
            "NoSelf",
            Mode.APPROVAL,
            List.of(n("start", Kind.START, null), self, n("end", Kind.END, null)),
            List.of(e("start", "review"), e("review", "end")),
            Map.of()));
    String id = start("NoSelf");
    assertThat(
            jdbc.queryForObject("SELECT actor FROM wf_task WHERE instance_id=?", String.class, id))
        .isNull();
    assertThat(jdbc.queryForObject("SELECT state FROM wf_instance WHERE id=?", String.class, id))
        .isEqualTo("RUNNING");
  }

  @Test
  void anyAndSequentialCompletionHaveDifferentTaskLifecycles() {
    for (var rule : List.of(Completion.ANY, Completion.SEQUENTIAL)) {
      var review =
          new Node(
              "review",
              "review",
              Kind.REVIEW,
              List.of(new Person("MEMBER", "first"), new Person("MEMBER", "second")),
              rule,
              false,
              null,
              Set.of(),
              Map.of(),
              1,
              3600,
              null,
              true);
      String purpose = "Multi" + rule.name();
      publish(
          m(
              purpose,
              Mode.APPROVAL,
              List.of(n("start", Kind.START, null), review, n("end", Kind.END, null)),
              List.of(e("start", "review"), e("review", "end")),
              Map.of()));
      String id = start(purpose);
      var first =
          jdbc.queryForMap(
              "SELECT id,actor FROM wf_task WHERE instance_id=? ORDER BY created_at LIMIT 1", id);
      runtime.complete(
          c(String.valueOf(first.get("actor"))),
          String.valueOf(first.get("id")),
          "APPROVE",
          Map.of());
      if (rule == Completion.ANY)
        assertThat(
                jdbc.queryForObject("SELECT state FROM wf_instance WHERE id=?", String.class, id))
            .isEqualTo("ENDED");
      else {
        assertThat(
                jdbc.queryForObject("SELECT state FROM wf_instance WHERE id=?", String.class, id))
            .isEqualTo("RUNNING");
        var second =
            jdbc.queryForMap(
                "SELECT id,actor FROM wf_task WHERE instance_id=? AND state='OPEN'", id);
        runtime.complete(
            c(String.valueOf(second.get("actor"))),
            String.valueOf(second.get("id")),
            "APPROVE",
            Map.of());
        assertThat(
                jdbc.queryForObject("SELECT state FROM wf_instance WHERE id=?", String.class, id))
            .isEqualTo("ENDED");
      }
    }
  }

  @Test
  void previewHasNoPersistentOrBusinessSideEffects() {
    var model =
        m(
            "Preview",
            Mode.BUSINESS,
            List.of(
                n("start", Kind.START, null),
                n("action", Kind.ACTION, "testBusiness"),
                n("end", Kind.END, null)),
            List.of(e("start", "action"), e("action", "end")),
            Map.of());
    long before = jdbc.queryForObject("SELECT COUNT(*) FROM wf_instance", Long.class);
    assertThat(WorkflowPreview.trace(model, Map.of())).containsExactly("start", "action", "end");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_instance", Long.class))
        .isEqualTo(before);
  }

  @Test
  void rejectTerminatesWithoutRunningDownstreamActions() {
    publish(
        m(
            "Reject",
            Mode.BUSINESS,
            List.of(
                n("start", Kind.START, null),
                n("review", Kind.REVIEW, null),
                n("action", Kind.ACTION, "testBusiness"),
                n("end", Kind.END, null)),
            List.of(e("start", "review"), e("review", "action"), e("action", "end")),
            Map.of()));
    String id = start("Reject"),
        task = jdbc.queryForObject("SELECT id FROM wf_task WHERE instance_id=?", String.class, id);
    runtime.complete(c("reviewer"), task, "REJECT", Map.of());
    assertThat(jdbc.queryForObject("SELECT result FROM wf_instance WHERE id=?", String.class, id))
        .isEqualTo("REJECTED");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM wf_job WHERE instance_id=?", Integer.class, id))
        .isZero();
  }

  @Test
  void withdrawalRequiresBusinessConfirmationAndPreservesHistory() {
    var base =
        m(
            "Withdraw",
            Mode.APPROVAL,
            List.of(
                n("start", Kind.START, null),
                n("review", Kind.REVIEW, null),
                n("end", Kind.END, null)),
            List.of(e("start", "review"), e("review", "end")),
            Map.of());
    var model =
        new WorkflowModel(
            base.name(),
            base.objectType(),
            base.purpose(),
            base.mode(),
            base.entryCapability(),
            base.matches(),
            base.nodes(),
            base.edges(),
            base.terminalActions(),
            List.of(new Person("MEMBER", "observer")),
            "testBusiness");
    publish(model);
    String id = start("Withdraw");
    assertThat(runtime.tasks(c("observer"), "copied")).anyMatch(row -> id.equals(row.get("id")));
    runtime.withdraw(c("author"), id, "测试撤回");
    assertThat(jdbc.queryForObject("SELECT result FROM wf_instance WHERE id=?", String.class, id))
        .isEqualTo("WITHDRAWN");
    assertThat(
            jdbc.queryForObject("SELECT state FROM wf_task WHERE instance_id=?", String.class, id))
        .isEqualTo("CANCELLED");
  }

  @Test
  void taskScopedFieldPermissionExpiresWhenTaskEnds() {
    publish(
        m(
            "FieldAccess",
            Mode.BUSINESS,
            List.of(
                n("start", Kind.START, null),
                n("handle", Kind.HANDLE, "testBusiness"),
                n("end", Kind.END, null)),
            List.of(e("start", "handle"), e("handle", "end")),
            Map.of()));
    String id = start("FieldAccess");
    var instance = jdbc.queryForMap("SELECT * FROM wf_instance WHERE id=?", id);
    String
        task = jdbc.queryForObject("SELECT id FROM wf_task WHERE instance_id=?", String.class, id),
        object = String.valueOf(instance.get("object_id"));
    assertThat(
            runtime.permits(
                c("reviewer"), task, "TestDocument", object, 1, "testBusiness", "score"))
        .isTrue();
    assertThat(
            runtime.permits(c("author"), task, "TestDocument", object, 1, "testBusiness", "score"))
        .isFalse();
    runtime.complete(c("reviewer"), task, "SUBMIT", Map.of("score", "9"));
    assertThat(
            runtime.permits(
                c("reviewer"), task, "TestDocument", object, 1, "testBusiness", "score"))
        .isFalse();
  }

  @Test
  void overdueAsyncResultRequiresExplicitRecovery() {
    publish(
        m(
            "Timeout",
            Mode.BUSINESS,
            List.of(
                n("start", Kind.START, null),
                n("wait", Kind.WAIT, "testAsync"),
                n("end", Kind.END, null)),
            List.of(e("start", "wait"), e("wait", "end")),
            Map.of()));
    String id = start("Timeout");
    runtime.dispatch();
    var job = jdbc.queryForMap("SELECT * FROM wf_job WHERE instance_id=?", id);
    jdbc.update(
        "UPDATE wf_job SET deadline=DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 1 SECOND) WHERE id=?",
        job.get("id"));
    runtime.maintain();
    assertThat(
            jdbc.queryForObject("SELECT state FROM wf_job WHERE id=?", String.class, job.get("id")))
        .isEqualTo("TIMEOUT");
    runtime.retry(c("author"), String.valueOf(job.get("id")), "业务已恢复");
    assertThat(
            jdbc.queryForObject("SELECT state FROM wf_job WHERE id=?", String.class, job.get("id")))
        .isEqualTo("READY");
  }

  @Test
  void technicalRetryKeepsStableInvocationAndResumesExactlyOneNode() {
    publish(
        m(
            "Retry",
            Mode.BUSINESS,
            List.of(
                n("start", Kind.START, null),
                n("action", Kind.ACTION, "flaky"),
                n("end", Kind.END, null)),
            List.of(e("start", "action"), e("action", "end")),
            Map.of()));
    String id = start("Retry");
    runtime.dispatch();
    var job = jdbc.queryForMap("SELECT * FROM wf_job WHERE instance_id=?", id);
    assertThat(job.get("state")).isEqualTo("READY");
    assertThat(job.get("error_code")).isEqualTo("EXECUTION_UNAVAILABLE");
    String jobId = String.valueOf(job.get("id"));
    jdbc.update("UPDATE wf_job SET due_at=CURRENT_TIMESTAMP(6) WHERE id=?", jobId);
    runtime.dispatch();
    assertThat(attempts.get(jobId)).isEqualTo(2);
    assertThat(jdbc.queryForObject("SELECT state FROM wf_instance WHERE id=?", String.class, id))
        .isEqualTo("ENDED");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM wf_job WHERE instance_id=?", Integer.class, id))
        .isEqualTo(1);
  }

  @Test
  void changedApprovalBasisCannotReusePriorDecision() {
    publish(
        m(
            "Basis",
            Mode.APPROVAL,
            List.of(
                n("start", Kind.START, null),
                n("review", Kind.REVIEW, null),
                n("end", Kind.END, null)),
            List.of(e("start", "review"), e("review", "end")),
            Map.of()));
    String id = start("Basis");
    String task =
        jdbc.queryForObject("SELECT id FROM wf_task WHERE instance_id=?", String.class, id);
    basis = "changed";
    try {
      assertThatThrownBy(() -> runtime.complete(c("reviewer"), task, "APPROVE", Map.of()))
          .hasMessage("APPROVAL_BASIS_CHANGED");
      assertThat(jdbc.queryForObject("SELECT state FROM wf_task WHERE id=?", String.class, task))
          .isEqualTo("OPEN");
    } finally {
      basis = "standard";
    }
  }

  @Test
  void sharedAndSeparateHandlingPassDifferentBusinessScopeKeys() {
    for (boolean separate : List.of(true, false)) {
      String purpose = separate ? "Separate" : "Shared";
      var handle =
          new Node(
              "handle",
              "handle",
              Kind.HANDLE,
              List.of(new Person("MEMBER", "first"), new Person("MEMBER", "second")),
              Completion.ALL,
              false,
              "testBusiness",
              Set.of("score"),
              Map.of(),
              2,
              3600,
              null,
              separate);
      publish(
          m(
              purpose,
              Mode.BUSINESS,
              List.of(n("start", Kind.START, null), handle, n("end", Kind.END, null)),
              List.of(e("start", "handle"), e("handle", "end")),
              Map.of()));
      String id = start(purpose);
      var tasks = jdbc.queryForList("SELECT id,actor FROM wf_task WHERE instance_id=?", id);
      for (var task : tasks)
        runtime.complete(
            c(String.valueOf(task.get("actor"))),
            String.valueOf(task.get("id")),
            "SUBMIT",
            Map.of("score", "8"));
      String first = workItems.get(String.valueOf(tasks.getFirst().get("id"))),
          second = workItems.get(String.valueOf(tasks.getLast().get("id")));
      if (separate) assertThat(first).isNotEqualTo(second);
      else assertThat(first).isEqualTo(second).isEqualTo(id + ":handle");
    }
  }

  @Test
  void reassignmentKeepsOriginalAndNewAllocationFacts() {
    var review =
        new Node(
            "review",
            "review",
            Kind.REVIEW,
            List.of(new Person("MEMBER", "late")),
            Completion.ALL,
            false,
            null,
            Set.of(),
            Map.of(),
            2,
            3600,
            null,
            true);
    publish(
        m(
            "Reassign",
            Mode.APPROVAL,
            List.of(n("start", Kind.START, null), review, n("end", Kind.END, null)),
            List.of(e("start", "review"), e("review", "end")),
            Map.of()));
    inactive.add("late");
    String id;
    try {
      id = start("Reassign");
    } finally {
      inactive.remove("late");
    }
    String task =
        jdbc.queryForObject("SELECT id FROM wf_task WHERE instance_id=?", String.class, id);
    runtime.reassign(c("author"), task, "late", "任职已恢复");
    var audit =
        jdbc.queryForMap(
            "SELECT from_actor,to_actor FROM wf_audit WHERE instance_id=? AND action='REASSIGNED'",
            id);
    assertThat(audit.get("from_actor")).isNull();
    assertThat(audit.get("to_actor")).isEqualTo("late");
    runtime.complete(c("late"), task, "APPROVE", Map.of());
    assertThat(jdbc.queryForObject("SELECT result FROM wf_instance WHERE id=?", String.class, id))
        .isEqualTo("APPROVED");
  }
}
