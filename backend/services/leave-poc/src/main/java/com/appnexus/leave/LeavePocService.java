package com.appnexus.leave;

import com.appnexus.contracts.workflow.*;
import com.appnexus.contracts.businessobject.*;
import com.appnexus.contracts.businessobject.BusinessObjects.Context;
import static com.appnexus.contracts.businessobject.ObjectRecordMapping.*;
import com.appnexus.contracts.businessobject.BusinessObjects.Field;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 请假 POC 业务存储与工作流适配；只依赖公开契约，提交后冻结依据，不计算余额或考勤。 */
public final class LeavePocService implements com.appnexus.contracts.businessobject.BusinessObjects.Provider {
  /** 页面提交、业务 API 和工作台共用行为；权限引用不等同于启动流程。 */
  public static final com.appnexus.contracts.businessobject.BusinessObjects.Operation SUBMIT_OPERATION =
      new com.appnexus.contracts.businessobject.BusinessObjects.Operation(
          "submit", "提交", "Submit", "leave.request:submit", "LeaveRequest.submit",
          new com.appnexus.contracts.businessobject.BusinessObjects.PageOperation(
              "workflow/leave", "/workflow/leave", "请假单 POC", "Leave request POC",
              "POST", "/api/leave-poc/{id}/submit"),
          List.of(),
          new InteractionContract("LeaveRequest.submit", 1, "LeaveRequest", "submit",
              "提交", "Submit", InteractionContract.Trigger.USER_ACTION,
              List.of(
                  new InteractionContract.Capability(InteractionContract.Service.BUSINESS,
                      InteractionContract.Completion.SYNCHRONOUS, "LeaveRequest.submit", "",
                      List.of(), Set.of(), "LeaveRequest.submit.data", 1),
                  new InteractionContract.Capability(InteractionContract.Service.WORKFLOW,
                      InteractionContract.Completion.CALLBACK, "leavePoc", "approval",
                      List.of(
                          new Field("days", "请假天数", "Leave days", "NUMBER", "天",true),
                          new Field("requiresHr", "是否超过三天", "More than three days", "BOOLEAN", "",true)),
                      Set.of("APPROVED", "REJECTED", "WITHDRAWN"), "leave.approval", 1))));

