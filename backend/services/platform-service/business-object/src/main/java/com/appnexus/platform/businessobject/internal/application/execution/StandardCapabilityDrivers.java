package com.appnexus.platform.businessobject.internal.application.execution;

import com.appnexus.contracts.businessobject.*;
import com.appnexus.contracts.printing.*;
import com.appnexus.contracts.workflow.*;
import java.util.*;
import java.util.function.Supplier;
import static com.appnexus.contracts.businessobject.BusinessObjects.*;

/** 平台只装配一组专业驱动；对象无需重复实现流程启动和打印请求拼装。 */
public final class StandardCapabilityDrivers {
  private StandardCapabilityDrivers() {}

  /**
   * @param providers 同一对象声明及运行能力，不建立第二套对象目录
   * @param workflows 延迟工作流公开端口；未启用时执行拒绝
   * @param printing 延迟打印公开端口；未启用时执行拒绝
   * @return BUSINESS、WORKFLOW 和 PRINT 的公共驱动集合
   */
  public static List<CapabilityDriver> create(List<Provider> providers,Supplier<WorkflowStarter> workflows,Supplier<BusinessPrinting> printing) {
    return List.of(new CapabilityDriver() {
      public InteractionContract.Service service(){return InteractionContract.Service.WORKFLOW;}
      public Map<String,Object> execute(Context c,InteractionContract.Capability capability,Resolution resolution,BusinessObjectRuntime.Snapshot snapshot){
        var port=workflows.get();if(port==null)throw new Rejection(503,"WORKFLOW_UNAVAILABLE");
        if(snapshot.organizationId()==null||snapshot.organizationId().isBlank())throw new Rejection(409,"BUSINESS_OBJECT_ORGANIZATION_REQUIRED");
        var context=new WorkflowContext(c.tenantId(),c.actorId(),c.requestId(),c.personId(),c.organizationId());
        String instance=port.startBound(context,resolution.objectType(),resolution.objectId(),snapshot.version(),snapshot.organizationId(),capability.purpose(),capability.reference(),resolution.id());
        return Map.of("instanceId",instance);
      }
    },new CapabilityDriver(){
      public InteractionContract.Service service(){return InteractionContract.Service.PRINT;}
      public Map<String,Object> execute(Context c,InteractionContract.Capability capability,Resolution resolution,BusinessObjectRuntime.Snapshot snapshot){
        var port=printing.get();if(port==null)throw new Rejection(503,"PRINT_UNAVAILABLE");
        if(!capability.dataContract().equals(resolution.dataContract()))throw new Rejection(409,"BUSINESS_OBJECT_PRINT_CONTRACT_MISMATCH");
        return port.request(new PrintContext(c.tenantId(),c.actorId(),c.requestId()),new BusinessPrinting.Request(
            resolution.sceneCode(),capability.dataContract(),resolution.objectId(),capability.schema().project(snapshot.values()),"PREVIEW",null,1));
      }
    },new CapabilityDriver(){
      public InteractionContract.Service service(){return InteractionContract.Service.BUSINESS;}
      public Map<String,Object> execute(Context c,InteractionContract.Capability capability,Resolution resolution,BusinessObjectRuntime.Snapshot snapshot){
        var runtime=providers.stream().filter(provider->provider.descriptor().objectType().equals(resolution.objectType())).findFirst().orElseThrow().runtime();
        if(runtime==null)throw new Rejection(503,"BUSINESS_OBJECT_EXECUTION_UNAVAILABLE");
        var result=runtime.execute(c,resolution.objectType(),resolution.objectId(),resolution.operationCode(),snapshot.version(),Map.of());
        return Map.of("version",result.version(),"values",result.values());
      }
    });
  }
}
