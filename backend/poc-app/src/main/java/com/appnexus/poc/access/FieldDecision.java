package com.appnexus.poc.access;

import java.util.EnumSet;
import java.util.Set;

public record FieldDecision(DisclosureLevel disclosure, Set<FieldCapability> capabilities) {

    public FieldDecision {
        capabilities = capabilities.isEmpty()
                ? Set.of()
                : Set.copyOf(EnumSet.copyOf(capabilities));
    }

    public static FieldDecision hidden() {
        return new FieldDecision(DisclosureLevel.HIDDEN, Set.of());
    }
}