  /** 列表和详情打印共享业务动作；字段投影同时用于打印目录，不能独立维护另一份。 */
  public static final com.appnexus.contracts.businessobject.BusinessObjects.Operation PRINT_OPERATION =
      new com.appnexus.contracts.businessobject.BusinessObjects.Operation(
          "print", "打印", "Print", "leave.request:print", "LeaveRequest.print",
          new com.appnexus.contracts.businessobject.BusinessObjects.PageOperation(
              "workflow/leave", "/workflow/leave", "请假列表", "Leave list",
              "POST", "/api/business-object-executions"),
          List.of(new com.appnexus.contracts.businessobject.BusinessObjects.OperationAlias(
              "LeaveRequest.detail.print", new com.appnexus.contracts.businessobject.BusinessObjects.PageOperation(
                  "workflow/leave", "/workflow/leave", "请假详情", "Leave details",
                  "POST", "/api/business-object-executions"))),
          new InteractionContract("LeaveRequest.print", 1, "LeaveRequest", "print",
              "打印", "Print", InteractionContract.Trigger.USER_ACTION,
              List.of(new InteractionContract.Capability(InteractionContract.Service.PRINT,
                  InteractionContract.Completion.CONFIRMATION, "leave.request.v1", "",
                  List.of(
                      new Field("documentNumber", "单据标识", "Document ID", "TEXT", "",true),
                      new Field("startDate", "开始日期", "Start date", "TEXT", "",true),
                      new Field("endDate", "结束日期", "End date", "TEXT", "",true),
                      new Field("days", "自然日天数", "Calendar days", "NUMBER", "天",true),
                      new Field("state", "业务状态", "Business state", "TEXT", "",true)), Set.of(), "leave.request.v1", 1))));
  private Supplier<com.appnexus.contracts.printing.BusinessPrinting> printing;
  /** 延迟公开打印端口，避免装配环；打印不可用时不伪造成功。 */
  public void printing(Supplier<com.appnexus.contracts.printing.BusinessPrinting> printing){this.printing=printing;}
  /** 业务授权投影不包含原因原文或人员敏感信息；打印服务按已发布场景再次校验。 */
  public Map<String,Object> print(WorkflowContext c,String id,String operation){
    active(c);if(!platform.allowed(c,PRINT_OPERATION.permissionCode())||!(PRINT_OPERATION.operationId().equals(operation)||PRINT_OPERATION.aliases().stream().anyMatch(a->a.operationId().equals(operation))))throw new SecurityException("LEAVE_ACCESS_DENIED");
    var row=row(c,id,false);if(!c.actorId().equals(row.get("applicant")))throw new SecurityException("LEAVE_ACCESS_DENIED");
    return execution.get().execute(objectContext(c),new com.appnexus.contracts.businessobject.BusinessObjectExecution.Request(
        "LeaveRequest",id,"print",operation,((Number)row.get("basis_version")).longValue())).output();
  }
  private Supplier<com.appnexus.contracts.businessobject.BusinessObjectExecution> execution;
  /** 注入对象公共执行入口；缺少装配时不回退到对象专用能力接线。 */
  public void execution(Supplier<com.appnexus.contracts.businessobject.BusinessObjectExecution> execution){this.execution=Objects.requireNonNull(execution);}
  private static com.appnexus.contracts.businessobject.BusinessObjects.Context objectContext(WorkflowContext c){
    return new com.appnexus.contracts.businessobject.BusinessObjects.Context(c.tenantId(),c.actorId(),c.requestId(),c.personId(),c.organizationId());
  }
  private BusinessObjectRuntime runtime;
  /** 固定映射由对象所有者代码声明，不进入租户配置；所有能力共用同一运行器。 */
  public void runtimeFactory(ObjectRecordRuntimeFactory factory){runtime=factory.create(this,recordMapping(),this::beforeCapability);}
  @Override public BusinessObjectRuntime runtime(){return Objects.requireNonNull(runtime,"OBJECT_RUNTIME_NOT_ASSEMBLED");}
  /** 请假现有文本身份表映射；派生是否人事审批只使用受控数值比较，不支持脚本。 */
  public static ObjectRecordMapping recordMapping(){
    var text=Encoding.TEXT;
    return new ObjectRecordMapping("leave_poc_request",new Column("tenant_id",text),new Column("id",text),new Column("organization_id",text),new Column("applicant",text),
      "basis_version","state","instance_id","last_delivery_id",new Column("person_id",text),Map.of(
        "days",new Value(new Column("days",text),Transform.VALUE,0),"requiresHr",new Value(new Column("days",text),Transform.GREATER_THAN,3),
        "documentNumber",new Value(new Column("id",text),Transform.VALUE,0),"startDate",new Value(new Column("start_date",text),Transform.DATE_TEXT,0),
        "endDate",new Value(new Column("end_date",text),Transform.DATE_TEXT,0),"state",new Value(new Column("state",text),Transform.VALUE,0)),
      Map.of("submit",new Transition(Set.of("SUBMITTED"),"ACCEPTED",true),"print",new Transition(Set.of("DRAFT","SUBMITTED","ACCEPTED","APPROVED","REJECTED","WITHDRAWN"),null,false)),
      new Receipt("leave_poc_approval_cycle",new Column("tenant_id",text),new Column("object_id",text),"basis_version","instance_id","delivery_id","result","completed_at"),false);
  }
  /** 主管资格属于请假提交规则，只有实际选择工作流时检查，不阻断直接受理和草稿。 */
  public void beforeCapability(com.appnexus.contracts.businessobject.BusinessObjects.Context context,String id,String operation,InteractionContract.Service service){
    if(service!=InteractionContract.Service.WORKFLOW)return;
    var c=new WorkflowContext(context.tenantId(),context.actorId(),context.requestId(),context.personId(),context.organizationId());
    var row=row(c,id,true);String organization=row.get("organization_id").toString();
    if(platform.documentOrganizations(c).stream().noneMatch(o->o.id().equalsIgnoreCase(organization)))throw new com.appnexus.contracts.businessobject.BusinessObjects.Rejection(409,"LEAVE_ORGANIZATION_UNAVAILABLE");
    if(platform.resolve(c.tenantId(),organization,"LEADER","").isEmpty())throw new com.appnexus.contracts.businessobject.BusinessObjects.Rejection(409,"LEAVE_ORGANIZATION_LEADER_REQUIRED");
  }
  /** 当前主体可用行为视图，不持久化权限判断；写入仍重新检查。 */
  public record AvailableOperation(String operationCode,String nameZh,String nameEn,String permissionCode,String operationId,boolean allowed,String reasonCode) {}
  /** 页面只依赖业务动作声明及有效授权，不用工作流设计/发起能力猜测提交资格。 */
  public List<AvailableOperation> actions(WorkflowContext c){active(c);var result=new ArrayList<AvailableOperation>();for(var a:List.of(SUBMIT_OPERATION,PRINT_OPERATION)){
    var operations=new ArrayList<String>();operations.add(a.operationId());a.aliases().forEach(alias->operations.add(alias.operationId()));
    for(String key:operations){boolean permission=platform.allowed(c,a.permissionCode());boolean relation=permission&&bindings.get().pageOperationAllowed(new com.appnexus.contracts.businessobject.BusinessObjects.Context(c.tenantId(),c.actorId(),c.requestId()),"LeaveRequest",a.operationCode(),key);
      result.add(new AvailableOperation(a.operationCode(),a.nameZh(),a.nameEn(),a.permissionCode(),key,permission&&relation,!permission?"OPERATION_PERMISSION_MISSING":!relation?"PAGE_RELATION_DISABLED":"READY"));}
  }return result;}
  private Supplier<com.appnexus.contracts.businessobject.BusinessObjectBindings> bindings;
  /** 单据级可执行性解释只面向本人，任务原文权限不能扩成操作权；最终写入仍重查。 */
  public List<com.appnexus.contracts.businessobject.BusinessObjects.Availability> availability(WorkflowContext c,String id){
    active(c);var row=row(c,id,false);if(!c.actorId().equals(row.get("applicant")))throw new SecurityException("LEAVE_ACCESS_DENIED");
    return actions(c).stream().map(a->com.appnexus.contracts.businessobject.BusinessObjectOperationAccess.evaluate(a.operationCode(),a.operationId(),
      !a.reasonCode().equals("OPERATION_PERMISSION_MISSING"),!a.reasonCode().equals("PAGE_RELATION_DISABLED"),!a.operationCode().equals("submit")||"DRAFT".equals(row.get("state")))).map(value->{
        if(!value.allowed()||bindings==null)return value;
        String status=bindings.get().configurationStatus(new com.appnexus.contracts.businessobject.BusinessObjects.Context(c.tenantId(),c.actorId(),c.requestId()),"LeaveRequest",value.operationCode(),LeaveContractData.facts(row));
        return new com.appnexus.contracts.businessobject.BusinessObjects.Availability(value.operationCode(),value.operationId(),status.equals("READY"),status);
      }).toList();
  }
  /** 单据关联时间线由业务事实及公开打印摘要组合，禁止跨模块读取工作流或打印表。 */
  public List<Map<String,Object>> timeline(WorkflowContext c,String id){
    active(c);var row=row(c,id,false);if(!c.actorId().equals(row.get("applicant")))throw new SecurityException("LEAVE_ACCESS_DENIED");
    var events=new ArrayList<Map<String,Object>>();
    events.add(Map.of("kind","BUSINESS_OBJECT_CREATED","at",eventTime(row.get("created_at")),"reference",id,"state",row.get("state")));
    for(var cycle:cycles(c,id))events.add(Map.of("kind","APPROVAL_STARTED","at",eventTime(cycle.get("created_at")),"reference",cycle.get("instance_id"),"state","BASIS_"+cycle.get("basis_version")));
    for(var result:statements.statement1(c.tenantId(),id))events.add(Map.of("kind","APPROVAL_APPLIED","at","","reference",result.get("instance_id"),"state",result.get("result")));
    if(printing!=null&&platform.allowed(c,"print.job:read"))for(var output:printing.get().history(new com.appnexus.contracts.printing.PrintContext(c.tenantId(),c.actorId(),c.requestId()),id)){
      events.add(Map.of("kind","PRINT_OUTPUT","at",eventTime(output.get("created_at")),"reference",output.get("id"),"state",output.get("state")));
      if(output.get("confirmedJobId")!=null)events.add(Map.of("kind","PRINT_DEVICE","at",eventTime(output.get("deliveryUpdatedAt")),"reference",output.get("confirmedJobId"),"state",Objects.toString(output.get("deliveryState"),"UNKNOWN")));
    }
    return events;
  }

