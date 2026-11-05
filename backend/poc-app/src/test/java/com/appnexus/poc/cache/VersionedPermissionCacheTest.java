package com.appnexus.poc.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class VersionedPermissionCacheTest {

    private final VersionedPermissionCache<String> cache = new VersionedPermissionCache<>();
    private final PermissionVersion versionOne = new PermissionVersion(1, 1, 1, 1, 1);
    private final PermissionVersion versionTwo = new PermissionVersion(1, 2, 1, 1, 1);

    @Test
    void staleEntryCannotBeUsedEvenBeforeInvalidationNotificationArrives() {
        var key = key(versionOne);
        cache.put(key, "allow");

        assertThat(cache.get(key, versionTwo)).isEmpty();
    }

    @Test
    void databaseFallbackLoadsOnceForTheSameVersionedKey() {
        var key = key(versionOne);
        AtomicInteger loads = new AtomicInteger();

        assertThat(cache.getOrLoad(key, () -> versionOne, () -> "plan-" + loads.incrementAndGet()))
                .isEqualTo("plan-1");
        assertThat(cache.getOrLoad(key, () -> versionOne, () -> "plan-" + loads.incrementAndGet()))
                .isEqualTo("plan-1");
        assertThat(loads).hasValue(1);
    }

    @Test
    void securityTighteningAbortsLongOperationWhenVersionChanges() {
        PermissionSnapshot snapshot = new PermissionSnapshot("member-1", versionOne);

        assertThatThrownBy(() -> cache.getOrLoad(key(snapshot.version()), () -> versionTwo, () -> "never"))
                .isInstanceOf(VersionedPermissionCache.PermissionVersionChangedException.class);
    }

    private static VersionedPermissionCache.Key key(PermissionVersion version) {
        return new VersionedPermissionCache.Key(
                "tenant-a", "MEMBER", "member-1", "org.unit", "view", "company-a", version);
    }
}
