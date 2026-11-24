package com.appnexus.bulkwarehousing.domain.inboundexecution;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** 按固定模板解释可信读数和抄码结果。设备身份、防重和分摊预占由事务用例核验。 */
public interface MeteringStrategy {
    /** 计量方式。 */
    enum Method { SCALE_DIFFERENCE, EXTERNAL_NET, THEORETICAL }

    /** 已验证的计量凭证，所有重量用千克，未采集值为空。 */
    record Evidence(UUID evidenceId, Method method, BigDecimal grossKg, BigDecimal tareKg,
                    BigDecimal externalNetKg, BigDecimal theoreticalKg, Integer packageCount) {}

    /** 发布版本固定的方式、双计量要求及最大绝对磅差比值，空阈值表示未配置。 */
    record Rule(Method method, boolean requireBothWeights, BigDecimal maxAbsoluteVarianceRatio) {}

    /** 正常实收定量，采用量为扣减前的计量基准。实测不存在时物理净重为空。 */
    record Result(Optional<BigDecimal> physicalNetKg, BigDecimal postingBasisKg,
                  Optional<Integer> packageCount, Optional<BigDecimal> varianceRatio) {}

    /**
     * @param evidence 同一作业的可信计量证据
     * @param rule 作业固定采用的计量规则
     * @param unloading 同一作业的监卸结论、凭证及二次扣量，计算物理重量时保留原读数口径
     * @return 正常正定量，退车返回空结果并由用例保留原始读数
     */
    Optional<Result> evaluate(Evidence evidence, Rule rule, UnloadingFact unloading);
}