  /** 注入公开解析端口，真实装配启用后禁止退回引擎自行选流程。 */
  public void bindings(Supplier<com.appnexus.contracts.businessobject.BusinessObjectBindings> bindings) {this.bindings=bindings;}

  /** 请假单据只声明已有业务能力，不生成表单或业务状态机。 */
  @Override public com.appnexus.contracts.businessobject.BusinessObjects.Descriptor descriptor() {
    return new com.appnexus.contracts.businessobject.BusinessObjects.Descriptor("LeaveRequest","请假单","Leave request","leave",2,com.appnexus.contracts.businessobject.BusinessObjects.Category.TRANSACTION,
      List.of(new com.appnexus.contracts.businessobject.BusinessObjects.Field("days","请假天数","Leave days","NUMBER","天",true),new com.appnexus.contracts.businessobject.BusinessObjects.Field("requiresHr","是否超过三天","More than three days","BOOLEAN","",true)),
      List.of(SUBMIT_OPERATION,PRINT_OPERATION),new com.appnexus.contracts.businessobject.BusinessObjects.DataAccess("TENANT",List.of(
        new com.appnexus.contracts.businessobject.BusinessObjects.DataResource("leave.request","","请假请求","Leave requests",List.of(
          protectedField("days","天数","Days",false),protectedField("requiresHr","人事审批条件","HR condition",false),protectedField("documentNumber","单据标识","Document number",true),
          protectedField("startDate","开始日期","Start date",true),protectedField("endDate","结束日期","End date",true),protectedField("state","状态","State",true)))),
        Map.of("submit",Set.of("ALL","ORGANIZATION","ORGANIZATION_SUBTREE"),"print",Set.of("ALL","ORGANIZATION","ORGANIZATION_SUBTREE"))));
  }
  /** 字段目录从同一对象声明提供；数值/布尔不开放字符串脱敏，隐藏必填依据将阻止能力执行。 */
  private static com.appnexus.contracts.businessobject.BusinessObjects.DataField protectedField(String key,String zh,String en,boolean text){
    return new com.appnexus.contracts.businessobject.BusinessObjects.DataField(key,zh,en,"TEXT",text?Set.of("READ_ONLY","MASKED","HIDDEN"):Set.of("READ_ONLY","HIDDEN"));
  }
  private final LeaveRecordMapper statements;
  private final TransactionTemplate tx;
  private final WorkflowPlatform platform;
  private final Supplier<WorkflowTaskAccess> tasks;

