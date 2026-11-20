package com.appnexus.platform;

import com.appnexus.masterdata.BusinessPartnerDeclaration;
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

    @Test void duplicateSubjectsAreValidatedOncePerPolicy() {
        var mapper=mock(RolePermissionMapper.class);UUID tenant=UUID.randomUUID(),member=UUID.randomUUID(),organization=UUID.randomUUID();
        when(mapper.validMembers(eq(tenant.toString()),anyList())).thenReturn(1);
        when(mapper.validOrganizations(eq(tenant.toString()),anyList())).thenReturn(1);
        var policy=new RolePermissionManagement.DataPolicy(UUID.randomUUID(),DataScopeAuthorization.ScopeType.RULES,"masterdata.partner",Set.of(),
            List.of(rule(DataGrantRules.SubjectType.MEMBER,Set.of(member)),rule(DataGrantRules.SubjectType.MEMBER,Set.of(member)),
                rule(DataGrantRules.SubjectType.ORGANIZATION,Set.of(organization)),rule(DataGrantRules.SubjectType.ORGANIZATION,Set.of(organization))));
        DataGrantPolicySupport.validate(tenant,"masterdata.partner:read",policy,DataAccessCatalog.fromObjects(List.of(new BusinessPartnerDeclaration())),mapper);
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
        DataGrantPolicySupport.validate(tenant,"masterdata.partner:read",policy,catalog,mapper);
        verify(mapper,times(2)).validMembers(eq(tenant.toString()),anyList());verifyNoMoreInteractions(mapper);
        reset(mapper);when(mapper.validMembers(eq(tenant.toString()),anyList())).thenReturn(500,0);
        assertThatThrownBy(()->DataGrantPolicySupport.validate(tenant,"masterdata.partner:read",policy,catalog,mapper))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("DATA_GRANT_SUBJECT_INVALID");
    }
}
