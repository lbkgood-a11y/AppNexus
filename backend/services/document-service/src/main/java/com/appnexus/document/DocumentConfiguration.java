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
      DocumentPlatform platform,ObjectProvider<WorkflowDefinitionCatalog> workflows,List<Documents.Provider> providers) {
    return new DocumentService(jdbc,manager,platform,workflows::getIfAvailable,providers);
  }
  /** 管理接口沿用宿主安全、会话与 CSRF 链。 */
  @Bean public DocumentController documentController(DocumentService service){return new DocumentController(service);}
}
