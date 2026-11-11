package com.appnexus.workflow.adapter.flowable;

import static com.appnexus.workflow.core.WorkflowModel.*;

import com.appnexus.contracts.workflow.*;
import com.appnexus.workflow.core.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 工作流应用入口；写入、引擎推进、幂等响应、任务及Outbox共享本地事务。 业务能力仍以稳定调用ID幂等，不能假设业务服务与引擎共享事务。
 * 所有对象查询带租户；历史只向参与者或受权运维开放，不返回原始业务实体。
 */
public final class WorkflowRuntime implements WorkflowResults, WorkflowTaskAccess, WorkflowStarter {
  private final ProcessEngine engine;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final WorkflowPlatform platform;
  private final Map<String, WorkflowBusiness> businesses;

  /**
   * 装配运行时；重复能力标识拒绝启动，避免同名能力被静默替换。
   *
   * @param handlers 代码注册的业务能力，无能力时允许管理流程草稿，但发布和发起失败关闭
   */
  public WorkflowRuntime(
      ProcessEngine engine,
      JdbcTemplate jdbc,
      PlatformTransactionManager manager,
      WorkflowPlatform platform,
      List<WorkflowBusiness> handlers) {
    this.engine = engine;
    this.jdbc = jdbc;
    this.tx = new TransactionTemplate(manager);
    this.platform = platform;
    var map = new HashMap<String, WorkflowBusiness>();
    for (var h : handlers) {
      var c = h.capability();
      if (!c.key().matches("[a-zA-Z][a-zA-Z0-9_]{0,63}")
          || c.permission() == null
          || c.permission().isBlank()
          || map.put(c.key(), h) != null) throw new IllegalArgumentException("CAPABILITY_INVALID");
      if (c.route() != null
          && !c.route().isBlank()
          && (!c.route().startsWith("/") || c.route().startsWith("//") || c.route().contains(":")))
        throw new IllegalArgumentException("CAPABILITY_ROUTE_INVALID");
      for (var f : c.inputs())
        if (f == null || !f.matches("[a-zA-Z][a-zA-Z0-9_]{0,63}"))
          throw new IllegalArgumentException("CAPABILITY_INPUT_INVALID");
      for (var f : c.outputs())
        if (!f.matches("[a-zA-Z][a-zA-Z0-9_]{0,63}"))
          throw new IllegalArgumentException("CAPABILITY_FIELD_INVALID");
    }
    businesses = Map.copyOf(map);
  }

  /** 公开业务发起入口不暴露引擎实体或领域模型。 */
  @Override
  public String startInstance(
      WorkflowContext c,
      String type,
      String object,
      long version,
      String organization,
      String purpose,
      String entry) {
    return String.valueOf(
        ((Map<?, ?>) start(c, type, object, version, organization, purpose, entry)).get("id"));
  }

  /** 受限任务授权每次重查有效成员、任职和冻结节点；不能用于任务结束后的编辑。 */
  @Override
  public boolean permits(
      WorkflowContext c,
      String taskId,
      String type,
      String object,
      long version,
      String capability,
      String field,
      boolean write) {
    requireActive(c);
    var rows =
        jdbc.queryForList(
            "SELECT * FROM wf_task WHERE tenant_id=? AND id=? AND actor=? AND state='OPEN'",
            c.tenantId(),
            taskId,
            c.actorId());
    if (rows.isEmpty()) return false;
    var t = rows.getFirst();
    var i = instance(c.tenantId(), str(t, "instance_id"), false);
    if (!"RUNNING".equals(i.get("state"))
        || !type.equals(i.get("object_type"))
        || !object.equals(i.get("object_id"))
        || version != number(i, "object_version")) return false;
    var model = definition(i);
    var n = model.node(str(t, "node_id"));
    var b = businesses.get(capability);
    return b != null
        && (write
            ? capability.equals(n.capability()) && b.capability().inputs().contains(field)
            : (capability.equals(n.capability()) || capability.equals(model.entryCapability()))
                && b.capability().outputs().contains(field))
        && resolve(i, n).contains(c.actorId());
  }

  /** 管理操作能力；普通处理任务只需有效成员及任务授权，不要求整个业务模块权限。 */
  public List<String> capabilities(WorkflowContext c) {
    requireActive(c);
    return List.of("read", "edit", "publish", "start", "operate").stream()
        .filter(a -> platform.allowed(c, "workflow:" + a))
        .toList();
  }

  /** 可配置能力不接受客户端动态注册；仅返回当前有权使用的能力元数据。 */
  public List<WorkflowBusiness.Capability> businesses(WorkflowContext c) {
    requireActive(c);
    return businesses.values().stream()
        .map(WorkflowBusiness::capability)
        .filter(b -> platform.allowed(c, b.permission()))
        .sorted(Comparator.comparing(WorkflowBusiness.Capability::key))
        .toList();
  }

  /** 业务设计目录与真实选人选项；只读流程人员可查看，能力仍逐项按既有功能权限过滤。 */
  public Map<String, Object> configuration(WorkflowContext c) {
    require(c, "read");
    var documents = businesses(c).stream()
        .filter(b -> businesses.get(b.key()).configuration() != null)
        .map(b -> Map.of("capability", b.key(), "profile", businesses.get(b.key()).configuration()))
        .toList();
    return Map.of("documents", documents, "options", platform.configurationOptions(c));
  }

  /** 最多100个定义；支持按名称筛选，分页由HTTP约束。 */
  public List<Map<String, Object>> definitions(WorkflowContext c) {
    require(c, "read");
    return jdbc.queryForList(
        "SELECT id,revision,model,enabled,published FROM wf_definition WHERE tenant_id=? ORDER BY"
            + " id LIMIT 100",
        c.tenantId());
  }

  /** 校验草稿，不部署；输入输出和权限由当前能力目录再校验。 */
  public List<String> validate(WorkflowContext c, WorkflowModel m) {
    require(c, "edit");
    return validateInternal(c, m);
  }