  /**
   * @param statements 业务数据源，只访问请假 POC 自有表
   * @param manager 与组合应用共享的事务管理器，提交失败时单据与流程同时回滚
   * @param platform 可信成员和功能权限桥接，每次请求重新复核
   * @param tasks 延迟取得有效任务授权端口，原文访问随待办结束失效
   */
  public LeavePocService(LeaveRecordMapper statements, PlatformTransactionManager manager,
      WorkflowPlatform platform, Supplier<WorkflowTaskAccess> tasks) {
    this.statements = statements;
    this.tx = new TransactionTemplate(manager);
    this.platform = platform;
    this.tasks = tasks;
  }

  /**
   * 请假依据输入；审批中冻结，撤回或驳回后可修改形成新依据版本并重提。
   * @param organizationId 单据组织 UUID，必填；主管按该组织解析
   * @param leaveType PERSONAL、ANNUAL 或 SICK，必填；不校验余额
   * @param startDate 首日，本地业务日期，必填，不转换时区
   * @param endDate 末日，包含当天；总计 1—365 个自然日
   * @param reason 请假原因，去除首尾空白后 1—2000 字符，不写入引擎变量
   */
  public record Input(String organizationId, String leaveType, LocalDate startDate,
      LocalDate endDate, String reason) {}

  /** 校验 3 天边界与自然日口径，禁止前端自行传入审批条件。 */
  public static int days(Input input) {
    Objects.requireNonNull(input);
    Objects.requireNonNull(input.startDate());
    Objects.requireNonNull(input.endDate());
    long days = ChronoUnit.DAYS.between(input.startDate(), input.endDate()) + 1;
    if (days < 1 || days > 365) throw new IllegalArgumentException("LEAVE_DATE_RANGE_INVALID");
    return (int) days;
  }

  /** 申请人只选择自身有效任职组织；不要求查看整个组织模块的权限。 */
  public List<WorkflowPlatform.Option> organizations(WorkflowContext c) {
    requireStart(c);
    return platform.documentOrganizations(c);
  }

