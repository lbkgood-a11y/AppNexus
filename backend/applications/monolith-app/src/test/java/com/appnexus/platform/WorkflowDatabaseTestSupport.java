package com.appnexus.platform;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 工作流启用后的 Provider 会将额外的数据范围投影写入数据库。
 * 同一测试进程中，工作流关闭的装配使用另一组容器，避免把该投影引入其角色初始化。
 */
abstract class WorkflowDatabaseTestSupport extends IntegrationDatabaseTestSupport {
    private static final MySQLContainer MYSQL = startMySql();
    private static final GenericContainer<?> VALKEY = startValkey();

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry properties) {
        registerInfrastructure(properties, MYSQL, VALKEY);
    }
}
