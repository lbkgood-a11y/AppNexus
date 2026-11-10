package com.appnexus.workflow.adapter.flowable;

import static org.assertj.core.api.Assertions.assertThat;

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
}
