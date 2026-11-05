package com.appnexus.poc.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FieldPolicyMergerTest {

    private final FieldPolicyMerger merger = new FieldPolicyMerger();

    @Test
    void ordinaryGrantsAccumulateExplicitCapabilitiesOnly() {
        FieldDecision result = merger.merge(
                List.of(
                        new FieldDecision(DisclosureLevel.MASKED, Set.of(FieldCapability.EXACT_QUERY)),
                        new FieldDecision(DisclosureLevel.ORIGINAL, Set.of(FieldCapability.EDIT))),
                List.of());

        assertThat(result.disclosure()).isEqualTo(DisclosureLevel.ORIGINAL);
        assertThat(result.capabilities()).containsExactlyInAnyOrder(FieldCapability.EXACT_QUERY, FieldCapability.EDIT);
        assertThat(result.capabilities()).doesNotContain(FieldCapability.EXPORT, FieldCapability.SORT);
    }

    @Test
    void mandatoryRestrictionCanOnlyReduceDisclosureAndCapabilities() {
        FieldDecision result = merger.merge(
                List.of(new FieldDecision(
                        DisclosureLevel.ORIGINAL,
                        Set.of(FieldCapability.EDIT, FieldCapability.EXPORT, FieldCapability.SORT))),
                List.of(new FieldDecision(DisclosureLevel.MASKED, Set.of(FieldCapability.SORT))));

        assertThat(result.disclosure()).isEqualTo(DisclosureLevel.MASKED);
        assertThat(result.capabilities()).containsExactly(FieldCapability.SORT);
    }

    @Test
    void editWithoutOriginalUsesReplacementSemanticsAndHiddenFieldsLeaveProjection() {
        FieldDecision editableMasked = new FieldDecision(DisclosureLevel.MASKED, Set.of(FieldCapability.EDIT));

        assertThat(merger.mayReplaceWithoutOriginal(editableMasked)).isTrue();
        assertThat(merger.projection(
                        Set.of("id"),
                        Map.of("phone", editableMasked, "identityNumber", FieldDecision.hidden())))
                .containsExactlyInAnyOrder("id", "phone");
        assertThat(new FieldPatch.Unchanged<String>()).isNotEqualTo(new FieldPatch.Clear<String>());
        assertThat(new FieldPatch.Replace<>("new-value").value()).isEqualTo("new-value");
    }
}
