package com.appnexus.leave;
import com.appnexus.leave.adapter.web.LeavePocController;
import com.appnexus.leave.adapter.persistence.LeaveRecordMapper;

import com.appnexus.contracts.workflow.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

import org.springframework.transaction.PlatformTransactionManager;

/** 请假 POC 仅随已启用工作流装配；不自动发布流程、创建人员或开启交付扫描。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.workflow.engine.enabled", havingValue = "true")
public class LeavePocConfiguration {
  /** 延迟取得工作流公开端口，避免业务能力注册与引擎初始化形成循环依赖。 */
  @Bean
  public LeavePocService leavePocService(LeaveRecordMapper statements, PlatformTransactionManager manager,
      WorkflowPlatform platform,
      ObjectProvider<WorkflowTaskAccess> tasks,
      ObjectProvider<com.appnexus.contracts.businessobject.BusinessObjectBindings> bindings,
      ObjectProvider<com.appnexus.contracts.printing.BusinessPrinting> printing,
      ObjectProvider<com.appnexus.contracts.businessobject.BusinessObjectExecution> execution,
      com.appnexus.contracts.businessobject.ObjectRecordRuntimeFactory runtimeFactory) {
    var service=new LeavePocService(statements, manager, platform, tasks::getObject);
    service.bindings(bindings::getObject);
    service.printing(printing::getObject);
    service.execution(execution::getObject);
    service.runtimeFactory(runtimeFactory);
    return service;
  }
  /** 注册只读对象诊断；复用本人读取及配置检查，不模拟指定用户或越过业务归属。 */
  @Bean public com.appnexus.contracts.operation.ObjectDiagnostics leaveObjectDiagnostics(LeavePocService service){
    return new com.appnexus.contracts.operation.ObjectDiagnostics(){
      public java.util.Set<String> operations(){return java.util.Set.of("LeaveRequest.submit","LeaveRequest.print");}
      public java.util.List<Check> inspect(Context c,String operation,String object){
        var value=service.availability(new WorkflowContext(c.tenantId(),c.accountId(),c.requestId()),object).stream().filter(item->item.operationId().equals(operation)).findFirst().orElseThrow();
        // availability 先校验本人归属；后续原因来自同一业务及配置执行器，无权记录不返回状态。
        return java.util.List.of(new Check("OWNERSHIP","READY","OWNER_VERIFIED"),new Check("BUSINESS_AND_CONFIGURATION",value.allowed()?"READY":"BLOCKED",value.reasonCode()));
      }
    };
  }
  /** API 沿用组合应用 Cookie/CSRF 及可信租户过滤器。 */
  @Bean
  public LeavePocController leavePocController(LeavePocService service) {
    return new LeavePocController(service);
  }
}
