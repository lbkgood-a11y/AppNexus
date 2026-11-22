package com.appnexus.workflow.adapter.flowable;
import com.appnexus.workflow.application.WorkflowRuntime;
import com.appnexus.workflow.api.WorkflowManagement;
import com.appnexus.contracts.businessobject.BusinessObjects.ScheduledOperations;

import org.springframework.scheduling.annotation.Scheduled;

/** 独立可靠交付扫描器；每次最多20条，避免单租户大批动作占满请求线程；失败留待下轮恢复。 */
public final class WorkflowWorker {
  private final WorkflowRuntime runtime;
  private final ScheduledOperations operations;

  /** @param runtime 可靠作业用例 @param operations 平台公开的定时操作准入 */
  public WorkflowWorker(WorkflowRuntime runtime,ScheduledOperations operations) {
    this.runtime = runtime;
    this.operations=operations;
  }

  /** 扫描间隔1000毫秒；单动作适配器须自行配置网络超时，不得无限阻塞。 */
  @Scheduled(fixedDelay = 1000)
  public void run() {
    operations.require(WorkflowManagement.PROCESS_JOBS,WorkflowManagement.JOB_WORKER_AUTHORITY);
    runtime.maintain();
    for (int i = 0; i < 20; i++) if (!runtime.dispatch()) break;
  }
}
