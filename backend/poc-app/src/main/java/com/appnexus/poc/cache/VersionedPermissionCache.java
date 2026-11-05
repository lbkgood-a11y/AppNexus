package com.appnexus.poc.cache;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class VersionedPermissionCache<T> {

    private final Map<Key, T> values = new ConcurrentHashMap<>();

    public Optional<T> get(Key key, PermissionVersion authoritativeVersion) {
        if (!key.version().equals(authoritativeVersion)) {
            return Optional.empty();
        }
        return Optional.ofNullable(values.get(key));
    }

    public T getOrLoad(
            Key key,
            Supplier<PermissionVersion> authoritativeVersion,
            Supplier<T> databaseLoader) {
        PermissionVersion version = authoritativeVersion.get();
        if (!version.equals(key.version())) {
            throw new PermissionVersionChangedException();
        }
        return values.computeIfAbsent(key, ignored -> databaseLoader.get());
    }

    public void put(Key key, T value) {
        values.put(key, value);
    }

    public void invalidateSubject(String tenantId, String subjectType, String subjectId) {
        values.keySet().removeIf(key -> key.tenantId().equals(tenantId)
                && key.subjectType().equals(subjectType)
                && key.subjectId().equals(subjectId));
    }

    public record Key(
            String tenantId,
            String subjectType,
            String subjectId,
            String resourceId,
            String actionId,
            String contextFingerprint,
            PermissionVersion version) {}

    public static final class PermissionVersionChangedException extends RuntimeException {}
}
