package com.appnexus.poc.access;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AccessPlan(UUID tenantId, List<AccessPredicate> grants, List<AccessPredicate> mandatoryDenies) {

    public AccessPlan {
        Objects.requireNonNull(tenantId, "tenantId");
        grants = List.copyOf(grants);
        mandatoryDenies = List.copyOf(mandatoryDenies);
        if (grants.isEmpty()) {
            throw new IllegalArgumentException("At least one complete grant is required");
        }
    }
}