  private List<String> validateInternal(WorkflowContext c, WorkflowModel m) {
    var caps = businesses(c);
    var keys = new HashSet<String>();
    var fields = new HashSet<String>();
    caps.forEach(
        b -> {
          keys.add(b.key());
          fields.addAll(b.outputs());
        });
    if (m != null && m.nodes() != null)
      m.nodes().stream().filter(Objects::nonNull).forEach(n -> fields.add(n.id() + "_decision"));
    var errors = new ArrayList<>(WorkflowValidator.validate(m, keys, fields));
    if (m != null && m.edges() != null) {
      // 数值条件必须显式来自本单据的 NUMBER 字段，避免按字符串或跨业务字段误判。
      var entry = businesses.get(m.entryCapability());
      var profile = entry == null ? null : entry.configuration();
      if (profile != null && m.mode() == Mode.APPROVAL) {
        for (String result : List.of("APPROVED", "REJECTED")) {
          String bound = m.terminalActions() == null ? null : m.terminalActions().get(result);
          if (profile.outcomes().stream().noneMatch(o -> o.result().equals(result) && o.capability().equals(bound)))
            errors.add("BUSINESS_OUTCOME_REQUIRED:" + result);
        }
        if (m.withdrawalCapability() != null && !m.withdrawalCapability().isBlank()) {
          String bound = m.terminalActions() == null ? null : m.terminalActions().get("WITHDRAWN");
          if (!m.withdrawalCapability().equals(profile.withdrawalCapability()) || profile.outcomes().stream()
              .noneMatch(o -> o.result().equals("WITHDRAWN") && o.capability().equals(bound)))
            errors.add("BUSINESS_WITHDRAWAL_INVALID");
        }
      }
      for (var edge : m.edges())
        if (edge != null && !edge.defaultEdge()
            && !Set.of("EQ", "NE").contains(WorkflowCondition.operator(edge.operator()))
            && (profile == null || profile.fields().stream()
                .noneMatch(f -> f.key().equals(edge.field()) && "NUMBER".equals(f.type()))))
          errors.add("NUMERIC_FIELD_REQUIRED:" + edge.from());
    }
    if (m != null && m.nodes() != null)
      for (var n : m.nodes())
        if (n != null && n.capability() != null && !n.capability().isBlank()) {
          if (!keys.contains(n.capability())) {
            errors.add("CAPABILITY_UNAVAILABLE:" + n.id());
            continue;
          }
          var cap = businesses.get(n.capability()).capability();
          if (n.input() != null && !cap.inputs().containsAll(n.input().keySet()))
            errors.add("INPUT_FIELD_INVALID:" + n.id());
          if (n.required() != null && !cap.inputs().containsAll(n.required()))
            errors.add("REQUIRED_FIELD_INVALID:" + n.id());
        }
    return errors;
  }

  /** 草稿乐观锁保存；相同请求重放返回原结果，不覆盖已发布版本。 */
  public Object save(WorkflowContext c, String id, long revision, WorkflowModel m) {
    require(c, "edit");
    identifier(id);
    if (m == null
        || m.nodes() == null
        || m.nodes().size() > 100
        || WorkflowJson.write(m).length() > 262144) throw fail("MODEL_INVALID", 400);
    return mutate(
        c,
        "SAVE:" + id + ":" + revision + ":" + WorkflowJson.write(m),
        () -> {
          if (revision < 0) {
            jdbc.update(
                "INSERT INTO wf_definition(tenant_id,id,model) VALUES(?,?,?)",
                c.tenantId(),
                id,
                WorkflowJson.write(m));
            revisionAudit(c, "DRAFT_CREATED", id);
            return Map.of("revision", 0);
          }
          if (jdbc.update(
                  "UPDATE wf_definition SET model=?,revision=revision+1 WHERE tenant_id=? AND id=?"
                      + " AND revision=?",
                  WorkflowJson.write(m),
                  c.tenantId(),
                  id,
                  revision)
              != 1) throw fail("VERSION_CONFLICT", 409);
          revisionAudit(c, "DRAFT_SAVED", id);
          return Map.of("revision", revision + 1);
        });
  }

  /** 发布时冻结JSON/BPMN；所有能力必须仍可用并获配置人授权。 */
  public Object publish(WorkflowContext c, String id, long revision) {
    require(c, "publish");
    return mutate(
        c,
        "PUBLISH:" + id + ":" + revision,
        () -> {
          var row =
              one(
                  "SELECT * FROM wf_definition WHERE tenant_id=? AND id=? FOR UPDATE",
                  c.tenantId(),
                  id);
          if (number(row, "revision") != revision) throw fail("VERSION_CONFLICT", 409);
          var model = model(row);
          var errors = validateInternal(c, model);
          if (!errors.isEmpty()) throw fail(errors.getFirst().split(":")[0], 400);
          int version = (int) number(row, "published") + 1;
          String key = "wf_" + id.replace("-", "") + "_" + version;
          byte[] bpmn = WorkflowCompiler.compile(key, model);
          var deployment =
              engine
                  .getRepositoryService()
                  .createDeployment()
                  .tenantId(c.tenantId())
                  .addBytes(key + ".bpmn20.xml", bpmn)
                  .deploy();
          var definition =
              engine
                  .getRepositoryService()
                  .createProcessDefinitionQuery()
                  .deploymentId(deployment.getId())
                  .processDefinitionTenantId(c.tenantId())
                  .singleResult();
          jdbc.update(
              "INSERT INTO"
                  + " wf_version(tenant_id,definition_id,version,model,bpmn,engine_definition,published_by)"
                  + " VALUES(?,?,?,?,?,?,?)",
              c.tenantId(),
              id,
              version,
              WorkflowJson.write(model),
              new String(bpmn, StandardCharsets.UTF_8),
              definition.getId(),
              c.actorId());
          jdbc.update(
              "UPDATE wf_definition SET published=?,enabled=TRUE,revision=revision+1 WHERE"
                  + " tenant_id=? AND id=?",
              version,
              c.tenantId(),
              id);
          revisionAudit(c, "PUBLISHED", id);
          return Map.of("version", version, "revision", revision + 1);
        });
  }

  /** 停用仅阻止新实例，不迁移或结束运行中的版本；必须填写审计原因。 */
  public Object disable(WorkflowContext c, String id, long revision, String reason) {
    require(c, "publish");
    reason(reason);
    return mutate(
        c,
        "DISABLE:" + id + ":" + revision + ":" + reason,
        () -> {
          if (jdbc.update(
                  "UPDATE wf_definition SET enabled=FALSE,revision=revision+1 WHERE tenant_id=? AND"
                      + " id=? AND revision=?",
                  c.tenantId(),
                  id,
                  revision)
              != 1) throw fail("VERSION_CONFLICT", 409);
          audit(c, null, "DISABLED", id, reason);
          return Map.of("revision", revision + 1);
        });
  }

  /** 发布版本用于比较，历史只读；不暴露原生引擎部署接口。 */
  public List<Map<String, Object>> versions(WorkflowContext c, String id) {
    require(c, "read");
    return jdbc.queryForList(
        "SELECT version,model,published_by,created_at FROM wf_version WHERE tenant_id=? AND"
            + " definition_id=? ORDER BY version DESC LIMIT 100",
        c.tenantId(),
        id);
  }

