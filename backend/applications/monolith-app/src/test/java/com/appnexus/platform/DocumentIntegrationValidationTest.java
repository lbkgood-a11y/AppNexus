package com.appnexus.platform;
import com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.appnexus.leave.adapter.web.LeavePocController;

import com.appnexus.platform.bootstrap.businessobject.BusinessObjectConfiguration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.appnexus.leave.application.LeavePocService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** 通用装配校验测试真实路径模式与权限目录，不以某一业务调用成功代替集成声明校验。 */
class BusinessObjectIntegrationValidationTest {
  private Provider provider(Operation action){
    return new Provider(){
      @Override public Descriptor descriptor(){return new Descriptor("LeaveRequest","请假","Leave","leave",1,Category.TRANSACTION,List.of(),List.of(action));}
      @Override public com.appnexus.contracts.businessobject.BusinessObjectRuntime runtime(){
        return mock(com.appnexus.contracts.businessobject.BusinessObjectRuntime.class);
      }
    };
  }

  /** 已声明公共能力的对象缺少实际运行端口时，连启动目录也不得暴露该能力。 */
  @Test void unassembledCapabilityFailsClosed(){
    var action=com.appnexus.leave.api.LeaveOperations.SUBMIT_OPERATION;
    Provider declaration=()->provider(action).descriptor();
    assertThatThrownBy(()->new BusinessObjectIntegrationValidation()
        .validateDocumentIntegration(List.of(declaration),mock(RequestMappingHandlerMapping.class))
        .afterSingletonsInstantiated())
        .hasMessage("BUSINESS_OBJECT_RUNTIME_REQUIRED:"+action.operationId());
  }
  @Test void registeredPageAndHandlerPass() throws Exception {
    var mappings=mock(RequestMappingHandlerMapping.class);
    when(mappings.getHandlerMethods()).thenReturn(Collections.singletonMap(RequestMappingInfo.paths("/api/leave-poc/{id}/submit").methods(RequestMethod.POST).build(),new org.springframework.web.method.HandlerMethod(mock(com.appnexus.leave.adapter.web.LeavePocController.class),com.appnexus.leave.adapter.web.LeavePocController.class.getMethod("submit",jakarta.servlet.http.HttpServletRequest.class,String.class,com.appnexus.leave.adapter.web.LeavePocController.SubmitOperation.class))));
    assertThatCode(()->new BusinessObjectIntegrationValidation().validateDocumentIntegration(List.of(provider(com.appnexus.leave.api.LeaveOperations.SUBMIT_OPERATION)),mappings).afterSingletonsInstantiated()).doesNotThrowAnyException();
  }
  @Test void missingHandlerFailsClosed(){
    var mappings=mock(RequestMappingHandlerMapping.class);when(mappings.getHandlerMethods()).thenReturn(Map.of());
    assertThatThrownBy(()->new BusinessObjectIntegrationValidation().validateDocumentIntegration(List.of(provider(com.appnexus.leave.api.LeaveOperations.SUBMIT_OPERATION)),mappings).afterSingletonsInstantiated()).hasMessageContaining("BUSINESS_OBJECT_API_REGISTRATION_INVALID");
  }
  @Test void unknownPageDoesNotBecomeConfigurable(){
    var a=com.appnexus.leave.api.LeaveOperations.SUBMIT_OPERATION;var wrong=new Operation(a.operationCode(),a.nameZh(),a.nameEn(),a.permissionCode(),a.operationId(),new PageOperation("unknown","/unknown","未知","Unknown","POST","/api/leave-poc/{id}/submit"),java.util.List.of(),a.interaction());
    assertThatThrownBy(()->new BusinessObjectIntegrationValidation().validateDocumentIntegration(List.of(provider(wrong)),mock(RequestMappingHandlerMapping.class)).afterSingletonsInstantiated()).hasMessageContaining("BUSINESS_OBJECT_PAGE_PERMISSION_REGISTRATION_INVALID");
  }
  /** 专业服务目录必须来自同一声明，打印字段改动不得只更新某一个目录。 */
  @Test void printingDirectoryProjectsCanonicalInteraction() {
    var provider = new com.appnexus.platform.bootstrap.businessobject.BusinessObjectConfiguration()
        .interactionPrintContracts(List.of(provider(com.appnexus.leave.api.LeaveOperations.PRINT_OPERATION)));
    var contract = provider.contracts().getFirst();
    var capability = com.appnexus.leave.api.LeaveOperations.PRINT_OPERATION.interaction().capabilities().getFirst();
    assertThat(contract.get("code")).isEqualTo(capability.reference());
    assertThat((List<?>)contract.get("fields")).hasSize(capability.fields().size());
    assertThat(contract.toString()).contains("documentNumber","startDate","endDate","days","state");
  }

  /** 声明不支持的完成方式必须在构建时失败，不能误报已经同步完成审批。 */
  @Test void unsupportedServiceCompletionFailsClosed() {
    assertThatThrownBy(() -> new com.appnexus.contracts.businessobject.InteractionContract.Capability(
        com.appnexus.contracts.businessobject.InteractionContract.Service.WORKFLOW,
        com.appnexus.contracts.businessobject.InteractionContract.Completion.SYNCHRONOUS,
        "leavePoc","approval",List.of(),Set.of("APPROVED"), "leavePoc.data", 1))
        .hasMessage("INTERACTION_COMPLETION_UNSUPPORTED");
  }

  /** 已声明的类型化操作漏接运行端口时阻止装配。 */
  @Test void typedCommandWithoutRuntimeFailsAtStartup(){
    Provider declaration=new Provider(){
      @Override public Descriptor descriptor(){return provider(com.appnexus.leave.api.LeaveOperations.SUBMIT_OPERATION).descriptor();}
      @Override public Set<String> commandOperations(){return Set.of("submit");}
    };
    assertThatThrownBy(()->new BusinessObjectIntegrationValidation()
        .validateDocumentIntegration(List.of(declaration),mock(RequestMappingHandlerMapping.class))
        .afterSingletonsInstantiated())
        .hasMessage("BUSINESS_OBJECT_COMMAND_REGISTRATION_INVALID:LeaveRequest");
  }

}
