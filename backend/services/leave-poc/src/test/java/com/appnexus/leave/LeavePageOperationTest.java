package com.appnexus.leave;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.appnexus.contracts.workflow.WorkflowContext;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/** 页面操作进入业务提交入口时必须匹配登记标识，错误操作不得产生业务副作用。 */
class LeavePageOperationTest {
  @Test void commonActionAccessKeepsDenialReasonsSeparate(){
    assertThat(com.appnexus.contracts.document.DocumentActionAccess.evaluate("confirm","Order.confirm",false,true,true).reasonCode()).isEqualTo("OPERATION_PERMISSION_MISSING");
    assertThat(com.appnexus.contracts.document.DocumentActionAccess.evaluate("confirm","Order.confirm",true,false,true).reasonCode()).isEqualTo("PAGE_RELATION_DISABLED");
    assertThat(com.appnexus.contracts.document.DocumentActionAccess.evaluate("confirm","Order.confirm",true,true,false).reasonCode()).isEqualTo("BUSINESS_STATE_DENIED");
  }
  private MockHttpServletRequest request() {
    var request=new MockHttpServletRequest("POST","/api/leave-poc/example/submit");
    request.setAttribute(WorkflowContext.class.getName(),new WorkflowContext("tenant","actor",UUID.randomUUID().toString()));
    request.addHeader("Idempotency-Key",UUID.randomUUID().toString());
    return request;
  }
  @Test void correctOperationInvokesBusinessWithVerifiedContext() {
    var service=mock(LeavePocService.class);var controller=new LeavePocController(service);var request=request();
    when(service.submit(any(),eq("example"))).thenReturn(Map.of("state","ACCEPTED"));
    var response=(ResponseEntity<?>)controller.submit(request,"example",new LeavePocController.SubmitOperation("LeaveRequest.submit"));
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    verify(service).submit(new WorkflowContext("tenant","actor",request.getHeader("Idempotency-Key")),"example");
  }
  @Test void wrongOperationNeverInvokesBusiness() {
    var service=mock(LeavePocService.class);var controller=new LeavePocController(service);
    var response=(ResponseEntity<?>)controller.submit(request(),"example",new LeavePocController.SubmitOperation("Order.submit"));
    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(((Map<?,?>)response.getBody()).get("code")).isEqualTo("DOCUMENT_PAGE_OPERATION_INVALID");
    verifyNoInteractions(service);
  }
}
