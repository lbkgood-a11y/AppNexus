package com.appnexus.workflow.adapter.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import com.appnexus.contracts.businessobject.BusinessObjects.ScheduledOperations;
import com.appnexus.workflow.api.WorkflowManagement;
import com.appnexus.workflow.application.WorkflowRuntime;

import com.appnexus.workflow.core.port.WorkflowEngine;
import org.flowable.engine.ProcessEngine;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class WorkflowEngineConfigurationTest {
  @Test
  void defaultDoesNotRequireDatabaseOrStartEngine() {
    new ApplicationContextRunner()
        .withUserConfiguration(WorkflowEngineConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(ProcessEngine.class);
              assertThat(context).doesNotHaveBean(WorkflowEngine.class);
            });
  }

  @Test
  void enablingWithoutInfrastructureFailsClosed() {
    new ApplicationContextRunner()
        .withUserConfiguration(WorkflowEngineConfiguration.class)
        .withPropertyValues("app.workflow.engine.enabled=true")
        .run(context -> assertThat(context).hasFailed());
  }

  /** 调度声明拒绝时，Worker 不得进入维护或领取逻辑。 */
  @Test
  void workerChecksAuthorizationBeforeTouchingJobs() {
    var runtime = mock(WorkflowRuntime.class);
    var authorization = mock(ScheduledOperations.class);
    var worker = new WorkflowWorker(runtime,authorization);
    var order = inOrder(authorization,runtime);
    worker.run();
    order.verify(authorization).require(WorkflowManagement.PROCESS_JOBS,WorkflowManagement.JOB_WORKER_AUTHORITY);
    order.verify(runtime).maintain();
    order.verify(runtime).dispatch();
    clearInvocations(runtime);
    doThrow(new SecurityException("denied")).when(authorization)
        .require(WorkflowManagement.PROCESS_JOBS,WorkflowManagement.JOB_WORKER_AUTHORITY);
    assertThatThrownBy(worker::run).isInstanceOf(SecurityException.class);
    verifyNoInteractions(runtime);
  }
}
