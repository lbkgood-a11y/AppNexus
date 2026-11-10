package com.appnexus.platform;

import com.appnexus.workflow.adapter.flowable.WorkflowEngineConfiguration;
import com.appnexus.printing.adapter.jdbc.PrintConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 平台、工作流与打印服务的组合启动入口；仅此装配层允许引用服务实现。
 * 保留平台扫描范围，通过显式导入装配共享服务，避免扩大扫描引入无关组件。
 */
@SpringBootApplication
@Import({WorkflowEngineConfiguration.class, PrintConfiguration.class})
public class PlatformApplication {
    /**
     * 启动组合应用，工作流引擎默认关闭，须完成受控数据库迁移后显式启用。
     * @param args Spring Boot 命令行参数；不得通过命令行传递敏感凭据
     */
    public static void main(String[] args) { SpringApplication.run(PlatformApplication.class, args); }
}