  /** 发起时只接收业务身份，流转依据由已授权入口能力从业务存储重读。 单据同用途活动唯一键处理并发，重复请求和业务调用分别幂等。 */
  public Object start(
      WorkflowContext c,
      String objectType,
      String objectId,
      long objectVersion,
      String organization,
      String purpose,
      String entry) {
    require(c, "start");
    identifier(objectType);
    identifier(purpose);
    text(objectId, 128);
    text(organization, 64);
    if (objectVersion < 0) throw fail("OBJECT_VERSION_INVALID", 400);
    return mutate(
        c,
        "START:"
            + objectType
            + ":"
            + objectId
            + ":"
            + objectVersion
            + ":"
            + organization
            + ":"
            + purpose
            + ":"
            + entry,
        () -> {
          var business = business(c, entry);
          var snapshot =
              outputs(
                  business,
                  business.snapshot(
                      new WorkflowBusiness.Invocation(
                          c.requestId(),
                          c,
                          objectType,
                          objectId,
                          organization,
                          objectVersion,
                          "SNAPSHOT",
                          c.requestId(),
                          null,
                          null,
                          Map.of())));
          var rows =
              jdbc.queryForList(
                  "SELECT v.*,d.id FROM wf_definition d JOIN wf_version v ON"
                      + " v.tenant_id=d.tenant_id AND v.definition_id=d.id AND"
                      + " v.version=d.published WHERE d.tenant_id=? AND d.enabled=TRUE FOR SHARE",
                  c.tenantId());
          var matching =
              rows.stream()
                  .filter(
                      row -> {
                        var m = model(row);
                        return m.objectType().equals(objectType)
                            && m.purpose().equals(purpose)
                            && m.entryCapability().equals(entry)
                            && (m.matches() == null
                                || m.matches().stream()
                                    .allMatch(
                                        f -> Objects.equals(snapshot.get(f.field()), f.value())));
                      })
                  .toList();
          if (matching.size() != 1)
            throw fail(matching.isEmpty() ? "FLOW_NOT_MATCHED" : "FLOW_MATCH_CONFLICT", 409);
          var selected = matching.getFirst();
          var m = model(selected);
          String id = UUID.randomUUID().toString();
          jdbc.update(
              "INSERT INTO"
                  + " wf_instance(tenant_id,id,definition_id,definition_version,object_type,object_id,object_version,purpose,organization_id,starter,active_key,snapshot)"
                  + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
              c.tenantId(),
              id,
              selected.get("definition_id"),
              selected.get("version"),
              objectType,
              objectId,
              objectVersion,
              purpose,
              organization,
              c.actorId(),
              hash(objectType + ":" + objectId + ":" + purpose),
              WorkflowJson.write(snapshot));
          if (m.copies() != null)
            for (var rule : m.copies())
              for (var actor :
                  platform.resolve(c.tenantId(), organization, rule.kind(), rule.reference()))
                if (platform.active(c.tenantId(), actor))
                  jdbc.update(
                      "INSERT IGNORE INTO wf_copy(tenant_id,instance_id,actor) VALUES(?,?,?)",
                      c.tenantId(),
                      id,
                      actor);
          var vars = new HashMap<String, Object>();
          vars.put("wf_id", id);
          vars.put("wf_fields", new HashMap<>(snapshot));
          vars.put("wf_result", "APPROVED");
          var process =
              engine
                  .getRuntimeService()
                  .startProcessInstanceById(
                      String.valueOf(selected.get("engine_definition")), id, vars);
          jdbc.update(
              "UPDATE wf_instance SET engine_id=? WHERE tenant_id=? AND id=?",
              process.getId(),
              c.tenantId(),
              id);
          audit(c, id, "STARTED", id, "");
          return Map.of("id", id);
        });
  }

  /** 本人待办/已办/发起查询均受租户和人员约束，不按前端提供账号查询。 */
  public List<Map<String, Object>> tasks(WorkflowContext c, String view) {
    requireActive(c);
    if (!Set.of("todo", "done", "started", "copied").contains(view))
      throw fail("VIEW_INVALID", 400);
    if (view.equals("copied"))
      return jdbc.queryForList(
          "SELECT i.id,i.object_type,i.object_id,i.purpose,i.state,i.result,i.created_at FROM"
              + " wf_instance i JOIN wf_copy c ON c.tenant_id=i.tenant_id AND c.instance_id=i.id"
              + " WHERE c.tenant_id=? AND c.actor=? ORDER BY i.created_at DESC LIMIT 100",
          c.tenantId(),
          c.actorId());
    if (view.equals("started"))
      return jdbc.queryForList(
          "SELECT id,object_type,object_id,object_version,purpose,state,result,created_at FROM"
              + " wf_instance WHERE tenant_id=? AND starter=? ORDER BY created_at DESC LIMIT 100",
          c.tenantId(),
          c.actorId());
    return jdbc.queryForList(
        "SELECT t.*,i.object_type,i.object_id,i.object_version FROM wf_task t JOIN wf_instance i ON"
            + " i.tenant_id=t.tenant_id AND i.id=t.instance_id WHERE t.tenant_id=? AND t.actor=?"
            + " AND t.state "
            + (view.equals("todo") ? "='OPEN'" : "IN('DONE','CANCELLED')")
            + " ORDER BY t.created_at DESC LIMIT 100",
        c.tenantId(),
        c.actorId());
  }

  /** 运维列表不返回单据原文；详细业务数据仍由业务端单独授权读取。 */
  public List<Map<String, Object>> instances(WorkflowContext c) {
    require(c, "operate");
    return jdbc.queryForList(
        "SELECT id,object_type,object_id,object_version,purpose,state,result,created_at,ended_at"
            + " FROM wf_instance WHERE tenant_id=? ORDER BY created_at DESC LIMIT 100",
        c.tenantId());
  }

