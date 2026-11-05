package com.appnexus.poc.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class PermissionPlanResolverTest {

    private final PermissionPlanResolver<String> resolver = new PermissionPlanResolver<>();

    @Test
    void fallsBackToAuthoritativeDatabaseWhenCacheIsUnavailable() {
        assertThat(resolver.resolve(
                        () -> { throw new IllegalStateException("valkey unavailable"); },
                        () -> "database-plan"))
                .isEqualTo("database-plan");
    }

    @Test
    void deniesWhenNeitherCacheNorDatabaseCanEstablishAuthority() {
        assertThatThrownBy(() -> resolver.resolve(
                        Optional::empty,
                        () -> { throw new IllegalStateException("database unavailable"); }))
                .isInstanceOf(PermissionPlanResolver.AccessDecisionUnavailable.class);
    }
}
