package com.appnexus.workflow.application;
import com.appnexus.workflow.application.WorkflowRecords;
import com.appnexus.workflow.application.WorkflowContent;

import static com.appnexus.workflow.core.WorkflowModel.*;

import com.appnexus.contracts.workflow.*;
import com.appnexus.workflow.core.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import com.appnexus.workflow.application.WorkflowEngineCommands;

import com.appnexus.workflow.application.WorkflowTransaction;


/**
 * 工作流应用入口；写入、引擎推进、幂等响应、任务及Outbox共享本地事务。 业务能力仍以稳定调用ID幂等，不能假设业务服务与引擎共享事务。
 * 所有对象查询带租户；历史只向参与者或受权运维开放，不返回原始业务实体。
 */
public final class WorkflowRuntime implements WorkflowResults, WorkflowTaskAccess, WorkflowStarter, WorkflowDefinitionCatalog, com.appnexus.workflow.api.WorkflowManagement {
  private java.util.function.Supplier<com.appnexus.contracts.businessobject.BusinessObjectBindings> documentBindings;

  /** 由装配层设置延迟公开契约；避免业务、配置和引擎相互初始化，非浏览器配置点。 */
  public void documentBindings(java.util.function.Supplier<com.appnexus.contracts.businessobject.BusinessObjectBindings> bindings) {
    this.documentBindings = bindings;
  }

  /** 本租户兼容发布版本目录，管理端是否可调用由公开目录宿主鉴权。 */
  @Override public List<WorkflowDefinitionCatalog.Option> published(WorkflowContext c,String type,String purpose,String entry,Set<String> outcomes) {
    requireActive(c);
    return statements.publishedDefinitions(c.tenantId()).stream()
      .filter(r->{var m=model(r);return (r.get("data_contract_schema")==null||content.schema(r.get("data_contract_schema").toString()).equals(dataSchema(businesses.get(entry),entry)))&&m.objectType().equals(type)&&m.purpose().equals(purpose)&&m.entryCapability().equals(entry)
        &&m.mode()==Mode.APPROVAL&&m.terminalActions()!=null&&outcomes.stream().allMatch(o->entry.equals(m.terminalActions().get(o)))
        &&(m.matches()==null||m.matches().isEmpty());})
      .map(r->new WorkflowDefinitionCatalog.Option(r.get("definition_id").toString(),((Number)r.get("version")).intValue(),model(r).name())).toList();
  }

  @Override public String startBound(WorkflowContext c,String type,String object,long version,String organization,String purpose,String entry,String resolution) {
    if(documentBindings==null)throw fail("BUSINESS_OBJECT_BINDINGS_UNAVAILABLE",503);
    return String.valueOf(((Map<?,?>)startInternal(c,type,object,version,organization,purpose,entry,resolution)).get("id"));
  }
  private final WorkflowEngineCommands engine;
  private final WorkflowRecords statements;
  private final WorkflowContent content;
  private final WorkflowCompilation compilation;
  private final WorkflowTransaction tx;
  private final WorkflowPlatform platform;
  private final Map<String, WorkflowBusiness> businesses;

