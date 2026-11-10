package com.appnexus.platform;

import com.appnexus.platform.security.TenantParameters;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import com.appnexus.platform.persistence.platformscope.MyBatisPlatformAdministration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.*;

/** 参数默认值、租户隔离、受权维护及并发版本在真实数据库上验证。 */
@SpringBootTest
@ActiveProfiles("local")
class TenantParameterTest extends DatabaseTestSupport {
    @Autowired TenantParameters parameters;
    @Autowired MyBatisPlatformAdministration administration;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    private UUID actor;
    @BeforeEach void initialize(){administration.initializeAdministrator("admin-test");actor=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM identity_account WHERE username='admin-test'",String.class));}
    private UUID account(){UUID id=UUID.randomUUID();administration.createUser(actor,id,"parameter-"+id,"Parameter account",encoder.encode("parameter-password-2026"));return id;}
    private VerifiedTenantContext context(){UUID account=account();var tenant=administration.createTenant(actor,UUID.randomUUID(),"Parameter tenant",account);String member=jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM tenant_member WHERE tenant_id=UUID_TO_BIN(?) AND account_id=UUID_TO_BIN(?)",String.class,tenant.id().toString(),account.toString());return new VerifiedTenantContext(account,tenant.id(),UUID.fromString(member),1);}
    @Test void defaultsVersionAndTenantBoundariesArePreserved(){
        var a=context();var b=context();assertThat(parameters.list(a)).allMatch(value->value.version()==-1);
        var saved=parameters.save(a,"display.time-zone","UTC",-1);assertThat(saved.version()).isZero();
        assertThat(parameters.list(b).stream().filter(value->value.key().equals("display.time-zone")).findFirst().orElseThrow().value()).isEqualTo("Asia/Shanghai");
        assertThatThrownBy(()->parameters.save(a,"display.time-zone","Asia/Tokyo",-1)).isInstanceOf(org.springframework.web.ErrorResponseException.class);
        assertThat(parameters.save(a,"display.time-zone","Asia/Tokyo",0).version()).isEqualTo(1);
        assertThatThrownBy(()->parameters.save(a,"display.time-zone","invalid-zone",1)).isInstanceOf(org.springframework.web.ErrorResponseException.class);
        assertThatThrownBy(()->parameters.save(a,"security.secret","forbidden",-1)).isInstanceOf(org.springframework.web.ErrorResponseException.class);
        assertThatThrownBy(()->parameters.save(a,"display.page-size","101",-1)).isInstanceOf(org.springframework.web.ErrorResponseException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_parameter_audit WHERE tenant_id=UUID_TO_BIN(?)",Integer.class,a.tenantId().toString())).isEqualTo(2);
    }
    @Test void ordinaryMemberCannotMaintainAndInactiveContextIsRejected(){
        var owner=context();UUID account=account();administration.addMember(actor,owner.tenantId(),account);
        UUID member=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM tenant_member WHERE tenant_id=UUID_TO_BIN(?) AND account_id=UUID_TO_BIN(?)",String.class,owner.tenantId().toString(),account.toString()));
        var ordinary=new VerifiedTenantContext(account,owner.tenantId(),member,1);
        assertThat(parameters.list(ordinary)).hasSize(3);
        assertThatThrownBy(()->parameters.save(ordinary,"display.currency","USD",-1)).isInstanceOf(org.springframework.web.ErrorResponseException.class);
        assertThatThrownBy(()->parameters.list(new VerifiedTenantContext(account,UUID.randomUUID(),member,1))).isInstanceOf(org.springframework.web.ErrorResponseException.class);
        administration.setTenantEnabled(actor,owner.tenantId(),false);
        assertThatThrownBy(()->parameters.list(owner)).isInstanceOf(org.springframework.web.ErrorResponseException.class);
    }
}
