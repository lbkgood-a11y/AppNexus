package com.appnexus.workflow.adapter.flowable;

import java.util.*;
import org.flowable.engine.delegate.*;
import org.flowable.task.service.delegate.*;

/** 引擎固定监听器：与引擎推进同事务创建任务、交付记录及终态，避免结束通知丢失。 */
public final class WorkflowBridge
    implements ExecutionListener, org.flowable.task.service.delegate.TaskListener {
  private WorkflowRuntime runtime;

  /** 装配在引擎构建后、接收任何业务请求前完成；不支持运行中替换。 */
  void bind(WorkflowRuntime runtime) {
    if (this.runtime != null) throw new IllegalStateException("ALREADY_BOUND");
    this.runtime = Objects.requireNonNull(runtime);
  }

  /** 多人集合由当前租户组织规则解析；无人时保留不可处理占位任务，不自动跳过。 */
  public List<String> people(DelegateExecution execution, String node) {
    return runtime.people(execution, node);
  }

  /** 与预演复用固定比较器；参数只来自校验后的模型，不能调用任意函数或读取业务原文。 */
  @SuppressWarnings("unchecked")
  public boolean condition(DelegateExecution execution, String field, String operator, String value) {
    return com.appnexus.workflow.core.WorkflowCondition.test(
        (Map<String, String>) execution.getVariable("wf_fields"), field, operator, value);
  }

  @Override
  public void notify(DelegateExecution execution) {
    if ("end".equals(execution.getEventName())) runtime.ended(execution);
    else runtime.action(execution);
  }

  @Override
  public void notify(DelegateTask task) {
    runtime.created(task);
  }
}
