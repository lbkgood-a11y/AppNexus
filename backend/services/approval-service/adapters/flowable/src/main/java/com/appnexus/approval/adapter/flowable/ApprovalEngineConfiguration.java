package com.appnexus.approval.adapter.flowable;

import com.appnexus.approval.core.port.ApprovalEngine;
import javax.sql.DataSource;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 组合应用中的审批引擎装配入口，不注册 Flowable 原生 REST 接口。
 * 默认关闭；启用前必须由受控迁移执行器准备引擎表。数据库不匹配时启动失败，
 * 禁止自动建表、自动升级以及在未实现租户与任务权限之前启动后台执行器。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.approval.engine.enabled", havingValue = "true")
public class ApprovalEngineConfiguration {
    /**
     * 创建与审批服务同进程运行的 Flowable 引擎。
     * @param dataSource 组合应用数据源；初期复用平台连接池，表所有权仍属于审批服务
     * @param transactionManager 管理同一数据源的事务管理器；未来独立部署时由审批应用提供
     * @return 由 Spring 容器负责关闭的引擎
     */
    @Bean(destroyMethod = "close")
    public ProcessEngine approvalProcessEngine(
            DataSource dataSource, PlatformTransactionManager transactionManager) {
        var configuration = new SpringProcessEngineConfiguration();
        configuration.setEngineName("appnexus-approval");
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
     * 通过审批端口暴露引擎，避免业务代码直接依赖 Flowable。
     * @param approvalProcessEngine 容器托管的审批引擎
     * @return 审批服务内部使用的执行引擎端口
     */
    @Bean
    public ApprovalEngine approvalEngine(ProcessEngine approvalProcessEngine) {
        return new FlowableApprovalEngine(approvalProcessEngine);
    }
}
