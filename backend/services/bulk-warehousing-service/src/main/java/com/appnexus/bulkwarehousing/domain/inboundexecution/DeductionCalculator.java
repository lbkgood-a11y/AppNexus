package com.appnexus.bulkwarehousing.domain.inboundexecution;

import java.math.BigDecimal;
import java.util.Map;

/** 计算影响库存账的扣重，使用已发布规则，不处理 ERP 商务价格。 */
public interface DeductionCalculator {
    /** 公式版本及公斤舍入约定由固定模板解析。 */
    record Formula(String version, java.math.RoundingMode roundingMode) {}

    /** 库存重量分项，均为千克并保留四位小数。 */
    record Result(BigDecimal moistureKg, BigDecimal impurityKg, BigDecimal onSiteKg,
                  BigDecimal totalKg, BigDecimal accountingKg) {}

    /**
     * @param basisKg 计量选定的正基准重量，千克
     * @param inputs 校验后的公式变量，变量单位和范围由发布版本定义
     * @param formula 作业固定采用的公式版本及舍入约定
     * @param unloading 现场确认、凭证及二次扣量，现场扣量单列且仅计入一次
     * @return 分项和账面重量，扣量总和不得大于基准量
     */
    Result calculate(BigDecimal basisKg, Map<String, BigDecimal> inputs, Formula formula, UnloadingFact unloading);
}
