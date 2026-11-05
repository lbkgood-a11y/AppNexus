package com.appnexus.poc.access;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class FieldPolicyMerger {

    public FieldDecision merge(List<FieldDecision> matchedGrants, List<FieldDecision> mandatoryRestrictions) {
        if (matchedGrants.isEmpty()) {
            return FieldDecision.hidden();
        }

        DisclosureLevel disclosure = DisclosureLevel.HIDDEN;
        EnumSet<FieldCapability> capabilities = EnumSet.noneOf(FieldCapability.class);
        for (FieldDecision grant : matchedGrants) {
            disclosure = DisclosureLevel.mostRevealing(disclosure, grant.disclosure());
            capabilities.addAll(grant.capabilities());
        }

        for (FieldDecision restriction : mandatoryRestrictions) {
            disclosure = DisclosureLevel.leastRevealing(disclosure, restriction.disclosure());
            capabilities.retainAll(restriction.capabilities());
        }
        return new FieldDecision(disclosure, capabilities);
    }

    public boolean mayReplaceWithoutOriginal(FieldDecision decision) {
        return decision.capabilities().contains(FieldCapability.EDIT)
                && decision.disclosure() != DisclosureLevel.ORIGINAL;
    }

    public Set<String> projection(Set<String> requiredPublicFields, java.util.Map<String, FieldDecision> decisions) {
        java.util.Set<String> fields = new java.util.LinkedHashSet<>(requiredPublicFields);
        decisions.forEach((field, decision) -> {
            if (decision.disclosure() != DisclosureLevel.HIDDEN) {
                fields.add(field);
            }
        });
        return Set.copyOf(fields);
    }
}
