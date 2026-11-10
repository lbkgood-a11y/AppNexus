package com.appnexus.platform;

import com.appnexus.platform.authorization.*;
import com.appnexus.platform.persistence.platformscope.TransactionalAccessPlanning;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 正式计划在功能权限或资源不匹配时拒绝，不能把合法行范围跨操作复用。 */
class AccessPlanTest {
    @Test void resourceAndOperationMustMatchAndMissingFunctionCannotResolveRows(){
        var authorization=mock(AccessAuthorization.class);var scopes=mock(DataScopeAuthorization.class);
        var planning=new TransactionalAccessPlanning(authorization,scopes);
        var subject=AccessAuthorization.Subject.tenant(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        assertThatThrownBy(()->planning.plan(subject,"organization.unit:read","organization.unit")).isInstanceOf(SecurityException.class);
        verifyNoInteractions(scopes);
        when(authorization.hasPermission(subject,"organization.unit:read")).thenReturn(true);
        var data=new DataScopeAuthorization.DataScope(DataScopeAuthorization.ScopeType.ORGANIZATION,Set.of(UUID.randomUUID()));
        when(scopes.resolveDataScope(subject,"organization.unit:read","organization.unit")).thenReturn(data);
        var plan=planning.plan(subject,"organization.unit:read","organization.unit");assertThat(plan.rows()).isEqualTo(data);
        assertThatThrownBy(()->planning.plan(subject,"organization.unit:update","organization.unit")).isInstanceOf(SecurityException.class);
        assertThatThrownBy(()->planning.plan(subject,"organization.unit:read","unknown.resource")).isInstanceOf(SecurityException.class);
    }
    @Test void planCopiesMutableCollectionsAndRejectsPlatformSubject(){
        var ids=new HashSet<UUID>();ids.add(UUID.randomUUID());
        var fields=new HashMap<String,FieldSecurity.Mode>();fields.put("name",FieldSecurity.Mode.HIDDEN);
        var subject=AccessAuthorization.Subject.tenant(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        var plan=new AccessPlan(subject,"organization.unit:read","organization.unit",new DataScopeAuthorization.DataScope(DataScopeAuthorization.ScopeType.ORGANIZATION,ids),fields,java.time.Instant.now());
        ids.clear();fields.clear();assertThat(plan.rows().organizationIds()).hasSize(1);assertThat(plan.fields()).hasSize(1);
        assertThatThrownBy(()->new AccessPlan(AccessAuthorization.Subject.platform(UUID.randomUUID()),"organization.unit:read","organization.unit",DataScopeAuthorization.DataScope.deny(),Map.of(),java.time.Instant.now())).isInstanceOf(IllegalArgumentException.class);
    }
}
