package com.appnexus.bulkwarehousing.domain.inboundexecution;

import java.math.BigDecimal;
import java.util.Map;

/** 适用于发布模板明确禁用扣量的作业，拒绝带入非零现场扣量。 */
public final class NoDeductionCalculator implements DeductionCalculator {
    /** {@inheritDoc} */
    @Override
    public Result calculate(BigDecimal basisKg, Map<String, BigDecimal> inputs, Formula formula,
                            UnloadingFact unloading) {
        if (basisKg == null || basisKg.signum() <= 0 || unloading.extraDeductionKg().signum() != 0
                || unloading.disposition() == UnloadingFact.Disposition.RETURN) {
            throw new IllegalArgumentException("零扣量策略要求正实收定量、无现场扣量且未退车");
        }
        var zero = BigDecimal.ZERO.setScale(4);
        return new Result(zero, zero, zero, zero, basisKg.setScale(4));
    }
}
