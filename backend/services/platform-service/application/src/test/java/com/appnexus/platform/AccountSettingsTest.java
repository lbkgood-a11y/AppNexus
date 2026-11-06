package com.appnexus.platform;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.appnexus.platform.identity.AccountRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真实数据库与共享会话环境下的自助资料及改密验收，不使用内存数据库替代事务语义。 */
@SpringBootTest(properties = "app.public-url=http://localhost:18080")
@ActiveProfiles("local")
@AutoConfigureMockMvc
class AccountSettingsTest extends DatabaseTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountRepository accounts;
    private static final String OLD = "integration-original-password";
    private static final String NEXT = "integration-updated-password";

    /** 为每个测试创建独立账号，避免改密和限流污染其他登录测试。 */
    private String account() {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,?,?)",
            id, "self-" + id, "原显示名称", encoder.encode(OLD));
        return id;
    }
    @Test void profileRejectsAnonymousAndDoesNotLeakCredentials() throws Exception {
        mvc.perform(get("/bff/account")).andExpect(status().isUnauthorized());
        String id = account();
        mvc.perform(get("/bff/account").with(authenticated(id, 0L)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("原显示名称"))
            .andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void profileChangesOnlyDisplayNameAndRequiresCsrf() throws Exception {
        String id = account();
        mvc.perform(patch("/bff/account").with(authenticated(id, 0L))
            .contentType("application/json").content("{\"displayName\":\"新名称\"}")).andExpect(status().isForbidden());
        mvc.perform(patch("/bff/account").with(authenticated(id, 0L)).with(csrf())
            .contentType("application/json").content("{\"displayName\":\"  新名称  \"}")).andExpect(status().isNoContent());
        var account = accounts.findById(UUID.fromString(id)).orElseThrow();
        assertThat(account.displayName()).isEqualTo("新名称");
        assertThat(account.version()).isZero();
        assertThat(encoder.matches(OLD, account.passwordHash())).isTrue();
    }
    @Test void wrongPasswordAndWeakPasswordDoNotChangeCredentials() throws Exception {
        String id = account();
        mvc.perform(post("/bff/account/password").with(authenticated(id, 0L)).with(csrf())
            .contentType("application/json").content("{\"currentPassword\":\"wrong\",\"newPassword\":\"" + NEXT + "\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));
        mvc.perform(post("/bff/account/password").with(authenticated(id, 0L)).with(csrf())
            .contentType("application/json").content("{\"currentPassword\":\"" + OLD + "\",\"newPassword\":\"short\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PASSWORD_POLICY"));
        assertThat(accounts.findById(UUID.fromString(id)).orElseThrow().version()).isZero();
    }
    @Test void changingPasswordRevokesOldSessionsAndAuthorizations() throws Exception {
        String id = account();
        jdbc.update("INSERT INTO oauth2_authorization(id,registered_client_id,principal_name,authorization_grant_type) VALUES(?,?,?,?)",
            UUID.randomUUID().toString(), "appnexus-bff", id, "authorization_code");
        mvc.perform(post("/bff/account/password").with(authenticated(id, 0L)).with(csrf())
            .contentType("application/json").content("{\"currentPassword\":\"" + OLD + "\",\"newPassword\":\"" + NEXT + "\"}"))
            .andExpect(status().isNoContent());
        var account = accounts.findById(UUID.fromString(id)).orElseThrow();
        assertThat(account.version()).isEqualTo(1);
        assertThat(encoder.matches(NEXT, account.passwordHash())).isTrue();
        assertThat(encoder.matches(OLD, account.passwordHash())).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM oauth2_authorization WHERE principal_name=?",Integer.class,id)).isZero();
        mvc.perform(get("/bff/session").with(authenticated(id, 0L)))
            .andExpect(status().isUnauthorized());
        assertThat(accounts.changePassword(UUID.fromString(id), 0, encoder.encode("concurrent-password"))).isFalse();
    }

    /** 本人查询必须带账号隔离条件，客户端追加 accountId 不得扩大范围。 */
    @Test void securityLogsAreIsolatedAndPaginated() throws Exception {
        String id = account(); String other = account();
        logs.append(UUID.fromString(id), com.appnexus.platform.audit.SecurityLog.Action.LOGIN);
        logs.append(UUID.fromString(other), com.appnexus.platform.audit.SecurityLog.Action.LOGOUT);
        mvc.perform(get("/bff/account/security-logs").with(authenticated(id, 0))
                .param("size","1").param("accountId",other))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].action").value("LOGIN"))
            .andExpect(jsonPath("$.items[0].accountId").doesNotExist())
            .andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get("/bff/account/security-logs").with(authenticated(id,0)).param("size","51"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/bff/account/security-logs")).andExpect(status().isUnauthorized());
    }
    @Autowired com.appnexus.platform.audit.SecurityLogRepository logs;
}
