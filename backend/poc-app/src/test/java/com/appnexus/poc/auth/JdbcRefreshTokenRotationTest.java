package com.appnexus.poc.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.mysql.MySQLContainer;

@Tag("container")
class JdbcRefreshTokenRotationTest {

    @Test
    void concurrentUseOfOneRefreshTokenAllowsAtMostOneRotationAndRevokesFamily() throws Exception {
        try (MySQLContainer mysql = new MySQLContainer("mysql:8.4")) {
            mysql.start();
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(mysql.getJdbcUrl());
            config.setUsername(mysql.getUsername());
            config.setPassword(mysql.getPassword());
            try (HikariDataSource dataSource = new HikariDataSource(config)) {
                Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
                JdbcRefreshTokenFamilyRepository repository = new JdbcRefreshTokenFamilyRepository(dataSource);
                UUID familyId = UUID.randomUUID();
                repository.save(new RefreshTokenFamily(
                        familyId, UUID.randomUUID(), RefreshTokenRotationService.hash("original"), 1,
                        RefreshTokenFamily.Status.ACTIVE, Instant.parse("2026-11-06T02:00:00Z")));
                RefreshTokenRotationService service = new RefreshTokenRotationService(
                        repository, Clock.fixed(Instant.parse("2026-11-06T01:00:00Z"), ZoneOffset.UTC));
                CountDownLatch start = new CountDownLatch(1);
                try (var executor = Executors.newFixedThreadPool(2)) {
                    var first = executor.submit(() -> rotate(service, familyId, "new-a", start));
                    var second = executor.submit(() -> rotate(service, familyId, "new-b", start));
                    start.countDown();
                    long successes = java.util.stream.Stream.of(first.get(), second.get()).filter(Boolean::booleanValue).count();
                    assertThat(successes).isOne();
                }
                assertThat(repository.findByFamilyId(familyId).orElseThrow().status())
                        .isEqualTo(RefreshTokenFamily.Status.REVOKED);
            }
        }
    }

    private static boolean rotate(
            RefreshTokenRotationService service, UUID familyId, String replacement, CountDownLatch start) {
        try {
            start.await();
            service.rotate(familyId, "original", replacement);
            return true;
        } catch (RefreshTokenRotationService.RefreshTokenReplayDetected exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
