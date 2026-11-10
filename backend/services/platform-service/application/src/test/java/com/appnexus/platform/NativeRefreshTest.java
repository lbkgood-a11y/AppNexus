package com.appnexus.platform;

import java.time.*;
import java.util.*;
import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.settings.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 标准 Token 端点的公开客户端轮换及设备授权隔离；真实 MySQL 行锁防止并发刷新。 */
@SpringBootTest
@ActiveProfiles("local")
@AutoConfigureMockMvc
class NativeRefreshTest extends DatabaseTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired RegisteredClientRepository clients;
    @Autowired OAuth2AuthorizationService authorizations;
    private record Device(String client,String account,String authorization,String token){}
    private Device device(){
        String id=UUID.randomUUID().toString(),account=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,?,?)",account,"native-"+account,"Native test","unused");
        var client=RegisteredClient.withId(id).clientId("native-"+id).clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri("https://native.example.test/oauth/callback").scope("openid")
            .clientSettings(ClientSettings.builder().requireProofKey(true).build())
            .tokenSettings(TokenSettings.builder().accessTokenFormat(OAuth2TokenFormat.REFERENCE).reuseRefreshTokens(false)
                .refreshTokenTimeToLive(Duration.ofDays(7)).build()).build();clients.save(client);
        String refresh=UUID.randomUUID()+"-refresh-"+UUID.randomUUID();Instant now=Instant.now();
        var principal=UsernamePasswordAuthenticationToken.authenticated(account,null,List.of());
        var authorization=OAuth2Authorization.withRegisteredClient(client).id(UUID.randomUUID().toString()).principalName(account)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).authorizedScopes(Set.of("openid"))
            .attribute(Principal.class.getName(),principal)
            .refreshToken(new OAuth2RefreshToken(refresh,now,now.plus(Duration.ofDays(7))))
            .token(new org.springframework.security.oauth2.core.oidc.OidcIdToken("id-token-"+UUID.randomUUID(),now,now.plusSeconds(300),
                Map.of("sub",account,"iss","http://localhost:18080","aud",List.of(client.getClientId()),"auth_time",now.getEpochSecond())),
                metadata->metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME,
                    new LinkedHashMap<>(Map.of("sub",account,"iss","http://localhost:18080","aud",new ArrayList<>(List.of(client.getClientId())),"auth_time",Date.from(now)))))
            .accessToken(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,"access-"+UUID.randomUUID(),now,now.plusSeconds(300),Set.of("openid"))).build();
        authorizations.save(authorization);return new Device(client.getClientId(),account,authorization.getId(),refresh);
    }
    private org.springframework.test.web.servlet.ResultActions refresh(Device device,String token)throws Exception{
        return mvc.perform(post("/oauth2/token").contentType("application/x-www-form-urlencoded")
            .param("grant_type","refresh_token").param("client_id",device.client()).param("refresh_token",token));
    }
    @Test void rotationAndReplayRevokeOnlyTargetDevice()throws Exception{
        var device=device();var other=device();
        Instant expires=authorizations.findById(device.authorization()).getRefreshToken().getToken().getExpiresAt();
        refresh(device,device.token()).andExpect(status().isOk()).andExpect(jsonPath("$.refresh_token").isString());
        var rotated=authorizations.findById(device.authorization());
        assertThat(rotated.getRefreshToken().getToken().getTokenValue()).isNotEqualTo(device.token());
        assertThat(rotated.getRefreshToken().getToken().getExpiresAt()).isEqualTo(expires);
        String current=rotated.getRefreshToken().getToken().getTokenValue();
        refresh(device,device.token()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_grant"));
        assertThat(authorizations.findById(device.authorization())).isNull();
        assertThat(authorizations.findById(other.authorization())).isNotNull();
        refresh(device,current).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account_security_log WHERE account_id=UUID_TO_BIN(?) AND action='REFRESH_TOKEN_REPLAY'",Integer.class,device.account())).isEqualTo(1);
    }
    @Test void crossClientAndUnknownTokensCannotRevokeAnotherDevice()throws Exception{
        var device=device();var other=device();
        refresh(other,device.token()).andExpect(status().isBadRequest());
        refresh(device,"unknown-token").andExpect(status().isBadRequest());
        assertThat(authorizations.findById(device.authorization())).isNotNull();
        refresh(device,device.token()).andExpect(status().isOk());
    }
    /** 账号停用在刷新前复核；不能仅依赖 Access Token 访问时才检查账号状态。 */
    @Test void disabledAccountCannotRefresh()throws Exception{
        var device=device();
        jdbc.update("UPDATE identity_account SET enabled=FALSE,version=version+1 WHERE id=UUID_TO_BIN(?)",device.account());
        refresh(device,device.token()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_grant"));
    }
    @Test void concurrentRefreshHasOneWinnerAndRevokesTheFamily()throws Exception{
        var device=device();
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var gate=new java.util.concurrent.CountDownLatch(1);
            var first=pool.submit(()->{gate.await();return refresh(device,device.token()).andReturn().getResponse().getStatus();});
            var second=pool.submit(()->{gate.await();return refresh(device,device.token()).andReturn().getResponse().getStatus();});
            gate.countDown();assertThat(List.of(first.get(),second.get())).containsExactlyInAnyOrder(200,400);
        }
        assertThat(authorizations.findById(device.authorization())).isNull();
    }
    /** 真实公开客户端授权码交换必须验证 S256，成功后返回轮换 Refresh Token。 */
    @Test void publicAuthorizationCodeUsesPkceBeforeIssuingRefreshToken()throws Exception{
        var device=device();var registered=clients.findByClientId(device.client());
        String verifier="native-code-verifier-0123456789012345678901234567890123456789";
        String challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(java.security.MessageDigest.getInstance("SHA-256")
            .digest(verifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        var principal=UsernamePasswordAuthenticationToken.authenticated(device.account(),null,
            List.of(org.springframework.security.core.authority.FactorGrantedAuthority.fromFactor("PASSWORD")));
        var request=org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest.authorizationCode()
            .authorizationUri("http://localhost:18080/oauth2/authorize").clientId(device.client())
            .redirectUri("https://native.example.test/oauth/callback").scopes(Set.of("openid"))
            .additionalParameters(values->{values.put("code_challenge",challenge);values.put("code_challenge_method","S256");}).build();
        Instant now=Instant.now();String code="code-"+UUID.randomUUID();
        var authorization=OAuth2Authorization.withRegisteredClient(registered).principalName(device.account())
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).authorizedScopes(Set.of("openid"))
            .attribute(Principal.class.getName(),principal).attribute(org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest.class.getName(),request)
            .token(new OAuth2AuthorizationCode(code,now,now.plusSeconds(120))).build();
        authorizations.save(authorization);
        mvc.perform(post("/oauth2/token").contentType("application/x-www-form-urlencoded")
            .param("grant_type","authorization_code").param("client_id",device.client()).param("code",code)
            .param("redirect_uri","https://native.example.test/oauth/callback").param("code_verifier",verifier))
            .andExpect(status().isOk()).andExpect(jsonPath("$.refresh_token").isString()).andExpect(jsonPath("$.id_token").isString());
        mvc.perform(post("/oauth2/token").contentType("application/x-www-form-urlencoded")
            .param("grant_type","authorization_code").param("client_id",device.client()).param("code",code)
            .param("redirect_uri","https://native.example.test/oauth/callback").param("code_verifier","wrong-verifier"))
            .andExpect(status().isBadRequest());
    }
    /** Token 切换租户须刷新，旧 Token 不得被解释为新租户；成员停用也立即拒绝业务请求。 */
    @Test void nativeTenantBindingInvalidatesOldAccessAndRechecksMembership()throws Exception{
        var device=device();String tenant=UUID.randomUUID().toString(),member=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'Native Tenant')",tenant);
        jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",member,tenant,device.account());
        String access=authorizations.findById(device.authorization()).getAccessToken().getToken().getTokenValue();
        mvc.perform(get("/api/tenants").header("Authorization","Bearer "+access)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(post("/api/tenant-context").header("Authorization","Bearer "+access).contentType("application/json")
            .content("{\"tenantId\":\""+tenant+"\",\"expectedVersion\":0}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.refreshRequired").value(true)).andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/api/tenants").header("Authorization","Bearer "+access)).andExpect(status().isUnauthorized());
        refresh(device,device.token()).andExpect(status().isOk());
        String next=authorizations.findById(device.authorization()).getAccessToken().getToken().getTokenValue();
        // 有效成员身份不授予管理能力；403 和可信上下文响应头证明 Token 已进入租户权限管线。
        mvc.perform(get("/api/access/effective-permissions").header("Authorization","Bearer "+next)).andExpect(status().isForbidden())
            .andExpect(header().string("X-Tenant-Context-Version","1")).andExpect(jsonPath("$.code").value("OPERATION_NOT_AUTHORIZED"));
        mvc.perform(post("/api/tenant-context").header("Authorization","Bearer "+next).contentType("application/json")
            .content("{\"tenantId\":\""+tenant+"\",\"expectedVersion\":0}"))
            .andExpect(status().isConflict());
        jdbc.update("UPDATE tenant_member SET enabled=FALSE WHERE id=UUID_TO_BIN(?)",member);
        mvc.perform(get("/api/access/effective-permissions").header("Authorization","Bearer "+next)).andExpect(status().isConflict());
        mvc.perform(get("/bff/platform/users").header("Authorization","Bearer "+next)).andExpect(status().isUnauthorized());
    }
}
