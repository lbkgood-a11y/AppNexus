package com.appnexus.poc.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.mysql.MySQLContainer;

@Tag("container")
class OutboxLeaseRepositoryTest {

    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    private static HikariDataSource dataSource;

    @BeforeAll
    static void startDatabase() throws SQLException {
        MYSQL.start();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(MYSQL.getJdbcUrl());
        config.setUsername(MYSQL.getUsername());
        config.setPassword(MYSQL.getPassword());
        config.setMaximumPoolSize(4);
        dataSource = new HikariDataSource(config);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    }

    @AfterAll
    static void stopDatabase() {
        if (dataSource != null) {
            dataSource.close();
        }
        MYSQL.stop();
    }

    @BeforeEach
    void clearTables() throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM event_consumption");
            statement.executeUpdate("DELETE FROM outbox_event");
        }
    }

    @Test
    void separateWorkersClaimDisjointBatches() throws SQLException {
        insertEvents(3);
        OutboxLeaseRepository repository = new OutboxLeaseRepository(dataSource);

        List<UUID> first = repository.claim("worker-a", 2, Duration.ofSeconds(30));
        List<UUID> second = repository.claim("worker-b", 2, Duration.ofSeconds(30));

        assertThat(first).hasSize(2);
        assertThat(second).hasSize(1);
        assertThat(first).doesNotContainAnyElementsOf(second);
    }

    @Test
    void concurrentWorkersClaimDisjointEvents() throws Exception {
        insertEvents(8);
        OutboxLeaseRepository repository = new OutboxLeaseRepository(dataSource);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstFuture = executor.submit(() -> claimWithEmptyPollRetry(repository, "concurrent-a", 4));
            var secondFuture = executor.submit(() -> claimWithEmptyPollRetry(repository, "concurrent-b", 4));
            List<UUID> first = firstFuture.get(10, TimeUnit.SECONDS);
            List<UUID> second = secondFuture.get(10, TimeUnit.SECONDS);

            assertThat(first).hasSize(4);
            assertThat(second).hasSize(4);
            assertThat(first).doesNotContainAnyElementsOf(second);
        }
    }

    @Test
    void expiredLeaseCanBeRecoveredByAnotherWorker() throws Exception {
        insertEvents(1);
        OutboxLeaseRepository repository = new OutboxLeaseRepository(dataSource);
        List<UUID> first = repository.claim("crashed-worker", 1, Duration.ofMillis(100));
        Thread.sleep(150);

        List<UUID> recovered = repository.claim("recovery-worker", 1, Duration.ofSeconds(30));

        assertThat(recovered).containsExactlyElementsOf(first);
    }

    @Test
    void rolledBackBusinessTransactionDoesNotPublishOutboxEvent() throws SQLException {
        UUID eventId = UUID.randomUUID();
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("""
                    INSERT INTO outbox_event(
                        event_id, scope, tenant_id, event_type, contract_version,
                        aggregate_type, aggregate_id, aggregate_version, occurred_at, payload)
                    VALUES (?, 'TENANT', ?, 'RolledBack', 1, 'Test', ?, 1, CURRENT_TIMESTAMP(6), JSON_OBJECT())
                    """)) {
                statement.setString(1, eventId.toString());
                statement.setString(2, UUID.randomUUID().toString());
                statement.setString(3, UUID.randomUUID().toString());
                statement.executeUpdate();
                connection.rollback();
            }
        }
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("SELECT COUNT(*) FROM outbox_event WHERE event_id = ?")) {
            statement.setString(1, eventId.toString());
            try (var rows = statement.executeQuery()) {
                rows.next();
                assertThat(rows.getInt(1)).isZero();
            }
        }
    }

    @Test
    void consumerIdempotencyIsEnforcedByEventAndHandler() throws SQLException {
        UUID eventId = UUID.randomUUID();
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(
                        "INSERT INTO event_consumption(event_id, handler_id) VALUES (?, ?)")) {
            statement.setString(1, eventId.toString());
            statement.setString(2, "permission-cache-invalidator");
            statement.executeUpdate();
            assertThatThrownBy(statement::executeUpdate).isInstanceOf(SQLException.class);
        }
    }

    private static void insertEvents(int count) throws SQLException {
        String sql = """
                INSERT INTO outbox_event(
                    event_id, scope, tenant_id, event_type, contract_version,
                    aggregate_type, aggregate_id, aggregate_version, occurred_at, payload)
                VALUES (?, 'TENANT', ?, 'PermissionChanged', 1, 'Permission', ?, 1, CURRENT_TIMESTAMP(6), JSON_OBJECT())
                """;
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < count; index++) {
                statement.setString(1, UUID.randomUUID().toString());
                statement.setString(2, UUID.randomUUID().toString());
                statement.setString(3, UUID.randomUUID().toString());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static List<UUID> claimWithEmptyPollRetry(
            OutboxLeaseRepository repository, String workerId, int batchSize) throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            List<UUID> claimed = repository.claim(workerId, batchSize, Duration.ofSeconds(30));
            if (!claimed.isEmpty()) {
                return claimed;
            }
            Thread.sleep(50L * (attempt + 1));
        }
        return List.of();
    }
}
