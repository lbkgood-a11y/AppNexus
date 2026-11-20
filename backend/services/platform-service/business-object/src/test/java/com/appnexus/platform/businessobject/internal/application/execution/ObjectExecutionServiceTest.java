package com.appnexus.platform.businessobject.internal.application.execution;

import com.appnexus.contracts.businessobject.*;
import com.appnexus.platform.businessobject.port.ConfigurationTransaction;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.appnexus.contracts.businessobject.BusinessObjects.*;

/** 证明专业驱动只收到对象契约允许的数据，陈旧版本和跨对象解析在执行前拒绝。 */
class ObjectExecutionServiceTest {
  final Context context=new Context("tenant","actor","request","person","org");
  final BusinessObjectRuntime runtime=mock(BusinessObjectRuntime.class);
  final BusinessObjectBindings bindings=mock(BusinessObjectBindings.class);
  final CapabilityDriver driver=mock(CapabilityDriver.class);
  final BusinessObjectPlatform platform=mock(BusinessObjectPlatform.class);
  final Operation operation=new Operation("print","打印","Print","test:print","Example.print",null,List.of(),
      new InteractionContract("Example.print",1,"Example","print","打印","Print",InteractionContract.Trigger.USER_ACTION,List.of(
          new InteractionContract.Capability(InteractionContract.Service.PRINT,InteractionContract.Completion.CONFIRMATION,"example","",List.of(new Field("label","标签","Label","TEXT","",true)),Set.of(),"example",1))));
  final Provider provider=new Provider(){
    public Descriptor descriptor(){return new Descriptor("Example","对象","Example","test",1,Category.TRANSACTION,List.of(new Field("amount","金额","Amount","NUMBER","元",true)),List.of(operation));}
    public BusinessObjectRuntime runtime(){return runtime;}
  };
  final ConfigurationTransaction transaction=new ConfigurationTransaction(){
    public void run(Runnable work){work.run();}
    public <T>T call(Supplier<T> work){return work.get();}
  };
  final BusinessObjectExecution.Request request=new BusinessObjectExecution.Request("Example","record","print","Example.print",2);
  Resolution resolution(String tenant,String object){return new Resolution("resolution",tenant,"Example",object,2,1,null,0,Map.of("amount","2"),"print","PRINT","scene","example");}
  ObjectExecutionService service(){
    when(runtime.read(any(),anyString(),anyString(),anyString(),anyLong(),anySet())).thenReturn(new BusinessObjectRuntime.Snapshot(2,"org",Map.of("amount",2,"label","test","secret","private")));
    when(bindings.resolveOperation(any(),anyString(),anyString(),anyString(),anyLong(),anyMap())).thenReturn(resolution("tenant","record"));
    when(platform.active(any())).thenReturn(true);
    when(platform.authorizeRecord(any(),any(),anyString(),any())).thenAnswer(call->call.getArgument(3));
    when(platform.operationAllowed(any(),anyString(),anyString())).thenReturn(true);
    when(driver.service()).thenReturn(InteractionContract.Service.PRINT);
    when(driver.execute(any(),any(),any(),any())).thenReturn(Map.of("id","output"));
    return new ObjectExecutionService(List.of(provider),List.of(driver),bindings,transaction,platform);
  }
  @Test void projectsFieldsAndPreservesIdentity(){
    assertThat(service().execute(context,request).output()).containsEntry("id","output");
    var capture=org.mockito.ArgumentCaptor.forClass(BusinessObjectRuntime.Snapshot.class);
    verify(driver).execute(eq(context),eq(operation.interaction().capabilities().getFirst()),any(),capture.capture());
    assertThat(capture.getValue().values()).containsOnlyKeys("label");
    verify(bindings).resolveOperation(context,"Example","print","record",2,Map.of("amount","2"));
  }
  @Test void nullableOutputMetadataIsPreservedAndDetached(){
    var service=service();var output=new HashMap<String,Object>();output.put("id","output");output.put("deviceId",null);
    when(driver.execute(any(),any(),any(),any())).thenReturn(output);
    var result=service.execute(context,request);output.put("id","changed");
    assertThat(result.output()).containsEntry("id","output").containsEntry("deviceId",null);
    assertThatThrownBy(()->result.output().put("id","changed")).isInstanceOf(UnsupportedOperationException.class);
  }
  @Test void staleSnapshotCannotResolveOrExecute(){
    var service=service();when(runtime.read(any(),anyString(),anyString(),anyString(),anyLong(),anySet())).thenReturn(new BusinessObjectRuntime.Snapshot(3,"org",Map.of()));
    assertThatThrownBy(()->service.execute(context,request)).hasMessage("BUSINESS_OBJECT_VERSION_CONFLICT");
    verify(bindings,never()).resolveOperation(any(),anyString(),anyString(),anyString(),anyLong(),anyMap());
    verify(driver,never()).execute(any(),any(),any(),any());
  }
  @Test void spoofedResolutionCannotReachDriver(){
    var service=service();
    for(var invalid:List.of(resolution("other","record"),resolution("tenant","different"))){
      when(bindings.resolveOperation(any(),anyString(),anyString(),anyString(),anyLong(),anyMap())).thenReturn(invalid);
      assertThatThrownBy(()->service.execute(context,request)).hasMessage("BUSINESS_OBJECT_RESOLUTION_MISMATCH");
    }
    verify(driver,never()).execute(any(),any(),any(),any());
  }
  @Test void unknownEntryCannotReadRecord(){
    var service=service();assertThatThrownBy(()->service.execute(context,new BusinessObjectExecution.Request("Example","record","print","Other.print",2))).hasMessage("BUSINESS_OBJECT_OPERATION_UNAVAILABLE");
    verify(runtime,never()).read(any(),anyString(),anyString(),anyString(),anyLong(),anySet());
  }
  @Test void deniedPageStopsBeforeReading(){
    var service=service();doThrow(new Rejection(403,"DENIED")).when(bindings).verifyPageOperation(any(),anyString(),anyString(),anyString());
    assertThatThrownBy(()->service.execute(context,request)).hasMessage("DENIED");
    verify(runtime,never()).read(any(),anyString(),anyString(),anyString(),anyLong(),anySet());
  }
  @Test void revokedPermissionStopsBeforeReading(){
    var service=service();when(platform.operationAllowed(any(),anyString(),anyString())).thenReturn(false);
    assertThatThrownBy(()->service.execute(context,request)).hasMessage("BUSINESS_OBJECT_ACCESS_DENIED");
    verify(runtime,never()).read(any(),anyString(),anyString(),anyString(),anyLong(),anySet());
    verify(driver,never()).execute(any(),any(),any(),any());
  }
  @Test void deniedRecordScopeNeverResolvesOrDispatches(){
    var service=service();when(platform.authorizeRecord(any(),any(),anyString(),any())).thenThrow(new Rejection(403,"BUSINESS_OBJECT_DATA_ACCESS_DENIED"));
    assertThatThrownBy(()->service.execute(context,request)).hasMessage("BUSINESS_OBJECT_DATA_ACCESS_DENIED");
    verify(bindings,never()).resolveOperation(any(),anyString(),anyString(),anyString(),anyLong(),anyMap());
    verify(driver,never()).execute(any(),any(),any(),any());
  }
  @Test void missingRequiredProjectionStopsDriver(){
    var service=service();when(runtime.read(any(),anyString(),anyString(),anyString(),anyLong(),anySet())).thenReturn(new BusinessObjectRuntime.Snapshot(2,"org",Map.of("amount",2)));
    assertThatThrownBy(()->service.execute(context,request)).hasMessage("DATA_FIELD_REQUIRED");
    verify(driver,never()).execute(any(),any(),any(),any());
  }
  /** 专用API没有公共命令协议时，应在正文读取、绑定解析及能力写入前失败关闭。 */
  @Test void apiOnlyOperationNeverReadsResolvesOrDispatches(){
    var apiOperation=new Operation("edit","编辑","Edit","test:edit","ApiOnly.edit",null,List.of(),new InteractionContract("ApiOnly.edit",2,"ApiOnly","edit","编辑","Edit",InteractionContract.Trigger.USER_ACTION,List.of()));
    Provider apiProvider=new Provider(){
      public Descriptor descriptor(){return new Descriptor("ApiOnly","专用入口","API only","test",2,Category.MASTER_DATA,List.of(),List.of(apiOperation));}
      public BusinessObjectRuntime runtime(){return runtime;}
    };
    var executor=new ObjectExecutionService(List.of(apiProvider),List.of(),bindings,transaction,platform);
    assertThatThrownBy(()->executor.execute(context,new BusinessObjectExecution.Request("ApiOnly","record","edit","ApiOnly.edit",0))).hasMessage("BUSINESS_OBJECT_OPERATION_API_ONLY");
    verifyNoInteractions(runtime,bindings,driver,platform);
  }
}
