package com.appnexus.platform;
import com.appnexus.contracts.businessobject.BusinessObjects.Provider;
import com.appnexus.platform.security.operation.OperationRegistry;
import com.appnexus.platform.configuration.NavigationPages;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.*;
/** 启动时检查操作契约，禁止虚构页面、空接口或重复标识进入功能目录。 */
@Configuration(proxyBeanMethods=false)
public class OperationContractValidation {
 /** 聚合在 MVC 处理方法就绪后执行；不能用目录校验替代真实授权。 */
 @Bean public SmartInitializingSingleton validateOperationContracts(OperationRegistry registry,List<Provider> providers){return ()->{
  var dedicatedOperations=providers.stream().flatMap(provider->provider.descriptor().operations().stream())
    .filter(operation->operation.pageOperation()==null&&operation.aliases().isEmpty()&&operation.supportedServices().isEmpty())
    .map(operation->operation.operationId()).collect(java.util.stream.Collectors.toUnmodifiableSet());
  registry.objects();
  for(var operation:registry.all()){
   // 身份格式和入口完整性是构建契约，不能等待用户点击后才发现缺失。
   if(!operation.objectType().matches("[A-Za-z][A-Za-z0-9_]*")||!operation.operationCode().matches("[A-Za-z][A-Za-z0-9_]*"))throw new IllegalStateException("对象或操作编码无效："+operation.id());
   // 无页面的专用 API 仍须来自唯一 Provider，并登记真实 HTTP 入口。
   if(operation.pages().isEmpty()&&!dedicatedOperations.contains(operation.id()))throw new IllegalStateException("操作缺少页面登记："+operation.id());
   if(operation.eligibility()==com.appnexus.contracts.operation.OperationBinding.Eligibility.PERMISSION&&operation.permissions().isEmpty())throw new IllegalStateException("权限操作缺少授权表达式："+operation.id());
   if(!operation.id().equals(operation.objectType()+"."+operation.operationCode()))throw new IllegalStateException("对象操作身份不一致："+operation.id());
   if(operation.id().isBlank()||operation.nameZh().isBlank()||operation.nameEn().isBlank()||operation.policy().isBlank()||operation.endpoints().isEmpty())throw new IllegalStateException("操作契约不完整："+operation.id());
   for(var page:operation.pages())if(NavigationPages.find(page)==null&&!page.equals("account/profile"))throw new IllegalStateException("未知操作页面："+page+":"+operation.id());
   validatePermissions(operation,dedicatedOperations);
  }
 };}

 /** 合作方 OAuth scope 只用于无页面的合作方专用入口，账号权限仍按平台权限表达式校验。 */
 private void validatePermissions(OperationRegistry.Definition operation,Set<String> dedicatedOperations){
  boolean partner=operation.pages().isEmpty()&&dedicatedOperations.contains(operation.id())
      &&operation.endpoints().stream().allMatch(endpoint->endpoint.matches("[A-Z]+ /v1/.+"));
  for(var group:operation.permissions())for(var code:group.split("&",-1)){
   if(code.matches("[a-z][a-z0-9.]*:[a-z][a-z0-9_]*"))continue;
   if(partner&&code.matches("SCOPE_[a-z][a-z0-9.-]*:[a-z][a-z0-9_-]*"))continue;
   throw new IllegalStateException("操作权限表达式无效："+operation.id());
  }
 }
}
