package com.appnexus.bulkwarehousing.domain.inboundexecution;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/** 对已核验的差重、外部净重和理计证据计算计量事实。 */
public final class StandardMeteringStrategy implements MeteringStrategy {
    /** {@inheritDoc} */
    @Override
    public Optional<Result> evaluate(Evidence evidence, Rule rule, UnloadingFact unloading) {
        if (evidence.method() != rule.method()) {
            throw new IllegalArgumentException("计量方法与模板不符");
        }
        if (unloading.disposition() == UnloadingFact.Disposition.RETURN) {
            return Optional.empty();
        }
        BigDecimal actual = switch (rule.method()) {
            case SCALE_DIFFERENCE -> positiveWeight(evidence.grossKg()).subtract(positiveWeight(evidence.tareKg()));
            case EXTERNAL_NET -> positiveWeight(evidence.externalNetKg());
            case THEORETICAL -> evidence.grossKg() != null || evidence.tareKg() != null
                    ? positiveWeight(evidence.grossKg()).subtract(positiveWeight(evidence.tareKg()))
                    : evidence.externalNetKg();
        };
        if (actual != null) {
            actual = positiveWeight(actual);
        }
        BigDecimal theoretical = evidence.theoreticalKg() == null ? null : positiveWeight(evidence.theoreticalKg());
        if (rule.requireBothWeights() && (actual == null || theoretical == null)) {
            throw new IllegalArgumentException("缺少实重或理重");
        }
        BigDecimal variance = null;
        if (actual != null && theoretical != null) {
            var difference = actual.subtract(theoretical);
            var limit = rule.maxAbsoluteVarianceRatio();
            if (limit != null && difference.abs().compareTo(theoretical.multiply(limit)) > 0) {
                throw new IllegalArgumentException("超出模板允许磅差");
            }
            variance = difference.divide(theoretical, 12, RoundingMode.HALF_UP);
        }
        var basis = rule.method() == Method.THEORETICAL ? positiveWeight(theoretical) : positiveWeight(actual);
        if (evidence.packageCount() != null && evidence.packageCount() <= 0) {
            throw new IllegalArgumentException("件或捆数必须为正");
        }
        return Optional.of(new Result(Optional.ofNullable(actual), basis,
                Optional.ofNullable(evidence.packageCount()), Optional.ofNullable(variance)));
    }

    private static BigDecimal positiveWeight(BigDecimal weight) {
        if (weight == null || weight.signum() <= 0 || weight.stripTrailingZeros().scale() > 4
                || weight.compareTo(new BigDecimal("99999999999999.9999")) > 0) {
            throw new IllegalArgumentException("重量超出入库计量范围");
        }
        return weight.setScale(4);
    }
}
