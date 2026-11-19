package com.appnexus.platform;

import com.appnexus.contracts.operation.OperationBinding.Eligibility;
import com.appnexus.platform.authorization.*;
import com.appnexus.platform.configuration.NavigationQuery;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 诊断必须使用目标成员的执行端口，不把操作者的数据范围或字段策略套给目标。 */
class OperationDiagnosticsTest {
 @Test void resolvesTargetDataScopeAndFieldRestrictionsAfterManagementAuthorization(){
  var registry=mock(OperationRegistry.class);var subjects=mock(SubjectRoleManagement.class);var explanation=mock(EffectiveAuthorizationQuery.class);
  var navigation=mock(NavigationQuery.class);var scopes=mock(DataScopeAuthorization.class);var fields=mock(FieldProtection.class);var metadata=mock(DataPolicyMetadataCatalog.class);
  var actor=UUID.randomUUID();var actorMember=UUID.randomUUID();var tenant=UUID.randomUUID();var member=UUID.randomUUID();var account=UUID.randomUUID();var organization=UUID.randomUUID();
  var target=AccessAuthorization.Subject.tenant(account,tenant,member);
  when(registry.all()).thenReturn(List.of(new OperationRegistry.Definition("OrganizationUnit.update","OrganizationUnit","update",List.of("organization/index"),"修改组织","Update organization",List.of("organization.unit:update"),"数据范围及对象状态",List.of("PUT /api/organization/domain/units/{id}"),Eligibility.PERMISSION)));
  when(explanation.tenantMember(tenant,member)).thenReturn(List.of(new EffectiveAuthorizationQuery.Explanation("organization.unit:update",true,List.of())));
  when(navigation.inspectMember(tenant,member)).thenReturn(List.of());when(navigation.accountForMember(tenant,member)).thenReturn(account);
  when(metadata.list(PermissionCatalog.ScopeType.TENANT)).thenReturn(List.of(new DataPolicyMetadataCatalog.ScopeSupport(UUID.randomUUID(),"organization.unit:update",DataScopeAuthorization.ScopeType.ORGANIZATION,"organization.unit")));
  when(scopes.resolveDataScope(target,"organization.unit:update","organization.unit")).thenReturn(new DataScopeAuthorization.DataScope(DataScopeAuthorization.ScopeType.ORGANIZATION,Set.of(organization)));
  when(fields.restrictions(target,"organization.unit")).thenReturn(Map.of("name",FieldSecurity.Mode.MASKED));
  var controller=new OperationDiagnosticsController(registry,subjects,explanation,navigation,scopes,fields,metadata);
  var request=new MockHttpServletRequest();request.setAttribute(VerifiedTenantContext.class.getName(),new VerifiedTenantContext(actor,tenant,actorMember,1));
  var response=controller.tenant(new UsernamePasswordAuthenticationToken(actor.toString(),null),request,member);
  verify(subjects).memberRoles(new RoleManagement.Context(actor,tenant,actorMember),member);
  assertThat(response.getBody()).singleElement().satisfies(result->{
   assertThat(result.status()).isEqualTo("FUNCTION_GRANTED");assertThat(result.unchecked()).containsExactly("OBJECT_AND_BUSINESS_STATE");
   assertThat(result.data()).singleElement().satisfies(data->{assertThat(data.scope()).isEqualTo("ORGANIZATION");assertThat(data.organizations()).containsExactly(organization);assertThat(data.fields()).containsEntry("name",FieldSecurity.Mode.MASKED);});
  });
 }
 @Test void deniesBeforeReadingTargetDetails(){
  var subjects=mock(SubjectRoleManagement.class);var registry=mock(OperationRegistry.class);var explanation=mock(EffectiveAuthorizationQuery.class);var account=UUID.randomUUID();
  doThrow(new SecurityException()).when(subjects).accountRoles(any(),any());
  var controller=new OperationDiagnosticsController(registry,subjects,explanation,mock(NavigationQuery.class),mock(DataScopeAuthorization.class),mock(FieldProtection.class),mock(DataPolicyMetadataCatalog.class));
  assertThatThrownBy(()->controller.platform(new UsernamePasswordAuthenticationToken(UUID.randomUUID().toString(),null),account)).isInstanceOf(org.springframework.web.ErrorResponseException.class);
  verifyNoInteractions(registry,explanation);
 }
}
