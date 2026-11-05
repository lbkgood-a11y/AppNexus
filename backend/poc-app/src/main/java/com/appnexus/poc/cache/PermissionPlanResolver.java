package com.appnexus.poc.cache;

import java.util.Optional;
import java.util.function.Supplier;

public final class PermissionPlanResolver<T> {

    public T resolve(Supplier<Optional<T>> cacheLookup, Supplier<T> authoritativeDatabaseLookup) {
        try {
            Optional<T> cached = cacheLookup.get();
            if (cached.isPresent()) return cached.get();
        } catch (RuntimeException cacheFailure) {
            // The authoritative lookup below is the controlled fallback.
        }
        try {
            T plan = authoritativeDatabaseLookup.get();
            if (plan == null) throw new AccessDecisionUnavailable();
            return plan;
        } catch (RuntimeException databaseFailure) {
            throw new AccessDecisionUnavailable(databaseFailure);
        }
    }

    public static final class AccessDecisionUnavailable extends RuntimeException {
        AccessDecisionUnavailable() {}
        AccessDecisionUnavailable(Throwable cause) { super(cause); }
    }
}
