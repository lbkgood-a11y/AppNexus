package com.appnexus.contracts.workflow;

/** 业务服务发起公开契约；上下文须来自可信认证，实例匹配和幂等由工作流入口再校验。 */
public interface WorkflowStarter {
  /** 按类型、组织和可信业务快照匹配唯一发布流程；返回实例标识，未匹配或冲突拒绝。 */
  String startInstance(
      WorkflowContext context,
      String objectType,
      String objectId,
      long objectVersion,
      String organization,
      String purpose,
      String entryCapability);
}
