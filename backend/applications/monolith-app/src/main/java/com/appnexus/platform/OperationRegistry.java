package com.appnexus.platform;
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
  var declarations=new HashSet<java.lang.reflect.AnnotatedElement>();
  mappings.getHandlerMethods().values().forEach(handler->{
   java.lang.reflect.AnnotatedElement element=handler.getMethod();
   var object=handler.getMethodAnnotation(com.appnexus.contracts.operation.BusinessObjectType.class);
   if(object==null){element=handler.getBeanType();object=org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(),com.appnexus.contracts.operation.BusinessObjectType.class);}
   if(object!=null&&!declarations.add(element))return;
   if(object!=null){var value=new ObjectDefinition(object.code(),object.nameZh(),object.nameEn(),object.category().name(),object.module(),false,List.of());var old=definitions.putIfAbsent(value.code(),value);if(old!=null)throw new IllegalStateException("对象存在重复定义："+value.code());}
  });
  for(var provider:providers){var d=provider.descriptor();var value=new ObjectDefinition(d.objectType(),d.nameZh(),d.nameEn(),d.category().name(),d.module(),true,List.of());if(definitions.putIfAbsent(value.code(),value)!=null)throw new IllegalStateException("对象存在重复权威定义："+value.code());}
  var operations=all();for(var operation:operations)if(!definitions.containsKey(operation.objectType()))throw new IllegalStateException("操作所属对象未登记："+operation.id());
  return definitions.values().stream().map(value->new ObjectDefinition(value.code(),value.nameZh(),value.nameEn(),value.category(),value.module(),value.configurable(),operations.stream().filter(operation->operation.objectType().equals(value.code())).toList())).toList();
 }
 /** 页面权限来自真实操作契约；不把页面基础权限自动扩成全部业务资源。 */
 @Override public List<String> permissions(String page){if(page==null)return List.of();return all().stream().filter(item->item.pages().contains(page)).flatMap(item->item.permissions().stream()).flatMap(group->Arrays.stream(group.split("&"))).distinct().sorted().toList();}
 /** 聚合当前构建目录；重复标识的不同定义失败，防止静默覆盖。 */
 public synchronized List<Definition> all(){
  if(snapshot!=null)return snapshot;
  var result=new TreeMap<String,Definition>();
  mappings.getHandlerMethods().forEach((mapping,handler)->{
   for(var binding:handler.getMethod().getAnnotationsByType(OperationBinding.class)){
    var endpoints=new TreeSet<String>();
    for(var path:mapping.getPatternValues())for(var method:mapping.getMethodsCondition().getMethods())endpoints.add(method.name()+" "+path);
    var permissions=List.of(binding.permissions());
    // 平台代管使用独立平台权限，不把租户权限作为可择一的替代来源。
    var endpoint=handler.getMethodAnnotation(com.appnexus.platform.security.EndpointOperation.class);
    if(endpoint!=null){
     if(!permissions.isEmpty())throw new IllegalStateException("接口权限已由 EndpointOperation 声明，禁止重复："+binding.id());
     boolean managed=endpoints.stream().anyMatch(value->value.contains(" /bff/"))&&!endpoint.platformPermission().isBlank();permissions=List.of(managed?endpoint.platformPermission():endpoint.value());}
    var value=new Definition(binding.id(),binding.objectType(),binding.operationCode(),List.of(binding.pages()),binding.nameZh(),binding.nameEn(),permissions,binding.policy(),List.copyOf(endpoints),binding.eligibility());
    if(binding.reference()){
     if(!binding.objectType().isEmpty()||!binding.operationCode().isEmpty()||!binding.nameZh().isEmpty()||!binding.nameEn().isEmpty()||binding.pages().length>0||binding.permissions().length>0)throw new IllegalStateException("引用操作禁止重复声明："+binding.id());
     var matches=providers.stream().flatMap(provider->provider.descriptor().operations().stream().filter(operation->operation.operationId().equals(binding.id())).map(operation->Map.entry(provider.descriptor(),operation))).toList();
     if(matches.size()!=1)throw new IllegalStateException("操作引用不存在或不唯一："+binding.id());
     var descriptor=matches.getFirst().getKey();var operation=matches.getFirst().getValue();var pages=new TreeSet<String>();
     if(operation.pageOperation()!=null)pages.add(operation.pageOperation().pageKey());operation.aliases().forEach(alias->pages.add(alias.pageOperation().pageKey()));
     value=new Definition(operation.operationId(),descriptor.objectType(),operation.operationCode(),List.copyOf(pages),operation.nameZh(),operation.nameEn(),List.of(operation.permissionCode()),"业务对象归属、版本和操作状态由原业务用例复核",List.copyOf(endpoints),binding.eligibility());
    }
    var prior=result.putIfAbsent(value.id(),value);
    if(prior!=null&&!prior.equals(value))throw new IllegalStateException("重复操作契约："+value.id());
   }
  });snapshot=List.copyOf(result.values());return snapshot;
 }
}
