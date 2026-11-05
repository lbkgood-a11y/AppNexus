package com.appnexus.poc.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

public final class RefreshTokenRotationService {

    private final RefreshTokenFamilyRepository repository;
    private final Clock clock;

    public RefreshTokenRotationService(RefreshTokenFamilyRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public RotationResult rotate(UUID familyId, String presentedToken, String newToken) {
        RefreshTokenFamily family = repository.findByFamilyId(familyId).orElseThrow(InvalidRefreshToken::new);
        if (family.status() != RefreshTokenFamily.Status.ACTIVE || family.expiresAt().isBefore(Instant.now(clock))) {
            throw new InvalidRefreshToken();
        }
        if (!constantTimeEquals(family.currentTokenHash(), hash(presentedToken))) {
            repository.revoke(family.familyId());
            throw new RefreshTokenReplayDetected(family.sessionId());
        }
        RefreshTokenFamily rotated = new RefreshTokenFamily(
                family.familyId(), family.sessionId(), hash(newToken), family.generation() + 1,
                RefreshTokenFamily.Status.ACTIVE, family.expiresAt());
        if (!repository.rotateIfCurrent(familyId, family.currentTokenHash(), rotated)) {
            repository.revoke(family.familyId());
            throw new RefreshTokenReplayDetected(family.sessionId());
        }
        return new RotationResult(newToken, rotated.generation());
    }

    public static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.US_ASCII), right.getBytes(StandardCharsets.US_ASCII));
    }

    public record RotationResult(String refreshToken, long generation) {}
    public static class InvalidRefreshToken extends RuntimeException {}
    public static final class RefreshTokenReplayDetected extends RuntimeException {
        private final UUID sessionId;
        RefreshTokenReplayDetected(UUID sessionId) { this.sessionId = sessionId; }
        public UUID sessionId() { return sessionId; }
    }
}
