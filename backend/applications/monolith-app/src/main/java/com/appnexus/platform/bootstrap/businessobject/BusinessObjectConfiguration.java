package com.appnexus.platform.bootstrap.businessobject;

import com.appnexus.platform.appapi.businessobject.CodingDeclaration;

import com.appnexus.platform.appapi.businessobject.CodingController;

import com.appnexus.platform.managementapi.businessobject.BusinessObjectController;

import com.appnexus.platform.businessobject.BusinessObjectManagement;

import com.appnexus.platform.businessobject.CodingManagement;

import com.appnexus.contracts.businessobject.*;
import com.appnexus.contracts.workflow.WorkflowDefinitionCatalog;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;

/** 平台业务对象上下文的组合装配，未启用流程引擎时仍可管理目录，但不提供虚假的流程选项。 */
@Configuration(proxyBeanMethods=false)
public class BusinessObjectConfiguration {
  /** 动态对象操作逐次在公共用例验证，不借用配置管理权限。 */
  @Bean public com.appnexus.platform.appapi.businessobject.ObjectExecutionController objectExecutionController(BusinessObjectExecution execution){return new com.appnexus.platform.appapi.businessobject.ObjectExecutionController(execution);}
  /** 公共执行器延迟获取专业服务；对象只通过唯一 Provider 暴露一次运行能力。 */
  @Bean public BusinessObjectExecution businessObjectExecution(List<BusinessObjects.Provider> providers,
      BusinessObjectBindings bindings,PlatformTransactionManager manager,BusinessObjectPlatform platform,
      ObjectProvider<com.appnexus.contracts.workflow.WorkflowStarter> workflows,
      ObjectProvider<com.appnexus.contracts.printing.BusinessPrinting> printing) {
    return com.appnexus.platform.businessobject.BusinessObjectUseCases.execution(providers,bindings,
        new com.appnexus.platform.persistence.businessobject.configuration.SpringConfigurationTransaction(manager),platform,
        workflows::getIfAvailable,printing::getIfAvailable);
  }
  /** 编码器元数据与执行器均由当前业务对象模块装配，不引入另一套应用入口。 */
  @Bean public CodingDeclaration codingDeclaration(){return new CodingDeclaration();}
  /** 独立编码规则通过同一对象目录注册，不建立平行权限模型。 */
  @Bean public com.appnexus.platform.appapi.businessobject.CodingRuleDeclaration codingRuleDeclaration(){return new com.appnexus.platform.appapi.businessobject.CodingRuleDeclaration();}
  /** 发号公开端口同时支持同进程业务接入和 HTTP 协助录入。 */
  @Bean public CodingManagement codingService(com.appnexus.platform.persistence.businessobject.coding.CodingMapper mapper,PlatformTransactionManager manager,BusinessObjectPlatform platform,List<BusinessObjects.Provider> providers,BusinessObjectManagement objects){return com.appnexus.platform.persistence.businessobject.BusinessObjectPersistence.coding(mapper,manager,platform,providers,objects);}
  /** HTTP 写请求继续使用宿主 CSRF 防护。 */
  @Bean public CodingController codingController(CodingManagement service){return new CodingController(service);}
  /** 工作流目录延迟取得，不通过实现依赖形成服务循环。 */
  @Bean public BusinessObjectManagement businessObjectService(com.appnexus.platform.persistence.businessobject.configuration.ConfigurationMapper mapper,PlatformTransactionManager manager,
      BusinessObjectPlatform platform,ObjectProvider<WorkflowDefinitionCatalog> workflows,List<BusinessObjects.Provider> providers,
      ObjectProvider<com.appnexus.contracts.printing.BusinessPrinting> printing) {
    return com.appnexus.platform.persistence.businessobject.BusinessObjectPersistence.create(mapper,manager,platform,workflows::getIfAvailable,providers,printing::getIfAvailable);
  }
  /** 打印目录只投影统一交互声明；业务不再独立注册第二份字段契约，样本为空且不含真实数据。 */
  @Bean public com.appnexus.contracts.printing.PrintDataContractProvider interactionPrintContracts(List<BusinessObjects.Provider> providers) {
    return () -> {
      var catalog = new java.util.LinkedHashMap<String,java.util.Map<String,Object>>();
      for (var provider : providers) {
        var descriptor = provider.descriptor();
        for (var action : descriptor.operations()) for (var capability : action.interaction().capabilities()) {
          if (capability.service() != InteractionContract.Service.PRINT) continue;
          var contract = java.util.Map.<String,Object>of(
              "code",capability.dataContract(),"version",capability.dataVersion(),"purpose","PRINT","nameZh",descriptor.nameZh(),"nameEn",descriptor.nameEn(),"designOnly",false,
              "fields",capability.fields().stream().map(field -> java.util.Map.<String,Object>of(
                  "key",field.key(),"labelZh",field.nameZh(),"labelEn",field.nameEn(),"sample","","type",field.type(),"required",field.required(),"unit",field.unit())).toList());
          var previous = catalog.putIfAbsent(capability.reference(),contract);
          // 多个动作可引用同一打印投影，但同编码不能悄悄替换字段协议。
          if (previous != null && !previous.get("fields").equals(contract.get("fields"))) {
            throw new IllegalArgumentException("INTERACTION_PRINT_SCHEMA_CONFLICT");
          }
        }
      }
      return List.copyOf(catalog.values());
    };
  }
  /** 管理接口沿用宿主安全、会话与 CSRF 链。 */
  @Bean public BusinessObjectController businessObjectController(BusinessObjectManagement service){return new BusinessObjectController(service);}
}
