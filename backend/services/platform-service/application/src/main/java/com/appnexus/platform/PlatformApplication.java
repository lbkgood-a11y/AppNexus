package com.appnexus.platform;

import com.appnexus.workflow.adapter.flowable.WorkflowEngineConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 平台与工作流的组合启动入口；仅此装配层允许引用两个服务的实现。
 * 保留平台扫描范围，通过显式导入装配工作流，避免扩大扫描引入无关服务组件。
 */
@SpringBootApplication
@Import(WorkflowEngineConfiguration.class)
public class PlatformApplication {
    /**
     * 启动组合应用，工作流引擎默认关闭，须完成受控数据库迁移后显式启用。
     * @param args Spring Boot 命令行参数；不得通过命令行传递敏感凭据
     */
    public static void main(String[] args) { SpringApplication.run(PlatformApplication.class, args); }
}
