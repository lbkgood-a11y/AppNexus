package com.appnexus.poc.auth;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcRefreshTokenFamilyRepository implements RefreshTokenFamilyRepository {

    private final DataSource dataSource;

    public JdbcRefreshTokenFamilyRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<RefreshTokenFamily> findByFamilyId(UUID familyId) {
        String sql = "SELECT session_id, token_hash, generation, status, expires_at FROM refresh_token_family WHERE family_id = ?";
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, familyId.toString());
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                return Optional.of(new RefreshTokenFamily(
                        familyId,
                        UUID.fromString(rows.getString("session_id")),
                        rows.getString("token_hash"),
                        rows.getLong("generation"),
                        RefreshTokenFamily.Status.valueOf(rows.getString("status")),
                        rows.getTimestamp("expires_at").toInstant()));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot load refresh token family", exception);
        }
    }

    @Override
    public void save(RefreshTokenFamily family) {
        String sql = """
                INSERT INTO refresh_token_family(family_id, session_id, token_hash, generation, status, expires_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, family.familyId().toString());
            statement.setString(2, family.sessionId().toString());
            statement.setString(3, family.currentTokenHash());
            statement.setLong(4, family.generation());
            statement.setString(5, family.status().name());
            statement.setTimestamp(6, java.sql.Timestamp.from(family.expiresAt()));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot save refresh token family", exception);
        }
    }

    @Override
    public boolean rotateIfCurrent(UUID familyId, String expectedHash, RefreshTokenFamily replacement) {
        String sql = """
                UPDATE refresh_token_family
                SET token_hash = ?, generation = ?, status = ?
                WHERE family_id = ? AND token_hash = ? AND status = 'ACTIVE'
                """;
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, replacement.currentTokenHash());
            statement.setLong(2, replacement.generation());
            statement.setString(3, replacement.status().name());
            statement.setString(4, familyId.toString());
            statement.setString(5, expectedHash);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot rotate refresh token family", exception);
        }
    }

    @Override
    public void revoke(UUID familyId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(
                        "UPDATE refresh_token_family SET status = 'REVOKED' WHERE family_id = ?")) {
            statement.setString(1, familyId.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot revoke refresh token family", exception);
        }
    }
}
