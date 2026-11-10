package com.appnexus.poc.auth;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.oidc.authentication.OidcAuthorizationCodeAuthenticationProvider;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.*;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import static org.assertj.core.api.Assertions.*;

/**
 * 一个独立企业 OIDC 身份源的可复现 PoC；真实 HTTP 换码、JWKS 验签和 userinfo 校验。
 * 测试 IdP 只监听回环随机端口，所有密钥临时生成，不连接真实企业或创建真实账号。
 */
class EnterpriseOidcPocTest {
    private static final String CLIENT="enterprise-poc";
    private static final String CALLBACK="https://platform.example.test/login/oauth2/code/enterprise";
    private static final String VERIFIER="enterprise-poc-verifier-0123456789-ABCDEFGHIJKLMNOPQRSTUVWXYZ-abcdef";
    private HttpServer server;
    private RSAKey key;
    private String issuer,tokenIssuer,audience,nonce,subject="employee-42",userInfoSubject="employee-42";
    private String email="first@example.test";
    private Map<String,String> received;
    private OidcAuthorizationCodeAuthenticationProvider provider;
    private ClientRegistration client;
    private final Set<String> authorizationCodes=java.util.concurrent.ConcurrentHashMap.newKeySet();

    @BeforeEach void startProvider()throws Exception{
        key=new RSAKeyGenerator(2048).keyID("enterprise-poc-key").generate();
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        issuer="http://127.0.0.1:"+server.getAddress().getPort();tokenIssuer=issuer;audience=CLIENT;nonce=hash("nonce-evidence");
        server.createContext("/jwks",exchange->json(exchange,new JWKSet(key.toPublicJWK()).toString()));
        server.createContext("/userinfo",exchange->{
            if(!"Bearer enterprise-poc-access-token".equals(exchange.getRequestHeaders().getFirst("Authorization"))){exchange.sendResponseHeaders(401,-1);exchange.close();return;}
            json(exchange,"{\"sub\":\""+userInfoSubject+"\",\"email\":\""+email+"\"}");
        });
        server.createContext("/token",exchange->{
            String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            received=new HashMap<>();
            for(String pair:body.split("&")){String[] entry=pair.split("=",2);received.put(URLDecoder.decode(entry[0],StandardCharsets.UTF_8),entry.length==2?URLDecoder.decode(entry[1],StandardCharsets.UTF_8):"");}
            // IdP 端同样验证授权码、固定回调及 PKCE，不能只断言客户端生成了参数。
            if(!authorizationCodes.remove(received.getOrDefault("code",""))||!CALLBACK.equals(received.get("redirect_uri"))||!VERIFIER.equals(received.get("code_verifier"))){exchange.sendResponseHeaders(400,-1);exchange.close();return;}
            try{json(exchange,"{\"access_token\":\"enterprise-poc-access-token\",\"token_type\":\"Bearer\",\"expires_in\":300,\"scope\":\"openid profile email\",\"id_token\":\""+idToken()+"\"}");}
            catch(Exception failure){exchange.sendResponseHeaders(500,-1);exchange.close();}
        });
        server.start();
        client=ClientRegistration.withRegistrationId("enterprise").clientId(CLIENT).clientSecret("poc-only-client-secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri(CALLBACK)
            .scope("openid","profile","email").authorizationUri(issuer+"/authorize").tokenUri(issuer+"/token")
            .jwkSetUri(issuer+"/jwks").issuerUri(issuer).userInfoUri(issuer+"/userinfo").userNameAttributeName("sub").build();
        provider=new OidcAuthorizationCodeAuthenticationProvider(new RestClientAuthorizationCodeTokenResponseClient(),new OidcUserService());
    }
    @AfterEach void stopProvider(){if(server!=null)server.stop(0);}

    @Test void externalCodePkceSignatureAndStableBindingWork()throws Exception{
        OidcUser user=authenticate("state-evidence");
        assertThat(user.getSubject()).isEqualTo(subject);
        assertThat(user.getIssuer().toString()).isEqualTo(issuer);
        assertThat(received).containsEntry("grant_type","authorization_code").containsEntry("code_verifier",VERIFIER);
        UUID account=UUID.randomUUID();var binding=Map.of(new IdentityKey(issuer,subject),account);
        assertThat(boundAccount(binding,user)).contains(account);
        email="changed@example.test";
        assertThat(boundAccount(binding,authenticate("state-evidence"))).contains(account);
        assertThat(boundAccount(Map.of(),user)).isEmpty();
        assertThat(boundAccount(Map.of(new IdentityKey(issuer+"/other",subject),account),user)).isEmpty();
    }
    @Test void incorrectIssuerIsRejected(){tokenIssuer=issuer+"/untrusted";assertThatThrownBy(()->authenticate("state-evidence")).isInstanceOf(OAuth2AuthenticationException.class);}
    @Test void incorrectAudienceIsRejected(){audience="other-client";assertThatThrownBy(()->authenticate("state-evidence")).isInstanceOf(OAuth2AuthenticationException.class);}
    @Test void incorrectNonceAndStateAreRejected(){
        nonce="wrong-nonce";assertThatThrownBy(()->authenticate("state-evidence")).isInstanceOf(OAuth2AuthenticationException.class);
        assertThatThrownBy(()->authenticate("wrong-state")).isInstanceOf(OAuth2AuthenticationException.class);
    }
    @Test void differentUserInfoSubjectIsRejected(){userInfoSubject="another-person";assertThatThrownBy(()->authenticate("state-evidence")).isInstanceOf(OAuth2AuthenticationException.class);}

    /** 构造已完成浏览器回调的标准交换上下文；state、nonce 和 PKCE 由框架继续复核。 */
    private OidcUser authenticate(String responseState){
        var request=OAuth2AuthorizationRequest.authorizationCode().authorizationUri(issuer+"/authorize").clientId(CLIENT)
            .redirectUri(CALLBACK).scopes(Set.of("openid","profile","email")).state("state-evidence")
            .attributes(values->{values.put("nonce","nonce-evidence");values.put("code_verifier",VERIFIER);})
            .additionalParameters(values->{values.put("nonce",hash("nonce-evidence"));values.put("code_challenge",hash(VERIFIER));values.put("code_challenge_method","S256");}).build();
        String code=UUID.randomUUID().toString();authorizationCodes.add(code);
        var response=OAuth2AuthorizationResponse.success(code).redirectUri(CALLBACK).state(responseState).build();
        var result=(OAuth2LoginAuthenticationToken)provider.authenticate(new OAuth2LoginAuthenticationToken(client,new OAuth2AuthorizationExchange(request,response)));
        return (OidcUser)result.getPrincipal();
    }
    /** PoC 的受控预绑定样例；未知外部身份不自动建账、加入租户或获得任何业务权限。 */
    private Optional<UUID> boundAccount(Map<IdentityKey,UUID> bindings,OidcUser user){return Optional.ofNullable(bindings.get(new IdentityKey(user.getIssuer().toString(),user.getSubject())));}
    /** 持久绑定键必须是可信 issuer 和不可变 subject，不能替换为邮箱。 */
    private record IdentityKey(String issuer,String subject){}
    private String idToken()throws Exception{
        Instant now=Instant.now();
        var claims=new JWTClaimsSet.Builder().issuer(tokenIssuer).subject(subject).audience(audience)
            .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(300))).claim("nonce",nonce).build();
        var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),claims);
        jwt.sign(new RSASSASigner(key));return jwt.serialize();
    }
    private static String hash(String input){
        try{return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.US_ASCII)));}
        catch(Exception impossible){throw new IllegalStateException(impossible);}
    }
    private void json(HttpExchange exchange,String body)throws java.io.IOException{
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");
        exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
    }
}
