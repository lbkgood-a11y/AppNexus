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
    /**
     * @param maximum Web 会话绝对有效期，默认 8 小时，合法范围大于零至 24 小时
     * @return 在所有安全过滤器链中共享的期限策略
     */
    @Bean
    SessionLifetime sessionLifetime(@Value("${app.session.absolute-timeout:8h}") java.time.Duration maximum) {
        return new SessionLifetime(maximum);
    }
    @Bean
    @Order(1)
    SecurityFilterChain authorizationServer(HttpSecurity http, @Value("${app.frontend-url}") String frontendUrl, com.appnexus.platform.identity.AccountRepository accounts,
            org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector introspection,
            RegisteredClientRepository clients,RotatingAuthorizationService authorizations,
            PasswordEncoder encoder,JdbcTemplate jdbc,
            SessionLifetime lifetime) throws Exception {
        var server = new OAuth2AuthorizationServerConfigurer();
        // 授权端点可能直接签发授权码并结束链，必须在它执行前拒绝缺少认证时间的旧会话。
        http.addFilterAfter(new AccountStatusFilter(accounts, lifetime),
                org.springframework.security.web.context.SecurityContextHolderFilter.class)
            .securityMatcher(server.getEndpointsMatcher())
            .with(server, config -> config.oidc(Customizer.withDefaults())
                .clientAuthentication(client->client.authenticationConverter(NativeRefreshAuthentication.converter())
                    .authenticationProviders(providers->{
                        providers.add(0,new NativeRefreshAuthentication(clients));
                        for(var provider:providers)if(provider instanceof org.springframework.security.oauth2.server.authorization.authentication.ClientSecretAuthenticationProvider secret)
                            secret.setPasswordEncoder(new RotatingClientPasswordEncoder(encoder,jdbc));
                    }))
                .tokenEndpoint(endpoint->endpoint.authenticationProviders(providers->{
                    for(int index=0;index<providers.size();index++)
                        if(providers.get(index) instanceof org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationProvider)
                            providers.set(index,authorizations.protect(providers.get(index)));
                })))
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
    SecurityFilterChain partnerApi(HttpSecurity http,
            org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector introspection,
            JdbcTemplate jdbc,org.springframework.data.redis.core.StringRedisTemplate redis)throws Exception{
        http.securityMatcher("/v1/**")
            .sessionManagement(session->session.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            // 外部接口仅接受 Bearer 机密客户端，Cookie 不参与，不允许跨接口面借用 Web 登录。
            .csrf(csrf->csrf.disable()).requestCache(cache->cache.disable())
            .oauth2ResourceServer(resource->resource.opaqueToken(token->token.introspector(introspection)))
            .addFilterAfter(new PartnerCallFilter(jdbc,redis),org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class)
            .authorizeHttpRequests(auth->auth.anyRequest().hasAuthority("PARTNER_CLIENT"));
        return http.build();
    }
    @Bean
    @Order(3)
    SecurityFilterChain nativeApi(HttpSecurity http,
            org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector introspection,
            com.appnexus.platform.identity.AccountRepository accounts,SessionLifetime lifetime,
            com.appnexus.platform.tenant.TenantAccess tenants,
            com.appnexus.platform.authorization.OrganizationAuthorization organizationAuthorization)throws Exception{
        http.securityMatcher(request->request.getRequestURI().startsWith(request.getContextPath()+"/api/")&&request.getHeader("Authorization")!=null)
            .sessionManagement(session->session.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            // 此链仅接受 Authorization Bearer，Cookie 不参与认证，因此不使用 Cookie CSRF 模型。
            .csrf(csrf->csrf.disable()).requestCache(cache->cache.disable())
            .oauth2ResourceServer(resource->resource.opaqueToken(token->token.introspector(introspection)))
            .authorizeHttpRequests(auth->auth.anyRequest().authenticated())
            .addFilterAfter(new AccountStatusFilter(accounts,lifetime),org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class)
            .addFilterAfter(new TenantExecutionFilter(tenants,organizationAuthorization),AccountStatusFilter.class);
        return http.build();
    }
    @Bean
    @Order(4)
    SecurityFilterChain application(HttpSecurity http,
            @Value("${app.frontend-url}") String frontendUrl,
            ClientRegistrationRepository registrations,
            com.appnexus.platform.audit.SecurityLogRepository logs,
            com.appnexus.platform.tenant.TenantAccess tenantAccess,
            com.appnexus.platform.authorization.OrganizationAuthorization organizationAuthorization,
            org.springframework.data.redis.core.StringRedisTemplate redis,
            com.appnexus.platform.identity.AccountRepository accounts,
            SessionLifetime lifetime,
            com.appnexus.platform.identity.MultiFactorAuthentication mfa,
            com.appnexus.platform.tenant.PlatformAdministration platform,
            @Value("${app.mfa.require-platform:true}") boolean requirePlatformMfa,
            @Value("${app.login.max-attempts-per-ip:60}") long perIp,
            @Value("${app.login.max-attempts-per-account-ip:10}") long perAccountIp) throws Exception {
        var resolver = new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        http.addFilterBefore(new LoginAttemptFilter(redis, perIp, perAccountIp),
                org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new AccountStatusFilter(accounts, lifetime),
                org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class)
            .addFilterAfter(new TenantExecutionFilter(tenantAccess, organizationAuthorization), AccountStatusFilter.class)
            .addFilterAfter(new PlatformMfaFilter(requirePlatformMfa,mfa),AccountStatusFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/login/csrf", "/login/recovery", "/login/recovery/completion", "/error", "/oauth2/authorization/**", "/login/oauth2/code/**").permitAll()
                .requestMatchers("/v3/api-docs/**","/v3/api-docs.yaml").access((authentication,context)->{
                    var identity=authentication.get();
                    boolean allowed=identity!=null&&identity.isAuthenticated()&&!(identity instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)&&
                        platform.isAdministrator(java.util.UUID.fromString(identity.getName()));
                    return new org.springframework.security.authorization.AuthorizationDecision(allowed);
                })
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
                .failureHandler((request,response,failure)->{
                    // 响应不区分账号不存在、停用或密码错误；已存在账号的本人日志保留失败依据。
                    try {
                        String name=request.getParameter("username");
                        if(name!=null&&name.length()<=100)accounts.findByUsername(name).ifPresent(account->
                            logs.append(account.id(),com.appnexus.platform.audit.SecurityLog.Action.LOGIN_FAILED));
                    }catch(RuntimeException unavailable){response.setStatus(503);return;}
                    response.sendRedirect(frontendUrl + "/#/login?credentials=1&error=1");
                })
                .successHandler(new CredentialSession(accounts, "/oauth2/authorization/appnexus", false, logs, mfa, frontendUrl + "/#/login?credentials=1&error=1")))
            .oauth2Login(oauth -> oauth.authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(resolver))
                .successHandler(new CredentialSession(accounts, frontendUrl + "/#/welcome", true, logs, mfa, frontendUrl + "/#/login?credentials=1&error=1")))
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
            @Value("${app.public-url}") String publicUrl, JdbcTemplate jdbc,
            @Value("${app.native.redirect-uri:}") String nativeRedirect) {
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
        if(!nativeRedirect.isBlank()){
            var uri=java.net.URI.create(nativeRedirect);
            if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getFragment()!=null||uri.getUserInfo()!=null||nativeRedirect.contains("*"))
                throw new IllegalArgumentException("Native callback must be an exact HTTPS App Link");
            var nativeClient=repository.findByClientId("appnexus-native");
            if(nativeClient==null)repository.save(RegisteredClient.withId("appnexus-native").clientId("appnexus-native")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(nativeRedirect).scope(OidcScopes.OPENID).scope(OidcScopes.PROFILE)
                .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(true).build())
                .tokenSettings(TokenSettings.builder().accessTokenFormat(OAuth2TokenFormat.REFERENCE)
                    .accessTokenTimeToLive(java.time.Duration.ofMinutes(5)).refreshTokenTimeToLive(java.time.Duration.ofDays(7))
                    .reuseRefreshTokens(false).build()).build());
            else if(!nativeClient.getRedirectUris().equals(java.util.Set.of(nativeRedirect))||
                    !nativeClient.getClientAuthenticationMethods().equals(java.util.Set.of(ClientAuthenticationMethod.NONE))||
                    !nativeClient.getAuthorizationGrantTypes().equals(java.util.Set.of(AuthorizationGrantType.AUTHORIZATION_CODE,AuthorizationGrantType.REFRESH_TOKEN))||
                    !nativeClient.getClientSettings().isRequireProofKey()||nativeClient.getTokenSettings().isReuseRefreshTokens())
                throw new IllegalStateException("Native callback differs from persisted registration; migrate explicitly");
        }
        return repository;
    }

    @Bean
    RotatingAuthorizationService authorizationService(JdbcTemplate jdbc, RegisteredClientRepository clients,
            org.springframework.transaction.PlatformTransactionManager transactions,com.appnexus.platform.audit.SecurityLogRepository logs) {
        return new RotatingAuthorizationService(new JdbcOAuth2AuthorizationService(jdbc, clients),jdbc,transactions,logs);
    }
    /**
     * 标准 Token 生成管线；公开原生客户端仅在强制 PKCE 注册下获得可轮换刷新凭证。
     * @param keys 持久签名公私钥源，ID Token 继续签名，Access Token 保持不透明
     * @return 框架标准访问/身份 Token 生成器及受控公开客户端刷新生成器
     */
    @Bean
    org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator<?> tokenGenerator(
            com.nimbusds.jose.jwk.source.JWKSource<com.nimbusds.jose.proc.SecurityContext> keys,IntegrationClients integrations){
        var jwt=new org.springframework.security.oauth2.server.authorization.token.JwtGenerator(new org.springframework.security.oauth2.jwt.NimbusJwtEncoder(keys));
        var access=new org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator();
        access.setAccessTokenCustomizer(context->{
            if(AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())){
                var policy=integrations.policy(context.getRegisteredClient().getId());
                if(policy==null||!policy.enabled())throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
                context.getClaims().claim("integrationVersion",policy.version()).claim("integrationTenant",policy.tenant().toString());
            }
            var authorization=context.getAuthorization();
            if(authorization!=null&&authorization.getAttribute(RotatingAuthorizationService.VERSION) instanceof Number version){
                context.getClaims().claim(RotatingAuthorizationService.VERSION,version.intValue());
                context.getClaims().claim(RotatingAuthorizationService.TENANT,authorization.getAttribute(RotatingAuthorizationService.TENANT));
                context.getClaims().claim(RotatingAuthorizationService.MEMBER,authorization.getAttribute(RotatingAuthorizationService.MEMBER));
            }
        });
        var standard=new org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator();
        org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator<OAuth2RefreshToken> refresh=context->{
            if(!OAuth2TokenType.REFRESH_TOKEN.equals(context.getTokenType()))return null;
            var client=context.getRegisteredClient();
            if(!client.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE))return standard.generate(context);
            if(!client.getClientSettings().isRequireProofKey()||!client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN))return null;
            var now=java.time.Instant.now();
            var previous=context.getAuthorization()==null?null:context.getAuthorization().getRefreshToken();
            var expires=previous==null?now.plus(client.getTokenSettings().getRefreshTokenTimeToLive()):previous.getToken().getExpiresAt();
            byte[] bytes=new byte[64];new java.security.SecureRandom().nextBytes(bytes);
            return new OAuth2RefreshToken(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),now,expires);
        };
        return new org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator(jwt,access,refresh);
    }
    @Bean
    org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector localIntrospection(
            OAuth2AuthorizationService authorizations, com.appnexus.platform.identity.AccountRepository accounts,IntegrationClients integrations) {
        return token -> {
            var authorization = authorizations.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);
            if (authorization == null || authorization.getAccessToken() == null || !authorization.getAccessToken().isActive())
                throw new org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException("Invalid token");
            if(AuthorizationGrantType.CLIENT_CREDENTIALS.equals(authorization.getAuthorizationGrantType())){
                var policy=integrations.policy(authorization.getRegisteredClientId());var claims=authorization.getAccessToken().getClaims();
                if(policy==null||!policy.enabled()||claims==null||!(claims.get("integrationVersion") instanceof Number version)||
                        version.intValue()!=policy.version()||!policy.tenant().toString().equals(claims.get("integrationTenant")))
                    throw new org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException("Invalid client authority");
                var scopes=authorization.getAccessToken().getToken().getScopes();
                var authorities=new java.util.ArrayList<org.springframework.security.core.GrantedAuthority>();
                authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("PARTNER_CLIENT"));
                scopes.forEach(scope->authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_"+scope)));
                return new org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal(policy.id(),
                    java.util.Map.of("clientId",policy.id(),"tenantId",policy.tenant().toString(),"authorityVersion",policy.version(),"scope",scopes),authorities);
            }
            var account = accounts.findById(java.util.UUID.fromString(authorization.getPrincipalName()))
                .filter(com.appnexus.platform.identity.AccountCredentials::enabled)
                .orElseThrow(() -> new org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException("Invalid token"));
            var scopes = authorization.getAccessToken().getToken().getScopes();
            var authorities = scopes.stream().<org.springframework.security.core.GrantedAuthority>map(
                scope -> new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_" + scope)).toList();
            var attributes=new java.util.HashMap<String,Object>();attributes.put("sub",account.id().toString());attributes.put("scope",scopes);
            if(authorization.getAttribute(RotatingAuthorizationService.VERSION) instanceof Number version){
                var claims=authorization.getAccessToken().getClaims();
                if(claims==null||!(claims.get(RotatingAuthorizationService.VERSION) instanceof Number tokenVersion)||tokenVersion.longValue()!=version.longValue())
                    throw new org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException("Stale tenant token");
                attributes.put(RotatingAuthorizationService.VERSION,version.longValue());
                attributes.put(RotatingAuthorizationService.TENANT,authorization.getAttribute(RotatingAuthorizationService.TENANT));
                attributes.put(RotatingAuthorizationService.MEMBER,authorization.getAttribute(RotatingAuthorizationService.MEMBER));
            }
            return new org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal(account.id().toString(),attributes, authorities);
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
