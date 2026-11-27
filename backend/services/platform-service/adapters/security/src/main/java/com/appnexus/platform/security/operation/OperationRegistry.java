package com.appnexus.platform.security.operation;
import com.appnexus.contracts.operation.OperationBinding;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
/** 从真实 MVC 方法聚合显式操作声明；不代替业务授权。 */
@Component
public final class OperationRegistry implements com.appnexus.platform.configuration.PageOperationCatalog {
 private final RequestMappingHandlerMapping mappings;
 private final List<com.appnexus.contracts.businessobject.BusinessObjects.Provider> providers;
 /** 仅缓存不可变构建元数据，不缓存用户能力；MVC 映射由启动校验完成后固定。 */
 private List<Definition> snapshot;
 public OperationRegistry(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings,List<com.appnexus.contracts.businessobject.BusinessObjects.Provider> providers){this.mappings=mappings;this.providers=List.copyOf(providers);}
 /**
  * 真实操作定义。
  * @param id 全局稳定操作标识
  * @param objectType 明确登记的业务/系统对象编码
  * @param operationCode 对象内操作编码
  * @param pages 页面标识列表
  * @param nameZh 中文名称
  * @param nameEn 英文名称
  * @param permissions OR-of-AND 权限表达式
  * @param policy 额外执行约束
  * @param endpoints 真实方法与路径
  * @param eligibility 仅用于入口准入的权限/本人/对象策略
  */
 public record Definition(String id,String objectType,String operationCode,List<String> pages,String nameZh,String nameEn,List<String> permissions,String policy,List<String> endpoints,OperationBinding.Eligibility eligibility) {}
 /** 对象目录项；可配置能力与只读功能目录分开，不虚构配置入口。 */
 public record ObjectDefinition(String code,String nameZh,String nameEn,String category,String module,boolean configurable,List<Definition> operations) {}
 /** 汇总所有模块明确声明及业务 Provider，不根据页面名猜测分类；漏登记阻止启动。 */
 public List<ObjectDefinition> objects(){
  var definitions=new TreeMap<String,ObjectDefinition>();
  for(var provider:providers){var d=provider.descriptor();var value=new ObjectDefinition(d.objectType(),d.nameZh(),d.nameEn(),d.category().name(),d.module(),true,List.of());if(definitions.putIfAbsent(value.code(),value)!=null)throw new IllegalStateException("对象存在重复权威定义："+value.code());}
  var operations=all();for(var operation:operations)if(!definitions.containsKey(operation.objectType()))throw new IllegalStateException("操作所属对象未登记："+operation.id());
  return definitions.values().stream().map(value->new ObjectDefinition(value.code(),value.nameZh(),value.nameEn(),value.category(),value.module(),value.configurable(),operations.stream().filter(operation->operation.objectType().equals(value.code())).toList())).toList();
 }
 /** 页面权限来自真实操作契约；不把页面基础权限自动扩成全部业务资源。 */
 @Override public List<String> permissions(String page){if(page==null)return List.of();return all().stream().filter(item->item.pages().contains(page)).flatMap(item->item.permissions().stream()).flatMap(group->Arrays.stream(group.split("&"))).distinct().sorted().toList();}
 private static String route(com.appnexus.contracts.businessobject.BusinessObjects.PageOperation page){
  return page.method()+" "+page.apiPath();
 }
 private static Set<String> declaredRoutes(com.appnexus.contracts.businessobject.BusinessObjects.Operation operation){
  var declared=new HashSet<String>();
  if(operation.pageOperation()!=null)declared.add(route(operation.pageOperation()));
  operation.aliases().forEach(alias->declared.add(route(alias.pageOperation())));
  return declared;
 }
 private static Definition withEndpoints(Definition value,List<String> endpoints){
  return new Definition(value.id(),value.objectType(),value.operationCode(),value.pages(),value.nameZh(),
    value.nameEn(),value.permissions(),value.policy(),endpoints,value.eligibility());
 }
 /** 聚合当前构建目录，同一操作只合并已显式声明且元数据一致的入口。 */
 public synchronized List<Definition> all(){
  if(snapshot!=null)return snapshot;
  var result=new TreeMap<String,Definition>();
  mappings.getHandlerMethods().forEach((mapping,handler)->{
   for(var binding:handler.getMethod().getAnnotationsByType(OperationBinding.class)){
    var endpoints=new TreeSet<String>();
    for(var path:mapping.getPatternValues())for(var method:mapping.getMethodsCondition().getMethods())endpoints.add(method.name()+" "+path);
    if(!binding.reference())throw new IllegalStateException("HTTP 入口必须引用对象操作："+binding.id());
    if(binding.enforceDeclaredPermission() && (handler.getMethod().getAnnotationsByType(OperationBinding.class).length!=1
      || binding.eligibility()!=OperationBinding.Eligibility.PERMISSION || endpoints.isEmpty()
      || (!endpoints.stream().allMatch(route->route.contains(" /api/"))
          && !endpoints.stream().allMatch(route->route.contains(" /bff/platform/")))))
     throw new IllegalStateException("对象权限门禁需要单一管理域的操作引用："+binding.id());
    var matches=providers.stream().flatMap(provider->provider.descriptor().operations().stream().filter(operation->operation.operationId().equals(binding.id())).map(operation->Map.entry(provider.descriptor(),operation))).toList();
    if(matches.size()!=1)throw new IllegalStateException("操作引用不存在或不唯一："+binding.id());
    var descriptor=matches.getFirst().getKey();var operation=matches.getFirst().getValue();var pages=new TreeSet<String>();
    if(operation.pageOperation()!=null)pages.add(operation.pageOperation().pageKey());operation.aliases().forEach(alias->pages.add(alias.pageOperation().pageKey()));
    if(binding.enforceDeclaredPermission()){
     // 一次操作只进入一个路由管理域。对象的行和字段资源域可与管理操作的域不同。
     if(!operation.permissionCode().matches("[a-z][a-z0-9.]*:[a-z][a-z0-9_]*"))
      throw new IllegalStateException("对象权限门禁需要单一权限码："+binding.id());
     var declared=declaredRoutes(operation);
     // 无页面的专用操作由 MVC 映射提供路由；有页面的操作还必须匹配声明的入口。
     if(!declared.isEmpty()&&!declared.containsAll(endpoints))
      throw new IllegalStateException("对象权限门禁的 API 未在对象声明登记："+binding.id());
    }
    var value=new Definition(operation.operationId(),descriptor.objectType(),operation.operationCode(),List.copyOf(pages),operation.nameZh(),operation.nameEn(),operation.permissionCode().isBlank()?List.of():List.of(operation.permissionCode()),"业务对象归属、版本和操作状态由原业务用例复核",List.copyOf(endpoints),binding.eligibility());
    var prior=result.putIfAbsent(value.id(),value);
    if(prior!=null&&!prior.equals(value)){
     if(!withEndpoints(prior,value.endpoints()).equals(value))
      throw new IllegalStateException("重复操作契约："+value.id());
     var combined=new TreeSet<>(prior.endpoints());
     combined.addAll(value.endpoints());
     // 仅合并 Provider 明确登记的同一操作入口，避免新路由隐式复用已有权限。
     if(!declaredRoutes(operation).containsAll(combined))throw new IllegalStateException("重复操作契约："+value.id());
     result.put(value.id(),withEndpoints(value,List.copyOf(combined)));
    }
   }
  });
  // 公共对象执行入口不为每个对象重复标注 Controller；仍须证明声明指向真实 MVC 路由。
  for(var provider:providers){
   var descriptor=provider.descriptor();
   for(var operation:descriptor.operations()){
    if(result.containsKey(operation.operationId()))continue;
    var page=operation.pageOperation();
    if(page==null||!"POST".equals(page.method())||!"/api/business-object-executions".equals(page.apiPath())||operation.supportedServices().isEmpty())continue;
    boolean mapped=mappings.getHandlerMethods().keySet().stream().anyMatch(mapping->mapping.getPatternValues().contains(page.apiPath())&&mapping.getMethodsCondition().getMethods().contains(org.springframework.web.bind.annotation.RequestMethod.POST));
    if(!mapped)throw new IllegalStateException("OBJECT_EXECUTION_ENDPOINT_MISSING:"+operation.operationId());
    var pages=new TreeSet<String>();pages.add(page.pageKey());operation.aliases().forEach(alias->pages.add(alias.pageOperation().pageKey()));
    result.put(operation.operationId(),new Definition(operation.operationId(),descriptor.objectType(),operation.operationCode(),List.copyOf(pages),operation.nameZh(),operation.nameEn(),List.of(operation.permissionCode()),"公共执行器复核对象权限、字段、版本及业务状态",List.of("POST "+page.apiPath()),OperationBinding.Eligibility.OBJECT));
   }
  }
  snapshot=List.copyOf(result.values());return snapshot;
 }
}
