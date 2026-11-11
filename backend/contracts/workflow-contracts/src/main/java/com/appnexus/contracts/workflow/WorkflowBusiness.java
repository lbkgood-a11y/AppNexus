package com.appnexus.contracts.workflow;

import java.util.*;

/**
 * 业务代码注册的能力，不接受浏览器提供任意 URL、脚本或类名。 实现必须校验租户、对象状态、版本、字段范围与强制限制；写入以 invocationId 幂等。
 * 业务数据和敏感字段不存入引擎变量，返回值只允许契约声明的非敏感流转标量。
 */
public interface WorkflowBusiness {
  /**
   * 代码注册能力元数据，不含凭据或业务实体。
   *
   * @param key 稳定能力标识，1—64个字母、数字或下划线，以字母开头，必填
   * @param name 业务展示名称，必填；由业务模块提供本地化文案
   * @param permission 受控注册的功能权限码，必填，设计者选择能力及发起时校验
   * @param route 构建内业务路由，可为空；只允许以单斜杠开头的本地路径
   * @param inputs 允许办理或映射输入的字段名集合，必填，可为空；不是任意JSON实体
   * @param outputs 允许持久化及用于流转的非敏感标量字段，必填，可为空
   */
  record Capability(
      String key,
      String name,
      String permission,
      String route,
      Set<String> inputs,
      Set<String> outputs) {}

  /**
   * 公共执行关联，不暴露引擎内部ID；用于业务结果、下游事件和审计关联。
   *
   * @param instanceId 工作流实例标识，必填
   * @param definitionId 定义标识，必填
   * @param definitionVersion 不可变发布版本，正整数
   * @param nodeId 当前节点标识；首次发起前不存在实例，整份引用为null
   * @param mode APPROVAL或BUSINESS，用于理解完成边界
   * @param result 终态结果；流程尚未结束时为空
   */
  record Reference(
      String instanceId,
      String definitionId,
      int definitionVersion,
      String nodeId,
      String mode,
      String result) {}

  /**
   * 业务调用参数。
   *
   * @param invocationId 稳定执行标识，必填；重试必须使用同值并由业务侧持久化去重
   * @param context 服务端可信身份声明，接收方仍需认证和授权
   * @param objectType 业务对象类型，不代表权限
   * @param objectId 业务对象标识，必填
   * @param organizationId 声明的单据归属组织，业务端必须从自身存储复核，不能直接信任调用声明
   * @param objectVersion 本次审批依据版本，非负整数；普通节点办理可维护独立内容版本，依据变更必须拒绝旧版本
   * @param operation SNAPSHOT、SAVE、SUBMIT、APPROVE、REJECT、WITHDRAW 或 EXECUTE，业务端须区分暂存与正式提交
   * @param workItemKey 办理数据作用域标识；分别填写时为任务ID，共享填写时为实例ID与节点ID组合；非人工动作为调用ID
   * @param workflow 工作流公共关联；首次发起前的SNAPSHOT为空，业务不得把引用当作认证凭证
   * @param taskId 任务标识；发起或自动动作时可为空
   * @param values 受控字段输入，只允许已声明字段，不接受任意业务实体
   */
  record Invocation(
      String invocationId,
      WorkflowContext context,
      String objectType,
      String objectId,
      String organizationId,
      long objectVersion,
      String operation,
      String workItemKey,
      Reference workflow,
      String taskId,
      Map<String, String> values) {}

  /** 结果 PENDING 表示外部执行中；回调仍通过可信服务入口验权，不直接开放浏览器成功声明。 */
  record Result(boolean pending, Map<String, String> outputs) {
    public Result {
      outputs = Map.copyOf(outputs);
    }
  }

  /** 返回稳定能力声明，不读取调用者提交的目标地址。 */
  Capability capability();

  /** 返回业务配置目录；缺省 null 表示仅可在高级编排中使用，不编造可用单据或结果处理。 */
  default WorkflowBusinessProfile configuration() { return null; }

  /**
   * 执行业务动作或保存办理数据；调用方不得提前完成任务。
   * @param invocation 已验证对象与受控输入，必须区分暂存、提交、撤回和自动执行
   * @return 仅自动动作可返回pending=true；人工暂存/提交必须同步确认成功
   * @throws IllegalArgumentException 业务校验或状态不允许，交付进入人工处理而不自动重试
   * @throws SecurityException 业务/字段/强制访问限制拒绝，交付进入人工处理
   * 其他运行异常视为技术故障，触发有限重试；实现方必须将业务异常映射为上述拒绝类型。
   */
  Result execute(Invocation invocation);

  /**
   * 校验发起/办理时单据权限、归属组织和审批依据版本，并返回可信审批依据字段；默认拒绝，接入方必须显式实现。
   *
   * @param invocation 业务对象与提交版本；values 不可信，必须从业务存储重读审批依据
   * @return 可信的非敏感审批依据字段，仅允许 capability.outputs 内字段；正常评分等办理数据不加入固定依据快照，后续每次办理会复核此快照
   */
  default Map<String, String> snapshot(Invocation invocation) {
    throw new SecurityException("BUSINESS_ACCESS_DENIED");
  }
}