  /** 实例事实与历史受参与者或运维权限保护；业务原文不在此接口返回。 */
  public Map<String, Object> detail(WorkflowContext c, String id) {
    requireActive(c);
    var row = instance(c.tenantId(), id, false);
    canRead(c, row);
    var result = new LinkedHashMap<String, Object>();
    result.put("instance", publicInstance(row));
    result.put(
        "tasks",
        jdbc.queryForList(
            "SELECT"
                + " id,node_id,node_name,actor,state,decision,comment,deadline,created_at,completed_at"
                + " FROM wf_task WHERE tenant_id=? AND instance_id=? ORDER BY created_at",
            c.tenantId(),
            id));
    result.put(
        "history",
        jdbc.queryForList(
            "SELECT actor,action,target,detail,from_actor,to_actor,created_at FROM wf_audit WHERE"
                + " tenant_id=? AND instance_id=? ORDER BY id",
            c.tenantId(),
            id));
    if (platform.allowed(c, "workflow:operate"))
      result.put(
          "jobs",
          jdbc.queryForList(
              "SELECT id,node_id,capability,kind,state,attempts,error_code,deadline FROM wf_job"
                  + " WHERE tenant_id=? AND instance_id=? ORDER BY due_at",
              c.tenantId(),
              id));
    result.put(
        "relatedInstances",
        jdbc.queryForList(
            "SELECT i.id,i.definition_version,i.state,i.result,i.created_at FROM wf_instance i"
                + " WHERE i.tenant_id=? AND i.object_type=? AND i.object_id=? AND i.purpose=? AND"
                + " (i.starter=? OR ?=TRUE OR EXISTS(SELECT 1 FROM wf_task t WHERE"
                + " t.tenant_id=i.tenant_id AND t.instance_id=i.id AND t.actor=?) OR EXISTS(SELECT"
                + " 1 FROM wf_copy cp WHERE cp.tenant_id=i.tenant_id AND cp.instance_id=i.id AND"
                + " cp.actor=?)) ORDER BY i.created_at DESC LIMIT 100",
            c.tenantId(),
            row.get("object_type"),
            row.get("object_id"),
            row.get("purpose"),
            c.actorId(),
            platform.allowed(c, "workflow:operate"),
            c.actorId(),
            c.actorId()));
    return result;
  }

  /** 任务只返回业务能力声明，不返回原始单据内容；业务页面仍执行字段和强制限制。 */
  public Map<String, Object> task(WorkflowContext c, String taskId) {
    requireActive(c);
    var t = one("SELECT * FROM wf_task WHERE tenant_id=? AND id=?", c.tenantId(), taskId);
    if (!c.actorId().equals(t.get("actor"))) throw fail("TASK_DENIED", 403);
    var i = instance(c.tenantId(), str(t, "instance_id"), false);
    var n = definition(i).node(str(t, "node_id"));
    return Map.of(
        "task",
        t,
        "node",
        n,
        "instance",
        publicInstance(i),
        "business",
        n.capability() != null && businesses.containsKey(n.capability())
            ? businesses.get(n.capability()).capability()
            : Map.of());
  }

  /** 暂存通过业务能力执行但不推进；提交先确认业务保存成功，再完成引擎任务。 完成时重新解析任职，已离岗人员不能沿用旧分配；实例锁防止或签及重复完成竞争。 */
  public Object complete(
      WorkflowContext c, String taskId, String action, Map<String, String> values) {
    return complete(c, taskId, action, values, "");
  }

  /** 办理意见独立于业务字段保存，最多2000字符；不会作为引擎表达式或业务输入执行。 */
  public Object complete(
      WorkflowContext c, String taskId, String action, Map<String, String> values, String comment) {
    if (comment == null || comment.length() > 2000) throw fail("INPUT_INVALID", 400);
    requireActive(c);
    if (!Set.of("SAVE", "SUBMIT", "APPROVE", "REJECT").contains(action))
      throw fail("ACTION_INVALID", 400);
    bounded(values);
    return mutate(
        c,
        "TASK:" + taskId + ":" + action + ":" + WorkflowJson.write(values) + ":" + comment,
        () -> {
          var t = one("SELECT * FROM wf_task WHERE tenant_id=? AND id=?", c.tenantId(), taskId);
          var i = instance(c.tenantId(), str(t, "instance_id"), true);
          // 锁实例后重读任务；等待锁的另一请求不能沿用锁前OPEN快照重复调用业务。
          t =
              one(
                  "SELECT * FROM wf_task WHERE tenant_id=? AND id=? FOR UPDATE",
                  c.tenantId(),
                  taskId);
          if (!"RUNNING".equals(i.get("state"))
              || !"OPEN".equals(t.get("state"))
              || !c.actorId().equals(t.get("actor"))) throw fail("TASK_NOT_ACTIVE", 409);
          var n = definition(i).node(str(t, "node_id"));
          if (!resolve(i, n).contains(c.actorId())) throw fail("TASK_ASSIGNMENT_EXPIRED", 403);
          if (n.kind() == Kind.REVIEW && !Set.of("APPROVE", "REJECT").contains(action)
              || n.kind() == Kind.HANDLE && !Set.of("SAVE", "SUBMIT").contains(action))
            throw fail("ACTION_INVALID", 400);
          // 每次办理都复核提交时审批依据；正常节点填写应由业务端维护独立办理版本。
          var entryBusiness = businesses.get(definition(i).entryCapability());
          if (entryBusiness == null) throw fail("CAPABILITY_UNAVAILABLE", 409);
          var basis =
              outputs(
                  entryBusiness,
                  entryBusiness.snapshot(
                      invocation(c, i, taskId, c.requestId(), "SNAPSHOT", Map.of())));
          if (!basis.equals(WorkflowJson.map(str(i, "snapshot"))))
            throw fail("APPROVAL_BASIS_CHANGED", 409);
          Map<String, String> out = Map.of();
          if (n.capability() != null && !n.capability().isBlank()) {
            var business = businesses.get(n.capability());
            if (business == null) throw fail("CAPABILITY_UNAVAILABLE", 409);
            if (!business.capability().inputs().containsAll(values.keySet()))
              throw fail("INPUT_FIELD_INVALID", 400);
            if (!action.equals("SAVE")
                && n.required() != null
                && n.required().stream().anyMatch(k -> values.getOrDefault(k, "").isBlank()))
              throw fail("REQUIRED_INPUT", 400);
            var result = business.execute(invocation(c, i, taskId, c.requestId(), action, values));
            if (result.pending()) throw fail("BUSINESS_CONFIRMATION_REQUIRED", 409);
            out = outputs(business, result.outputs());
          } else if (!values.isEmpty()) throw fail("INPUT_FIELD_INVALID", 400);
          if (action.equals("SAVE")) {
            jdbc.update(
                "UPDATE wf_task SET comment=? WHERE tenant_id=? AND id=?",
                comment,
                c.tenantId(),
                taskId);
            audit(c, str(i, "id"), "TASK_SAVED", taskId, "");
            return Map.of("saved", true);
          }
          var fields = fields(i);
          fields.putAll(out);
          fields.put(n.id() + "_decision", action);
          engine.getRuntimeService().setVariable(str(i, "engine_id"), "wf_fields", fields);
          if (action.equals("REJECT"))
            engine.getRuntimeService().setVariable(str(i, "engine_id"), "wf_result", "REJECTED");
          jdbc.update(
              "UPDATE wf_task SET"
                  + " state='DONE',decision=?,comment=?,completed_at=CURRENT_TIMESTAMP(6) WHERE"
                  + " tenant_id=? AND id=? AND state='OPEN'",
              action,
              comment,
              c.tenantId(),
              taskId);
          audit(c, str(i, "id"), "TASK_COMPLETED", taskId, action);
          if (action.equals("REJECT")) {
            finish(i, "REJECTED");
            engine.getRuntimeService().deleteProcessInstance(str(i, "engine_id"), "REJECTED");
            jdbc.update(
                "UPDATE wf_job SET state='CANCELLED',lease_token=NULL WHERE tenant_id=? AND"
                    + " instance_id=? AND kind<>'TERMINAL' AND state<>'DONE'",
                c.tenantId(),
                i.get("id"));
          } else engine.getTaskService().complete(taskId);
          reconcileTasks(c.tenantId(), str(i, "id"));
          return Map.of("completed", true);
        });
  }

