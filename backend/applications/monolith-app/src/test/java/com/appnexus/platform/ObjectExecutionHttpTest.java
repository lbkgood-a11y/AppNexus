package com.appnexus.platform;

import com.appnexus.platform.appapi.businessobject.ObjectExecutionController;
import com.appnexus.contracts.businessobject.*;
import com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.appnexus.contracts.workflow.WorkflowContext;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 统一HTTP入口不从JSON读取身份，缺会话/请求键失败且不调用执行器。 */
class ObjectExecutionHttpTest {
  @Test void requiresTrustedIdentityAndStableKey(){
    var execution=mock(BusinessObjectExecution.class);var controller=new ObjectExecutionController(execution);
    var input=new BusinessObjectExecution.Request("Example","record","print","Example.print",0);
    var request=new MockHttpServletRequest();
    assertThat(controller.execute(request,input).getStatusCode().value()).isEqualTo(403);
    request.setAttribute(WorkflowContext.class.getName(),new WorkflowContext("tenant","actor","original","person","org"));
    assertThat(controller.execute(request,input).getStatusCode().value()).isEqualTo(400);
    verifyNoInteractions(execution);
    String key=UUID.randomUUID().toString();request.addHeader("Idempotency-Key",key);
    var resolution=new Resolution("resolution","tenant","Example","record",0,1,null,0,Map.of(),"print","PRINT","scene","example");
    when(execution.execute(any(),eq(input))).thenReturn(new BusinessObjectExecution.Result(resolution,Map.of("id","output")));
    var response=controller.execute(request,input);
    assertThat(response.getStatusCode().value()).isEqualTo(200);assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    verify(execution).execute(new Context("tenant","actor",key,"person","org"),input);
  }
  @Test void rejectsBackendDenialWithoutLeakingDetails(){
    var execution=mock(BusinessObjectExecution.class);var controller=new ObjectExecutionController(execution);
    var request=new MockHttpServletRequest();request.setAttribute(WorkflowContext.class.getName(),new WorkflowContext("tenant","actor","original"));request.addHeader("Idempotency-Key",UUID.randomUUID().toString());
    var input=new BusinessObjectExecution.Request("Example","record","print","Example.print",0);
    when(execution.execute(any(),any())).thenThrow(new Rejection(403,"BUSINESS_OBJECT_DATA_ACCESS_DENIED"));
    var response=controller.execute(request,input);assertThat(response.getStatusCode().value()).isEqualTo(403);
    assertThat(response.getBody()).isEqualTo(Map.of("status",403,"code","BUSINESS_OBJECT_DATA_ACCESS_DENIED"));
  }
}
