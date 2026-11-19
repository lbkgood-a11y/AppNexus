package com.appnexus.platform;

import com.appnexus.platform.security.UnifiedAudit;
import com.appnexus.platform.persistence.platformscope.MyBatisPlatformAdministration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** 统一审计固定读模型验证租户隔离、追踪关联和敏感原值不进入摘要。 */
@SpringBootTest
@ActiveProfiles("local")
class UnifiedAuditTest extends DatabaseTestSupport {
    @Autowired UnifiedAudit audit;
    @Autowired JdbcTemplate jdbc;
    @Autowired MyBatisPlatformAdministration administration;
    @Test void platformTenantQueryCannotMixForeignAuditAndRetainsTrace(){
        administration.initializeAdministrator("admin-test");UUID actor=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM identity_account WHERE username='admin-test'",String.class));
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();String trace=UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO integration_call_audit(tenant_id,client_id,operation,trace_id,status) VALUES(UUID_TO_BIN(?),'test-a','dictionaries.items:read',?,200)",a.toString(),trace);
        jdbc.update("INSERT INTO integration_call_audit(tenant_id,client_id,operation,trace_id,status) VALUES(UUID_TO_BIN(?),'test-b','dictionary.references',?,201)",b.toString(),UUID.randomUUID().toString().replace("-",""));
        var result=audit.platform(actor,a,1,20);assertThat(result.total()).isEqualTo(1);assertThat(result.items().getFirst().traceId()).isEqualTo(trace);assertThat(result.items().getFirst().actor()).isEqualTo("test-a");
        assertThatThrownBy(()->audit.platform(UUID.randomUUID(),a,1,20)).isInstanceOf(org.springframework.web.ErrorResponseException.class);
        assertThatThrownBy(()->audit.platform(actor,a,0,20)).isInstanceOf(org.springframework.web.ErrorResponseException.class);
    }
}
