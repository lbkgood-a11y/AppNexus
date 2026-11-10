package com.appnexus.printing.adapter.jdbc;

import com.appnexus.contracts.printing.PrintPlatform;
import javax.sql.DataSource;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** 组合应用打印装配；HTTP 和 JDBC 使用宿主认证、事务及受控 Flyway 迁移。 */
@Configuration(proxyBeanMethods=false)
public class PrintConfiguration {
  /** @return 打印运行时；缺失平台权限桥接时应用启动失败，禁止降级为匿名打印。 */
  @Bean @DependsOnDatabaseInitialization
  public PrintRuntime printRuntime(DataSource source,PrintPlatform platform){return new PrintRuntime(new JdbcTemplate(source),platform,tools.jackson.databind.json.JsonMapper.builder().build());}

  /** @return 租户打印 HTTP 入口；由现有安全过滤链保护。 */
  @Bean public PrintController printController(PrintRuntime runtime){return new PrintController(runtime);}
}
