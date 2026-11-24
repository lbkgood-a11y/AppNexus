package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.bulkwarehousing.domain.inboundexecution.DeductionCalculator;
import com.appnexus.bulkwarehousing.domain.inboundexecution.LpnWeightAllocation;
import com.appnexus.bulkwarehousing.domain.inboundexecution.MeteringStrategy;
import com.appnexus.bulkwarehousing.domain.inboundexecution.QualityGateStrategy;
import com.appnexus.bulkwarehousing.domain.inboundexecution.UnloadingFact;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 从事务调用方已授权并锁定的事实生成过账计算结果。无写入和事件发布副作用。 */
public final class PrepareInboundPostingService {
    /** 当前作业采用的已发布策略实例。 */
    public record Policies(MeteringStrategy metering, QualityGateStrategy quality, DeductionCalculator deduction) {}

    /** 已授权且锁内读取的模板、监卸、质检、计量和适用 LPN 数据。 */
    public record Facts(MeteringStrategy.Evidence evidence, MeteringStrategy.Rule rule,
                        UnloadingFact unloading, Optional<QualityGateStrategy.QualityFact> activeQuality,
                        Map<String, BigDecimal> formulaInputs, DeductionCalculator.Formula formula,
                        List<LpnWeightAllocation.Line> lpnLines) {}

    /** 过账门槛与重量事实。阻断时两个重量结果均为空。 */
    public record Result(QualityGateStrategy.Decision gate, Optional<MeteringStrategy.Result> measurement,
                         Optional<DeductionCalculator.Result> deduction,
                         List<LpnWeightAllocation.Allocation> lpnWeights) {}

    /**
     * @param policies 已发布模板解析出的策略组合
     * @param facts 受控记录与适用 LPN 明细，散货传空集合
     * @return 阻断原因，或供同事务后续过账采用的确定性计算
     * @throws IllegalArgumentException 计量、扣量或明细结果违反过账约束
     */
    public Result prepare(Policies policies, Facts facts) {
        var gate = policies.quality().evaluate(QualityGateStrategy.Point.POST_RECEIPT,
                facts.activeQuality(), facts.unloading());
        if (gate.outcome() == QualityGateStrategy.Outcome.BLOCK) {
            return new Result(gate, Optional.empty(), Optional.empty(), List.of());
        }
        var measurement = policies.metering().evaluate(facts.evidence(), facts.rule(), facts.unloading())
                .orElseThrow(() -> new IllegalArgumentException("无有效实收计量"));
        var deduction = policies.deduction().calculate(measurement.postingBasisKg(), facts.formulaInputs(),
                facts.formula(), facts.unloading());
        var total = deduction.moistureKg().add(deduction.impurityKg()).add(deduction.onSiteKg());
        if (deduction.moistureKg().signum() < 0 || deduction.impurityKg().signum() < 0
                || deduction.onSiteKg().compareTo(facts.unloading().extraDeductionKg()) != 0
                || deduction.totalKg().compareTo(total) != 0 || deduction.accountingKg().signum() <= 0
                || measurement.postingBasisKg().subtract(total).compareTo(deduction.accountingKg()) != 0) {
            throw new IllegalArgumentException("扣量结果不满足入账守恒");
        }
        List<LpnWeightAllocation.Allocation> lpnWeights = List.of();
        if (!facts.lpnLines().isEmpty() && facts.rule().method() == MeteringStrategy.Method.SCALE_DIFFERENCE) {
            if (total.signum() != 0 || measurement.packageCount().orElse(0) != facts.lpnLines().size()) {
                throw new IllegalArgumentException("实重件货要求零扣量且件数与 LPN 明细一致");
            }
            lpnWeights = LpnWeightAllocation.allocate(measurement.physicalNetKg().orElseThrow(), facts.lpnLines());
            var lineTheoretical = facts.lpnLines().stream().map(LpnWeightAllocation.Line::theoreticalKg)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (facts.evidence().theoreticalKg() == null
                    || facts.evidence().theoreticalKg().compareTo(lineTheoretical) != 0) {
                throw new IllegalArgumentException("LPN 理重合计与计量凭证不一致");
            }
        }
        return new Result(gate, Optional.of(measurement), Optional.of(deduction), lpnWeights);
    }
}
