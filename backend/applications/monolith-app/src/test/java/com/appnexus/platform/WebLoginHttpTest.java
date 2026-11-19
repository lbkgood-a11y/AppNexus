package com.appnexus.platform;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
    "server.port=18080", "app.public-url=http://localhost:18080",
    "app.frontend-url=http://localhost:18080"
})
@ActiveProfiles("local")
class WebLoginHttpTest extends DatabaseTestSupport {
    @org.springframework.beans.factory.annotation.Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Test void realAuthorizationCodeSessionAndLogout() throws Exception {
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var client = HttpClient.newBuilder().cookieHandler(cookies)
            .followRedirects(HttpClient.Redirect.NEVER).build();
        var response = get(client, "/oauth2/authorization/appnexus");
        var authorizationUrl = response.headers().firstValue("location").orElseThrow();
        response = get(client, authorizationUrl);
        assertThat(response.headers().firstValue("location").orElseThrow()).contains("/#/login?credentials=1");
        response = get(client, "/login/csrf");
        var matcher = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(response.body());
        assertThat(matcher.find()).isTrue();
        var before = cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue();
        response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:18080/login"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("username=admin-test&password=integration-test-password&_csrf=" +
                URLEncoder.encode(matcher.group(1), StandardCharsets.UTF_8))).build(), HttpResponse.BodyHandlers.ofString());
        var after = cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue();
        assertThat(after).isNotEqualTo(before);
        for (int i = 0; i < 6 && response.statusCode() / 100 == 3; i++) {
            var location = response.headers().firstValue("location").orElseThrow();
            if (location.endsWith("/#/welcome")) break;
            response = get(client, location);
        }
        assertThat(response.headers().firstValue("location").orElse("")).endsWith("/#/welcome");
        response = get(client, "/bff/session");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("admin-test").doesNotContain("accessToken", "refreshToken");
        assertThat(response.headers().allValues("set-cookie").toString()).doesNotContain("accessToken");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM oauth2_authorization", Integer.class)).isPositive();
        // A fresh application instance must accept the same cookie without another login.
        try (var second = new org.springframework.boot.builder.SpringApplicationBuilder(PlatformApplication.class)
                .run("--spring.profiles.active=local", "--server.port=18081",
                    "--app.public-url=http://localhost:18080", "--app.frontend-url=http://localhost:18080",
                    "--app.bff-secret=integration-test-client-secret",
                    "--spring.datasource.url=" + MYSQL.getJdbcUrl() + "?preserveInstants=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true",
                    "--spring.datasource.username=" + MYSQL.getUsername(),
                    "--spring.datasource.password=" + MYSQL.getPassword(),
                    "--spring.data.redis.host=" + VALKEY.getHost(),
                    "--spring.data.redis.port=" + VALKEY.getMappedPort(6379),
                    "--spring.data.redis.password=integration-test-password",
                    "--app.signing-key-store=" + KEY_STORE,
                    "--app.signing-key-password=integration-test-password")) {
            assertThat(get(client, "http://localhost:18081/bff/session").statusCode()).isEqualTo(200);
        }
        var csrf = get(client, "/bff/csrf").body();
        var token = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(csrf);
        var header = Pattern.compile("\"headerName\":\"([^\"]+)\"").matcher(csrf);
        assertThat(token.find()).isTrue(); assertThat(header.find()).isTrue();
        response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:18080/bff/logout"))
            .header(header.group(1), token.group(1)).POST(HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(get(client, "/bff/session").statusCode()).isEqualTo(401);
    }
    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(path.startsWith("http") ? path : "http://localhost:18080" + path))
            .GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
