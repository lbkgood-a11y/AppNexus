package com.appnexus.document;

import com.appnexus.contracts.document.*;
import com.appnexus.contracts.workflow.WorkflowDefinitionCatalog;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** 单据服务独立装配，未启用流程引擎时仍可管理目录，但不提供虚假的流程选项。 */
@Configuration(proxyBeanMethods=false)
public class DocumentConfiguration {
  /** 工作流目录延迟取得，不通过实现依赖形成服务循环。 */
  @Bean public DocumentService documentService(JdbcTemplate jdbc,PlatformTransactionManager manager,
      DocumentPlatform platform,ObjectProvider<WorkflowDefinitionCatalog> workflows,List<Documents.Provider> providers,
      ObjectProvider<com.appnexus.contracts.printing.BusinessPrinting> printing) {
    var service=new DocumentService(jdbc,manager,platform,workflows::getIfAvailable,providers);service.printing(printing::getIfAvailable);return service;
  }
  /** 打印目录只投影统一交互声明；业务不再独立注册第二份字段契约，样本为空且不含真实数据。 */
  @Bean public com.appnexus.contracts.printing.PrintDataContractProvider interactionPrintContracts(List<Documents.Provider> providers) {
    return () -> {
      var catalog = new java.util.LinkedHashMap<String,java.util.Map<String,Object>>();
      for (var provider : providers) {
        var descriptor = provider.descriptor();
        for (var action : descriptor.actions()) for (var capability : action.interaction().capabilities()) {
          if (capability.service() != InteractionContract.Service.PRINT) continue;
          var contract = java.util.Map.<String,Object>of(
              "code",capability.reference(),"nameZh",descriptor.nameZh(),"nameEn",descriptor.nameEn(),"designOnly",false,
              "fields",capability.fields().stream().map(field -> java.util.Map.<String,Object>of(
                  "key",field.key(),"labelZh",field.nameZh(),"labelEn",field.nameEn(),"sample","")).toList());
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
  @Bean public DocumentController documentController(DocumentService service){return new DocumentController(service);}
}
