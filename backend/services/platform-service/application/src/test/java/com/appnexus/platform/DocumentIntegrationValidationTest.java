package com.appnexus.platform;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.appnexus.contracts.document.Documents.*;
import com.appnexus.leave.LeavePocService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** 通用装配校验测试真实路径模式与权限目录，不以某一业务调用成功代替集成声明校验。 */
class DocumentIntegrationValidationTest {
  private Provider provider(Action action){return ()->new Descriptor("LeaveRequest","请假","Leave","leave",1,List.of(),List.of(action));}
  @Test void registeredPageAndHandlerPass(){
    var mappings=mock(RequestMappingHandlerMapping.class);
    when(mappings.getHandlerMethods()).thenReturn(Collections.singletonMap(RequestMappingInfo.paths("/api/leave-poc/{id}/submit").methods(RequestMethod.POST).build(),null));
    assertThatCode(()->new DocumentIntegrationValidation().validateDocumentIntegration(List.of(provider(LeavePocService.SUBMIT_ACTION)),mappings).afterSingletonsInstantiated()).doesNotThrowAnyException();
  }
  @Test void missingHandlerFailsClosed(){
    var mappings=mock(RequestMappingHandlerMapping.class);when(mappings.getHandlerMethods()).thenReturn(Map.of());
    assertThatThrownBy(()->new DocumentIntegrationValidation().validateDocumentIntegration(List.of(provider(LeavePocService.SUBMIT_ACTION)),mappings).afterSingletonsInstantiated()).hasMessageContaining("DOCUMENT_API_REGISTRATION_INVALID");
  }
  @Test void unknownPageDoesNotBecomeConfigurable(){
    var a=LeavePocService.SUBMIT_ACTION;var wrong=new Action(a.actionCode(),a.nameZh(),a.nameEn(),a.permissionCode(),a.operationKey(),new PageOperation("unknown","/unknown","未知","Unknown","POST","/api/leave-poc/{id}/submit"),java.util.List.of(),a.interaction());
    assertThatThrownBy(()->new DocumentIntegrationValidation().validateDocumentIntegration(List.of(provider(wrong)),mock(RequestMappingHandlerMapping.class)).afterSingletonsInstantiated()).hasMessageContaining("DOCUMENT_PAGE_PERMISSION_REGISTRATION_INVALID");
  }
  /** 专业服务目录必须来自同一声明，打印字段改动不得只更新某一个目录。 */
  @Test void printingDirectoryProjectsCanonicalInteraction() {
    var provider = new com.appnexus.document.DocumentConfiguration()
        .interactionPrintContracts(List.of(provider(LeavePocService.PRINT_ACTION)));
    var contract = provider.contracts().getFirst();
    var capability = LeavePocService.PRINT_ACTION.interaction().capabilities().getFirst();
    assertThat(contract.get("code")).isEqualTo(capability.reference());
    assertThat((List<?>)contract.get("fields")).hasSize(capability.fields().size());
    assertThat(contract.toString()).contains("documentNumber","startDate","endDate","days","state");
  }

  /** 声明不支持的完成方式必须在构建时失败，不能误报已经同步完成审批。 */
  @Test void unsupportedServiceCompletionFailsClosed() {
    assertThatThrownBy(() -> new com.appnexus.contracts.document.InteractionContract.Capability(
        com.appnexus.contracts.document.InteractionContract.Service.WORKFLOW,
        com.appnexus.contracts.document.InteractionContract.Completion.SYNCHRONOUS,
        "leavePoc","approval",List.of(),Set.of("APPROVED")))
        .hasMessage("INTERACTION_COMPLETION_UNSUPPORTED");
  }
}
