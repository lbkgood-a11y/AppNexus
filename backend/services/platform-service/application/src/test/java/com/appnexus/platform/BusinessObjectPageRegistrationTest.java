package com.appnexus.platform;

import static org.assertj.core.api.Assertions.*;
import com.appnexus.leave.LeavePocController;
import com.appnexus.leave.LeavePocService;
import com.appnexus.platform.configuration.NavigationPages;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;

/** 页面、授权目录和业务接口的注册关系必须真实一致，不能只在单据目录填入展示字符串。 */
class BusinessObjectPageRegistrationTest {
  @Test void declaredPageAndPermissionExistInBuildRegistry() {
    for(var action:java.util.List.of(LeavePocService.SUBMIT_OPERATION,LeavePocService.PRINT_OPERATION)){
    var operation=action.pageOperation();var page=NavigationPages.find(operation.pageKey());
    assertThat(page).isNotNull();assertThat(page.path()).isEqualTo(operation.pagePath());
    var binding=java.util.Arrays.stream(LeavePocController.class.getMethods()).flatMap(method->java.util.Arrays.stream(method.getAnnotationsByType(com.appnexus.contracts.operation.OperationBinding.class))).filter(item->item.id().equals(action.operationId())).findFirst().orElseThrow();
    assertThat(binding.reference()).isTrue();
    for(var alias:action.aliases()){var aliasPage=NavigationPages.find(alias.pageOperation().pageKey());assertThat(aliasPage.path()).isEqualTo(alias.pageOperation().pagePath());assertThat(alias.pageOperation().pageKey()).isEqualTo(operation.pageKey());}
    }
  }
  @Test void declaredApiMatchesRealControllerMapping() throws Exception {
    var operation=LeavePocService.SUBMIT_OPERATION.pageOperation();
    var method=LeavePocController.class.getMethod("submit",jakarta.servlet.http.HttpServletRequest.class,String.class,LeavePocController.SubmitOperation.class);
    String base=LeavePocController.class.getAnnotation(RequestMapping.class).value()[0];
    assertThat(operation.method()).isEqualTo("POST");
    assertThat(operation.apiPath()).isEqualTo(base+method.getAnnotation(PostMapping.class).value()[0]);
  }
}
