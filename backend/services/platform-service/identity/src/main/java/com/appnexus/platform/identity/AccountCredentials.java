package com.appnexus.platform.identity;
import java.util.Objects;
import java.util.UUID;
/** Internal authentication projection; never return the password hash through HTTP. */
public record AccountCredentials(UUID id, String username, String displayName,
        String passwordHash, boolean enabled, long version) {
    public AccountCredentials {
        Objects.requireNonNull(id);
        Objects.requireNonNull(username);
        Objects.requireNonNull(passwordHash);
        if (username.isBlank() || username.length() > 100) throw new IllegalArgumentException("Invalid username");
        if (passwordHash.isBlank()) throw new IllegalArgumentException("Missing password hash");
    }
}