  /** 人工重分配只选当前规则内有效人员，不能以运维权绕过必需审核或自审限制。 */
  public Object reassign(WorkflowContext c, String taskId, String actor, String reason) {
    require(c, "operate");
    reason(reason);
    return mutate(
        c,
        "REASSIGN:" + taskId + ":" + actor + ":" + reason,
        () -> {
          var t = one("SELECT * FROM wf_task WHERE tenant_id=? AND id=?", c.tenantId(), taskId);
          var i = instance(c.tenantId(), str(t, "instance_id"), true);
          // 锁实例后重读任务；等待锁的另一请求不能沿用锁前OPEN快照重复调用业务。
          t =
              one(
                  "SELECT * FROM wf_task WHERE tenant_id=? AND id=? FOR UPDATE",
                  c.tenantId(),
                  taskId);
          var n = definition(i).node(str(t, "node_id"));
          if (!"OPEN".equals(t.get("state")) || !resolve(i, n).contains(actor))
            throw fail("ASSIGNEE_INVALID", 409);
          if (jdbc.queryForObject(
                  "SELECT COUNT(*) FROM wf_task WHERE tenant_id=? AND instance_id=? AND node_id=?"
                      + " AND actor=? AND state='OPEN' AND id<>?",
                  Long.class,
                  c.tenantId(),
                  i.get("id"),
                  n.id(),
                  actor,
                  taskId)
              > 0) throw fail("ASSIGNEE_INVALID", 409);
          engine.getTaskService().setAssignee(taskId, actor);
          jdbc.update(
              "UPDATE wf_task SET actor=? WHERE tenant_id=? AND id=?", actor, c.tenantId(), taskId);
          auditAssignment(
              c, str(i, "id"), "REASSIGNED", taskId, reason, (String) t.get("actor"), actor);
          return Map.of("assigned", true);
        });
  }

  /** 人工恢复仅作用于失败交付；稳定调用ID不变，不允许重放已成功业务操作。 */
  public Object retry(WorkflowContext c, String id, String reason) {
    require(c, "operate");
    reason(reason);
    return mutate(
        c,
        "RETRY:" + id + ":" + reason,
        () -> {
          var job =
              one("SELECT * FROM wf_job WHERE tenant_id=? AND id=? FOR UPDATE", c.tenantId(), id);
          if (!Set.of("MANUAL", "TIMEOUT").contains(str(job, "state")))
            throw fail("JOB_NOT_MANUAL", 409);
          jdbc.update(
              "UPDATE wf_job SET"
                  + " state='READY',attempts=0,due_at=CURRENT_TIMESTAMP(6),deadline=DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL"
                  + " 1 DAY),error_code=NULL WHERE tenant_id=? AND id=?",
              c.tenantId(),
              id);
          audit(c, str(job, "instance_id"), "JOB_RETRIED", id, reason);
          return Map.of("retried", true);
        });
  }

  /** 撤回由业务能力确认允许，不能只删除流程记录；业务未确认时不终止实例。 */
  public Object withdraw(WorkflowContext c, String id, String reason) {
    requireActive(c);
    reason(reason);
    return mutate(
        c,
        "WITHDRAW:" + id + ":" + reason,
        () -> {
          var i = instance(c.tenantId(), id, true);
          if (!c.actorId().equals(i.get("starter")) || !"RUNNING".equals(i.get("state")))
            throw fail("WITHDRAWAL_DENIED", 403);
          var m = definition(i);
          if (m.withdrawalCapability() == null || m.withdrawalCapability().isBlank())
            throw fail("WITHDRAWAL_NOT_CONFIGURED", 409);
          var business = business(c, m.withdrawalCapability());
          var result =
              business.execute(invocation(c, i, null, c.requestId(), "WITHDRAW", Map.of()));
          if (result.pending()) throw fail("BUSINESS_CONFIRMATION_REQUIRED", 409);
          finish(i, "WITHDRAWN");
          engine.getRuntimeService().deleteProcessInstance(str(i, "engine_id"), "WITHDRAWN");
          jdbc.update(
              "UPDATE wf_job SET state='CANCELLED',lease_token=NULL WHERE tenant_id=? AND"
                  + " instance_id=? AND kind<>'TERMINAL' AND state<>'DONE'",
              c.tenantId(),
              id);
          reconcileTasks(c.tenantId(), id);
          audit(c, id, "WITHDRAWN", id, reason);
          return Map.of("withdrawn", true);
        });
  }

  /** 每次发起或运行节点从冻结版本读取模型，不受后续草稿修改影响。 */
  WorkflowModel definition(Map<String, Object> instance) {
    return model(
        one(
            "SELECT model FROM wf_version WHERE tenant_id=? AND definition_id=? AND version=?",
            instance.get("tenant_id"),
            instance.get("definition_id"),
            instance.get("definition_version")));
  }

  List<String> people(DelegateExecution e, String node) {
    var i = instance(e.getTenantId(), String.valueOf(e.getVariable("wf_id")), false);
    var people = resolve(i, definition(i).node(node));
    return people.isEmpty() ? List.of("__UNASSIGNED__") : people;
  }

  private List<String> resolve(Map<String, Object> i, Node n) {
    var people = new LinkedHashSet<String>();
    for (var p : n.people())
      people.addAll(
          platform.resolve(
              str(i, "tenant_id"), str(i, "organization_id"), p.kind(), p.reference()));
    people.removeIf(
        a ->
            !platform.active(str(i, "tenant_id"), a)
                || !n.allowSelf() && a.equals(i.get("starter")));
    return List.copyOf(people);
  }

