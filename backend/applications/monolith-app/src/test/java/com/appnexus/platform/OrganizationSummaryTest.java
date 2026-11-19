package com.appnexus.platform;

import com.appnexus.platform.authorization.*;
import com.appnexus.platform.persistence.organization.ScopedOrganizationSummary;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实 SQL 验证固定联表聚合在统计前应用行权限，A/B 相同标识不泄漏计数。 */
@SpringBootTest
@ActiveProfiles("local")
class OrganizationSummaryTest extends DatabaseTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Test void aggregateFiltersBothTenantAndRowsBeforeGrouping(){
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),type=UUID.randomUUID(),shared=UUID.randomUUID(),hidden=UUID.randomUUID();
        for(UUID tenant:List.of(a,b)){
            jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'Summary tenant')",tenant.toString());
            jdbc.update("INSERT INTO tenant_organization_type(tenant_id,id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'BUSINESS','Business')",tenant.toString(),type.toString());
            jdbc.update("INSERT INTO tenant_organization_unit(tenant_id,id,code,type_id,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'SHARED',UUID_TO_BIN(?),'Shared')",tenant.toString(),shared.toString(),type.toString());
        }
        jdbc.update("INSERT INTO tenant_organization_unit(tenant_id,id,code,type_id,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'HIDDEN',UUID_TO_BIN(?),'Hidden')",a.toString(),hidden.toString(),type.toString());
        var subject=AccessAuthorization.Subject.tenant(UUID.randomUUID(),a,UUID.randomUUID());
        var planning=mock(AccessPlanning.class);var service=new ScopedOrganizationSummary(jdbc,planning);
        when(planning.plan(subject,"organization.unit:read","organization.unit")).thenReturn(new AccessPlan(subject,"organization.unit:read","organization.unit",
            new DataScopeAuthorization.DataScope(DataScopeAuthorization.ScopeType.ORGANIZATION,Set.of(shared)),Map.of(),java.time.Instant.now()));
        assertThat(service.summarize(subject)).hasSize(1);assertThat(service.summarize(subject).getFirst().count()).isEqualTo(1);
        when(planning.plan(subject,"organization.unit:read","organization.unit")).thenReturn(new AccessPlan(subject,"organization.unit:read","organization.unit",
            new DataScopeAuthorization.DataScope(DataScopeAuthorization.ScopeType.ALL,Set.of()),Map.of(),java.time.Instant.now()));
        assertThat(service.summarize(subject).getFirst().count()).isEqualTo(2);
        when(planning.plan(subject,"organization.unit:read","organization.unit")).thenReturn(new AccessPlan(subject,"organization.unit:read","organization.unit",DataScopeAuthorization.DataScope.deny(),Map.of(),java.time.Instant.now()));
        assertThat(service.summarize(subject)).isEmpty();
    }
}
