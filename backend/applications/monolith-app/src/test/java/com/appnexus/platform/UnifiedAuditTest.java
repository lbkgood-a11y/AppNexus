package com.appnexus.platform;

import com.appnexus.platform.audit.AuditQueries;
import com.appnexus.platform.persistence.platformscope.MyBatisPlatformAdministration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 统一审计固定读模型验证租户隔离、追踪关联和敏感原值不进入摘要。 */
@SpringBootTest
@ActiveProfiles("local")
@AutoConfigureMockMvc
class UnifiedAuditTest extends DatabaseTestSupport {
    @Autowired AuditQueries audit;
    @Autowired JdbcTemplate jdbc;
    @Autowired MyBatisPlatformAdministration administration;
    @Autowired com.appnexus.platform.persistence.organization.OrganizationAdministratorBootstrap bootstrap;

    @Autowired com.appnexus.masterdata.organization.PersonnelDirectory directory;
    @Autowired MockMvc mvc;

    /** 经真实会话和响应组装验证平台审计契约，集成客户端标识即使形似账号也不查名。 */
    @Test void platformAuditResponsePreservesActorAndResolvesOnlyAccounts() throws Exception {
        administration.initializeAdministrator("admin-test");
        String actor=jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM identity_account WHERE username='admin-test'",String.class);
        String display=jdbc.queryForObject("SELECT display_name FROM identity_account WHERE id=UUID_TO_BIN(?)",String.class,actor);
        String tenant=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO platform_administration_audit(actor_account_id,action,target_id) VALUES(UUID_TO_BIN(?),'AUDIT_NAME_HTTP_TEST',UUID_TO_BIN(?))",actor,actor);
        jdbc.update("INSERT INTO integration_call_audit(tenant_id,client_id,operation,trace_id,status) VALUES(UUID_TO_BIN(?),?,'reference.test',?,200)",
            tenant,actor,UUID.randomUUID().toString().replace("-",""));

        mvc.perform(get("/bff/platform/audits").param("pageSize","1").with(authenticated(actor,0L)))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.pageSize").value(1))
            .andExpect(jsonPath("$.items[0].action").value("AUDIT_NAME_HTTP_TEST"))
            .andExpect(jsonPath("$.items[0].actor").value(actor))
            .andExpect(jsonPath("$.items[0].actorName").value(display));
        mvc.perform(get("/bff/platform/audits").param("tenantId",tenant).with(authenticated(actor,0L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].source").value("integration"))
            .andExpect(jsonPath("$.items[0].actor").value(actor))
            .andExpect(jsonPath("$.items[0].actorName").doesNotExist());
    }

    /** 平台域按原审计权限读取多个账号名称，普通账号直接调用仍被 SQL 门禁拒绝。 */
    @Test void platformActorNamesRequirePlatformAuditPermission(){
        administration.initializeAdministrator("admin-test");
        String platform=jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM identity_account WHERE username='admin-test'",String.class);
        String display=jdbc.queryForObject("SELECT display_name FROM identity_account WHERE id=UUID_TO_BIN(?)",String.class,platform);
        var other=UUID.randomUUID();
        jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'审计第二账号','unused')",
            other.toString(),"audit-platform-"+other);
        var ids=List.of(platform,other.toString(),UUID.randomUUID().toString());
        assertThat(directory.platformAccountNames(UUID.fromString(platform),ids))
            .containsExactlyInAnyOrderEntriesOf(java.util.Map.of(platform,display,other.toString(),"审计第二账号"));
        assertThatThrownBy(()->directory.platformAccountNames(other,ids))
            .hasRootCauseInstanceOf(SecurityException.class);
    }

