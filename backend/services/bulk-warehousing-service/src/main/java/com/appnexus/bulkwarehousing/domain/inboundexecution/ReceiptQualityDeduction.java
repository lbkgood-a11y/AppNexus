package com.appnexus.bulkwarehousing.domain.inboundexecution;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;

/** 按版本控制的线性扣水、扣杂公式。只产生物理库存扣量。 */
public final class ReceiptQualityDeduction {
    private ReceiptQualityDeduction() {}

    /** 指标用百分数，系数表示每超一个百分点对应的扣量百分点。 */
    public record Rule(String metric, BigDecimal baseline, BigDecimal multiplier) {}
    /** 发布版本固定两条规则，舍入统一为四位 HALF_UP。 */
    public record Formula(String version, Rule moisture, Rule impurity) {}

    /**
     * @param netKg 地磅物理净重，千克
     * @param metrics 现行合格质检的百分数快照
     * @param formula 已发布公式，不允许缺省参数
     * @param onSiteKg 监卸确认的异常扣重，千克
     * @return 各项扣量和库存账面重量
     */
    public static DeductionCalculator.Result calculate(BigDecimal netKg, Map<String, BigDecimal> metrics,
                                                        Formula formula, BigDecimal onSiteKg) {
        if (formula == null || formula.version() == null || formula.version().isBlank()
                || formula.moisture() == null || formula.impurity() == null
                || Objects.equals(formula.moisture().metric(), formula.impurity().metric())
                || netKg.signum() <= 0 || onSiteKg.signum() < 0) {
            throw new IllegalArgumentException("无效的扣量配置或计量事实");
        }
        var water = component(netKg, metrics, formula.moisture());
        var impurity = component(netKg, metrics, formula.impurity());
        var site = onSiteKg.setScale(4, RoundingMode.UNNECESSARY);
        var total = water.add(impurity).add(site);
        var accounting = netKg.subtract(total);
        if (accounting.signum() <= 0) throw new IllegalArgumentException("扣量须小于物理净重");
        return new DeductionCalculator.Result(water, impurity, site, total, accounting);
    }

    private static BigDecimal component(BigDecimal net, Map<String, BigDecimal> metrics, Rule rule) {
        if (rule == null || rule.metric() == null || rule.metric().isBlank()
                || !percentage(rule.baseline()) || rule.multiplier() == null || rule.multiplier().signum() < 0) {
            throw new IllegalArgumentException("公式条件未完整配置");
        }
        var measured = metrics.get(rule.metric());
        if (!percentage(measured)) throw new IllegalArgumentException("质检指标缺失或超出百分数范围");
        return net.multiply(measured.subtract(rule.baseline()).max(BigDecimal.ZERO))
                .multiply(rule.multiplier()).movePointLeft(2).setScale(4, RoundingMode.HALF_UP);
    }

    private static boolean percentage(BigDecimal value) {
        return value != null && value.signum() >= 0 && value.compareTo(BigDecimal.valueOf(100)) <= 0;
    }
}
