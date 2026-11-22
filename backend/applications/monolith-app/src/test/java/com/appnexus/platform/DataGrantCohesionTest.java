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
        assertThat(DataGrantRules.matchesSubject(organizationRule,member,Set.of(organization))).isTrue();
        assertThat(DataGrantRules.matchesSubject(organizationRule,member,null)).isFalse();
        assertThat(DataGrantRules.matchesSubject(organizationRule,member,Set.of())).isFalse();
    }

    /** 人员参数只有可信当前人员时才能形成匹配条件，不能用账号或成员代替。 */
    @Test void currentPersonIsResolvedOnlyFromVerifiedSubject(){
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
        var resolver=new MyBatisDataScopeAuthorization(mapper,mock(com.appnexus.platform.organization.OrganizationMembership.class),catalog);
        UUID account=UUID.randomUUID(),tenant=UUID.randomUUID(),member=UUID.randomUUID(),person=UUID.randomUUID();
        assertThat(resolver.resolveDataScope(AccessAuthorization.Subject.tenant(account,tenant,member),"masterdata.partner:read",
            "masterdata.partner").type()).isEqualTo(DataScopeAuthorization.ScopeType.DENY);
        var scope=resolver.resolveDataScope(new AccessAuthorization.Subject(account,tenant,member,person,UUID.randomUUID()),
            "masterdata.partner:read","masterdata.partner");
        assertThat(scope.rules().getFirst().conditions()).containsExactly(new DataGrantRules.Condition("assignedPerson",
            DataGrantRules.Operator.EQ,List.of(person.toString())));
    }

    /** 组织集合投影保持记录与字段交集，并保留旧固定组织分支。 */
    @Test void organizationProjectionKeepsAndWithinBranchesAndOrAcrossScopes(){
        UUID root=UUID.randomUUID(),child=UUID.randomUUID(),fixed=UUID.randomUUID(),excluded=UUID.randomUUID();
        var rule=new DataGrantRules.Rule(DataGrantRules.SubjectType.ROLE_MEMBERS,Set.of(),Set.of(root,excluded),List.of(
            new DataGrantRules.Condition("organizationId",DataGrantRules.Operator.RESOLVED_IN,List.of(root.toString(),child.toString()))),false);
        var subject=AccessAuthorization.Subject.tenant(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        var plan=new AccessPlan(subject,"organization.unit:read","organization.unit",
            new DataScopeAuthorization.DataScope(DataScopeAuthorization.ScopeType.RULES,Set.of(fixed),List.of(rule)),Map.of(),java.time.Instant.now());
        var range=new MySqlRowAccessControl().organizationRange(plan,"organizationId");
        assertThat(range.all()).isFalse();
        assertThat(range.ids()).containsExactlyInAnyOrder(root.toString(),fixed.toString());
        var executor=new MySqlRowAccessControl();
        assertThat(executor.organizationRangeForRecord(plan,"organizationId",root).ids())
            .containsExactlyInAnyOrder(root.toString(),child.toString(),fixed.toString());
        assertThat(executor.organizationRangeForRecord(plan,"organizationId",UUID.randomUUID()).ids())
            .containsExactly(fixed.toString());
    }

    @Test void duplicateSubjectsAreValidatedOncePerPolicy() {
        var mapper=mock(RolePermissionMapper.class);UUID tenant=UUID.randomUUID(),member=UUID.randomUUID(),organization=UUID.randomUUID();
        when(mapper.validMembers(eq(tenant.toString()),anyList())).thenReturn(1);
        when(mapper.validOrganizations(eq(tenant.toString()),anyList())).thenReturn(1);
        var policy=new RolePermissionManagement.DataPolicy(UUID.randomUUID(),DataScopeAuthorization.ScopeType.RULES,"masterdata.partner",Set.of(),
            List.of(rule(DataGrantRules.SubjectType.MEMBER,Set.of(member)),rule(DataGrantRules.SubjectType.MEMBER,Set.of(member)),
                rule(DataGrantRules.SubjectType.ORGANIZATION,Set.of(organization)),rule(DataGrantRules.SubjectType.ORGANIZATION,Set.of(organization))));
        DataGrantPolicySupport.validate(tenant,"masterdata.partner:read",policy,DataAccessCatalog.fromObjects(List.of(new BusinessPartnerDeclaration())),mapper,mock(com.appnexus.platform.configuration.BusinessValueCatalog.class));
        verify(mapper).validMembers(tenant.toString(),List.of(member.toString()));
        verify(mapper).validOrganizations(tenant.toString(),List.of(organization.toString()));verifyNoMoreInteractions(mapper);
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
