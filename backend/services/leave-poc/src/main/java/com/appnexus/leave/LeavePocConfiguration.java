package com.appnexus.leave;

import com.appnexus.contracts.workflow.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** 请假 POC 仅随已启用工作流装配；不自动发布流程、创建人员或开启交付扫描。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.workflow.engine.enabled", havingValue = "true")
public class LeavePocConfiguration {
  /** 延迟取得工作流公开端口，避免业务能力注册与引擎初始化形成循环依赖。 */
  @Bean
  public LeavePocService leavePocService(JdbcTemplate jdbc, PlatformTransactionManager manager,
      WorkflowPlatform platform, ObjectProvider<WorkflowStarter> starter,
      ObjectProvider<WorkflowTaskAccess> tasks,
      ObjectProvider<com.appnexus.contracts.document.DocumentBindings> bindings,
      ObjectProvider<com.appnexus.contracts.printing.BusinessPrinting> printing) {
    var service=new LeavePocService(jdbc, manager, platform, starter::getObject, tasks::getObject);
    service.bindings(bindings::getObject);
    service.printing(printing::getObject);
    return service;
  }
  /** API 沿用组合应用 Cookie/CSRF 及可信租户过滤器。 */
  @Bean
  public LeavePocController leavePocController(LeavePocService service) {
    return new LeavePocController(service);
  }
}
