package com.appnexus.poc.access;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.mysql.MySQLContainer;

@Tag("container")
class SecureAggregationTest {

    private static final UUID TENANT_A = UUID.fromString("019a56e8-4ae4-7000-8000-00000000000a");
    private static final UUID TENANT_B = UUID.fromString("019a56e8-4ae4-7000-8000-00000000000b");
    private static final UUID COMPANY_VISIBLE = UUID.fromString("019a56e8-4ae4-7000-8000-000000000101");
    private static final UUID COMPANY_HIDDEN = UUID.fromString("019a56e8-4ae4-7000-8000-000000000102");
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    private static HikariDataSource dataSource;

    @BeforeAll
    static void setup() throws SQLException {
        MYSQL.start();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(MYSQL.getJdbcUrl());
        config.setUsername(MYSQL.getUsername());
        config.setPassword(MYSQL.getPassword());
        dataSource = new HikariDataSource(config);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        insert(TENANT_A, COMPANY_VISIBLE, 3);
        insert(TENANT_A, COMPANY_HIDDEN, 4);
        insert(TENANT_B, COMPANY_VISIBLE, 5);
    }

    @AfterAll
    static void stop() {
        if (dataSource != null) dataSource.close();
        MYSQL.stop();
    }

    @Test
    void filtersTenantAndDataScopeBeforeAggregationAndSuppressesSmallGroups() throws SQLException {
        String sql = """
                SELECT company_id, COUNT(*) AS member_count
                FROM member_fact
                WHERE tenant_id = ? AND company_id = ? AND status = 'ACTIVE'
                GROUP BY company_id
                HAVING COUNT(*) >= ?
                """;
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, TENANT_A.toString());
            statement.setString(2, COMPANY_VISIBLE.toString());
            statement.setInt(3, 3);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("member_count")).isEqualTo(3);
                assertThat(rows.next()).isFalse();
            }
            statement.setInt(3, 4);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isFalse();
            }
        }
    }

    private static void insert(UUID tenantId, UUID companyId, int count) throws SQLException {
        String sql = "INSERT INTO member_fact(id, tenant_id, company_id, status) VALUES (?, ?, ?, 'ACTIVE')";
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            List<UUID> ids = new ArrayList<>();
            for (int index = 0; index < count; index++) ids.add(UUID.randomUUID());
            for (UUID id : ids) {
                statement.setString(1, id.toString());
                statement.setString(2, tenantId.toString());
                statement.setString(3, companyId.toString());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
}
