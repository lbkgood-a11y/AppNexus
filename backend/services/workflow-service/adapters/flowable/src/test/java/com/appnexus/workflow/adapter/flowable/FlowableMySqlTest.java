package com.appnexus.workflow.adapter.flowable;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/** 使用真实 MySQL 验证 Spring 事务与引擎兼容性；建表仅发生于一次性测试容器。 */
@Tag("container")
@Testcontainers
class FlowableMySqlTest {
  @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

  @Test
  void embeddedEngineUsesExternalSchemaAndParticipatesInSpringTransaction() {
    var dataSource =
        new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    var transactionManager = new DataSourceTransactionManager(dataSource);
    // 仅测试容器允许自动建表；正式配置必须在表缺失时启动失败。
    var bootstrap = new SpringProcessEngineConfiguration();
    bootstrap.setDataSource(dataSource);
    bootstrap.setTransactionManager(transactionManager);
    bootstrap.setDisableIdmEngine(true);
    bootstrap.setDisableEventRegistry(true);
    bootstrap.setEnableConfiguratorServiceLoader(false);
    bootstrap.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
    bootstrap.setAsyncExecutorActivate(false);
    bootstrap.buildProcessEngine().close();

    var configuration = new WorkflowEngineConfiguration();
    var engine =
        configuration.workflowProcessEngine(dataSource, transactionManager, new WorkflowBridge());
    try {
      assertThat(configuration.workflowEngine(engine).engineName()).isEqualTo("appnexus-workflow");
      assertThat(engine.getProcessEngineConfiguration().isAsyncExecutorActivate()).isFalse();
      var bpmn =
          """
          <?xml version="1.0" encoding="UTF-8"?>
          <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                       targetNamespace="urn:appnexus:workflow:test">
            <process id="workflowSmoke" isExecutable="true">
              <startEvent id="start"/>
              <sequenceFlow id="f1" sourceRef="start" targetRef="review"/>
              <userTask id="review" name="工作流"/>
              <sequenceFlow id="f2" sourceRef="review" targetRef="end"/>
              <endEvent id="end"/>
            </process>
          </definitions>
          """;
      engine
          .getRepositoryService()
          .createDeployment()
          .tenantId("tenant-a")
          .addBytes("smoke.bpmn20.xml", bpmn.getBytes(StandardCharsets.UTF_8))
          .deploy();
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(
              status -> {
                engine
                    .getRuntimeService()
                    .startProcessInstanceByKeyAndTenantId("workflowSmoke", "tenant-a");
                assertThat(
                        engine.getTaskService().createTaskQuery().taskTenantId("tenant-a").count())
                    .isEqualTo(1);
                assertThat(
                        engine.getTaskService().createTaskQuery().taskTenantId("tenant-b").count())
                    .isZero();
                status.setRollbackOnly();
              });
      assertThat(engine.getRuntimeService().createProcessInstanceQuery().count()).isZero();
      assertThat(engine.getTaskService().createTaskQuery().count()).isZero();
    } finally {
      engine.close();
    }
  }
}
