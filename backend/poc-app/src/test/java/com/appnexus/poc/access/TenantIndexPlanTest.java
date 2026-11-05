package com.appnexus.poc.access;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.mysql.MySQLContainer;

@Tag("container")
class TenantIndexPlanTest {

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
        seed(20_000);
    }

    @AfterAll
    static void stop() {
        if (dataSource != null) dataSource.close();
        MYSQL.stop();
    }

    @Test
    void tenantLeadingCompositeIndexIsSelected() throws SQLException {
        String sql = "EXPLAIN SELECT company_id, COUNT(*) FROM member_fact "
                + "WHERE tenant_id = ? AND company_id = ? AND status = 'ACTIVE' GROUP BY company_id";
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, "019a56e8-4ae4-7000-8000-00000000000a");
            statement.setString(2, "019a56e8-4ae4-7000-8000-000000000101");
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("key")).isEqualTo("idx_member_fact_scope");
                assertThat(rows.getLong("rows")).isLessThan(20_000L);
            }
        }
    }

    private static void seed(int count) throws SQLException {
        String sql = "INSERT INTO member_fact(id, tenant_id, company_id, status) VALUES (?, ?, ?, 'ACTIVE')";
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < count; index++) {
                statement.setString(1, UUID.randomUUID().toString());
                statement.setString(2, index % 2 == 0
                        ? "019a56e8-4ae4-7000-8000-00000000000a"
                        : "019a56e8-4ae4-7000-8000-00000000000b");
                statement.setString(3, index % 4 < 2
                        ? "019a56e8-4ae4-7000-8000-000000000101"
                        : "019a56e8-4ae4-7000-8000-000000000102");
                statement.addBatch();
                if (index % 1000 == 999) statement.executeBatch();
            }
            statement.executeBatch();
        }
    }
}
