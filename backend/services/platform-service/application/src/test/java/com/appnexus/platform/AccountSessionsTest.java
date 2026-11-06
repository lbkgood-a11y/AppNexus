package com.appnexus.platform;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/** 在真实 Valkey 主体索引上验证会话查询、跨账号隔离及撤销后失效。 */
@SpringBootTest(properties="app.public-url=http://localhost:18080")
@ActiveProfiles("local")
@AutoConfigureMockMvc
class AccountSessionsTest extends DatabaseTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    /** 创建独立账号，避免撤销测试影响其他测试类使用的初始化管理员。 */
    private String account() {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,?,?)",
            id, "sessions-"+id, "session-test", "unused-hash");
        return id;
    }
    /** 仓储创建已认证版本会话，返回原始 ID 仅供测试验证，不作为 HTTP 响应。 */
    private String session(String id) {
        var session = sessionRepository.createSession();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            User.withUsername(id).password("").authorities("ACCOUNT").build(),null,List.of(new SimpleGrantedAuthority("ACCOUNT"))));
        session.setAttribute("SPRING_SECURITY_CONTEXT",context);
        session.setAttribute("appnexus.credentialVersion",0L);
        sessionRepository.save(session);
        return session.getId();
    }
    private jakarta.servlet.http.Cookie cookie(String id) {
        return new jakarta.servlet.http.Cookie("JSESSIONID",Base64.getEncoder().encodeToString(id.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    private String handle(String id) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(id.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    @Test void onlyOwnSessionsAreVisibleAndOthersCanBeRevoked() throws Exception {
        String id = account(), other = account();
        String current = session(id), target = session(id), foreign = session(other);
        var response = mvc.perform(get("/bff/account/sessions").cookie(cookie(current))).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].current").value(true)).andExpect(jsonPath("$[0].id").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(current,target,foreign);
        mvc.perform(delete("/bff/account/sessions/"+handle(foreign)).cookie(cookie(current)).with(csrf()))
            .andExpect(status().isNotFound());
        assertThat(sessionRepository.findById(foreign)).isNotNull();
        mvc.perform(delete("/bff/account/sessions/"+handle(current)).cookie(cookie(current)).with(csrf()))
            .andExpect(status().isConflict());
        mvc.perform(delete("/bff/account/sessions/"+handle(target)).cookie(cookie(current)))
            .andExpect(status().isForbidden());
        mvc.perform(delete("/bff/account/sessions/"+handle(target)).cookie(cookie(current)).with(csrf()))
            .andExpect(status().isNoContent());
        assertThat(sessionRepository.findById(target)).isNull();
        mvc.perform(get("/bff/session").cookie(cookie(target))).andExpect(status().isUnauthorized());
        mvc.perform(get("/bff/account/sessions")).andExpect(status().isUnauthorized());
    }
    /** 批量撤销不触及当前会话或其他账号；重复执行为零条，CSRF 仍不可省略。 */
    @Test void batchRevocationKeepsCurrentAndForeignSessions() throws Exception {
        String id=account(), foreignAccount=account();
        String current=session(id), first=session(id), second=session(id), foreign=session(foreignAccount);
        mvc.perform(post("/bff/account/sessions/revoke-others").cookie(cookie(current)))
            .andExpect(status().isForbidden());
        mvc.perform(post("/bff/account/sessions/revoke-others").cookie(cookie(current)).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.revoked").value(2));
        assertThat(sessionRepository.findById(current)).isNotNull();
        assertThat(sessionRepository.findById(foreign)).isNotNull();
        assertThat(sessionRepository.findById(first)).isNull();
        assertThat(sessionRepository.findById(second)).isNull();
        mvc.perform(post("/bff/account/sessions/revoke-others").cookie(cookie(current)).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.revoked").value(0));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account_security_log WHERE account_id=UUID_TO_BIN(?) AND action='OTHER_SESSIONS_REVOKED'",Integer.class,id)).isEqualTo(1);
    }
}
