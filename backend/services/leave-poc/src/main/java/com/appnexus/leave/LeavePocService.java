package com.appnexus.leave;

import com.appnexus.contracts.workflow.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 请假 POC 业务存储与工作流适配；只依赖公开契约，提交后冻结依据，不计算余额或考勤。 */
public final class LeavePocService implements WorkflowBusiness {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final WorkflowPlatform platform;
  private final Supplier<WorkflowStarter> starter;
  private final Supplier<WorkflowTaskAccess> tasks;

  /**
   * @param jdbc 业务数据源，只访问请假 POC 自有表
   * @param manager 与组合应用共享的事务管理器，提交失败时单据与流程同时回滚
   * @param platform 可信成员和功能权限桥接，每次请求重新复核
   * @param starter 延迟取得公开发起端口，不允许替换成客户端 URL
   * @param tasks 延迟取得有效任务授权端口，原文访问随待办结束失效
   */
  public LeavePocService(JdbcTemplate jdbc, PlatformTransactionManager manager,
      WorkflowPlatform platform, Supplier<WorkflowStarter> starter, Supplier<WorkflowTaskAccess> tasks) {
    this.jdbc = jdbc;
    this.tx = new TransactionTemplate(manager);
    this.platform = platform;
    this.starter = starter;
    this.tasks = tasks;
  }

  /**
   * 请假依据输入；POC 不支持提交后修改。
   * @param organizationId 单据组织 UUID，必填；主管按该组织解析
   * @param leaveType PERSONAL、ANNUAL 或 SICK，必填；不校验余额
   * @param startDate 首日，本地业务日期，必填，不转换时区
   * @param endDate 末日，包含当天；总计 1—365 个自然日
   * @param reason 请假原因，去除首尾空白后 1—2000 字符，不写入引擎变量
   */
  public record Input(String organizationId, String leaveType, LocalDate startDate,
      LocalDate endDate, String reason) {}

  /** 只公开非敏感路由依据；原因原文通过受任务授权的独立接口读取。 */
  @Override
  public Capability capability() {
    return new Capability("leavePoc", "请假单 POC", "workflow:start", "/workflow/leave",
        Set.of(), Set.of("days", "requiresHr"));
  }

