package com.appnexus.platform;
import com.appnexus.platform.security.operation.OperationRegistry;
import com.appnexus.contracts.operation.OperationBinding;
import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
/** 操作登记直接关联真实 MVC 方法，并验证平台代管不能借租户权限替代平台权限。 */
class OperationRegistryTest {
 static class Handler {
  @OperationBinding(id="test.managed",reference=true,enforceDeclaredPermission=true)
  public void managed(){}
  @OperationBinding(id="test.task",reference=true,eligibility=OperationBinding.Eligibility.OBJECT)
  public void task(){}
 }
 @Test void derivesEndpointAndKeepsManagedScopeAndObjectPolicy() throws Exception {
  var mappings=mock(RequestMappingHandlerMapping.class);var handler=new Handler();
  var entries=new LinkedHashMap<RequestMappingInfo,HandlerMethod>();
  entries.put(RequestMappingInfo.paths("/bff/platform/tenants/{tenant}/roles/{role}").methods(RequestMethod.PUT).build(),new HandlerMethod(handler,Handler.class.getMethod("managed")));
  entries.put(RequestMappingInfo.paths("/api/workflow/tasks/{id}/actions").methods(RequestMethod.POST).build(),new HandlerMethod(handler,Handler.class.getMethod("task")));
  when(mappings.getHandlerMethods()).thenReturn(entries);
  Provider provider=()->new Descriptor("test","测试对象","Test object","test",1,Category.MASTER_DATA,List.of(),List.of(
      operation("test","managed","代管角色","Manage role","tenant.role:update"),
      operation("test","task","处理任务","Handle task","")));
  var registry=new OperationRegistry(mappings,List.of(provider));
  var result=registry.all();
  assertThat(registry.objects()).singleElement().satisfies(object->{assertThat(object.category()).isEqualTo("MASTER_DATA");assertThat(object.operations()).hasSize(2);assertThat(object.configurable()).isTrue();});
  assertThat(result.get(0).permissions()).containsExactly("tenant.role:update");
  assertThat(result.get(0).endpoints()).containsExactly("PUT /bff/platform/tenants/{tenant}/roles/{role}");
  assertThat(result.get(1).permissions()).isEmpty();
  assertThat(result.get(1).eligibility()).isEqualTo(OperationBinding.Eligibility.OBJECT);
 }
 /** 对象元数据只能声明一次，即使两份定义相同也拒绝，避免后续修改出现多权威。 */
 @Test void duplicateObjectDeclarationsAreRejected() throws Exception {
  var mappings=mock(RequestMappingHandlerMapping.class);
  Provider provider=()->new Descriptor("Duplicated","对象","Object","test",1,Category.SYSTEM,List.of(),List.of());
  assertThatThrownBy(()->new OperationRegistry(mappings,List.of(provider,provider)).objects())
      .hasMessageContaining("对象存在重复权威定义");
 }
}
