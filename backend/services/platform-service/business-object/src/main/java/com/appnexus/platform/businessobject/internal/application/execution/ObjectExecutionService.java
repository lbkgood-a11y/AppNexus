package com.appnexus.platform.businessobject.internal.application.execution;

import com.appnexus.contracts.businessobject.*;
import com.appnexus.contracts.data.DataContract;
import com.appnexus.platform.businessobject.port.ConfigurationTransaction;
import java.util.*;
import static com.appnexus.contracts.businessobject.BusinessObjects.*;

/** 统一对象操作执行器；禁止客户端选择专业服务目标或注入业务事实。 */
public final class ObjectExecutionService implements BusinessObjectExecution {
  private final Map<String,Provider> objects;
  private final Map<InteractionContract.Service,CapabilityDriver> drivers;
  private final BusinessObjectBindings bindings;
  private final ConfigurationTransaction transaction;
  private final BusinessObjectPlatform platform;

  /**
   * @param providers 唯一对象声明和对象级运行端口，重复身份拒绝装配
   * @param drivers 平台专业能力驱动，不是逐对象处理器
   * @param bindings 真实关联解析和页面启停入口
   * @param platform 与编码共用的实时对象操作准入，默认拒绝
   * @param transaction 与对象及能力本地写入共用数据源的 REQUIRED 事务
   */
  public ObjectExecutionService(List<Provider> providers,List<CapabilityDriver> drivers,
      BusinessObjectBindings bindings,ConfigurationTransaction transaction,BusinessObjectPlatform platform) {
    var objectMap=new HashMap<String,Provider>();
    for(var provider:providers){
      if(objectMap.putIfAbsent(provider.descriptor().objectType(),provider)!=null)throw new IllegalArgumentException("BUSINESS_OBJECT_DUPLICATE");
      for(var operation:provider.descriptor().operations())if(operation.interaction().capabilities().stream().map(InteractionContract.Capability::service).distinct().count()!=operation.interaction().capabilities().size())
        throw new IllegalArgumentException("BUSINESS_OBJECT_SERVICE_DUPLICATE");
    }
    var driverMap=new EnumMap<InteractionContract.Service,CapabilityDriver>(InteractionContract.Service.class);
    for(var driver:drivers)if(driverMap.putIfAbsent(driver.service(),driver)!=null)
      throw new IllegalArgumentException("CAPABILITY_DRIVER_DUPLICATE");
    this.objects=Map.copyOf(objectMap);this.drivers=Map.copyOf(driverMap);
    this.bindings=Objects.requireNonNull(bindings);this.transaction=Objects.requireNonNull(transaction);this.platform=Objects.requireNonNull(platform);
  }

  @Override public Result execute(Context context,Request request) {
    Objects.requireNonNull(context);Objects.requireNonNull(request);
    if(request.basisVersion()<0)throw new Rejection(400,"BUSINESS_OBJECT_VERSION_INVALID");
    return transaction.call(()->executeLocked(context,request));
  }

  private Result executeLocked(Context context,Request request) {
    var provider=objects.get(request.objectType());
    if(provider==null||provider.runtime()==null)throw new Rejection(403,"BUSINESS_OBJECT_EXECUTION_UNAVAILABLE");
    var descriptor=provider.descriptor();
    var operation=descriptor.operations().stream().filter(value->value.operationCode().equals(request.operationCode()))
        .findFirst().orElseThrow(()->new Rejection(403,"BUSINESS_OBJECT_OPERATION_UNAVAILABLE"));
    // 仅专用API的操作没有公共命令协议；在读取记录或创建关联解析前拒绝，不伪造默认输入。
    if(operation.interaction().capabilities().isEmpty())throw new Rejection(403,"BUSINESS_OBJECT_OPERATION_API_ONLY");
    if(!operation.operationId().equals(request.operationId())&&operation.aliases().stream().noneMatch(alias->alias.operationId().equals(request.operationId())))
      throw new Rejection(403,"BUSINESS_OBJECT_OPERATION_UNAVAILABLE");
    if(!platform.active(context)||!platform.operationAllowed(context,operation.operationId(),operation.permissionCode()))throw new Rejection(403,"BUSINESS_OBJECT_ACCESS_DENIED");
    bindings.verifyPageOperation(context,descriptor.objectType(),operation.operationCode(),request.operationId());
    var fields=new HashSet<String>();descriptor.fields().forEach(field->fields.add(field.key()));
    operation.interaction().capabilities().forEach(capability->capability.fields().forEach(field->fields.add(field.key())));
    // 运行端口负责实时统一授权及行锁；只请求契约字段，不能以关联配置拓宽数据访问。
    var original=provider.runtime().read(context,descriptor.objectType(),request.objectId(),operation.operationCode(),request.basisVersion(),Set.copyOf(fields));
    var snapshot=platform.authorizeRecord(context,descriptor,operation.operationCode(),original);
    if(snapshot.version()!=request.basisVersion())throw new Rejection(409,"BUSINESS_OBJECT_VERSION_CONFLICT");
    var factsSchema=new DataContract(descriptor.objectType()+".facts",descriptor.version(),"FACTS",descriptor.fields().stream()
        .map(field->new DataContract.Field(field.key(),DataContract.Type.valueOf(field.type()),field.required(),field.unit())).toList());
    var facts=factsSchema.encode(snapshot.values());
    var resolution=bindings.resolveOperation(context,descriptor.objectType(),operation.operationCode(),request.objectId(),snapshot.version(),facts);
    if(!Objects.equals(resolution.tenantId(),context.tenantId())||!Objects.equals(resolution.objectType(),descriptor.objectType())
        ||!Objects.equals(resolution.objectId(),request.objectId())||resolution.basisVersion()!=snapshot.version()
        ||!Objects.equals(resolution.operationCode(),operation.operationCode())||!resolution.facts().equals(facts))
      throw new Rejection(409,"BUSINESS_OBJECT_RESOLUTION_MISMATCH");
    var capability=operation.interaction().capabilities().stream().filter(value->value.service().name().equals(resolution.service()))
        .findFirst().orElseThrow(()->new Rejection(409,"BUSINESS_OBJECT_SERVICE_UNDECLARED"));
    var driver=drivers.get(capability.service());
    if(driver==null)throw new Rejection(503,"BUSINESS_OBJECT_DRIVER_UNAVAILABLE");
    var projected=capability.schema().project(snapshot.values());
    provider.runtime().beforeCapability(context,request.objectId(),operation.operationCode(),capability.service());
    return new Result(resolution,driver.execute(context,capability,resolution,
        new BusinessObjectRuntime.Snapshot(snapshot.version(),snapshot.organizationId(),projected)));
  }
}