  void created(DelegateTask task) {
    String tenant = task.getTenantId(), id = String.valueOf(task.getVariable("wf_id"));
    var i = instance(tenant, id, false);
    var n = definition(i).node(task.getTaskDefinitionKey());
    String actor = task.getAssignee();
    if ("__UNASSIGNED__".equals(actor)) {
      task.setAssignee(null);
      actor = null;
    }
    jdbc.update(
        "INSERT INTO wf_task(tenant_id,id,instance_id,node_id,node_name,actor,deadline)"
            + " VALUES(?,?,?,?,?,?,?)",
        tenant,
        task.getId(),
        id,
        n.id(),
        n.name(),
        actor,
        java.sql.Timestamp.from(Instant.now().plusSeconds(n.timeoutSeconds())));
    auditAssignment(
        new WorkflowContext(tenant, str(i, "starter"), UUID.randomUUID().toString()),
        id,
        actor == null ? "NO_ASSIGNEE" : "TASK_CREATED",
        task.getId(),
        "",
        null,
        actor);
  }

  void action(DelegateExecution e) {
    String tenant = e.getTenantId(), id = String.valueOf(e.getVariable("wf_id"));
    var i = instance(tenant, id, false);
    var n = definition(i).node(e.getCurrentActivityId());
    var values = new TreeMap<String, String>();
    var fields = fields(i);
    if (n.input() != null)
      n.input()
          .forEach(
              (key, value) ->
                  values.put(
                      key,
                      value.startsWith("literal:")
                          ? value.substring(8)
                          : fields.getOrDefault(value, "")));
    createJob(
        i,
        n.id(),
        e.getId(),
        n.capability(),
        n.kind().name(),
        values,
        n.retries() + 1,
        n.timeoutSeconds());
  }

  void ended(DelegateExecution e) {
    String tenant = e.getTenantId(), id = String.valueOf(e.getVariable("wf_id"));
    var i = instance(tenant, id, false);
    if (!"RUNNING".equals(i.get("state"))) return;
    String result = String.valueOf(e.getVariable("wf_result"));
    finish(i, result);
  }

  private void finish(Map<String, Object> i, String result) {
    jdbc.update(
        "UPDATE wf_instance SET"
            + " state='ENDED',result=?,active_key=NULL,ended_at=CURRENT_TIMESTAMP(6) WHERE"
            + " tenant_id=? AND id=? AND state='RUNNING'",
        result,
        i.get("tenant_id"),
        i.get("id"));
    var model = definition(i);
    if (model.mode() == Mode.APPROVAL
        && model.terminalActions() != null
        && model.terminalActions().containsKey(result))
      createJob(
          i,
          "terminal",
          null,
          model.terminalActions().get(result),
          "TERMINAL",
          Map.of("result", result),
          4,
          86400);
    audit(
        new WorkflowContext(str(i, "tenant_id"), str(i, "starter"), UUID.randomUUID().toString()),
        str(i, "id"),
        "ENDED",
        str(i, "id"),
        result);
  }

  private void createJob(
      Map<String, Object> i,
      String node,
      String execution,
      String capability,
      String kind,
      Map<String, String> payload,
      int attempts,
      int timeout) {
    jdbc.update(
        "INSERT INTO"
            + " wf_job(tenant_id,id,instance_id,node_id,execution_id,capability,kind,payload,max_attempts,deadline)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?)",
        i.get("tenant_id"),
        UUID.randomUUID().toString(),
        i.get("id"),
        node,
        execution,
        capability,
        kind,
        WorkflowJson.write(payload),
        attempts,
        java.sql.Timestamp.from(Instant.now().plusSeconds(timeout)));
  }

  /** 领取后在事务外调用业务；成功回写与引擎推进同事务。租约令牌防止过期执行者覆盖新结果。 宕机后可再次执行，因此业务适配器必须按job.id持久化幂等，不能承诺外部副作用恰好一次。 */
  public boolean dispatch() {
    Map<String, Object> job =
        tx.execute(
            s -> {
              var rows =
                  jdbc.queryForList(
                      "SELECT * FROM wf_job WHERE deadline>CURRENT_TIMESTAMP(6) AND ((state='READY'"
                          + " AND due_at<=CURRENT_TIMESTAMP(6)) OR (state='RUNNING' AND"
                          + " lease_until<CURRENT_TIMESTAMP(6))) ORDER BY due_at LIMIT 1 FOR UPDATE"
                          + " SKIP LOCKED");
              if (rows.isEmpty()) return null;
              var j = rows.getFirst();
              if (number(j, "attempts") >= number(j, "max_attempts")) {
                jdbc.update(
                    "UPDATE wf_job SET state='MANUAL',error_code='LEASE_EXHAUSTED' WHERE"
                        + " tenant_id=? AND id=?",
                    j.get("tenant_id"),
                    j.get("id"));
                return null;
              }
              String token = UUID.randomUUID().toString();
              jdbc.update(
                  "UPDATE wf_job SET"
                      + " state='RUNNING',attempts=attempts+1,lease_token=?,lease_until=DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL"
                      + " 120 SECOND) WHERE tenant_id=? AND id=?",
                  token,
                  j.get("tenant_id"),
                  j.get("id"));
              j.put("lease_token", token);
              return j;
            });
    if (job == null) return false;
    WorkflowBusiness.Result result = null;
    String error = null;
    boolean businessError = false;
    try {
      var i = instance(str(job, "tenant_id"), str(job, "instance_id"), false);
      var business = businesses.get(str(job, "capability"));
      if (business == null) throw new IllegalArgumentException("CAPABILITY_UNAVAILABLE");
      var c = new WorkflowContext(str(i, "tenant_id"), str(i, "starter"), str(job, "id"));
      result =
          business.execute(
              invocation(
                  c, i, null, str(job, "id"), "EXECUTE", WorkflowJson.map(str(job, "payload"))));
      outputs(business, result.outputs());
    } catch (IllegalArgumentException | SecurityException e) {
      error = "BUSINESS_REJECTED";
      businessError = true;
    } catch (Exception e) {
      error = "EXECUTION_UNAVAILABLE";
    }
    var completed = result;
    var code = error;
    boolean manual = businessError;
    tx.executeWithoutResult(
        s -> {
          var j =
              one(
                  "SELECT * FROM wf_job WHERE tenant_id=? AND id=? FOR UPDATE",
                  job.get("tenant_id"),
                  job.get("id"));
          if (!"RUNNING".equals(j.get("state"))
              || !job.get("lease_token").equals(j.get("lease_token"))) return;
          if (completed != null) {
            if (completed.pending())
              jdbc.update(
                  "UPDATE wf_job SET state='WAITING',result=? WHERE tenant_id=? AND id=?",
                  WorkflowJson.write(completed.outputs()),
                  j.get("tenant_id"),
                  j.get("id"));
            else acknowledge(j, completed.outputs());
          } else
            jdbc.update(
                "UPDATE wf_job SET"
                    + " state=?,error_code=?,due_at=DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 30"
                    + " SECOND),lease_token=NULL WHERE tenant_id=? AND id=?",
                manual || number(j, "attempts") >= number(j, "max_attempts") ? "MANUAL" : "READY",
                code,
                j.get("tenant_id"),
                j.get("id"));
        });
    return true;
  }

