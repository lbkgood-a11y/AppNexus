package com.appnexus.printing.adapter.jdbc;

import com.appnexus.contracts.printing.PrintPlatform;
import javax.sql.DataSource;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** 组合应用打印装配；HTTP 和 JDBC 使用宿主认证、事务及受控 Flyway 迁移。 */
@Configuration(proxyBeanMethods=false)
public class PrintConfiguration {
  /** 单据与业务模块只引用打印公开契约；模板和输出状态仍由打印服务拥有。 */
  @Bean public com.appnexus.contracts.printing.BusinessPrinting businessPrinting(DataSource source,PrintOutputs outputs,PrintPlatform platform){return new BusinessPrintingAdapter(new JdbcTemplate(source),outputs,platform);}
  /** @return 打印运行时；缺失平台权限桥接时应用启动失败，禁止降级为匿名打印。 */
  @Bean @DependsOnDatabaseInitialization
  public PrintRuntime printRuntime(DataSource source,PrintPlatform platform){return new PrintRuntime(new JdbcTemplate(source),platform,tools.jackson.databind.json.JsonMapper.builder().build());}

  /** @return 租户打印 HTTP 入口；由现有安全过滤链保护。 */
  @Bean public PrintController printController(PrintRuntime runtime,PrintFlow flow,PrintOutputs outputs){return new PrintController(runtime,flow,outputs);}
  /** 统一输出编排复用宿主事务和可信身份，外部 API 不建立第二套认证。 */
  @Bean @DependsOnDatabaseInitialization
  public PrintOutputs printOutputs(DataSource source,PrintPlatform platform,PrintRuntime runtime,PrintFlow flow){return new PrintOutputs(new JdbcTemplate(source),platform,runtime,flow,tools.jackson.databind.json.JsonMapper.builder().build());}

  /** @return 配置式场景与手工单据事务服务，复用宿主数据库及身份权限。 */
  @Bean @DependsOnDatabaseInitialization
  public PrintFlow printFlow(DataSource source,PrintPlatform platform,PrintRuntime runtime,java.util.List<com.appnexus.contracts.printing.PrintDataContractProvider> providers){var flow=new PrintFlow(new JdbcTemplate(source),platform,runtime,tools.jackson.databind.json.JsonMapper.builder().build());flow.businessContracts(providers);return flow;}
}
