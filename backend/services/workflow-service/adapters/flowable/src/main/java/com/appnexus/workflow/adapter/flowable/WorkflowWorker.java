package com.appnexus.workflow.adapter.flowable;

import org.springframework.scheduling.annotation.Scheduled;

/** 独立可靠交付扫描器；每次最多20条，避免单租户大批动作占满请求线程；失败留待下轮恢复。 */
public final class WorkflowWorker {
  private final WorkflowRuntime runtime;

  public WorkflowWorker(WorkflowRuntime runtime) {
    this.runtime = runtime;
  }

  /** 扫描间隔1000毫秒；单动作适配器须自行配置网络超时，不得无限阻塞。 */
  @Scheduled(fixedDelay = 1000)
  public void run() {
    runtime.maintain();
    for (int i = 0; i < 20; i++) if (!runtime.dispatch()) break;
  }
}
