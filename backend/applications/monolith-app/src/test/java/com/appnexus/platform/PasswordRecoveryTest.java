package com.appnexus.platform;

import com.appnexus.platform.identity.*;
import com.appnexus.platform.persistence.platformscope.MyBatisPlatformAdministration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/** 真实数据库验证短时恢复凭证的越权、过期、版本失效、并发消费及会话撤销。 */
@SpringBootTest
@ActiveProfiles("local")
@AutoConfigureMockMvc
class PasswordRecoveryTest extends DatabaseTestSupport {
    @Autowired PasswordRecovery recovery;
    @Autowired MyBatisPlatformAdministration administration;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired MockMvc mvc;
    private UUID actor;
    @BeforeEach void initialize(){
        administration.initializeAdministrator("admin-test");
        actor=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM identity_account WHERE username='admin-test'",String.class));
    }
    private UUID account(){
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,?,?)",
            id.toString(),"recovery-"+id,"Recovery test",encoder.encode("old-test-password-2026"));return id;
    }
    @Test void ticketIsSingleUseAndRevokesPreviousSession() throws Exception {
        UUID target=account();var old=authenticated(target.toString(),0);
        String ticket=recovery.issue(actor,target);
        assertThat(ticket).matches("[a-f0-9]{64}");
        byte[] hash=jdbc.queryForObject("SELECT token_hash FROM identity_password_recovery WHERE account_id=UUID_TO_BIN(?)",byte[].class,target.toString());
        assertThat(HexFormat.of().formatHex(hash)).isNotEqualTo(ticket);
        mvc.perform(post("/login/recovery/completion").with(csrf()).contentType("application/json")
            .content("{\"username\":\"recovery-"+target+"\",\"token\":\""+ticket+"\",\"newPassword\":\"new-test-password-2026\"}"))
            .andExpect(status().isNoContent());
        mvc.perform(get("/bff/session").with(old)).andExpect(status().isUnauthorized());
        assertThat(recovery.complete("recovery-"+target,ticket,encoder.encode("another-password-2026"))).isFalse();
        assertThat(encoder.matches("new-test-password-2026",jdbc.queryForObject("SELECT password_hash FROM identity_account WHERE id=UUID_TO_BIN(?)",String.class,target.toString()))).isTrue();
    }
    @Test void expiredReplacedAndVersionStaleTicketsAreRejected(){
        UUID target=account(),other=account();String hash=encoder.encode("new-test-password-2026");
        String first=recovery.issue(actor,target),second=recovery.issue(actor,target);
        assertThat(recovery.complete("recovery-"+target,first,hash)).isFalse();
        assertThat(recovery.complete("recovery-"+other,second,hash)).isFalse();
        jdbc.update("UPDATE identity_password_recovery SET expires_at=CURRENT_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE account_id=UUID_TO_BIN(?)",target.toString());
        assertThat(recovery.complete("recovery-"+target,second,hash)).isFalse();
        String current=recovery.issue(actor,target);administration.revokeUserSessions(actor,target);
        assertThat(recovery.complete("recovery-"+target,current,hash)).isFalse();
    }
    @Test void administratorAuthorityAndCsrfCannotBeBypassed() throws Exception {
        UUID ordinary=account(),target=account();
        assertThatThrownBy(()->recovery.issue(ordinary,target)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(()->recovery.issue(actor,actor)).isInstanceOf(IllegalArgumentException.class);
        mvc.perform(post("/bff/platform/users/"+target+"/password-recovery").with(authenticated(actor.toString(),0))).andExpect(status().isForbidden());
        mvc.perform(post("/bff/platform/users/"+target+"/password-recovery").with(authenticated(ordinary.toString(),0)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/login/recovery").contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/login/recovery").with(csrf()).contentType("application/json").content("{\"username\":\"missing\",\"recoveryCode\":\"00000000000000000000000000000000\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PASSWORD_RECOVERY_INVALID"));
    }
    @Test void concurrentTicketConsumptionHasOnlyOneWinner() throws Exception {
        UUID target=account();String ticket=recovery.issue(actor,target),hash=encoder.encode("new-test-password-2026");
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var gate=new java.util.concurrent.CountDownLatch(1);
            var first=pool.submit(()->{gate.await();return recovery.complete("recovery-"+target,ticket,hash);});
            var second=pool.submit(()->{gate.await();return recovery.complete("recovery-"+target,ticket,hash);});
            gate.countDown();assertThat(List.of(first.get(),second.get())).containsExactlyInAnyOrder(true,false);
        }
    }
}
