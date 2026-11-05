package com.appnexus.poc.outbox;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

public final class OutboxLeaseRepository {

    private final DataSource dataSource;

    public OutboxLeaseRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<UUID> claim(String workerId, int batchSize, Duration leaseDuration) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                List<UUID> ids = selectAvailableForUpdate(connection, batchSize);
                lease(connection, ids, workerId, leaseDuration);
                connection.commit();
                return ids;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static List<UUID> selectAvailableForUpdate(Connection connection, int batchSize) throws SQLException {
        String sql = """
                SELECT event_id
                FROM outbox_event
                WHERE status = 'PENDING'
                  AND next_attempt_at <= CURRENT_TIMESTAMP(6)
                  AND (leased_until IS NULL OR leased_until < CURRENT_TIMESTAMP(6))
                ORDER BY occurred_at, event_id
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, batchSize);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<UUID> ids = new ArrayList<>();
                while (resultSet.next()) {
                    ids.add(UUID.fromString(resultSet.getString(1)));
                }
                return ids;
            }
        }
    }

    private static void lease(Connection connection, List<UUID> ids, String workerId, Duration duration)
            throws SQLException {
        String sql = """
                UPDATE outbox_event
                SET worker_id = ?,
                    leased_until = TIMESTAMPADD(MICROSECOND, ?, CURRENT_TIMESTAMP(6)),
                    attempt_count = attempt_count + 1
                WHERE event_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (UUID id : ids) {
                statement.setString(1, workerId);
                statement.setLong(2, duration.toNanos() / 1_000);
                statement.setString(3, id.toString());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
}