    /** 平台代查与租户本人查询使用各自角色策略，同一审计摘要不会跨管理域套用字段权限。 */
    @Test void auditTargetIsProtectedInEachActorDomain(){
        administration.initializeAdministrator("admin-test");
        String platform=jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM identity_account WHERE username='admin-test'",String.class);
        UUID tenant=UUID.randomUUID(),tenantActor=UUID.randomUUID(),member=UUID.randomUUID(),target=UUID.randomUUID(),role=UUID.randomUUID();
        jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'Audit field test')",tenant.toString());
        jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'Audit field test','unused')",tenantActor.toString(),"audit-"+tenantActor);
        jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",member.toString(),tenant.toString(),tenantActor.toString());
        bootstrap.initialize(tenant,member);
        jdbc.update("INSERT INTO platform_administration_audit(actor_account_id,tenant_id,target_id,action) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),'AUDIT_FIELD_TEST')",platform,tenant.toString(),target.toString());
        var current=new com.appnexus.platform.tenant.VerifiedTenantContext(tenantActor,tenant,member,1);
        assertThat(target(audit.platform(UUID.fromString(platform),tenant,1,20))).isEqualToIgnoringCase(target.toString());
        assertThat(target(audit.tenant(current,1,20))).isEqualToIgnoringCase(target.toString());
        jdbc.update("INSERT INTO access_role(id,scope_type,role_type,code,name,status) VALUES(UUID_TO_BIN(?),'PLATFORM','CUSTOM',?,'Audit field test','ENABLED')",role.toString(),"audit_"+role.toString().replace("-",""));
        jdbc.update("INSERT INTO access_account_role(account_id,role_id,scope_type,assigned_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'PLATFORM',UUID_TO_BIN(?))",platform,role.toString(),platform);
        jdbc.update("INSERT INTO access_role_field_security(role_id,resource_type,field_code,display_mode) VALUES(UUID_TO_BIN(?),'audit.platform','target','HIDDEN')",role.toString());
        try{
            assertThat(target(audit.platform(UUID.fromString(platform),tenant,1,20))).isNull();
            assertThat(target(audit.tenant(current,1,20))).isEqualToIgnoringCase(target.toString());
            assertThat(jdbc.update("INSERT INTO access_role_field_security(role_id,resource_type,field_code,display_mode,tenant_id) "+
                "SELECT role_id,'audit.tenant','target','HIDDEN',tenant_id FROM access_member_role WHERE tenant_id=UUID_TO_BIN(?) AND member_id=UUID_TO_BIN(?)",
                tenant.toString(),member.toString())).isPositive();
            assertThat(target(audit.tenant(current,1,20))).isNull();
        }finally{
            jdbc.update("DELETE FROM access_role_field_security WHERE role_id=UUID_TO_BIN(?)",role.toString());
        }
    }

    private String target(AuditQueries.Page page){
        return page.items().stream().filter(entry->entry.action().equals("AUDIT_FIELD_TEST")).findFirst().orElseThrow().target();
    }
    @Test void platformTenantQueryCannotMixForeignAuditAndRetainsTrace(){
        administration.initializeAdministrator("admin-test");UUID actor=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM identity_account WHERE username='admin-test'",String.class));
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();String trace=UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO integration_call_audit(tenant_id,client_id,operation,trace_id,status) VALUES(UUID_TO_BIN(?),'test-a','dictionaries.items:read',?,200)",a.toString(),trace);
        jdbc.update("INSERT INTO integration_call_audit(tenant_id,client_id,operation,trace_id,status) VALUES(UUID_TO_BIN(?),'test-b','dictionary.references',?,201)",b.toString(),UUID.randomUUID().toString().replace("-",""));
        var result=audit.platform(actor,a,1,20);assertThat(result.total()).isEqualTo(1);assertThat(result.items().getFirst().traceId()).isEqualTo(trace);assertThat(result.items().getFirst().actor()).isEqualTo("test-a");
        assertThatThrownBy(()->audit.platform(UUID.randomUUID(),a,1,20)).isInstanceOfSatisfying(com.appnexus.platform.audit.AuditQueries.Rejection.class, error -> {
            assertThat(error.status()).isEqualTo(403);
            assertThat(error).hasMessage("AUDIT_QUERY_DENIED");
        });
        assertThatThrownBy(()->audit.platform(actor,a,0,20)).isInstanceOfSatisfying(com.appnexus.platform.audit.AuditQueries.Rejection.class, error -> {
            assertThat(error.status()).isEqualTo(400);
            assertThat(error).hasMessage("INVALID_AUDIT_QUERY");
        });
    }
}