  /**
   * 创建本人草稿；相同请求键同内容返回原单据，异内容拒绝。
   * @param c 服务端可信租户、操作者和必填 UUID 请求键
   * @param input 经服务端校验的业务字段；组织须配置有效主管
   * @return 本人单据，不包含任何其他租户信息
   */
  public Map<String, Object> create(WorkflowContext c, Input input) {
    requireStart(c);
    if(c.personId()==null||!Objects.equals(c.organizationId(),input.organizationId()))throw new com.appnexus.contracts.businessobject.BusinessObjects.Rejection(409,"PERSON_CONTEXT_REQUIRED");
    int count = days(input);
    UUID.fromString(input.organizationId());
    UUID.fromString(c.requestId());
    if (!Set.of("PERSONAL", "ANNUAL", "SICK").contains(input.leaveType())
        || input.reason() == null || input.reason().isBlank() || input.reason().trim().length() > 2000)
      throw new IllegalArgumentException("LEAVE_INPUT_INVALID");
    // 保存仅校验表单业务值及标识格式；组织任职与负责人是审批提交条件，不在草稿阶段提前执行。
    return tx.execute(s -> {
      String id = UUID.randomUUID().toString();
      // 唯一键序列化并发重放；ON DUPLICATE 不更新依据或状态。
      statements.statement2(c.tenantId(),id,c.actorId(),input.organizationId(),input.leaveType(),input.startDate(),input.endDate(),count,input.reason().trim(),c.requestId(),c.personId());
      var row = statements.statement3(c.tenantId(),c.actorId(),c.requestId());
      if (!Objects.equals(c.personId(),row.get("person_id"))||!input.organizationId().equals(row.get("organization_id"))
          || !input.leaveType().equals(row.get("leave_type"))
          || !input.startDate().toString().equals(row.get("start_date").toString())
          || !input.endDate().toString().equals(row.get("end_date").toString())
          || !input.reason().trim().equals(row.get("reason")))
        throw new IllegalArgumentException("LEAVE_IDEMPOTENCY_CONFLICT");
      return calendarDates(row);
    });
  }

  /** 本人最近 100 张单据；不因工作流管理员权限开放全租户请假原文。 */
  public List<Map<String, Object>> list(WorkflowContext c) {
    active(c);
    return statements.statement4(c.tenantId(),c.actorId(),c.personId()).stream().map(LeavePocService::calendarDates).toList();
  }

  /**
   * 原文读取；本人或当前有效待办的审批人才可访问。
   * @param taskId 当前待办 ID；本人读取可省略，历史任务不授予原文权限
   */
  public Map<String, Object> read(WorkflowContext c, String id, String taskId) {
    active(c);
    var row = row(c, id, false);
    if ((!c.actorId().equals(row.get("applicant"))||(row.get("person_id")!=null&&!Objects.equals(c.personId(),row.get("person_id")))) && !permitted(c, row, taskId))
      throw new SecurityException("LEAVE_ACCESS_DENIED");
    return row;
  }

  /** 按 submit 策略直接受理或发起审批；记录及状态同事务，重复提交返回原业务结果。 */
  public Map<String, Object> submit(WorkflowContext c, String id) {
    requireStart(c);
    bindings.get().verifyPageOperation(new com.appnexus.contracts.businessobject.BusinessObjects.Context(c.tenantId(),c.actorId(),c.requestId()),"LeaveRequest","submit",SUBMIT_OPERATION.operationId());
    return tx.execute(s -> {
      var row = row(c, id, true);
      if (!c.actorId().equals(row.get("applicant"))) throw new SecurityException("LEAVE_ACCESS_DENIED");
      if(!Objects.equals(c.personId(),row.get("person_id"))||!Objects.equals(c.organizationId(),row.get("organization_id")))throw new com.appnexus.contracts.businessobject.BusinessObjects.Rejection(409,"PERSON_CONTEXT_REQUIRED");
      if (!"DRAFT".equals(row.get("state"))) return row;
      statements.statement5(c.tenantId(),id);
      long basis=((Number)row.get("basis_version")).longValue();
      var executed=execution.get().execute(objectContext(c),new com.appnexus.contracts.businessobject.BusinessObjectExecution.Request("LeaveRequest",id,"submit",SUBMIT_OPERATION.operationId(),basis));
      var resolved=executed.resolution();
      statements.statement6(c.tenantId(),id,basis,resolved.id(),resolved.service());
      if("BUSINESS".equals(resolved.service()))return row(c,id,false);
      String instance=executed.output().get("instanceId").toString();
      statements.statement7(c.tenantId(),UUID.randomUUID().toString(),id,basis,instance,resolved.id(),resolved.configurationVersion(),row.get("leave_type"),row.get("start_date").toString(),row.get("end_date").toString(),row.get("days"),row.get("reason"),row.get("organization_id"));
      statements.statement8(instance,c.tenantId(),id);
      return row(c, id, false);
    });
  }

