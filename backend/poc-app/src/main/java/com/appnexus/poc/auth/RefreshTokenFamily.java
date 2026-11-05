package com.appnexus.poc.auth;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RefreshTokenFamily(
        UUID familyId,
        UUID sessionId,
        String currentTokenHash,
        long generation,
        Status status,
        Instant expiresAt) {

    public RefreshTokenFamily {
        Objects.requireNonNull(familyId);
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(currentTokenHash);
        Objects.requireNonNull(status);
        Objects.requireNonNull(expiresAt);
    }

    public enum Status { ACTIVE, REVOKED }
}
