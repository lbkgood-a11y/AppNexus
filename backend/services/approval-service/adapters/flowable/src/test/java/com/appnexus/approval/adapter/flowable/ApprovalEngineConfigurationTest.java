package com.appnexus.approval.adapter.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import com.appnexus.approval.core.port.ApprovalEngine;
import org.flowable.engine.ProcessEngine;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ApprovalEngineConfigurationTest {
    @Test
    void defaultDoesNotRequireDatabaseOrStartEngine() {
        new ApplicationContextRunner().withUserConfiguration(ApprovalEngineConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ProcessEngine.class);
                    assertThat(context).doesNotHaveBean(ApprovalEngine.class);
                });
    }

    @Test
    void enablingWithoutInfrastructureFailsClosed() {
        new ApplicationContextRunner().withUserConfiguration(ApprovalEngineConfiguration.class)
                .withPropertyValues("app.approval.engine.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }
}
