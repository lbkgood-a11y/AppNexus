package com.appnexus.platform.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.settings.*;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.*;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;

/**
 * 装配授权服务器、BFF 登录与持久授权能力。
 * 浏览器仅持有会话 Cookie，Token 交换和存储由服务端完成；认证成功不代表取得业务权限。
 */
@Configuration(proxyBeanMethods = false)
public class WebSecurityConfiguration {
    @Bean
    @Order(1)
    SecurityFilterChain authorizationServer(HttpSecurity http, @Value("${app.frontend-url}") String frontendUrl, com.appnexus.platform.identity.AccountRepository accounts,
            org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector introspection) throws Exception {
        var server = new OAuth2AuthorizationServerConfigurer();
        // 授权端点可能直接签发授权码并结束链，必须在它执行前拒绝缺少认证时间的旧会话。
        http.addFilterAfter(new AccountStatusFilter(accounts),
                org.springframework.security.web.context.SecurityContextHolderFilter.class)
            .securityMatcher(server.getEndpointsMatcher())
            .with(server, config -> config.oidc(Customizer.withDefaults()))
            // SAS 7 initializes its resource-server JWT config for OIDC. Override its bearer
            // authentication manager with the standard opaque-token provider; ID tokens remain signed JWTs.
            .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.authenticationManager(
                new org.springframework.security.authentication.ProviderManager(
                    new org.springframework.security.oauth2.server.resource.authentication.OpaqueTokenAuthenticationProvider(introspection)))))
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .exceptionHandling(errors -> errors.authenticationEntryPoint(
                new LoginUrlAuthenticationEntryPoint(frontendUrl + "/#/login?credentials=1")));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain application(HttpSecurity http,
            @Value("${app.frontend-url}") String frontendUrl,
            ClientRegistrationRepository registrations,
            com.appnexus.platform.audit.SecurityLogRepository logs,
            com.appnexus.platform.tenant.TenantAccess tenantAccess,
            com.appnexus.platform.authorization.OrganizationAuthorization organizationAuthorization,
            org.springframework.data.redis.core.StringRedisTemplate redis,
            com.appnexus.platform.identity.AccountRepository accounts,
            @Value("${app.login.max-attempts-per-ip:60}") long perIp,
            @Value("${app.login.max-attempts-per-account-ip:10}") long perAccountIp) throws Exception {
        var resolver = new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        http.addFilterBefore(new LoginAttemptFilter(redis, perIp, perAccountIp),
                org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new AccountStatusFilter(accounts),
                org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class)
            .addFilterAfter(new TenantExecutionFilter(tenantAccess, organizationAuthorization), AccountStatusFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/login/csrf", "/error", "/oauth2/authorization/**", "/login/oauth2/code/**").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(errors -> errors.defaultAuthenticationEntryPointFor(
                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                request -> request.getRequestURI().startsWith("/bff/") || request.getRequestURI().startsWith("/api/"))
                .accessDeniedHandler((request, response, denied) -> {
                    // 登录页停留至会话过期时，旧 CSRF 必须拒绝；返回可恢复的前端提示，不能禁用 CSRF。
                    if ("POST".equals(request.getMethod()) && request.getRequestURI().equals(request.getContextPath() + "/login") &&
                            denied instanceof org.springframework.security.web.csrf.CsrfException) {
                        response.sendRedirect(frontendUrl + "/#/login?credentials=1&reason=csrfExpired");
                    } else {
                        response.sendError(403);
                    }
                }))
            .sessionManagement(session -> session.sessionAuthenticationStrategy(
                new ChangeSessionIdAuthenticationStrategy()))
            .formLogin(form -> form.loginProcessingUrl("/login").loginPage(frontendUrl + "/#/login?credentials=1")
                .failureUrl(frontendUrl + "/#/login?credentials=1&error=1")
                .successHandler(new CredentialSession(accounts, "/oauth2/authorization/appnexus", false, logs)))
            .oauth2Login(oauth -> oauth.authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(resolver))
                .successHandler(new CredentialSession(accounts, frontendUrl + "/#/welcome", true, logs)))
            .logout(logout -> logout.logoutUrl("/bff/logout")
                .addLogoutHandler((request, response, auth) -> {
                    if (auth != null) logs.append(java.util.UUID.fromString(auth.getName()), com.appnexus.platform.audit.SecurityLog.Action.LOGOUT);
                })
                .invalidateHttpSession(true).clearAuthentication(true).deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    /**
     * 初始化或校验持久 BFF 客户端注册，禁止重启时静默覆盖密钥和回调地址。
     *
     * @param encoder 客户端密钥的哈希编码器
     * @param secret 服务端注入的 BFF 密钥；各实例必须一致，不得记录到日志
     * @param publicUrl 浏览器使用的公开基地址；回调地址必须与数据库注册精确匹配
     * @param jdbc 已完成必要数据库迁移的 JDBC 访问入口
     * @return 持久化客户端注册仓储
     * @throws IllegalStateException 已有注册与当前密钥或回调配置不一致时抛出，要求显式迁移
     */
    @Bean
    @org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization
    RegisteredClientRepository registeredClients(PasswordEncoder encoder,
            @Value("${app.bff-secret}") String secret,
            @Value("${app.public-url}") String publicUrl, JdbcTemplate jdbc) {
        var repository = new JdbcRegisteredClientRepository(jdbc);
        var client = RegisteredClient.withId("appnexus-bff")
            .clientId("appnexus-bff").clientSecret(encoder.encode(secret))
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(publicUrl + "/login/oauth2/code/appnexus")
            .scope(OidcScopes.OPENID).scope(OidcScopes.PROFILE)
            .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(false).build())
            .tokenSettings(TokenSettings.builder().accessTokenFormat(OAuth2TokenFormat.REFERENCE).build())
            .build();
        var existing = repository.findByClientId("appnexus-bff");
        if (existing == null) {
            try { repository.save(client); }
            catch (org.springframework.dao.DuplicateKeyException race) {
                if (repository.findByClientId("appnexus-bff") == null) throw race;
            }
        } else if (!encoder.matches(secret, existing.getClientSecret())
                || !existing.getRedirectUris().contains(publicUrl + "/login/oauth2/code/appnexus")) {
            throw new IllegalStateException("BFF client configuration differs from persisted registration; migrate it explicitly");
        }
        return repository;
    }

    @Bean
    OAuth2AuthorizationService authorizationService(JdbcTemplate jdbc, RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationService(jdbc, clients);
    }
    @Bean
    org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector localIntrospection(
            OAuth2AuthorizationService authorizations, com.appnexus.platform.identity.AccountRepository accounts) {
        return token -> {
            var authorization = authorizations.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);
            if (authorization == null || authorization.getAccessToken() == null || !authorization.getAccessToken().isActive())
                throw new org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException("Invalid token");
            var account = accounts.findById(java.util.UUID.fromString(authorization.getPrincipalName()))
                .filter(com.appnexus.platform.identity.AccountCredentials::enabled)
                .orElseThrow(() -> new org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException("Invalid token"));
            var scopes = authorization.getAccessToken().getToken().getScopes();
            var authorities = scopes.stream().<org.springframework.security.core.GrantedAuthority>map(
                scope -> new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_" + scope)).toList();
            return new org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal(
                account.id().toString(), java.util.Map.of("sub", account.id().toString(), "scope", scopes), authorities);
        };
    }
    @Bean
    OAuth2AuthorizationConsentService consentService(JdbcTemplate jdbc, RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationConsentService(jdbc, clients);
    }
    @Bean
    OAuth2AuthorizedClientRepository authorizedClients() {
        return new HttpSessionOAuth2AuthorizedClientRepository();
    }
    @Bean
    AuthorizationServerSettings authorizationServerSettings(@Value("${app.public-url}") String publicUrl) {
        return AuthorizationServerSettings.builder().issuer(publicUrl).build();
    }
}
