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
    @Autowired ScopedWriteGate writes;
    /** 真实领域入口不可借已登记的平面仓储执行器绕过数据范围；read和update使用不同策略。 */
    @org.springframework.transaction.annotation.Transactional
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
        var service=new com.appnexus.platform.persistence.organization.MyBatisOrganizationDomain(domainMapper,persons,planning,rows,writes);
        when(planning.plan(eq(subject),anyString(),eq("organization.unit"))).thenAnswer(call->new AccessPlan(subject,call.getArgument(1),"organization.unit",new DataScopeAuthorization.DataScope(DataScopeAuthorization.ScopeType.ORGANIZATION,Set.of(allowed)),Map.of(),java.time.Instant.now()));
        assertThat(service.units()).extracting(com.appnexus.platform.organization.OrganizationStructure.Unit::id).containsExactly(allowed);
        assertThatThrownBy(()->service.impact(hidden)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(()->service.updateUnit(hidden,"stolen",true,0)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(()->service.moveUnit(allowed,hidden,0)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(()->service.createUnit("DENIED",type,null,"Denied")).isInstanceOf(SecurityException.class);
        assertThatThrownBy(()->service.createUnit("DENIED",type,hidden,"Denied")).isInstanceOf(SecurityException.class);
        assertThat(service.createUnit("CHILD",type,allowed,"Allowed child").parentId()).isEqualTo(allowed);
        assertThat(jdbc.queryForObject("SELECT name FROM tenant_organization_unit WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",String.class,tenant.toString(),hidden.toString())).isEqualTo("Hidden");
        when(planning.plan(subject,"organization.unit:read","organization.unit")).thenReturn(new AccessPlan(subject,"organization.unit:read","organization.unit",DataScopeAuthorization.DataScope.deny(),Map.of(),java.time.Instant.now()));
        assertThat(service.units()).isEmpty();
        assertThat(service.updateUnit(allowed,"Updated",true,0).name()).isEqualTo("Updated");
        assertThat(jdbc.queryForObject("SELECT name FROM tenant_organization_unit WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",String.class,other.toString(),allowed.toString())).isEqualTo("Visible");
    }
    /** 组织派生资源按两端或岗位归属执行同一组织策略，更新和删除不能绕过列表范围。 */
    @org.springframework.transaction.annotation.Transactional
    @Test void relatedRowsUseOrganizationScopeAndOperationSpecificPlans(){
        UUID tenant=UUID.randomUUID(),other=UUID.randomUUID(),type=UUID.randomUUID();
        UUID allowed=UUID.randomUUID(),second=UUID.randomUUID(),hidden=UUID.randomUUID(),visiblePosition=UUID.randomUUID(),hiddenPosition=UUID.randomUUID();
        UUID visibleRelation=UUID.randomUUID(),hiddenRelation=UUID.randomUUID();
        UUID visibleAssignment=UUID.randomUUID(),hiddenAssignment=UUID.randomUUID();
        for(UUID t:List.of(tenant,other)){
            jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'Related scope fixture')",t.toString());
            jdbc.update("INSERT INTO tenant_organization_type(tenant_id,id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'BUSINESS','Business')",t.toString(),type.toString());
            for (UUID unit:List.of(allowed,second,hidden)) {
                jdbc.update("INSERT INTO tenant_organization_unit(tenant_id,id,code,type_id,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,UUID_TO_BIN(?),?)",
                    t.toString(),unit.toString(),unit.toString(),type.toString(),unit.toString());
            }
            jdbc.update("INSERT INTO tenant_organization_position(tenant_id,id,organization_id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),'VISIBLE','Visible')",t.toString(),visiblePosition.toString(),allowed.toString());
            jdbc.update("INSERT INTO tenant_organization_position(tenant_id,id,organization_id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),'HIDDEN','Hidden')",t.toString(),hiddenPosition.toString(),hidden.toString());
            jdbc.update("INSERT INTO tenant_organization_relation(tenant_id,id,relation_type,source_id,target_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'DEPENDENCY',UUID_TO_BIN(?),UUID_TO_BIN(?))",t.toString(),visibleRelation.toString(),allowed.toString(),second.toString());
            jdbc.update("INSERT INTO tenant_organization_relation(tenant_id,id,relation_type,source_id,target_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'DEPENDENCY',UUID_TO_BIN(?),UUID_TO_BIN(?))",t.toString(),hiddenRelation.toString(),allowed.toString(),hidden.toString());
            UUID person=UUID.randomUUID();
            jdbc.update("INSERT INTO md_person(id,tenant_id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'PERSON','Person')",person.toString(),t.toString());
            jdbc.update("INSERT INTO tenant_person_position(tenant_id,id,person_id,position_id,starts_at) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),CURRENT_TIMESTAMP(6))",t.toString(),visibleAssignment.toString(),person.toString(),visiblePosition.toString());
            jdbc.update("INSERT INTO tenant_person_position(tenant_id,id,person_id,position_id,starts_at) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),CURRENT_TIMESTAMP(6))",t.toString(),hiddenAssignment.toString(),person.toString(),hiddenPosition.toString());
        }
        var subject=AccessAuthorization.Subject.tenant(UUID.randomUUID(),tenant,UUID.randomUUID());
        var request=new org.springframework.mock.web.MockHttpServletRequest();
        request.setAttribute(com.appnexus.platform.tenant.VerifiedTenantContext.class.getName(),new com.appnexus.platform.tenant.VerifiedTenantContext(subject.accountId(),tenant,subject.memberId(),1));
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
        var planning=mock(AccessPlanning.class);
        var scope=new DataScopeAuthorization.DataScope(DataScopeAuthorization.ScopeType.ORGANIZATION,Set.of(allowed,second));
        when(planning.plan(eq(subject),anyString(),eq("organization.unit"))).thenAnswer(call->new AccessPlan(subject,call.getArgument(1),"organization.unit",scope,Map.of(),java.time.Instant.now()));
        var service=new com.appnexus.platform.persistence.organization.MyBatisOrganizationDomain(domainMapper,persons,planning,rows,writes);
        assertThat(service.positions()).extracting(com.appnexus.platform.organization.OrganizationStructure.Position::id).containsExactly(visiblePosition);
        assertThat(service.assignments()).extracting(com.appnexus.platform.organization.OrganizationStructure.Assignment::id).containsExactly(visibleAssignment);
        assertThat(service.relations()).extracting(com.appnexus.platform.organization.OrganizationStructure.Relation::id).containsExactly(visibleRelation);
        assertThatThrownBy(()->service.deleteRelation(hiddenRelation,0)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(()->service.updatePosition(hiddenPosition,"Denied",true,0)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(()->service.updateAssignment(hiddenAssignment,null,false,0)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(()->service.createPosition(hidden,"DENIED","Denied")).isInstanceOf(SecurityException.class);
        assertThatThrownBy(()->service.createRelation("DEPENDENCY",allowed,hidden)).isInstanceOf(SecurityException.class);
        when(planning.plan(subject,"organization.unit:read","organization.unit")).thenReturn(new AccessPlan(subject,"organization.unit:read","organization.unit",DataScopeAuthorization.DataScope.deny(),Map.of(),java.time.Instant.now()));
        assertThat(service.positions()).isEmpty();
        assertThat(service.assignments()).isEmpty();
        assertThat(service.relations()).isEmpty();
        assertThat(service.updatePosition(visiblePosition,"Updated",true,0).name()).isEqualTo("Updated");
        assertThat(jdbc.queryForObject("SELECT name FROM tenant_organization_position WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",String.class,other.toString(),visiblePosition.toString())).isEqualTo("Visible");
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
        var planning=mock(AccessPlanning.class);var service=new ScopedOrganizationSummary(summaryMapper,planning,rows);
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