  /**
   * 装配运行时；重复能力标识拒绝启动，避免同名能力被静默替换。
   *
   * @param handlers 代码注册的业务能力，无能力时允许管理流程草稿，但发布和发起失败关闭
   */
  public WorkflowRuntime(
      WorkflowEngineCommands engine,
      WorkflowRecords statements,
      WorkflowTransaction transaction,
      WorkflowPlatform platform,
      List<WorkflowBusiness> handlers,WorkflowContent content,WorkflowCompilation compilation) {
    this.compilation=compilation;
    this.content=content;
    this.engine = engine;
    this.statements = statements;
    this.tx = transaction;
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
        statements.openActorTask(c.tenantId(),taskId,c.actorId());
    if (rows.isEmpty()) return false;
    var t = rows.getFirst();
    var i = instance(c.tenantId(), str(t, "instance_id"), false);
    if (!"RUNNING".equals(i.get("state"))
        || !type.equals((String)i.get("object_type"))
        || !object.equals((String)i.get("object_id"))
        || version != number(i, "object_version")) return false;
    var model = definition(i);
    var n = model.node(str(t, "node_id"));
    var b = businesses.get(capability);
    return b != null
        && (write
            ? capability.equals(n.capability()) && b.capability().inputs().contains(field)
            : (capability.equals(n.capability()) || capability.equals(model.entryCapability()))
                && b.capability().outputs().contains(field))
        && resolve(i, n).contains(c.actorId())
        && n.people().stream().anyMatch(p->platform.candidate(c,str(i,"organization_id"),p.kind(),p.reference()));
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

  /** 数据契约身份来自业务声明；非单据能力不伪造契约。 */
  private com.appnexus.contracts.data.DataContract dataSchema(WorkflowBusiness business,String entry){
    if(!(business instanceof com.appnexus.contracts.businessobject.BusinessObjects.Provider provider))return null;
    return provider.descriptor().operations().stream().flatMap(a->a.interaction().capabilities().stream()).filter(c->c.service()==com.appnexus.contracts.businessobject.InteractionContract.Service.WORKFLOW&&c.reference().equals(entry)).findFirst().orElseThrow().schema();
  }

  /** 在流程匹配和实例写入前验证业务快照；字符串传输按声明显式解码，缺失和非法值失败关闭。 */
  private void validateContractSnapshot(WorkflowBusiness business,String entry,Map<String,String> snapshot){
    if(!(business instanceof com.appnexus.contracts.businessobject.BusinessObjects.Provider provider))return;
    var capability=provider.descriptor().operations().stream().flatMap(a->a.interaction().capabilities().stream()).filter(c->c.service()==com.appnexus.contracts.businessobject.InteractionContract.Service.WORKFLOW&&c.reference().equals(entry)).findFirst().orElseThrow();
    capability.schema().decode(snapshot);
  }

  /** 流程设计视图由统一单据行为声明投影生成，业务不再维护第二份工作流目录。 */
  private WorkflowBusinessProfile businessProfile(WorkflowBusiness business,String capability){
    if(!(business instanceof com.appnexus.contracts.businessobject.BusinessObjects.Provider provider))return null;
    var d=provider.descriptor();
    var a=d.operations().stream().filter(action->action.approval()!=null && action.approval().entry().equals(capability)).findFirst().orElse(null);
    if(a==null)return null;
    var contract=java.util.Objects.requireNonNull(a.approval(),"INTERACTION_WORKFLOW_CONTRACT_REQUIRED");
    return new WorkflowBusinessProfile(d.objectType(),contract.purpose(),d.nameZh(),d.nameEn(),"适用业务契约；触发策略在单据工作台配置。","Compatible business contract; configure triggers in the document workbench.",
      a.interaction().capabilities().stream().filter(c->c.service()==com.appnexus.contracts.businessobject.InteractionContract.Service.WORKFLOW).findFirst().orElseThrow().fields().stream().map(f->new WorkflowBusinessProfile.Field(f.key(),f.nameZh(),f.nameEn(),f.type(),f.unit(),f.unit())).toList(),
      contract.outcomes().stream().map(result->new WorkflowBusinessProfile.Outcome(result,contract.entry(),result,result)).toList(),
      contract.outcomes().contains("WITHDRAWN")?contract.entry():null);
  }

  /** 业务设计目录与真实选人选项；只读流程人员可查看，能力仍逐项按既有功能权限过滤。 */
  public Map<String, Object> configuration(WorkflowContext c) {
    require(c, "read");
    var documents = businesses(c).stream()
        .filter(b -> businessProfile(businesses.get(b.key()),b.key()) != null)
        .map(b -> Map.of("capability", b.key(), "profile", businessProfile(businesses.get(b.key()),b.key()), "dataContract", ((com.appnexus.contracts.businessobject.BusinessObjects.Provider)businesses.get(b.key())).descriptor().operations().stream().flatMap(a->a.interaction().capabilities().stream()).filter(cap->cap.service()==com.appnexus.contracts.businessobject.InteractionContract.Service.WORKFLOW&&cap.reference().equals(b.key())).findFirst().orElseThrow().schema()))
        .toList();
    return Map.of("documents", documents, "options", platform.configurationOptions(c));
  }

  /** 最多100个定义；支持按名称筛选，分页由HTTP约束。 */
  public List<Map<String, Object>> definitions(WorkflowContext c) {
    require(c, "read");
    return statements.definitions(c.tenantId());
  }

  /** 校验草稿，不部署；输入输出和权限由当前能力目录再校验。 */
  public List<String> validate(WorkflowContext c, WorkflowModel m) {
    require(c, "edit");
    return validateInternal(c, m);
  }

  /**
   * 从可信代码目录取得能力及业务视图后委托纯应用校验，客户端不能提供能力白名单。
   * @param c 已经入口权限校验的上下文；能力目录仍复核主体有效性
   * @param m 待校验草稿，可空以保持原标准错误集合
   * @return 当前只读校验错误，不创建部署、任务或业务解析
   */
  private List<String> validateInternal(WorkflowContext c, WorkflowModel m) {
    var caps = businesses(c);
    var profiles = new HashMap<String, WorkflowBusinessProfile>();
    if (m != null) {
      var entry = businesses.get(m.entryCapability());
      var profile = entry == null ? null : businessProfile(entry, m.entryCapability());
      if (profile != null) profiles.put(m.entryCapability(), profile);
    }
    return com.appnexus.workflow.application.WorkflowModelValidation.validate(m, caps, profiles);
  }

  /** 草稿乐观锁保存；相同请求重放返回原结果，不覆盖已发布版本。 */
  public Object save(WorkflowContext c, String id, long revision, WorkflowModel m) {
    require(c, "edit");
    identifier(id);
    if (m == null
        || m.nodes() == null
        || m.nodes().size() > 100
        || content.encode(m).length() > 262144) throw fail("MODEL_INVALID", 400);
    return mutate(
        c,
        "SAVE:" + id + ":" + revision + ":" + content.encode(m),
        () -> {
          if (revision < 0) {
            statements.createDefinition(c.tenantId(),id,content.encode(m));
            revisionAudit(c, "DRAFT_CREATED", id);
            return Map.of("revision", 0);
          }
          if (statements.updateDraft(content.encode(m),c.tenantId(),id,revision)
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
              required(statements.lockDefinition(c.tenantId(),id));
          if (number(row, "revision") != revision) throw fail("VERSION_CONFLICT", 409);
          var model = model(row);
          var errors = validateInternal(c, model);
          if (!errors.isEmpty()) throw fail(errors.getFirst().split(":")[0], 400);
          int version = (int) number(row, "published") + 1;
          String key = "wf_" + id.replace("-", "") + "_" + version;
          byte[] bpmn = compilation.compile(key, model);
          String definition=engine.deploy(c.tenantId(),key,bpmn);
          statements.createPublishedVersion(c.tenantId(),id,version,content.encode(model),new String(bpmn, StandardCharsets.UTF_8),definition,c.actorId(),dataSchema(businesses.get(model.entryCapability()),model.entryCapability())==null?null:content.encode(dataSchema(businesses.get(model.entryCapability()),model.entryCapability())));
          statements.activateVersion(version,c.tenantId(),id);
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
          if (statements.disableDefinition(c.tenantId(),id,revision)
              != 1) throw fail("VERSION_CONFLICT", 409);
          audit(c, null, "DISABLED", id, reason);
          return Map.of("revision", revision + 1);
        });
  }

  /** 发布版本用于比较，历史只读；不暴露原生引擎部署接口。 */
  public List<Map<String, Object>> versions(WorkflowContext c, String id) {
    require(c, "read");
    return statements.definitionVersions(c.tenantId(),id);
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
    if(businesses.get(entry) instanceof com.appnexus.contracts.businessobject.BusinessObjects.Provider)
      throw fail("BUSINESS_OBJECT_BINDING_REQUIRED",409);
    return startInternal(c,objectType,objectId,objectVersion,organization,purpose,entry,null);
  }

  private Object startInternal(WorkflowContext c,String objectType,String objectId,long objectVersion,
      String organization,String purpose,String entry,String resolution) {
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
            + entry + ":" + Objects.toString(resolution,"orchestration"),
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
          validateContractSnapshot(business,entry,snapshot);
          var bound = resolution==null?null:documentBindings.get().verifyApproval(
              new com.appnexus.contracts.businessobject.BusinessObjects.Context(c.tenantId(),c.actorId(),c.requestId()),
              resolution,objectType,objectId,objectVersion,snapshot,purpose,entry);
          var rows =
              statements.publishedCandidates(c.tenantId(),bound==null?null:bound.definitionVersion());
          var matching =
              rows.stream()
                  .filter(
                      row -> {
                        var m = model(row);
                        return (bound==null||bound.definitionId().equals((String)row.get("definition_id")))
                            && m.objectType().equals(objectType)
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
          var currentSchema=dataSchema(business,entry);
          Object frozenSchema=selected.get("data_contract_schema");
          if(frozenSchema!=null&&!content.schema(frozenSchema.toString()).equals(currentSchema))throw fail("DATA_CONTRACT_CHANGED",409);
          String id = UUID.randomUUID().toString();
          platform.authorizeRecordWrite(c,"workflow:start",id,c.actorId(),objectId);
          statements.createInstance(c.tenantId(),id,(String)selected.get("definition_id"),((Number)selected.get("version")).intValue(),objectType,objectId,objectVersion,purpose,organization,c.actorId(),hash(objectType + ":" + objectId + ":" + purpose),content.encode(snapshot),currentSchema==null?null:content.encode(currentSchema));
          if (m.copies() != null)
            for (var rule : m.copies())
              for (var actor :
                  platform.resolve(c.tenantId(), organization, rule.kind(), rule.reference()))
                if (platform.active(c.tenantId(), actor))
                  statements.copyActor(c.tenantId(),id,actor);
          var vars = new HashMap<String, Object>();
          vars.put("wf_id", id);
          vars.put("wf_fields", new HashMap<>(snapshot));
          vars.put("wf_result", "APPROVED");
          String process=engine.start(String.valueOf(selected.get("engine_definition")),id,vars);
          statements.bindEngineInstance(process,c.tenantId(),id);
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
      return statements.copiedInstances(c.tenantId(),c.actorId());
    if (view.equals("started"))
      return statements.participantInstances(c.tenantId(),c.actorId());
    return statements.actorTasks(c.tenantId(),c.actorId(),view.equals("todo"));
  }

  /** 运维列表不返回单据原文；详细业务数据仍由业务端单独授权读取。 */
  public List<Map<String, Object>> instances(WorkflowContext c) {
    require(c, "operate");
    return statements.startedInstances(c.tenantId());
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
        statements.instanceTasks(c.tenantId(),id));
    result.put(
        "history",
        statements.instanceAudit(c.tenantId(),id));
    if (platform.allowed(c, "workflow:operate"))
      result.put(
          "jobs",
          statements.instanceJobs(c.tenantId(),id));
    result.put(
        "relatedInstances",
        statements.objectInstances(c.tenantId(),(String)row.get("object_type"),(String)row.get("object_id"),(String)row.get("purpose"),c.actorId(),platform.allowed(c, "workflow:operate"),c.actorId(),c.actorId()));
    return result;
  }

  /** 任务只返回业务能力声明，不返回原始单据内容；业务页面仍执行字段和强制限制。 */
  public Map<String, Object> task(WorkflowContext c, String taskId) {
    requireActive(c);
    var t = required(statements.taskRecord(c.tenantId(),taskId));
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
        "TASK:" + taskId + ":" + action + ":" + content.encode(values) + ":" + comment,
        () -> {
          var service=new com.appnexus.workflow.application.TaskCompletionService(new com.appnexus.workflow.application.TaskCompletionPort(){
            private Map<String,Object> instance;
            private WorkflowModel.Node node;
            private String nodeId;
            @Override public Task lock(WorkflowContext context,String id){
              var task=required(statements.taskForCompletion(context.tenantId(),id));
              instance=instance(context.tenantId(),str(task,"instance_id"),true);
              task=required(statements.lockTaskForCompletion(context.tenantId(),id));
              // 模型仅在状态通过后加载，避免已关闭实例改变原拒绝顺序。
              node=null;nodeId=str(task,"node_id");
              return new Task(str(instance,"id"),str(instance,"state"),str(task,"state"),str(task,"actor"));
            }
            @Override public boolean assigned(WorkflowContext context,Task task){
              node=definition(instance).node(nodeId);
              return resolve(instance,node).contains(context.actorId())&&node.people().stream().anyMatch(person->platform.candidate(context,str(instance,"organization_id"),person.kind(),person.reference()));
            }
            @Override public WorkflowModel.Node node(Task task){return node;}
            @Override public boolean basisUnchanged(WorkflowContext context,Task task,String id){
              var business=businesses.get(definition(instance).entryCapability());
              if(business==null)throw fail("CAPABILITY_UNAVAILABLE",409);
              return outputs(business,business.snapshot(invocation(context,instance,id,context.requestId(),"SNAPSHOT",Map.of())))
                  .equals(content.fields(str(instance,"snapshot")));
            }
            @Override public Set<String> inputs(String capability){
              var business=businesses.get(capability);if(business==null)throw fail("CAPABILITY_UNAVAILABLE",409);
              return business.capability().inputs();
            }
            @Override public Map<String,String> execute(WorkflowContext context,Task task,String id,String operation,Map<String,String> input){
              var business=businesses.get(node.capability());
              var result=business.execute(invocation(context,instance,id,context.requestId(),operation,input));
              if(result.pending())throw fail("BUSINESS_CONFIRMATION_REQUIRED",409);
              return outputs(business,result.outputs());
            }
            @Override public void save(WorkflowContext context,Task task,String id,String opinion){
            platform.authorizeTaskWrite(context,id);
            statements.saveTaskComment(opinion,context.tenantId(),id,context.actorId());
            audit(context, str(instance, "id"), "TASK_SAVED", id, "");

            }
            @Override public void complete(WorkflowContext context,Task task,String id,String operation,String opinion,Map<String,String> output){
          var fields = fields(instance);
          fields.putAll(output);
          fields.put(node.id() + "_decision", operation);
          engine.fields(str(instance, "engine_id"),fields);
          if (operation.equals("REJECT"))
            engine.result(str(instance, "engine_id"),"REJECTED");
          platform.authorizeTaskWrite(context,id);
          statements.completeTask(operation,opinion,context.tenantId(),id,context.actorId());
          audit(context, str(instance, "id"), "TASK_COMPLETED", id, operation);
          if (operation.equals("REJECT")) {
            finish(instance, "REJECTED");
            engine.terminate(str(instance, "engine_id"), "REJECTED");
            statements.cancelOpenJobs(context.tenantId(),(String)instance.get("id"));
          } else engine.complete(id);
          reconcileTasks(context.tenantId(), str(instance, "id"));

            }
          });
          try{return service.complete(c,taskId,action,values,comment);}
          catch(com.appnexus.contracts.workflow.WorkflowRejection rejection){throw fail(rejection.code(),rejection.status());}
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
          var service=new com.appnexus.workflow.application.TaskReassignmentService(new com.appnexus.workflow.application.TaskReassignmentPort(){
            private Map<String,Object> t,i;
            private WorkflowModel.Node n;
            @Override public Task lock(WorkflowContext context,String id){
          platform.authorizeRecordWrite(context,"workflow:operate",taskId);
          t = required(statements.taskForReassignment(c.tenantId(),taskId));
          i = instance(c.tenantId(), str(t, "instance_id"), true);
          // 锁实例后重读任务；等待锁的另一请求不能沿用锁前OPEN快照重复调用业务。
          t =
              required(statements.lockTaskForReassignment(c.tenantId(),taskId));
          n = definition(i).node(str(t, "node_id"));

              return new Task("OPEN".equals(t.get("state")),(String)t.get("actor"));
            }
            @Override public boolean candidate(WorkflowContext context,String target){return resolve(i,n).contains(target);}
            @Override public boolean duplicate(WorkflowContext context,String id,String target){
              return statements.duplicateAssignmentCount(c.tenantId(),(String)i.get("id"),n.id(),actor,taskId)
              > 0;

            }
            @Override public void assign(WorkflowContext context,String id,String target,String previous,String cause){
          engine.assign(taskId, actor);
          statements.assignTask(actor,c.tenantId(),taskId);
          auditAssignment(
              c, str(i, "id"), "REASSIGNED", taskId, reason, previous, actor);

            }
          });
          try{return service.assign(c,taskId,actor,reason);}
          catch(com.appnexus.contracts.workflow.WorkflowRejection error){throw fail(error.code(),error.status());}
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
              required(statements.lockRetryJob(c.tenantId(),id));
          if (!Set.of("MANUAL", "TIMEOUT").contains(str(job, "state")))
            throw fail("JOB_NOT_MANUAL", 409);
          statements.retryJob(c.tenantId(),id);
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
          engine.terminate(str(i, "engine_id"), "WITHDRAWN");
          statements.cancelInstanceJobs(c.tenantId(),id);
          reconcileTasks(c.tenantId(), id);
          audit(c, id, "WITHDRAWN", id, reason);
          return Map.of("withdrawn", true);
        });
  }

  /** 每次发起或运行节点从冻结版本读取模型，不受后续草稿修改影响。 */
  WorkflowModel definition(Map<String, Object> instance) {
    return model(
        required(statements.frozenDefinition((String)instance.get("tenant_id"),(String)instance.get("definition_id"),((Number)instance.get("definition_version")).intValue())));
  }

  public List<String> people(String tenant,String instanceId,String node) {
    var i = instance(tenant,instanceId,false);
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

  public void created(String tenant,String id,String taskId,String node,String assigned) {
    var i = instance(tenant, id, false);
    var n = definition(i).node(node);
    String actor = assigned;
    if ("__UNASSIGNED__".equals(actor)) {
      engine.assign(taskId,null);
      actor = null;
    }
    statements.createTask(tenant,taskId,id,n.id(),n.name(),actor,Instant.now().plusSeconds(n.timeoutSeconds()));
    auditAssignment(
        new WorkflowContext(tenant, str(i, "starter"), UUID.randomUUID().toString()),
        id,
        actor == null ? "NO_ASSIGNEE" : "TASK_CREATED",
        taskId,
        "",
        null,
        actor);
  }

  public void action(String tenant,String id,String activity,String execution) {
    var i = instance(tenant, id, false);
    var n = definition(i).node(activity);
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
        execution,
        n.capability(),
        n.kind().name(),
        values,
        n.retries() + 1,
        n.timeoutSeconds());
  }

  public void ended(String tenant,String id,String result) {
    var i = instance(tenant, id, false);
    if (!"RUNNING".equals(i.get("state"))) return;
    finish(i, result);
  }

  private void finish(Map<String, Object> i, String result) {
    statements.endInstance(result,(String)i.get("tenant_id"),(String)i.get("id"));
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
    statements.createDeliveryJob((String)i.get("tenant_id"),UUID.randomUUID().toString(),(String)i.get("id"),node,execution,capability,kind,content.encode(payload),attempts,Instant.now().plusSeconds(timeout));
  }

  /** 交付引用按固定发布版本和权威实例复核，队列表中的能力键与结果不能独立授权。 */
  private WorkflowBusiness deliveryBusiness(Map<String,Object> job,Map<String,Object> instance,Map<String,String> payload){
    var model=definition(instance);
    String capability=str(job,"capability");
    String kind=str(job,"kind");
    if("TERMINAL".equals(kind)){
      String result=str(instance,"result");
      if(model.mode()!=Mode.APPROVAL||!"ENDED".equals(str(instance,"state"))||result==null
          ||model.terminalActions()==null||!Objects.equals(capability,model.terminalActions().get(result))
          ||!payload.equals(Map.of("result",result)))throw new SecurityException("WORKFLOW_DELIVERY_DENIED");
    }else{
      var node=model.node(str(job,"node_id"));
      if(node==null||!Objects.equals(capability,node.capability())||!node.kind().name().equals(kind))
        throw new SecurityException("WORKFLOW_DELIVERY_DENIED");
    }
    var business=businesses.get(capability);
    if(business==null)throw new IllegalArgumentException("CAPABILITY_UNAVAILABLE");
    return business;
  }

  /** 领取后在事务外调用业务；成功回写与引擎推进同事务。租约令牌防止过期执行者覆盖新结果。 宕机后可再次执行，因此业务适配器必须按job.id持久化幂等，不能承诺外部副作用恰好一次。 */
  public boolean dispatch() {
    Map<String, Object> job =
        tx.required(()-> {
              var rows =
                  statements.lockDueJob();
              if (rows.isEmpty()) return null;
              var j = rows.getFirst();
              if (number(j, "attempts") >= number(j, "max_attempts")) {
                statements.markLeaseExhausted((String)j.get("tenant_id"),(String)j.get("id"));
                return null;
              }
              String token = UUID.randomUUID().toString();
              statements.claimJobLease(token,(String)j.get("tenant_id"),(String)j.get("id"));
              j.put("lease_token", token);
              return j;
            });
    if (job == null) return false;
    WorkflowBusiness.Result result = null;
    String error = null;
    boolean businessError = false;
    try {
      var i = instance(str(job, "tenant_id"), str(job, "instance_id"), false);
      var payload=content.fields(str(job,"payload"));
      var business=deliveryBusiness(job,i,payload);
      var c = new WorkflowContext(str(i, "tenant_id"), str(i, "starter"), str(job, "id"));
      result =
          business.execute(
              invocation(
                  c, i, null, str(job, "id"), "EXECUTE", payload));
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
    tx.run(()-> {
          var j =
              required(statements.lockDispatchedJob((String)job.get("tenant_id"),(String)job.get("id")));
          if (!"RUNNING".equals(j.get("state"))
              || !job.get("lease_token").equals(j.get("lease_token"))) return;
          if (completed != null) {
            if (completed.pending())
              statements.awaitCallback(content.encode(completed.outputs()),(String)j.get("tenant_id"),(String)j.get("id"));
            else acknowledge(j, completed.outputs());
          } else
            statements.scheduleRetry(manual || number(j, "attempts") >= number(j, "max_attempts") ? "MANUAL" : "READY",code,(String)j.get("tenant_id"),(String)j.get("id"));
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
    tx.run(()-> {
          var j = required(statements.lockCallbackJob(tenant,jobId));
          if (!capability.equals(j.get("capability"))) throw fail("CALLBACK_DENIED", 403);
          var b = businesses.get(capability);
          if (b == null) throw fail("CAPABILITY_UNAVAILABLE", 409);
          var checked = outputs(b, outputs);
          if ("DONE".equals(j.get("state"))) {
            if (!content.fields(str(j, "result")).equals(checked))
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
      engine.fields(str(i, "engine_id"),fields);
      engine.trigger(str(j, "execution_id"));
    }
    statements.completeDelivery(content.encode(output),(String)j.get("tenant_id"),(String)j.get("id"));
    audit(
        new WorkflowContext(str(j, "tenant_id"), str(i, "starter"), str(j, "id")),
        str(i, "id"),
        "ACTION_COMPLETED",
        str(j, "id"),
        "");
  }

  /** 超时不自动通过；到期交付转人工，任务到期写告警事实但不改变业务决定。 */
  public void maintain() {
    tx.run(()-> {
          statements.auditExpiredAssignment();
          statements.timeoutJobs();
          var rows =
              statements.tasksForMaintenance();
          for (var t : rows) {
            statements.markTaskChecked((String)t.get("tenant_id"),(String)t.get("id"));
            var i = instance(str(t, "tenant_id"), str(t, "instance_id"), true);
            if (!"RUNNING".equals(i.get("state"))) continue;
            var n = definition(i).node(str(t, "node_id"));
            String actor = (String) t.get("actor");
            if (actor != null && !resolve(i, n).contains(actor)) {
              engine.assign(str(t, "id"), null);
              statements.unassignTask((String)t.get("tenant_id"),(String)t.get("id"));
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
        statements.openInstanceTasks(tenant,id))
      if (!engine.taskExists(tenant,str(row,"id")))
        statements.cancelTask(tenant,(String)row.get("id"));
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
          required(statements.taskNodeForAction(c.tenantId(),task));
      var node = definition(i).node(str(taskRow, "node_id"));
      workItemKey = node.separate() ? task : str(i, "id") + ":" + node.id();
    }
    String nodeId = null;
    if (task != null)
      nodeId =
          str(
              required(statements.taskNodeForCallback(c.tenantId(),task)),
              "node_id");
    else {
      var jobs =
          statements.deliveryNode(c.tenantId(),key);
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
        && engine.running(engineId))
      return new TreeMap<>(
          engine.fields(engineId));
    return new TreeMap<>(content.fields(str(i, "snapshot")));
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
    if (statements.copyCount(c.tenantId(),(String)i.get("id"),c.actorId())
        > 0) return;
    if (statements.participantTaskCount(c.tenantId(),(String)i.get("id"),c.actorId())
        == 0) throw fail("INSTANCE_DENIED", 403);
  }

  private Map<String, Object> instance(String tenant, String id, boolean lock) {
    return required(statements.instanceRecord(tenant,id,lock));
  }

  private Map<String,Object> required(List<Map<String,Object>> rows){if(rows.size()!=1)throw fail("WORKFLOW_NOT_FOUND",404);return rows.getFirst();}

  private WorkflowModel model(Map<String, Object> row) {
    return content.model(str(row, "model"));
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
    String hash = hash(Objects.toString(c.personId(),"")+"|"+Objects.toString(c.organizationId(),"")+"|"+fingerprint);
    return tx.required(()-> {
          statements.registerRequest(c.tenantId(),c.actorId(),c.requestId(),hash);
          var row =
              required(statements.lockRequest(c.tenantId(),c.actorId(),c.requestId()));
          if (!hash.equals(row.get("fingerprint"))) throw fail("IDEMPOTENCY_CONFLICT", 409);
          if (row.get("response") != null)
            return content.response(str(row, "response"));
          Object result = work.get();
          statements.saveRequestResponse(content.encode(result),c.tenantId(),c.actorId(),c.requestId());
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
    statements.auditAssignment(c.tenantId(),instance,c.actorId(),action,target,reason,previous,next);
  }

  private void revisionAudit(WorkflowContext c, String action, String id) {
    audit(c, null, action, id, "");
  }

  /** 审计记录真实执行账号和已选业务人员，不把流程分配账号解释为人员唯一身份。 */
  private void audit(WorkflowContext c,String instance,String action,String target,String detail){
    statements.auditOperation(c.tenantId(),instance,c.actorId(),action,target,detail,c.personId(),c.organizationId());
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

  private static WorkflowRejection fail(String code, int status) {
    return new WorkflowRejection(code, status);
  }
}
