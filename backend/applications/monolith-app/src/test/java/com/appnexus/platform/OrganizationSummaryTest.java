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
    @Autowired com.appnexus.platform.persistence.organization.OrganizationSummaryMapper summaryMapper;
    @Autowired com.appnexus.platform.persistence.organization.OrganizationDomainMapper domainMapper;
    @Autowired com.appnexus.platform.organization.PersonnelCatalog persons;
    @Autowired RowAccessControl rows;
    /** 真实领域入口不可借已登记的平面仓储执行器绕过数据范围；read和update使用不同策略。 */
    @Test void domainUnitPathsEnforceTheirOwnOperationAndTargetScope(){
        UUID tenant=UUID.randomUUID(),other=UUID.randomUUID(),type=UUID.randomUUID(),allowed=UUID.randomUUID(),hidden=UUID.randomUUID();
        for(UUID t:List.of(tenant,other)){
            jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'Scope fixture')",t.toString());
            jdbc.update("INSERT INTO tenant_organization_type(tenant_id,id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'BUSINESS','Business')",t.toString(),type.toString());
            jdbc.update("INSERT INTO tenant_organization_unit(tenant_id,id,code,type_id,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'VISIBLE',UUID_TO_BIN(?),'Visible')",t.toString(),allowed.toString(),type.toString());
        }
        jdbc.update("INSERT INTO tenant_organization_unit(tenant_id,id,code,type_id,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'HIDDEN',UUID_TO_BIN(?),'Hidden')",tenant.toString(),hidden.toString(),type.toString());
        var subject=AccessAuthorization.Subject.tenant(UUID.randomUUID(),tenant,UUID.randomUUID());
        var request=new org.springframework.mock.web.MockHttpServletRequest();
        request.setAttribute(com.appnexus.platform.tenant.VerifiedTenantContext.class.getName(),new com.appnexus.platform.tenant.VerifiedTenantContext(subject.accountId(),tenant,subject.memberId(),1));
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
        var planning=mock(AccessPlanning.class);
        var service=new com.appnexus.platform.persistence.organization.MyBatisOrganizationDomain(domainMapper,persons,planning,rows);
        when(planning.plan(eq(subject),anyString(),eq("organization.unit"))).thenAnswer(call->new AccessPlan(subject,call.getArgument(1),"organization.unit",new DataScopeAuthorization.DataScope(DataScopeAuthorization.ScopeType.ORGANIZATION,Set.of(allowed)),Map.of(),java.time.Instant.now()));
        assertThat(service.units()).extracting(com.appnexus.platform.organization.OrganizationStructure.Unit::id).containsExactly(allowed);
        assertThatThrownBy(()->service.impact(hidden)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(()->service.updateUnit(hidden,"stolen",true,0)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(()->service.moveUnit(allowed,hidden,0)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(()->service.createUnit("DENIED",type,null,"Denied")).isInstanceOf(SecurityException.class);
        assertThat(jdbc.queryForObject("SELECT name FROM tenant_organization_unit WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",String.class,tenant.toString(),hidden.toString())).isEqualTo("Hidden");
        when(planning.plan(subject,"organization.unit:read","organization.unit")).thenReturn(new AccessPlan(subject,"organization.unit:read","organization.unit",DataScopeAuthorization.DataScope.deny(),Map.of(),java.time.Instant.now()));
        assertThat(service.units()).isEmpty();
        assertThat(service.updateUnit(allowed,"Updated",true,0).name()).isEqualTo("Updated");
        assertThat(jdbc.queryForObject("SELECT name FROM tenant_organization_unit WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",String.class,other.toString(),allowed.toString())).isEqualTo("Visible");
    }
    @Test void aggregateFiltersBothTenantAndRowsBeforeGrouping(){
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),type=UUID.randomUUID(),shared=UUID.randomUUID(),hidden=UUID.randomUUID();
        for(UUID tenant:List.of(a,b)){
            jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'Summary tenant')",tenant.toString());
            jdbc.update("INSERT INTO tenant_organization_type(tenant_id,id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'BUSINESS','Business')",tenant.toString(),type.toString());
            jdbc.update("INSERT INTO tenant_organization_unit(tenant_id,id,code,type_id,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'SHARED',UUID_TO_BIN(?),'Shared')",tenant.toString(),shared.toString(),type.toString());
        }
        jdbc.update("INSERT INTO tenant_organization_unit(tenant_id,id,code,type_id,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'HIDDEN',UUID_TO_BIN(?),'Hidden')",a.toString(),hidden.toString(),type.toString());
        var subject=AccessAuthorization.Subject.tenant(UUID.randomUUID(),a,UUID.randomUUID());
        var request=new org.springframework.mock.web.MockHttpServletRequest();
        request.setAttribute(com.appnexus.platform.tenant.VerifiedTenantContext.class.getName(),new com.appnexus.platform.tenant.VerifiedTenantContext(subject.accountId(),a,subject.memberId(),1));
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
        var planning=mock(AccessPlanning.class);var service=new ScopedOrganizationSummary(summaryMapper,planning);
        when(planning.plan(subject,"organization.unit:read","organization.unit")).thenReturn(new AccessPlan(subject,"organization.unit:read","organization.unit",
            new DataScopeAuthorization.DataScope(DataScopeAuthorization.ScopeType.ORGANIZATION,Set.of(shared)),Map.of(),java.time.Instant.now()));
        assertThat(service.summarize(subject)).hasSize(1);assertThat(service.summarize(subject).getFirst().count()).isEqualTo(1);
        when(planning.plan(subject,"organization.unit:read","organization.unit")).thenReturn(new AccessPlan(subject,"organization.unit:read","organization.unit",
            new DataScopeAuthorization.DataScope(DataScopeAuthorization.ScopeType.ALL,Set.of()),Map.of(),java.time.Instant.now()));
        assertThat(service.summarize(subject).getFirst().count()).isEqualTo(2);
        var foreign=AccessAuthorization.Subject.tenant(subject.accountId(),b,subject.memberId());
        assertThatThrownBy(()->service.summarize(foreign)).isInstanceOf(SecurityException.class).hasMessage("ORGANIZATION_SUMMARY_SUBJECT_MISMATCH");
        verify(planning,never()).plan(eq(foreign),anyString(),anyString());
        when(planning.plan(subject,"organization.unit:read","organization.unit")).thenReturn(new AccessPlan(subject,"organization.unit:read","organization.unit",DataScopeAuthorization.DataScope.deny(),Map.of(),java.time.Instant.now()));
        assertThat(service.summarize(subject)).isEmpty();
    }
    /** 请求可信租户不得泄漏到其他测试或异步执行。 */
    @org.junit.jupiter.api.AfterEach void clearRequest(){org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();}
}
