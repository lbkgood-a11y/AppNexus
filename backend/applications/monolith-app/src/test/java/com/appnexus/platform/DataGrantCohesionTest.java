package com.appnexus.platform;

import com.appnexus.masterdata.adapter.integration.partner.BusinessPartnerDeclaration;
import com.appnexus.platform.authorization.*;
import com.appnexus.platform.persistence.platformscope.*;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 权限规则内聚及批量校验回归：优化查询次数不能改变主体边界或忽略失败批次。 */
class DataGrantCohesionTest {
    private DataGrantRules.Rule rule(DataGrantRules.SubjectType type,Set<UUID> subjects) {
        return new DataGrantRules.Rule(type,subjects,Set.of(UUID.randomUUID()),List.of(),false);
    }

    @Test void subjectMatchingRemainsInPureAuthorizationModel() {
        UUID member=UUID.randomUUID(),organization=UUID.randomUUID();
        assertThat(DataGrantRules.matchesSubject(rule(DataGrantRules.SubjectType.ROLE_MEMBERS,Set.of()),member,null)).isTrue();
        assertThat(DataGrantRules.matchesSubject(rule(DataGrantRules.SubjectType.ROLE_MEMBERS,Set.of()),null,Set.of())).isFalse();
        var memberRule=rule(DataGrantRules.SubjectType.MEMBER,Set.of(member));
        assertThat(DataGrantRules.matchesSubject(memberRule,member,null)).isTrue();
        assertThat(DataGrantRules.matchesSubject(memberRule,UUID.randomUUID(),Set.of(organization))).isFalse();
        var organizationRule=rule(DataGrantRules.SubjectType.ORGANIZATION,Set.of(organization));
        assertThat(DataGrantRules.matchesSubject(organizationRule,member,Set.of(organization))).isFalse();
        assertThat(DataGrantRules.matchesSubject(organizationRule,member,null)).isFalse();
        assertThat(DataGrantRules.matchesSubject(organizationRule,member,Set.of())).isFalse();
    }

    /** 已存的人员参数整条分支拒绝，人员上下文不能激活历史规则。 */
    @Test void retiredPersonParameterDoesNotMatchVerifiedSubject(){
        var root=DataAccessCatalog.fromObjects(List.of(new BusinessPartnerDeclaration())).list(PermissionCatalog.ScopeType.TENANT).getFirst();
        var personDimension=new com.appnexus.contracts.businessobject.BusinessObjects.DataDimension(
            "assignedPerson","经办人","Assigned person","UUID",Set.of("EQ"),"PERSON","",Set.of("list"),Set.of("CURRENT_PERSON"));
        var catalog=new DataAccessCatalog(List.of(new DataAccessDeclaration.Descriptor(root.objectType(),root.version(),root.scope(),
            root.resources(),root.operations(),List.of(personDimension))));
        var mapper=mock(DataScopeMapper.class);
        var policy=new DataGrantRules.Rule(DataGrantRules.SubjectType.ROLE_MEMBERS,Set.of(),Set.of(),List.of(
            new DataGrantRules.Condition("assignedPerson",DataGrantRules.Operator.EQ,List.of(),DataGrantRules.Parameter.CURRENT_PERSON)),false);
        when(mapper.policies(anyString(),anyString(),anyString(),eq("masterdata.partner:read"),eq("masterdata.partner")))
            .thenReturn(List.of(Map.of("scope_type","RULES","rules_json",DataGrantPolicySupport.encode(List.of(policy)))));
        var access=mock(AccessAuthorization.class);
        when(access.hasPermission(any(), anyString())).thenReturn(true);
        var resolver=new MyBatisDataScopeAuthorization(mapper,catalog,access,mock(ScopedWriteGate.class));
        UUID account=UUID.randomUUID(),tenant=UUID.randomUUID(),member=UUID.randomUUID(),person=UUID.randomUUID();
        assertThat(resolver.resolveDataScope(AccessAuthorization.Subject.tenant(account,tenant,member),"masterdata.partner:read",
            "masterdata.partner").type()).isEqualTo(DataScopeAuthorization.ScopeType.DENY);
        var scope=resolver.resolveDataScope(new AccessAuthorization.Subject(account,tenant,member,person,UUID.randomUUID()),
            "masterdata.partner:read","masterdata.partner");
        assertThat(scope.type()).isEqualTo(DataScopeAuthorization.ScopeType.DENY);
    }

