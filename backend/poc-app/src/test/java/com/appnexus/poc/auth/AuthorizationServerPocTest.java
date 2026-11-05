package com.appnexus.poc.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.appnexus.poc.PocApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = PocApplication.class)
@AutoConfigureMockMvc
class AuthorizationServerPocTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegisteredClientRepository clients;

    @Test
    void publishesOidcDiscoveryMetadata() throws Exception {
        mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value("http://localhost:9000"))
                .andExpect(jsonPath("$.authorization_endpoint").exists())
                .andExpect(jsonPath("$.token_endpoint").exists())
                .andExpect(jsonPath("$.revocation_endpoint").exists());
    }

    @Test
    void mobileClientRequiresPkceAndDoesNotHaveASecret() {
        var client = clients.findByClientId("appnexus-mobile");

        assertThat(client).isNotNull();
        assertThat(client.getClientSecret()).isNull();
        assertThat(client.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(client.getAuthorizationGrantTypes()).contains(
                AuthorizationGrantType.AUTHORIZATION_CODE,
                AuthorizationGrantType.REFRESH_TOKEN);
        assertThat(client.getTokenSettings().getAccessTokenFormat()).isEqualTo(OAuth2TokenFormat.REFERENCE);
    }

    @Test
    void integrationClientUsesOnlyClientCredentials() {
        var client = clients.findByClientId("appnexus-integration");

        assertThat(client).isNotNull();
        assertThat(client.getAuthorizationGrantTypes()).containsExactly(AuthorizationGrantType.CLIENT_CREDENTIALS);
        assertThat(client.getTokenSettings().getAccessTokenFormat()).isEqualTo(OAuth2TokenFormat.REFERENCE);
    }

    @Test
    void authorizationCodeRequestWithPkceProducesACode() throws Exception {
        mockMvc.perform(get("/oauth2/authorize")
                        .queryParam("response_type", "code")
                        .queryParam("client_id", "appnexus-mobile")
                        .queryParam("scope", "openid profile")
                        .queryParam("redirect_uri", "https://mobile.example.test/oauth/callback")
                        .queryParam("state", "test-state")
                        .queryParam("nonce", "test-nonce")
                        .queryParam("code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
                        .queryParam("code_challenge_method", "S256")
                        .with(user("poc-user").roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("https://mobile.example.test/oauth/callback?code=*&state=test-state"));
    }
}
