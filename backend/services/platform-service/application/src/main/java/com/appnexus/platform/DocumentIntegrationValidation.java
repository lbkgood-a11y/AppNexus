package com.appnexus.platform;

import com.appnexus.contracts.document.Documents;
import com.appnexus.platform.configuration.NavigationPages;
import java.util.*;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** 装配级统一行为校验：所有业务提供者的页面、权限和真实 API 必须一致，避免只登记展示字符串。 */
@Configuration(proxyBeanMethods=false)
public class DocumentIntegrationValidation {
  /** 在 MVC 映射初始化后校验；缺失页面或处理接口阻止应用提供虚假的可配置入口。 */
  @Bean public SmartInitializingSingleton validateDocumentIntegration(List<Documents.Provider> providers,
      @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings){
    return ()->{for(var provider:providers)for(var action:provider.descriptor().actions()){
      var pages=new ArrayList<Documents.PageOperation>();if(action.pageOperation()!=null)pages.add(action.pageOperation());
      action.aliases().forEach(alias->pages.add(alias.pageOperation()));
      for(var operation:pages){
        var page=NavigationPages.find(operation.pageKey());
        if(page==null||!page.path().equals(operation.pagePath())||!NavigationPages.operations(operation.pageKey()).contains(action.permissionCode()))throw new IllegalStateException("DOCUMENT_PAGE_PERMISSION_REGISTRATION_INVALID:"+action.operationKey());
        boolean found=mappings.getHandlerMethods().keySet().stream().anyMatch(mapping->mapping.getPatternValues().contains(operation.apiPath())&&mapping.getMethodsCondition().getMethods().stream().anyMatch(method->method.name().equals(operation.method())));
        if(!found)throw new IllegalStateException("DOCUMENT_API_REGISTRATION_INVALID:"+action.operationKey());
      }
    }};
  }
}
