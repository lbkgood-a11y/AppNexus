package com.appnexus.platform;
import com.appnexus.masterdata.adapter.integration.partner.BusinessPartnerDeclaration;
import com.appnexus.masterdata.adapter.web.masterdata.BusinessPartnerController;

import static org.assertj.core.api.Assertions.*;
import com.appnexus.leave.adapter.web.LeavePocController;
import com.appnexus.leave.application.LeavePocService;
import com.appnexus.platform.configuration.NavigationPages;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;

/** 页面、授权目录和业务接口的注册关系必须真实一致，不能只在单据目录填入展示字符串。 */
class BusinessObjectPageRegistrationTest {
  @Test void declaredPageAndPermissionExistInBuildRegistry() {
    for(var action:java.util.List.of(com.appnexus.leave.api.LeaveOperations.SUBMIT_OPERATION,com.appnexus.leave.api.LeaveOperations.PRINT_OPERATION)){
    var operation=action.pageOperation();var page=NavigationPages.find(operation.pageKey());
    assertThat(page).isNotNull();assertThat(page.path()).isEqualTo(operation.pagePath());
    var binding=java.util.Arrays.stream(LeavePocController.class.getMethods()).flatMap(method->java.util.Arrays.stream(method.getAnnotationsByType(com.appnexus.contracts.operation.OperationBinding.class))).filter(item->item.id().equals(action.operationId())).findFirst().orElseThrow();
    assertThat(binding.reference()).isTrue();
    for(var alias:action.aliases()){var aliasPage=NavigationPages.find(alias.pageOperation().pageKey());assertThat(aliasPage.path()).isEqualTo(alias.pageOperation().pagePath());assertThat(alias.pageOperation().pageKey()).isEqualTo(operation.pageKey());}
    }
  }
  /** 客商Controller只引用统一声明；对象注解和重复操作参数均禁止重新引入。 */
  @Test void partnerControllerReferencesOneAuthoritativeDeclaration(){
    var descriptor=new com.appnexus.masterdata.adapter.integration.partner.BusinessPartnerDeclaration().descriptor();
    assertThat(com.appnexus.masterdata.adapter.web.masterdata.BusinessPartnerController.class.getAnnotation(com.appnexus.contracts.operation.BusinessObjectType.class)).isNull();
    var bindings=java.util.Arrays.stream(com.appnexus.masterdata.adapter.web.masterdata.BusinessPartnerController.class.getMethods()).flatMap(method->java.util.Arrays.stream(method.getAnnotationsByType(com.appnexus.contracts.operation.OperationBinding.class))).toList();
    assertThat(bindings).hasSize(descriptor.operations().size());
    for(var operation:descriptor.operations())assertThat(bindings).anySatisfy(binding->{
      assertThat(binding.id()).isEqualTo(operation.operationId());assertThat(binding.reference()).isTrue();
      assertThat(binding.objectType()).isEmpty();assertThat(binding.permissions()).isEmpty();assertThat(binding.pages()).isEmpty();
    });
  }
  /** 所有统一声明页面键必须映射真实路由，禁止将页面键拼成URL。 */
  @Test void unifiedObjectPagesUseRegisteredRoutes(){
    var objects=java.util.List.of(new com.appnexus.masterdata.adapter.integration.partner.BusinessPartnerDeclaration().descriptor(),new com.appnexus.platform.security.OrganizationUnitDeclaration().descriptor(),new com.appnexus.platform.security.AccountDeclaration().descriptor(),new com.appnexus.platform.security.TenantMemberDeclaration().descriptor(),new com.appnexus.platform.security.PersonDirectoryDeclaration().descriptor());
    for(var object:objects)for(var action:object.operations()){
      var page=NavigationPages.find(action.pageOperation().pageKey());
      assertThat(page).as(action.operationId()).isNotNull();
      assertThat(action.pageOperation().pagePath()).as(action.operationId()).isEqualTo(page.path());
    }
  }
  @Test void declaredApiMatchesRealControllerMapping() throws Exception {
    var operation=com.appnexus.leave.api.LeaveOperations.SUBMIT_OPERATION.pageOperation();
    var method=LeavePocController.class.getMethod("submit",jakarta.servlet.http.HttpServletRequest.class,String.class,LeavePocController.SubmitOperation.class);
    String base=LeavePocController.class.getAnnotation(RequestMapping.class).value()[0];
    assertThat(operation.method()).isEqualTo("POST");
    assertThat(operation.apiPath()).isEqualTo(base+method.getAnnotation(PostMapping.class).value()[0]);
  }
}
