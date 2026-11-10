package com.appnexus.platform;

import com.appnexus.platform.security.SessionLifetime;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import static org.assertj.core.api.Assertions.*;

/** 验证绝对期限边界及旧会话兼容，不以活动时间或可变上下文延长认证寿命。 */
class SessionLifetimeTest {
    private final SessionLifetime lifetime = new SessionLifetime(Duration.ofHours(8));

    @Test void exactDeadlineRejectsDespiteContinuedActivity() {
        var session = new MockHttpSession();
        var start = Instant.parse("2026-11-10T00:00:00Z");
        session.setAttribute(SessionLifetime.AUTHENTICATED_AT, start);
        assertThat(lifetime.expired(session, start.plusSeconds(28799))).isFalse();
        assertThat(lifetime.expired(session, start.plusSeconds(28800))).isTrue();
        session.setAttribute("appnexus.credentialVersion", 100L);
        assertThat(lifetime.expired(session, start.plusSeconds(28801))).isTrue();
    }

    @Test void legacySessionUsesOriginalCreationTime() {
        var session = new MockHttpSession();
        var created = Instant.ofEpochMilli(session.getCreationTime());
        assertThat(lifetime.expired(session, created.plusSeconds(1))).isFalse();
        assertThat(lifetime.expired(session, created.plus(Duration.ofHours(8)))).isTrue();
    }

    @Test void invalidEvidenceAndUnsafeConfigurationFailClosed() {
        var session = new MockHttpSession();
        var now = Instant.now();
        session.setAttribute(SessionLifetime.AUTHENTICATED_AT, now.plusSeconds(1));
        assertThat(lifetime.expired(session, now)).isTrue();
        session.setAttribute(SessionLifetime.AUTHENTICATED_AT, "invalid");
        assertThat(lifetime.expired(session, now)).isTrue();
        assertThat(lifetime.expired((jakarta.servlet.http.HttpSession)null, now)).isTrue();
        for (var duration : new Duration[]{Duration.ZERO, Duration.ofSeconds(-1), Duration.ofHours(25)})
            assertThatIllegalArgumentException().isThrownBy(() -> new SessionLifetime(duration));
    }
}