  /**
   * 可信业务适配器回传结果的内部入口，不直接映射HTTP；调用方须绑定能力并验证外部签名/身份。
   *
   * @param tenant 租户标识，来自可信业务端
   * @param jobId 稳定调用标识，重复同结果可安全重放
   * @param capability 必须与原交付能力相同
   * @param outputs 仅允许能力声明的非敏感输出
   */
  public void callback(
      String tenant, String jobId, String capability, Map<String, String> outputs) {
    tx.executeWithoutResult(
        s -> {
          var j = one("SELECT * FROM wf_job WHERE tenant_id=? AND id=? FOR UPDATE", tenant, jobId);
          if (!capability.equals(j.get("capability"))) throw fail("CALLBACK_DENIED", 403);
          var b = businesses.get(capability);
          if (b == null) throw fail("CAPABILITY_UNAVAILABLE", 409);
          var checked = outputs(b, outputs);
          if ("DONE".equals(j.get("state"))) {
            if (!WorkflowJson.map(str(j, "result")).equals(checked))
              throw fail("CALLBACK_CONFLICT", 409);
            return;
          }
          if (!Set.of("WAITING", "RUNNING").contains(str(j, "state")))
            throw fail("CALLBACK_NOT_WAITING", 409);
          acknowledge(j, checked);
        });
  }

  private void acknowledge(Map<String, Object> j, Map<String, String> output) {
    var i = instance(str(j, "tenant_id"), str(j, "instance_id"), true);
    if (!"TERMINAL".equals(j.get("kind"))) {
      if (!"RUNNING".equals(i.get("state"))) throw fail("INSTANCE_NOT_RUNNING", 409);
      var fields = fields(i);
      fields.putAll(output);
      engine.getRuntimeService().setVariable(str(i, "engine_id"), "wf_fields", fields);
      engine.getRuntimeService().trigger(str(j, "execution_id"));
    }
    jdbc.update(
        "UPDATE wf_job SET state='DONE',result=?,lease_token=NULL,error_code=NULL WHERE tenant_id=?"
            + " AND id=?",
        WorkflowJson.write(output),
        j.get("tenant_id"),
        j.get("id"));
    audit(
        new WorkflowContext(str(j, "tenant_id"), str(i, "starter"), str(j, "id")),
        str(i, "id"),
        "ACTION_COMPLETED",
        str(j, "id"),
        "");
  }

  /** 超时不自动通过；到期交付转人工，任务到期写告警事实但不改变业务决定。 */
  public void maintain() {
    tx.executeWithoutResult(
        s -> {
          jdbc.update(
              "INSERT INTO wf_audit(tenant_id,instance_id,actor,action,target,detail) SELECT"
                  + " t.tenant_id,t.instance_id,'system','TASK_OVERDUE',t.id,'' FROM wf_task t"
                  + " WHERE t.state='OPEN' AND t.deadline<CURRENT_TIMESTAMP(6) AND NOT"
                  + " EXISTS(SELECT 1 FROM wf_audit a WHERE a.tenant_id=t.tenant_id AND"
                  + " a.action='TASK_OVERDUE' AND a.target=t.id)");
          jdbc.update(
              "UPDATE wf_job SET state='TIMEOUT',error_code='WAIT_TIMEOUT',lease_token=NULL WHERE"
                  + " state IN('READY','WAITING','RUNNING') AND deadline<CURRENT_TIMESTAMP(6)");
          var rows =
              jdbc.queryForList(
                  "SELECT t.* FROM wf_task t WHERE t.state='OPEN' AND (t.actor IS NOT NULL OR"
                      + " t.deadline<CURRENT_TIMESTAMP(6)) ORDER BY t.checked_at,t.id LIMIT 100");
          for (var t : rows) {
            jdbc.update(
                "UPDATE wf_task SET checked_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=? AND id=?",
                t.get("tenant_id"),
                t.get("id"));
            var i = instance(str(t, "tenant_id"), str(t, "instance_id"), true);
            if (!"RUNNING".equals(i.get("state"))) continue;
            var n = definition(i).node(str(t, "node_id"));
            String actor = (String) t.get("actor");
            if (actor != null && !resolve(i, n).contains(actor)) {
              engine.getTaskService().setAssignee(str(t, "id"), null);
              jdbc.update(
                  "UPDATE wf_task SET actor=NULL WHERE tenant_id=? AND id=?",
                  t.get("tenant_id"),
                  t.get("id"));
              auditAssignment(
                  new WorkflowContext(
                      str(t, "tenant_id"), str(i, "starter"), UUID.randomUUID().toString()),
                  str(i, "id"),
                  "REASSIGN_REQUIRED",
                  str(t, "id"),
                  "",
                  actor,
                  null);
            }
          }
        });
  }

  private void reconcileTasks(String tenant, String id) {
    for (var row :
        jdbc.queryForList(
            "SELECT id FROM wf_task WHERE tenant_id=? AND instance_id=? AND state='OPEN'",
            tenant,
            id))
      if (engine
              .getTaskService()
              .createTaskQuery()
              .taskId(str(row, "id"))
              .taskTenantId(tenant)
              .count()
          == 0)
        jdbc.update(
            "UPDATE wf_task SET state='CANCELLED',completed_at=CURRENT_TIMESTAMP(6) WHERE"
                + " tenant_id=? AND id=?",
            tenant,
            row.get("id"));
  }

  private WorkflowBusiness business(WorkflowContext c, String key) {
    var b = businesses.get(key);
    if (b == null) throw fail("CAPABILITY_UNAVAILABLE", 409);
    if (!platform.allowed(c, b.capability().permission()))
      throw fail("BUSINESS_ACCESS_DENIED", 403);
    return b;
  }

  private WorkflowBusiness.Invocation invocation(
      WorkflowContext c,
      Map<String, Object> i,
      String task,
      String key,
      String operation,
      Map<String, String> values) {
    // 共享与分别填写的业务数据键显式传递，避免配置开关只有界面效果。
    String workItemKey = key;
    if (task != null) {
      var taskRow =
          one("SELECT node_id FROM wf_task WHERE tenant_id=? AND id=?", c.tenantId(), task);
      var node = definition(i).node(str(taskRow, "node_id"));
      workItemKey = node.separate() ? task : str(i, "id") + ":" + node.id();
    }
    String nodeId = null;
    if (task != null)
      nodeId =
          str(
              one("SELECT node_id FROM wf_task WHERE tenant_id=? AND id=?", c.tenantId(), task),
              "node_id");
    else {
      var jobs =
          jdbc.queryForList(
              "SELECT node_id FROM wf_job WHERE tenant_id=? AND id=?", c.tenantId(), key);
      if (!jobs.isEmpty()) nodeId = str(jobs.getFirst(), "node_id");
    }
    var reference =
        new WorkflowBusiness.Reference(
            str(i, "id"),
            str(i, "definition_id"),
            (int) number(i, "definition_version"),
            nodeId,
            definition(i).mode().name(),
            (String) i.get("result"));
    return new WorkflowBusiness.Invocation(
        key,
        c,
        str(i, "object_type"),
        str(i, "object_id"),
        str(i, "organization_id"),
        number(i, "object_version"),
        operation,
        workItemKey,
        reference,
        task,
        values);
  }

