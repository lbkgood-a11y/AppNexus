package com.appnexus.platform;
import com.appnexus.platform.security.operation.OperationRegistry;
import com.appnexus.contracts.operation.OperationBinding;
import com.appnexus.platform.security.EndpointOperation;
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
 @com.appnexus.contracts.operation.BusinessObjectType(code="test",nameZh="测试对象",nameEn="Test object",category=com.appnexus.contracts.operation.BusinessObjectType.Category.MASTER_DATA,module="test")
 static class Handler {
  @OperationBinding(id="test.managed",objectType="test",operationCode="managed",pages={"roles/index"},nameZh="代管角色",nameEn="Manage role",policy="平台代管")
  @EndpointOperation(value="access.tenant.role:update",platformPermission="tenant.role:update")
  public void managed(){}
  @OperationBinding(id="test.task",objectType="test",operationCode="task",pages={"workflow/index"},nameZh="处理任务",nameEn="Handle task",policy="任务归属")
  public void task(){}
 }
 @Test void derivesEndpointAndKeepsManagedScopeAndObjectPolicy() throws Exception {
  var mappings=mock(RequestMappingHandlerMapping.class);var handler=new Handler();
  var entries=new LinkedHashMap<RequestMappingInfo,HandlerMethod>();
  entries.put(RequestMappingInfo.paths("/bff/platform/tenants/{tenant}/roles/{role}").methods(RequestMethod.PUT).build(),new HandlerMethod(handler,Handler.class.getMethod("managed")));
  entries.put(RequestMappingInfo.paths("/api/workflow/tasks/{id}/actions").methods(RequestMethod.POST).build(),new HandlerMethod(handler,Handler.class.getMethod("task")));
  when(mappings.getHandlerMethods()).thenReturn(entries);
  var registry=new OperationRegistry(mappings,List.of());
  var result=registry.all();
  assertThat(registry.objects()).singleElement().satisfies(object->{assertThat(object.category()).isEqualTo("MASTER_DATA");assertThat(object.operations()).hasSize(2);assertThat(object.configurable()).isFalse();});
  assertThat(result.get(0).permissions()).containsExactly("tenant.role:update");
  assertThat(result.get(0).endpoints()).containsExactly("PUT /bff/platform/tenants/{tenant}/roles/{role}");
  assertThat(result.get(1).permissions()).isEmpty();
  assertThat(result.get(1).policy()).isEqualTo("任务归属");
 }
 /** 对象元数据只能声明一次，即使两份定义相同也拒绝，避免后续修改出现多权威。 */
 @Test void duplicateObjectDeclarationsAreRejected() throws Exception {
  var mappings=mock(RequestMappingHandlerMapping.class);var first=new DuplicateOne();var second=new DuplicateTwo();
  var entries=new LinkedHashMap<RequestMappingInfo,HandlerMethod>();
  entries.put(RequestMappingInfo.paths("/api/one").methods(RequestMethod.GET).build(),new HandlerMethod(first,DuplicateOne.class.getMethod("read")));
  entries.put(RequestMappingInfo.paths("/api/two").methods(RequestMethod.GET).build(),new HandlerMethod(second,DuplicateTwo.class.getMethod("read")));
  when(mappings.getHandlerMethods()).thenReturn(entries);
  assertThatThrownBy(()->new OperationRegistry(mappings,List.of()).objects()).hasMessageContaining("对象存在重复定义");
 }
 @com.appnexus.contracts.operation.BusinessObjectType(code="Duplicated",nameZh="对象",nameEn="Object",category=com.appnexus.contracts.operation.BusinessObjectType.Category.SYSTEM,module="test")
 static class DuplicateOne {public void read(){}}
 @com.appnexus.contracts.operation.BusinessObjectType(code="Duplicated",nameZh="对象",nameEn="Object",category=com.appnexus.contracts.operation.BusinessObjectType.Category.SYSTEM,module="test")
 static class DuplicateTwo {public void read(){}}

}