    /** 历史顶层策略不能产生记录权限，也不会阻断同一主体的有效 ALL 分支。 */
    @Test void retiredTopLevelPoliciesCannotGrantAccess(){
        var catalog=DataAccessCatalog.fromObjects(List.of(new BusinessPartnerDeclaration()));
        var mapper=mock(DataScopeMapper.class);
        var subject=AccessAuthorization.Subject.tenant(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        var access=mock(AccessAuthorization.class);
        when(access.hasPermission(any(), anyString())).thenReturn(true);
        var resolver=new MyBatisDataScopeAuthorization(mapper,catalog,access,mock(ScopedWriteGate.class));
        var retired=List.<Map<String,Object>>of(Map.of("scope_type","SELF"),
            Map.of("scope_type","ORGANIZATION"),Map.of("scope_type","ORGANIZATION_SUBTREE"));
        when(mapper.policies(anyString(),anyString(),anyString(),eq("masterdata.partner:read"),eq("masterdata.partner")))
            .thenReturn(retired);
        assertThat(resolver.resolveDataScope(subject,"masterdata.partner:read","masterdata.partner").type())
            .isEqualTo(DataScopeAuthorization.ScopeType.DENY);

        var policies=new ArrayList<>(retired);
        policies.add(Map.of("scope_type","ALL"));
        when(mapper.policies(anyString(),anyString(),anyString(),eq("masterdata.partner:read"),eq("masterdata.partner")))
            .thenReturn(policies);
        assertThat(resolver.resolveDataScope(subject,"masterdata.partner:read","masterdata.partner").type())
            .isEqualTo(DataScopeAuthorization.ScopeType.ALL);
    }

    /** 权限解释应与实际运行一致，将历史策略标为不支持。 */
    @Test void retiredTopLevelPoliciesAreNotExplainedAsEffective(){
        var mapper=mock(EffectiveAuthorizationMapper.class);
        UUID tenant=UUID.randomUUID(),member=UUID.randomUUID();
        var rows=List.of("SELF","ORGANIZATION","ORGANIZATION_SUBTREE").stream()
            .map(scope->Map.<String,Object>of("code","masterdata.partner:read","role_id",UUID.randomUUID().toString(),
                "role_name","历史角色","role_status","ENABLED","permission_enabled",true,"scope_type",scope))
            .toList();
        when(mapper.tenant(tenant.toString(),member.toString())).thenReturn(rows);
        var query=new MyBatisEffectiveAuthorizationQuery(mapper,mock(ScopedWriteGate.class),new DataAccessCatalog(List.of()));
        assertThat(query.tenantMember(tenant,member)).singleElement().satisfies(explanation->{
            assertThat(explanation.effective()).isFalse();
            assertThat(explanation.sources()).hasSize(3).allSatisfy(source->{
                assertThat(source.effective()).isFalse();
                assertThat(source.inactiveReason()).isEqualTo("DATA_SCOPE_UNSUPPORTED");
            });
        });
    }

    /** 组织集合投影保持记录与字段交集，忽略混入的旧固定组织集合。 */
    @Test void organizationProjectionKeepsAndWithinBranchesAndOrAcrossScopes(){
        UUID root=UUID.randomUUID(),child=UUID.randomUUID(),fixed=UUID.randomUUID(),excluded=UUID.randomUUID();
        var rule=new DataGrantRules.Rule(DataGrantRules.SubjectType.ROLE_MEMBERS,Set.of(),Set.of(root,excluded),List.of(
            new DataGrantRules.Condition("organizationId",DataGrantRules.Operator.RESOLVED_IN,List.of(root.toString(),child.toString()))),false);
        var subject=AccessAuthorization.Subject.tenant(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        var plan=new AccessPlan(subject,"organization.unit:read","organization.unit",
            new DataScopeAuthorization.DataScope(DataScopeAuthorization.ScopeType.RULES,Set.of(fixed),List.of(rule)),Map.of(),java.time.Instant.now());
        var range=new MySqlRowAccessControl().organizationRange(plan,"organizationId");
        assertThat(range.all()).isFalse();
        assertThat(range.ids()).containsExactlyInAnyOrder(root.toString());
        var executor=new MySqlRowAccessControl();
        assertThat(executor.organizationRangeForRecord(plan,"organizationId",root).ids())
            .containsExactlyInAnyOrder(root.toString(),child.toString());
        assertThat(executor.organizationRangeForRecord(plan,"organizationId",UUID.randomUUID()).ids())
            .isEmpty();
    }

    @Test void duplicateSubjectsAreValidatedOncePerPolicy() {
        var mapper=mock(RolePermissionMapper.class);UUID tenant=UUID.randomUUID(),member=UUID.randomUUID(),organization=UUID.randomUUID();
        when(mapper.validMembers(eq(tenant.toString()),anyList())).thenReturn(1);
        var policy=new RolePermissionManagement.DataPolicy(UUID.randomUUID(),DataScopeAuthorization.ScopeType.RULES,"masterdata.partner",Set.of(),
            List.of(rule(DataGrantRules.SubjectType.MEMBER,Set.of(member)),rule(DataGrantRules.SubjectType.MEMBER,Set.of(member))));
        DataGrantPolicySupport.validate(tenant,"masterdata.partner:read",policy,DataAccessCatalog.fromObjects(List.of(new BusinessPartnerDeclaration())),mapper,mock(com.appnexus.platform.configuration.BusinessValueCatalog.class));
        verify(mapper).validMembers(tenant.toString(),List.of(member.toString()));
        verifyNoMoreInteractions(mapper);
    }

    @Test void largeSubjectSetsAreBoundedAndAnyMissingSubjectRejectsWholePolicy() {
        var mapper=mock(RolePermissionMapper.class);UUID tenant=UUID.randomUUID();
        var first=IntStream.range(0,500).mapToObj(n->new UUID(0,n+1)).collect(java.util.stream.Collectors.toSet());
        var second=Set.of(new UUID(0,501));
        var policy=new RolePermissionManagement.DataPolicy(UUID.randomUUID(),DataScopeAuthorization.ScopeType.RULES,"masterdata.partner",Set.of(),
            List.of(rule(DataGrantRules.SubjectType.MEMBER,first),rule(DataGrantRules.SubjectType.MEMBER,second)));
        when(mapper.validMembers(eq(tenant.toString()),anyList())).thenAnswer(call->{List<?> ids=call.getArgument(1);assertThat(ids.size()).isLessThanOrEqualTo(500);return ids.size();});
        var catalog=DataAccessCatalog.fromObjects(List.of(new BusinessPartnerDeclaration()));
        DataGrantPolicySupport.validate(tenant,"masterdata.partner:read",policy,catalog,mapper,mock(com.appnexus.platform.configuration.BusinessValueCatalog.class));
        verify(mapper,times(2)).validMembers(eq(tenant.toString()),anyList());verifyNoMoreInteractions(mapper);
        reset(mapper);when(mapper.validMembers(eq(tenant.toString()),anyList())).thenReturn(500,0);
        assertThatThrownBy(()->DataGrantPolicySupport.validate(tenant,"masterdata.partner:read",policy,catalog,mapper,mock(com.appnexus.platform.configuration.BusinessValueCatalog.class)))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("DATA_GRANT_SUBJECT_INVALID");
    }
}
