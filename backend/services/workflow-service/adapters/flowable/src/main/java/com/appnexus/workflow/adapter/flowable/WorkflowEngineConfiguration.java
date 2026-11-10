package com.appnexus.workflow.adapter.flowable;

import com.appnexus.workflow.core.port.WorkflowEngine;
import javax.sql.DataSource;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 组合应用中的工作流引擎装配入口，不注册 Flowable 原生 REST 接口。 默认关闭；启用前必须由受控迁移执行器准备引擎表。数据库不匹配时启动失败，
 * 禁止运行时自动建表或升级；引擎自身异步执行器固定关闭，可靠交付由独立开关控制。
 */
@org.springframework.scheduling.annotation.EnableScheduling
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.workflow.engine.enabled", havingValue = "true")
public class WorkflowEngineConfiguration {
  /** 内部监听器仅由本配置装配，不接受流程JSON指定其他Bean。 */
  @Bean
  public WorkflowBridge workflowBridge() {
    return new WorkflowBridge();
  }

  /** 工作流运行时；无平台公开桥接时启动失败，禁止绕过认证和租户检查。 */
  @Bean
  public WorkflowRuntime workflowRuntime(
      ProcessEngine engine,
      DataSource source,
      PlatformTransactionManager manager,
      com.appnexus.contracts.workflow.WorkflowPlatform platform,
      java.util.List<com.appnexus.contracts.workflow.WorkflowBusiness> businesses,
      WorkflowBridge bridge) {
    var runtime =
        new WorkflowRuntime(
            engine,
            new org.springframework.jdbc.core.JdbcTemplate(source),
            manager,
            platform,
            businesses);
    bridge.bind(runtime);
    return runtime;
  }

  /** HTTP接口只在引擎显式启用时装配，沿用宿主安全链。 */
  @Bean
  public WorkflowController workflowController(WorkflowRuntime runtime) {
    return new WorkflowController(runtime);
  }

  /** 交付扫描器默认关闭，启用前须准备幂等业务适配器。 */
  @Bean
  @ConditionalOnProperty(name = "app.workflow.worker.enabled", havingValue = "true")
  public WorkflowWorker workflowWorker(WorkflowRuntime runtime) {
    return new WorkflowWorker(runtime);
  }

  /**
   * 创建与工作流服务同进程运行的 Flowable 引擎。
   *
   * @param dataSource 组合应用数据源；初期复用平台连接池，表所有权仍属于工作流服务
   * @param bridge 固定引擎监听器，实例/任务/交付事实与引擎同事务维护
   * @param transactionManager 管理同一数据源的事务管理器；未来独立部署时由工作流应用提供
   * @return 由 Spring 容器负责关闭的引擎
   */
  @org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization
  @Bean(destroyMethod = "close")
  public ProcessEngine workflowProcessEngine(
      DataSource dataSource, PlatformTransactionManager transactionManager, WorkflowBridge bridge) {
    var configuration = new SpringProcessEngineConfiguration();
    configuration.setBeans(java.util.Map.of("wfBridge", bridge));
    configuration.setEngineName("appnexus-workflow");
    configuration.setDataSource(dataSource);
    configuration.setTransactionManager(transactionManager);
    // 身份与事件由平台契约承接，禁止引擎自行建立另一套用户体系或发现额外子引擎。
    configuration.setDisableIdmEngine(true);
    configuration.setDisableEventRegistry(true);
    configuration.setEnableConfiguratorServiceLoader(false);
    configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_FALSE);
    configuration.setAsyncExecutorActivate(false);
    return configuration.buildProcessEngine();
  }

  /**
   * 通过工作流端口暴露引擎，避免业务代码直接依赖 Flowable。
   *
   * @param workflowProcessEngine 容器托管的工作流引擎
   * @return 工作流服务内部使用的执行引擎端口
   */
  @Bean
  public WorkflowEngine workflowEngine(ProcessEngine workflowProcessEngine) {
    return new FlowableWorkflowEngine(workflowProcessEngine);
  }
}
