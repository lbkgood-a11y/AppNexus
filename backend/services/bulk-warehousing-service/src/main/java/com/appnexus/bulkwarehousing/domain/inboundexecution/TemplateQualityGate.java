package com.appnexus.bulkwarehousing.domain.inboundexecution;

import java.util.Objects;
import java.util.Optional;

/** 基于模板发布键构造的质量门槛，重复调用使用锁内事实。 */
public final class TemplateQualityGate implements QualityGateStrategy {
    /** 已发布模板所选门槛。 */
    public enum Mode { QUALIFIED_AND_UNLOADED, SAMPLED, DOCUMENT_AND_APPEARANCE, EXEMPT }

    private final Mode mode;

    /** @param mode 已解析且获准采用的模板门槛 */
    public TemplateQualityGate(Mode mode) {
        this.mode = Objects.requireNonNull(mode);
    }

    /** {@inheritDoc} */
    @Override
    public Decision evaluate(Point point, Optional<QualityFact> activeQuality, UnloadingFact unloading) {
        Objects.requireNonNull(point);
        if (unloading.disposition() == UnloadingFact.Disposition.RETURN) {
            return decision(Outcome.BLOCK, "RETURNED_CARGO", activeQuality);
        }
        if (activeQuality.filter(QualityFact::rejected).isPresent()) {
            return decision(Outcome.BLOCK, "QUALITY_REJECTED", activeQuality);
        }
        return switch (mode) {
            case QUALIFIED_AND_UNLOADED -> {
                boolean unloaded = unloading.disposition() == UnloadingFact.Disposition.ACCEPTED
                        || unloading.disposition() == UnloadingFact.Disposition.ACCEPTED_WITH_DEDUCTION;
                yield unloaded && activeQuality.filter(QualityFact::passed).isPresent()
                        ? decision(Outcome.ACCEPT, "QUALIFIED", activeQuality)
                        : decision(Outcome.BLOCK, "QUALITY_OR_UNLOAD_REQUIRED", activeQuality);
            }
            case SAMPLED -> {
                if (activeQuality.filter(QualityFact::sampled).isEmpty()) {
                    yield decision(Outcome.BLOCK, "SAMPLE_REQUIRED", activeQuality);
                }
                yield activeQuality.filter(QualityFact::passed).isPresent()
                        ? decision(Outcome.ACCEPT, "QUALIFIED", activeQuality)
                        : decision(Outcome.ACCEPT_PENDING_LAB, "PENDING_LAB", activeQuality);
            }
            case DOCUMENT_AND_APPEARANCE -> activeQuality.filter(fact ->
                    fact.appearanceVerified() && fact.mtcVerified() && fact.passed()).isPresent()
                    ? decision(Outcome.ACCEPT, "DOCUMENT_AND_APPEARANCE_VERIFIED", activeQuality)
                    : decision(Outcome.BLOCK, "DOCUMENT_OR_APPEARANCE_REQUIRED", activeQuality);
            case EXEMPT -> decision(Outcome.ACCEPT, "QUALITY_EXEMPT", Optional.empty());
        };
    }

    private static Decision decision(Outcome outcome, String code, Optional<QualityFact> quality) {
        return new Decision(outcome, code, quality.map(QualityFact::id));
    }
}
