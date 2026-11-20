package com.appnexus.platform;
import com.appnexus.platform.appapi.operation.ObjectDiagnosticsController;
import com.appnexus.contracts.operation.ObjectDiagnostics;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.ErrorResponseException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
/** 统一对象诊断隐藏无权记录的存在性，存储故障不能被解释成权限缺失。 */
class ObjectDiagnosticsTest {
 @Test void separatesMissingObjectFromDependencyFailure(){
  var provider=mock(ObjectDiagnostics.class);when(provider.operations()).thenReturn(Set.of("LeaveRequest.submit"));
  var controller=new ObjectDiagnosticsController(List.of(provider));var request=new MockHttpServletRequest();request.setAttribute(VerifiedTenantContext.class.getName(),new VerifiedTenantContext(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1));
  doThrow(new SecurityException()).when(provider).inspect(any(),anyString(),anyString());
  assertThatThrownBy(()->controller.inspect("LeaveRequest.submit","id",request)).isInstanceOfSatisfying(ErrorResponseException.class,error->assertThat(error.getStatusCode().value()).isEqualTo(404));
  doThrow(new org.springframework.dao.DataAccessResourceFailureException("private infrastructure detail")).when(provider).inspect(any(),anyString(),anyString());
  assertThatThrownBy(()->controller.inspect("LeaveRequest.submit","id",request)).isInstanceOfSatisfying(ErrorResponseException.class,error->{assertThat(error.getStatusCode().value()).isEqualTo(503);assertThat(error.getBody().toString()).doesNotContain("private infrastructure detail");});
 }
}
