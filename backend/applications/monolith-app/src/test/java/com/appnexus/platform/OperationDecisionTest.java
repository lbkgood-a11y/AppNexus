package com.appnexus.platform;
import com.appnexus.platform.appapi.operation.OperationDecisionController;
import com.appnexus.platform.security.operation.OperationRegistry;
import com.appnexus.contracts.operation.OperationBinding.Eligibility;
import com.appnexus.platform.authorization.AccessAuthorization;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
/** 展示准入只返回操作标识，并验证 OR-of-AND 与平台/租户接口隔离。 */
class OperationDecisionTest {
 @Test void preservesExpressionsAndRejectsUnknownOrWrongDomain(){
  var registry=mock(OperationRegistry.class);var authorization=mock(AccessAuthorization.class);var actor=UUID.randomUUID();
  var subject=AccessAuthorization.Subject.platform(actor);
  when(authorization.hasPermission(subject,"a:read")).thenReturn(true);
  when(authorization.hasPermission(subject,"a:update")).thenReturn(false);
  when(registry.all()).thenReturn(List.of(
   definition("both",List.of("a:read&a:update"),"PUT /bff/platform/a",Eligibility.PERMISSION),
   definition("either",List.of("a:update","a:read"),"PUT /bff/platform/b",Eligibility.PERMISSION),
   definition("unknown",List.of(),"POST /bff/platform/c",Eligibility.PERMISSION),
   definition("tenant",List.of("a:read"),"PUT /api/a",Eligibility.PERMISSION),
   definition("self",List.of(),"PATCH /bff/account",Eligibility.SELF)));
  var controller=new OperationDecisionController(registry,authorization);
  var response=controller.platform(new UsernamePasswordAuthenticationToken(actor.toString(),null));
  assertThat(response.getBody()).containsExactly("either","self");
  assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
 }
 private OperationRegistry.Definition definition(String id,List<String> permissions,String endpoint,Eligibility eligibility){return new OperationRegistry.Definition(id,"test",id,List.of("platform/menus"),"操作","Operation",permissions,"真实业务仍逐次复核",List.of(endpoint),eligibility);}
}
