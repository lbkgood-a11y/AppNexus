package com.appnexus.platform;
import java.nio.file.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
abstract class DatabaseTestSupport {
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    static final GenericContainer<?> VALKEY = new GenericContainer<>(DockerImageName.parse("valkey/valkey:8-alpine"))
        .withExposedPorts(6379).withCommand("valkey-server", "--requirepass", "integration-test-password")
        .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));
    static final Path KEY_STORE;
    static {
        MYSQL.start(); VALKEY.start();
        try {
            KEY_STORE = Files.createTempDirectory("appnexus-test-keys").resolve("signing.p12");
            var keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
            var process = new ProcessBuilder(keytool, "-genkeypair", "-alias", "appnexus",
                "-keyalg", "RSA", "-keysize", "2048", "-storetype", "PKCS12",
                "-keystore", KEY_STORE.toString(), "-storepass", "integration-test-password",
                "-dname", "CN=AppNexus Integration Test", "-validity", "2", "-noprompt")
                .redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            if (process.waitFor() != 0) throw new IllegalStateException("Cannot create test signing key");
        } catch (Exception error) { throw new ExceptionInInitializerError(error); }
    }
    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> MYSQL.getJdbcUrl() +
            "?preserveInstants=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
        properties.add("spring.data.redis.host", VALKEY::getHost);
        properties.add("spring.data.redis.port", () -> VALKEY.getMappedPort(6379));
        properties.add("spring.data.redis.password", () -> "integration-test-password");
        properties.add("app.signing-key-store", KEY_STORE::toString);
        properties.add("app.signing-key-password", () -> "integration-test-password");
        properties.add("app.bootstrap.username", () -> "admin-test");
        properties.add("app.bootstrap.password", () -> "integration-test-password");
        properties.add("app.bff-secret", () -> "integration-test-client-secret");
        // 仅容器测试使用的 32 字节加密密钥，不是部署示例或真实环境密钥。
        properties.add("app.mfa.encryption-key", () -> java.util.Base64.getEncoder().encodeToString(new byte[32]));
        // 旧功能回归不修改共享管理员因子；平台强制策略由独立过滤器测试覆盖。
        properties.add("app.mfa.require-platform", () -> false);
        // 任务租约测试显式领取，防止后台 Worker 消费测试事件；生产默认仍开启。
        properties.add("app.events.worker.enabled", () -> false);
        // 所有共享容器测试使用同一 OAuth 注册地址，避免不同测试上下文误触生产式配置漂移保护。
        properties.add("app.public-url", () -> "http://localhost:18080");
        properties.add("app.frontend-url", () -> "http://localhost:8848");
    }

    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.session.SessionRepository sessionRepository;

    /**
     * 在真实 Valkey 仓储创建带凭据版本的登录会话，以 Cookie 驱动完整过滤器链。
     * @param id 当前测试账号的不可变主体标识
     * @param version 模拟认证时的凭据版本；旧版本应被正式过滤器拒绝
     */
    protected org.springframework.test.web.servlet.request.RequestPostProcessor authenticated(String id, long version) {
        var session = sessionRepository.createSession();
        var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
            org.springframework.security.core.userdetails.User.withUsername(id).password("").authorities("ACCOUNT").build(),
            null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ACCOUNT"))));
        session.setAttribute("SPRING_SECURITY_CONTEXT", context);
        session.setAttribute("appnexus.credentialVersion", version);
        sessionRepository.save(session);
        String cookie = java.util.Base64.getEncoder().encodeToString(session.getId().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return request -> { request.setCookies(new jakarta.servlet.http.Cookie("JSESSIONID", cookie)); return request; };
    }
}
