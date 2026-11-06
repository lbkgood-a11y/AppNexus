package com.appnexus.platform;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.public-url=http://localhost:18080")
@ActiveProfiles("local")
@AutoConfigureMockMvc
class WebLoginTest extends DatabaseTestSupport {
    @Autowired MockMvc mvc;
    @Autowired com.appnexus.platform.identity.AccountRepository accounts;
    /** 缺少真实认证因子时间的旧会话必须重认证，不能进入 OIDC 签发后触发 500。 */
    @Test void legacySessionWithoutAuthenticationTimeMustAuthenticateAgain() throws Exception {
        var request=new org.springframework.mock.web.MockHttpServletRequest("GET","/oauth2/authorize");
        request.getSession().setAttribute("pending-oauth-request","preserved");
        var response=new org.springframework.mock.web.MockHttpServletResponse();
        var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
            "legacy",null,java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ACCOUNT"))));
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
        var continued=new java.util.concurrent.atomic.AtomicBoolean();
        try {
            new com.appnexus.platform.security.AccountStatusFilter(accounts).doFilter(request,response,(req,res)->{
                continued.set(true);
                org.assertj.core.api.Assertions.assertThat(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()).isNull();
            });
            org.assertj.core.api.Assertions.assertThat(continued).isTrue();
            org.assertj.core.api.Assertions.assertThat(request.getSession(false).getAttribute("pending-oauth-request")).isEqualTo("preserved");
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }
    @Test void anonymousSessionIsRejected() throws Exception {
        mvc.perform(get("/bff/session")).andExpect(status().isUnauthorized());
    }
    @Test void sessionReturnsOnlyDisplayInformation() throws Exception {
        mvc.perform(get("/bff/session").with(authenticated(accounts.findByUsername("admin-test").orElseThrow().id().toString(), 0L)))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.username").value("admin-test"))
            .andExpect(jsonPath("$.accessToken").doesNotExist());
    }
    @Test void authorizationUsesPkceAndState() throws Exception {
        mvc.perform(get("/oauth2/authorization/appnexus"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("code_challenge=")))
            .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("code_challenge_method=S256")))
            .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("state=")));
    }
    @Test void logoutRequiresCsrf() throws Exception {
        mvc.perform(post("/bff/logout").with(authenticated(accounts.findByUsername("admin-test").orElseThrow().id().toString(), 0L))).andExpect(status().isForbidden());
        mvc.perform(post("/bff/logout").with(authenticated(accounts.findByUsername("admin-test").orElseThrow().id().toString(), 0L)).with(csrf())).andExpect(status().isNoContent());
    }
    @Test void persistentPasswordIsActuallyChecked() throws Exception {
        mvc.perform(post("/login").with(csrf()).param("username","admin-test").param("password","wrong"))
            .andExpect(redirectedUrl("http://localhost:8848/#/login?credentials=1&error=1"));
        mvc.perform(post("/login").with(csrf()).param("username","admin-test").param("password","integration-test-password"))
            .andExpect(status().is3xxRedirection());
    }
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Test void bootstrapIsIdempotentAndAccountIdsAreUuidV7() {
        var account = accounts.findByUsername("admin-test").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(account.id().version()).isEqualTo(7);
        org.assertj.core.api.Assertions.assertThat(accounts.createInitialAdministrator(
            new com.appnexus.platform.identity.AccountCredentials(java.util.UUID.randomUUID(),
                "replacement-admin", "replacement", "not-a-real-hash", true, 0))).isFalse();
        org.assertj.core.api.Assertions.assertThat(accounts.findByUsername("replacement-admin")).isEmpty();
    }
    @Test void disabledAccountCannotUseExistingSession() throws Exception {
        var id = accounts.findByUsername("admin-test").orElseThrow().id().toString();
        jdbc.update("UPDATE identity_account SET enabled=FALSE WHERE username='admin-test'");
        try {
            mvc.perform(get("/bff/session").with(authenticated(id, 0L))).andExpect(status().isUnauthorized());
        } finally {
            jdbc.update("UPDATE identity_account SET enabled=TRUE WHERE username='admin-test'");
        }
    }
    @Test void loginAttemptsAreLimitedAcrossRequests() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/login").with(csrf()).param("username","limit-test").param("password","incorrect"))
                .andExpect(status().is3xxRedirection());
        }
        mvc.perform(post("/login").with(csrf()).param("username","limit-test").param("password","incorrect"))
            .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"));
    }

    /** 过期或缺失的登录 CSRF 仍拒绝认证，但返回前端可恢复页面，不展示后端错误白页。 */
    @Test void expiredLoginCsrfReturnsToFrontend() throws Exception {
        mvc.perform(post("/login").param("username", "admin-test").param("password", "unused"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("http://localhost:8848/#/login?credentials=1&reason=csrfExpired"));
    }
}
