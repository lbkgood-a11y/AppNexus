package com.appnexus.poc.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.appnexus.poc.PocApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;

@SpringBootTest(classes = PocApplication.class)
@AutoConfigureMockMvc
class WebSessionSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private Environment environment;
    @Autowired private SessionAuthenticationStrategy sessionAuthenticationStrategy;

    @Test
    void stateChangingRequestRequiresCsrfBoundToTheSession() throws Exception {
        mockMvc.perform(post("/poc/session/mutate").with(user("poc-user")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/poc/session/mutate").with(user("poc-user")).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void cookieUsesHostPrefixAndBrowserSecurityAttributes() {
        assertThat(environment.getProperty("server.servlet.session.cookie.name"))
                .isEqualTo("__Host-appnexus-session");
        assertThat(environment.getProperty("server.servlet.session.cookie.http-only", Boolean.class)).isTrue();
        assertThat(environment.getProperty("server.servlet.session.cookie.secure", Boolean.class)).isTrue();
        assertThat(environment.getProperty("server.servlet.session.cookie.same-site")).isEqualTo("lax");
        assertThat(environment.getProperty("server.servlet.session.cookie.path")).isEqualTo("/");
        assertThat(environment.getProperty("server.servlet.session.cookie.domain")).isNull();
    }

    @Test
    void explicitSessionFixationStrategyIsConfiguredAndLogoutInvalidatesSession() throws Exception {
        assertThat(sessionAuthenticationStrategy).isInstanceOf(ChangeSessionIdAuthenticationStrategy.class);
        MockHttpSession authenticated = new MockHttpSession();
        mockMvc.perform(post("/logout").session(authenticated).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(authenticated.isInvalid()).isTrue();
    }
}