  /** 请假业务配置目录；只公开自然日和是否超过三天，不公开请假原因。 */
  @Override
  public WorkflowBusinessProfile configuration() {
    return new WorkflowBusinessProfile("LeaveRequest", "approval", "请假单", "Leave request",
        "提交请假单后启动审批，按单据所属组织确定主管；审批结果自动回写单据。",
        "Starts on submission. The document organization determines the supervisor; outcomes update the request.",
        List.of(new WorkflowBusinessProfile.Field("days", "请假天数", "Leave days", "NUMBER", "天", "days"),
            new WorkflowBusinessProfile.Field("requiresHr", "是否超过三天", "More than three days", "BOOLEAN", "", "")),
        List.of(new WorkflowBusinessProfile.Outcome("APPROVED", "leavePoc", "将请假单标记为已批准", "Mark request approved"),
            new WorkflowBusinessProfile.Outcome("REJECTED", "leavePoc", "将请假单标记为已驳回", "Mark request rejected"),
            new WorkflowBusinessProfile.Outcome("WITHDRAWN", "leavePoc", "将请假单标记为已撤回", "Mark request withdrawn")),
        "leavePoc");
  }

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
    int count = days(input);
    UUID.fromString(input.organizationId());
    UUID.fromString(c.requestId());
    if (platform.documentOrganizations(c).stream().noneMatch(o -> o.id().equalsIgnoreCase(input.organizationId())))
      throw new SecurityException("LEAVE_ORGANIZATION_DENIED");
    if (!Set.of("PERSONAL", "ANNUAL", "SICK").contains(input.leaveType())
        || input.reason() == null || input.reason().isBlank() || input.reason().trim().length() > 2000)
      throw new IllegalArgumentException("LEAVE_INPUT_INVALID");
    // POC 不允许用不存在的组织启动流程；组织负责人解析同时验证组织和有效任职。
    if (platform.resolve(c.tenantId(), input.organizationId(), "LEADER", "").isEmpty())
      throw new IllegalArgumentException("LEAVE_ORGANIZATION_LEADER_REQUIRED");
    return tx.execute(s -> {
      String id = UUID.randomUUID().toString();
      // 唯一键序列化并发重放；ON DUPLICATE 不更新依据或状态。
      jdbc.update("INSERT INTO leave_poc_request(tenant_id,id,applicant,organization_id,leave_type,"
          + "start_date,end_date,days,reason,create_key) VALUES(?,?,?,?,?,?,?,?,?,?)"
          + " ON DUPLICATE KEY UPDATE id=id", c.tenantId(), id, c.actorId(), input.organizationId(),
          input.leaveType(), input.startDate(), input.endDate(), count, input.reason().trim(), c.requestId());
      var row = jdbc.queryForMap("SELECT * FROM leave_poc_request WHERE tenant_id=? AND applicant=? AND create_key=?",
          c.tenantId(), c.actorId(), c.requestId());
      if (!input.organizationId().equals(row.get("organization_id"))
          || !input.leaveType().equals(row.get("leave_type"))
          || !input.startDate().toString().equals(row.get("start_date").toString())
          || !input.endDate().toString().equals(row.get("end_date").toString())
          || !input.reason().trim().equals(row.get("reason")))
        throw new IllegalArgumentException("LEAVE_IDEMPOTENCY_CONFLICT");
      return row;
    });
  }

  /** 本人最近 100 张单据；不因工作流管理员权限开放全租户请假原文。 */
  public List<Map<String, Object>> list(WorkflowContext c) {
    active(c);
    return jdbc.queryForList("SELECT * FROM leave_poc_request WHERE tenant_id=? AND applicant=? ORDER BY created_at DESC LIMIT 100",
        c.tenantId(), c.actorId());
  }

  /**
   * 原文读取；本人或当前有效待办的审批人才可访问。
   * @param taskId 当前待办 ID；本人读取可省略，历史任务不授予原文权限
   */
  public Map<String, Object> read(WorkflowContext c, String id, String taskId) {
    active(c);
    var row = row(c, id, false);
    if (!c.actorId().equals(row.get("applicant")) && !permitted(c, row, taskId))
      throw new SecurityException("LEAVE_ACCESS_DENIED");
    return row;
  }

  /** 提交与实例创建同事务；重复提交返回原实例，不改变审批依据或重复启动。 */
  public Map<String, Object> submit(WorkflowContext c, String id) {
    requireStart(c);
    return tx.execute(s -> {
      var row = row(c, id, true);
      if (!c.actorId().equals(row.get("applicant"))) throw new SecurityException("LEAVE_ACCESS_DENIED");
      if (!"DRAFT".equals(row.get("state"))) return row;
      jdbc.update("UPDATE leave_poc_request SET state='SUBMITTED' WHERE tenant_id=? AND id=?", c.tenantId(), id);
      String instance = starter.get().startInstance(c, "LeaveRequest", id, 0,
          row.get("organization_id").toString(), "approval", "leavePoc");
      jdbc.update("UPDATE leave_poc_request SET instance_id=? WHERE tenant_id=? AND id=?", instance, c.tenantId(), id);
      return row(c, id, false);
    });
  }

  /** 从业务存储重读依据；任务授权不隐式授予模块功能权限。 */
  @Override
  public Map<String, String> snapshot(Invocation i) {
    active(i.context());
    var row = checked(i, false);
    if (i.workflow() == null) {
      requireStart(i.context());
      if (!i.context().actorId().equals(row.get("applicant"))) throw new SecurityException("LEAVE_ACCESS_DENIED");
    } else if (!permitted(i.context(), row, i.taskId())) {
      throw new SecurityException("LEAVE_TASK_DENIED");
    }
    return Map.of("days", row.get("days").toString(), "requiresHr",
        Boolean.toString(((Number) row.get("days")).intValue() > 3));
  }

  /**
   * 人工动作只复核有效任务；终态由可信引擎关联独立交付，不能把某一级通过当整单通过。
   * @param i 可信公开调用；EXECUTE 必须包含匹配的终态实例引用，重复交付同事务去重
   * @return 同步完成，无流转输出；业务原文不进入结果变量
   */
  @Override
  public Result execute(Invocation i) {
    return tx.execute(s -> {
      var row = checked(i, true);
      if ("WITHDRAW".equals(i.operation())) {
        active(i.context());
        if (!Objects.equals(row.get("applicant"), i.context().actorId()) || i.workflow() == null
            || !Objects.equals(row.get("instance_id"), i.workflow().instanceId()) || !i.values().isEmpty())
          throw new SecurityException("LEAVE_WITHDRAWAL_DENIED");
        // 撤回只允许冻结中的本人单据；终态由随后可靠交付回写，不能在校验阶段提前生效。
        return new Result(false, Map.of());
      }
      if (!"EXECUTE".equals(i.operation())) {
        if (!Set.of("APPROVE", "REJECT").contains(i.operation())
            || !permitted(i.context(), row, i.taskId()) || !i.values().isEmpty())
          throw new SecurityException("LEAVE_TASK_DENIED");
        return new Result(false, Map.of());
      }
      var ref = i.workflow();
      if (ref == null || ref.result() == null || !"APPROVAL".equals(ref.mode())
          || !Objects.equals(row.get("instance_id"), ref.instanceId())
          || !Objects.equals(row.get("applicant"), i.context().actorId())
          || !Set.of("APPROVED", "REJECTED", "WITHDRAWN").contains(ref.result()))
        throw new SecurityException("LEAVE_RESULT_DENIED");
      var prior = jdbc.queryForList("SELECT * FROM leave_poc_delivery WHERE tenant_id=? AND invocation_id=?",
          i.context().tenantId(), i.invocationId());
      if (!prior.isEmpty()) {
        var p = prior.getFirst();
        if (!i.objectId().equals(p.get("object_id")) || !ref.instanceId().equals(p.get("instance_id"))
            || !ref.result().equals(p.get("result"))) throw new SecurityException("LEAVE_RESULT_CONFLICT");
        return new Result(false, Map.of());
      }
      if (!"SUBMITTED".equals(row.get("state"))) throw new IllegalArgumentException("LEAVE_STATE_CONFLICT");
      jdbc.update("INSERT INTO leave_poc_delivery VALUES(?,?,?,?,?)", i.context().tenantId(),
          i.invocationId(), i.objectId(), ref.instanceId(), ref.result());
      jdbc.update("UPDATE leave_poc_request SET state=? WHERE tenant_id=? AND id=?", ref.result(), i.context().tenantId(), i.objectId());
      return new Result(false, Map.of());
    });
  }

  private Map<String, Object> checked(Invocation i, boolean lock) {
    if (!"LeaveRequest".equals(i.objectType())) throw new SecurityException("LEAVE_OBJECT_DENIED");
    var row = row(i.context(), i.objectId(), lock);
    if (!Objects.equals(row.get("organization_id"), i.organizationId())
        || ((Number) row.get("basis_version")).longValue() != i.objectVersion())
      throw new IllegalArgumentException("LEAVE_BASIS_CHANGED");
    if (!"EXECUTE".equals(i.operation()) && !"SUBMITTED".equals(row.get("state")))
      throw new IllegalArgumentException("LEAVE_STATE_CONFLICT");
    return row;
  }

  private boolean permitted(WorkflowContext c, Map<String, Object> row, String taskId) {
    return taskId != null && platform.active(c.tenantId(), c.actorId())
        && tasks.get().permits(c, taskId, "LeaveRequest", row.get("id").toString(),
            ((Number) row.get("basis_version")).longValue(), "leavePoc", "days", false);
  }

  private Map<String, Object> row(WorkflowContext c, String id, boolean lock) {
    UUID.fromString(id);
    var rows = jdbc.queryForList("SELECT * FROM leave_poc_request WHERE tenant_id=? AND id=?" + (lock ? " FOR UPDATE" : ""), c.tenantId(), id);
    if (rows.isEmpty()) throw new SecurityException("LEAVE_ACCESS_DENIED");
    return rows.getFirst();
  }

  private void active(WorkflowContext c) {
    if (!platform.active(c.tenantId(), c.actorId())) throw new SecurityException("LEAVE_ACCESS_DENIED");
  }

  private void requireStart(WorkflowContext c) {
    if (!platform.allowed(c, "workflow:start")) throw new SecurityException("LEAVE_ACCESS_DENIED");
  }
}
