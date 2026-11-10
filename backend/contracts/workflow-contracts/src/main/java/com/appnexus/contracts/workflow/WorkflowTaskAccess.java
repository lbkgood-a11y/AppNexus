package com.appnexus.contracts.workflow;

/** 业务服务校验任务受限授权；不授予整个模块权限，不能绕过强制限制或脱敏。 */
public interface WorkflowTaskAccess {
  /**
   * 校验当前有效任务、人员、对象、版本及字段，业务端仍须校验自身状态。
   *
   * @param write true校验当前节点业务能力的输入字段；false校验节点或单据接入能力声明的输出字段
   * @return 仅匹配当前有效任务和人员时允许；任务结束立即拒绝
   */
  boolean permits(
      WorkflowContext context,
      String taskId,
      String objectType,
      String objectId,
      long objectVersion,
      String capability,
      String field,
      boolean write);

  /** 校验填写权限的便捷入口，默认不开放输出字段写入。 */
  default boolean permits(
      WorkflowContext context,
      String taskId,
      String objectType,
      String objectId,
      long objectVersion,
      String capability,
      String field) {
    return permits(context, taskId, objectType, objectId, objectVersion, capability, field, true);
  }
}
