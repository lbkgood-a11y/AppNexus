package com.appnexus.platform;

import com.appnexus.contracts.businessobject.BusinessObjects;
import com.appnexus.platform.configuration.NavigationPages;
import java.util.*;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** 装配级统一行为校验：所有业务提供者的页面、权限和真实 API 必须一致，避免只登记展示字符串。 */
@Configuration(proxyBeanMethods=false)
public class BusinessObjectIntegrationValidation {
  /** 在 MVC 映射初始化后校验；缺失页面或处理接口阻止应用提供虚假的可配置入口。 */
  @Bean public SmartInitializingSingleton validateDocumentIntegration(List<BusinessObjects.Provider> providers,
      @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings){
    return ()->{for(var provider:providers){
      var descriptor=provider.descriptor();
      var runtime=provider.commandRuntime();
      var commands=provider.commandOperations();
      var implemented=runtime==null?Set.<String>of():runtime.inputTypes().keySet();
      if(!commands.equals(implemented)||!descriptor.operations().stream().map(BusinessObjects.Operation::operationCode)
          .collect(java.util.stream.Collectors.toSet()).containsAll(commands))
        throw new IllegalStateException("BUSINESS_OBJECT_COMMAND_REGISTRATION_INVALID:"+descriptor.objectType());
      for(var action:descriptor.operations()){
      if(commands.contains(action.operationCode())){
        var commandPages=new ArrayList<BusinessObjects.PageOperation>();
        if(action.pageOperation()!=null)commandPages.add(action.pageOperation());
        action.aliases().forEach(alias->commandPages.add(alias.pageOperation()));
        boolean platformCommand=runtime.platformOperation(action.operationCode());
        var commandPermissions=provider.commandPermissions(action);
        if(commandPermissions==null||!commandPermissions.contains(action.permissionCode())
            ||commandPermissions.stream().anyMatch(code->code==null||!code.matches("[a-z][a-z0-9.]*:[a-z][a-z0-9_]*"))
            ||platformCommand&&commandPermissions.size()!=1)
          throw new IllegalStateException("BUSINESS_OBJECT_COMMAND_PERMISSIONS_INVALID:"+action.operationId());
        if(action.permissionCode().isBlank()||commandPages.isEmpty()||commandPages.stream()
            .anyMatch(page->page.apiPath().startsWith("/bff/platform/")!=platformCommand))
          throw new IllegalStateException("BUSINESS_OBJECT_COMMAND_DOMAIN_INVALID:"+action.operationId());
      }
      // 后台和专用 API 可声明不含公共能力的操作；对外提供公共能力必须装配实际对象运行端口。
      if(!action.interaction().capabilities().isEmpty()&&provider.runtime()==null)
        throw new IllegalStateException("BUSINESS_OBJECT_RUNTIME_REQUIRED:"+action.operationId());
      var pages=new ArrayList<BusinessObjects.PageOperation>();if(action.pageOperation()!=null)pages.add(action.pageOperation());
      action.aliases().forEach(alias->pages.add(alias.pageOperation()));
      for(var operation:pages){
        var page=NavigationPages.findRoute(operation.pageKey());
        if(page==null||!page.path().equals(operation.pagePath()))throw new IllegalStateException("BUSINESS_OBJECT_PAGE_PERMISSION_REGISTRATION_INVALID:"+action.operationId());
        boolean found=mappings.getHandlerMethods().keySet().stream().anyMatch(mapping->mapping.getPatternValues().contains(operation.apiPath())&&mapping.getMethodsCondition().getMethods().stream().anyMatch(method->method.name().equals(operation.method())));
        if(!found)throw new IllegalStateException("BUSINESS_OBJECT_API_REGISTRATION_INVALID:"+action.operationId());
        boolean bound=mappings.getHandlerMethods().entrySet().stream().anyMatch(entry->entry.getKey().getPatternValues().contains(operation.apiPath())&&java.util.Arrays.stream(entry.getValue().getMethod().getAnnotationsByType(com.appnexus.contracts.operation.OperationBinding.class)).anyMatch(binding->binding.reference()&&binding.id().equals(action.operationId())));
        // 公共入口按请求中的唯一对象操作动态准入；只认可构建内固定控制器，不能配置任意URL绕过校验。
        if(!bound&&operation.apiPath().equals("/api/business-object-executions"))bound=mappings.getHandlerMethods().entrySet().stream().anyMatch(entry->entry.getKey().getPatternValues().contains(operation.apiPath())&&entry.getValue().getBeanType().equals(com.appnexus.platform.appapi.businessobject.ObjectExecutionController.class));
        if(!bound)throw new IllegalStateException("BUSINESS_OBJECT_OPERATION_BINDING_INVALID:"+action.operationId());

      }
    }}};
  }
}
