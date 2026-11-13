package com.appnexus.platform;

import static org.assertj.core.api.Assertions.*;
import com.appnexus.leave.LeavePocController;
import com.appnexus.leave.LeavePocService;
import com.appnexus.platform.configuration.NavigationPages;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;

/** 页面、授权目录和业务接口的注册关系必须真实一致，不能只在单据目录填入展示字符串。 */
class DocumentPageRegistrationTest {
  @Test void declaredPageAndPermissionExistInBuildRegistry() {
    for(var action:java.util.List.of(LeavePocService.SUBMIT_ACTION,LeavePocService.PRINT_ACTION)){
    var operation=action.pageOperation();var page=NavigationPages.find(operation.pageKey());
    assertThat(page).isNotNull();assertThat(page.path()).isEqualTo(operation.pagePath());
    assertThat(NavigationPages.operations(operation.pageKey())).contains(action.permissionCode());
    for(var alias:action.aliases()){var aliasPage=NavigationPages.find(alias.pageOperation().pageKey());assertThat(aliasPage.path()).isEqualTo(alias.pageOperation().pagePath());assertThat(NavigationPages.operations(alias.pageOperation().pageKey())).contains(action.permissionCode());}
    }
  }
  @Test void declaredApiMatchesRealControllerMapping() throws Exception {
    var operation=LeavePocService.SUBMIT_ACTION.pageOperation();
    var method=LeavePocController.class.getMethod("submit",jakarta.servlet.http.HttpServletRequest.class,String.class,LeavePocController.SubmitOperation.class);
    String base=LeavePocController.class.getAnnotation(RequestMapping.class).value()[0];
    assertThat(operation.method()).isEqualTo("POST");
    assertThat(operation.apiPath()).isEqualTo(base+method.getAnnotation(PostMapping.class).value()[0]);
  }
}