  /**
   * 本人可编辑草稿或驳回/撤回单据；提交中的审批依据冻结，乐观版本防止陈旧覆盖。
   * @param expectedBasisVersion 读取的非负依据版本，修改成功递增，旧审批结果不再适用
   */
  public Map<String,Object> update(WorkflowContext c,String id,long expectedBasisVersion,Input input) {
    requireStart(c);int count=days(input);
    if(!Set.of("PERSONAL","ANNUAL","SICK").contains(input.leaveType())||input.reason()==null||input.reason().isBlank()||input.reason().trim().length()>2000)throw new IllegalArgumentException("LEAVE_INPUT_INVALID");
    UUID.fromString(input.organizationId()); // 仅校验字段格式，不读取组织任职或负责人。
    return tx.execute(s->{var row=row(c,id,true);
      if(!c.actorId().equals(row.get("applicant")))throw new SecurityException("LEAVE_ACCESS_DENIED");
      if(!Set.of("DRAFT","REJECTED","WITHDRAWN").contains(row.get("state"))||expectedBasisVersion!=((Number)row.get("basis_version")).longValue())throw new IllegalArgumentException("LEAVE_BASIS_CHANGED");
      statements.statement9(input.organizationId(),input.leaveType(),input.startDate(),input.endDate(),count,input.reason().trim(),c.tenantId(),id);
      return row(c,id,false);});
  }

  /** 本人审批轮次历史，不返回历史原因原文，避免把公共关联查询变成数据泄露入口。 */
  public List<Map<String,Object>> cycles(WorkflowContext c,String id) {
    active(c);var row=row(c,id,false);if(!c.actorId().equals(row.get("applicant")))throw new SecurityException("LEAVE_ACCESS_DENIED");
    return statements.statement10(c.tenantId(),id);
  }

  private boolean permitted(WorkflowContext c, Map<String, Object> row, String taskId) {
    return taskId != null && platform.active(c.tenantId(), c.actorId())
        && tasks.get().permits(c, taskId, "LeaveRequest", row.get("id").toString(),
            ((Number) row.get("basis_version")).longValue(), "leavePoc", "days", false);
  }

  private Map<String, Object> row(WorkflowContext c, String id, boolean lock) {
    UUID.fromString(id);
    var rows = statements.statement11(c.tenantId(),id,lock);
    if (rows.isEmpty()) throw new SecurityException("LEAVE_ACCESS_DENIED");
    return calendarDates(rows.getFirst());
  }

  /** 历史时刻统一返回 UTC ISO 格式；没有事实时间留空，不按查询时间伪造。 */
  private static String eventTime(Object value){return value instanceof java.sql.Timestamp time?time.toInstant().toString():value==null?"":value.toString();}

  /** SQL DATE 是业务日期，序列化前转 ISO 日期字符串，禁止作为 UTC 时刻导致显示/编辑减一天。 */
  private static Map<String,Object> calendarDates(Map<String,Object> source) {
    var result=new LinkedHashMap<String,Object>(source);
    for(String key:List.of("start_date","end_date")) {
      Object value=result.get(key);
      if(value instanceof java.sql.Date date)result.put(key,date.toLocalDate().toString());
      else if(value instanceof LocalDate date)result.put(key,date.toString());
    }
    return result;
  }

  private void active(WorkflowContext c) {
    if (!platform.active(c.tenantId(), c.actorId())) throw new SecurityException("LEAVE_ACCESS_DENIED");
  }

  private void requireStart(WorkflowContext c) {
    if (!platform.allowed(c, SUBMIT_OPERATION.permissionCode())) throw new SecurityException("LEAVE_ACCESS_DENIED");
  }
}