  private Map<String, String> outputs(WorkflowBusiness b, Map<String, String> values) {
    bounded(values);
    if (!b.capability().outputs().containsAll(values.keySet()))
      throw fail("OUTPUT_FIELD_INVALID", 409);
    return new TreeMap<>(values);
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> fields(Map<String, Object> i) {
    String engineId = (String) i.get("engine_id");
    if (engineId != null
        && engine
                .getRuntimeService()
                .createProcessInstanceQuery()
                .processInstanceId(engineId)
                .count()
            > 0)
      return new TreeMap<>(
          (Map<String, String>) engine.getRuntimeService().getVariable(engineId, "wf_fields"));
    return new TreeMap<>(WorkflowJson.map(str(i, "snapshot")));
  }

  private Map<String, Object> publicInstance(Map<String, Object> i) {
    var result = new LinkedHashMap<String, Object>();
    for (var k :
        List.of(
            "id",
            "definition_id",
            "definition_version",
            "object_type",
            "object_id",
            "object_version",
            "purpose",
            "state",
            "result",
            "created_at",
            "ended_at")) result.put(k, i.get(k));
    return result;
  }

  private void canRead(WorkflowContext c, Map<String, Object> i) {
    if (c.actorId().equals(i.get("starter")) || platform.allowed(c, "workflow:operate")) return;
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM wf_copy WHERE tenant_id=? AND instance_id=? AND actor=?",
            Long.class,
            c.tenantId(),
            i.get("id"),
            c.actorId())
        > 0) return;
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM wf_task WHERE tenant_id=? AND instance_id=? AND actor=?",
            Long.class,
            c.tenantId(),
            i.get("id"),
            c.actorId())
        == 0) throw fail("INSTANCE_DENIED", 403);
  }

  private Map<String, Object> instance(String tenant, String id, boolean lock) {
    return one(
        "SELECT * FROM wf_instance WHERE tenant_id=? AND id=?" + (lock ? " FOR UPDATE" : ""),
        tenant,
        id);
  }

  private Map<String, Object> one(String sql, Object... values) {
    var rows = jdbc.queryForList(sql, values);
    if (rows.size() != 1) throw fail("WORKFLOW_NOT_FOUND", 404);
    return rows.getFirst();
  }

  private WorkflowModel model(Map<String, Object> row) {
    return WorkflowJson.read(str(row, "model"), WorkflowModel.class);
  }

  private void requireActive(WorkflowContext c) {
    if (!platform.active(c.tenantId(), c.actorId())) throw fail("MEMBER_NOT_ACTIVE", 403);
  }

  private void require(WorkflowContext c, String action) {
    requireActive(c);
    if (!platform.allowed(c, "workflow:" + action)) throw fail("WORKFLOW_DENIED", 403);
  }

  private Object mutate(WorkflowContext c, String fingerprint, Supplier<Object> work) {
    UUID.fromString(c.requestId());
    String hash = hash(fingerprint);
    return tx.execute(
        s -> {
          jdbc.update(
              "INSERT INTO wf_request(tenant_id,actor,request_id,fingerprint) VALUES(?,?,?,?) ON"
                  + " DUPLICATE KEY UPDATE request_id=request_id",
              c.tenantId(),
              c.actorId(),
              c.requestId(),
              hash);
          var row =
              one(
                  "SELECT * FROM wf_request WHERE tenant_id=? AND actor=? AND request_id=? FOR"
                      + " UPDATE",
                  c.tenantId(),
                  c.actorId(),
                  c.requestId());
          if (!hash.equals(row.get("fingerprint"))) throw fail("IDEMPOTENCY_CONFLICT", 409);
          if (row.get("response") != null)
            return WorkflowJson.read(str(row, "response"), Object.class);
          Object result = work.get();
          jdbc.update(
              "UPDATE wf_request SET response=? WHERE tenant_id=? AND actor=? AND request_id=?",
              WorkflowJson.write(result),
              c.tenantId(),
              c.actorId(),
              c.requestId());
          return result;
        });
  }

  /** 原分配与新分配独立保存，不通过覆盖任务当前办理人丢失历史。 */
  private void auditAssignment(
      WorkflowContext c,
      String instance,
      String action,
      String target,
      String reason,
      String previous,
      String next) {
    jdbc.update(
        "INSERT INTO wf_audit(tenant_id,instance_id,actor,action,target,detail,from_actor,to_actor)"
            + " VALUES(?,?,?,?,?,?,?,?)",
        c.tenantId(),
        instance,
        c.actorId(),
        action,
        target,
        reason,
        previous,
        next);
  }

  private void revisionAudit(WorkflowContext c, String action, String id) {
    audit(c, null, action, id, "");
  }

  private void audit(
      WorkflowContext c, String instance, String action, String target, String detail) {
    jdbc.update(
        "INSERT INTO wf_audit(tenant_id,instance_id,actor,action,target,detail)"
            + " VALUES(?,?,?,?,?,?)",
        c.tenantId(),
        instance,
        c.actorId(),
        action,
        target,
        detail);
  }

  private static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String str(Map<String, Object> row, String key) {
    return String.valueOf(row.get(key));
  }

  private static long number(Map<String, Object> row, String key) {
    return ((Number) row.get(key)).longValue();
  }

  private static void identifier(String s) {
    if (s == null || !s.matches("[a-zA-Z0-9_-]{1,64}")) throw fail("IDENTIFIER_INVALID", 400);
  }

  private static void text(String s, int max) {
    if (s == null || s.isBlank() || s.length() > max) throw fail("INPUT_INVALID", 400);
  }

  private static void reason(String s) {
    text(s, 500);
  }

  private static void bounded(Map<String, String> v) {
    if (v == null
        || v.size() > 100
        || v.entrySet().stream()
            .anyMatch(
                e ->
                    e.getKey() == null
                        || e.getValue() == null
                        || e.getKey().length() > 64
                        || e.getValue().length() > 2000)) throw fail("INPUT_INVALID", 400);
  }

  private static WorkflowFailure fail(String code, int status) {
    return new WorkflowFailure(code, status);
  }
}
