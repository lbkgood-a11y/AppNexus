package com.appnexus.bulkwarehousing.domain.inboundexecution;

import java.math.BigDecimal;
import java.util.Map;

/** 按作业绑定的模板评定实验室指标。规则只处理数值判定，不接收客户端放行结论。 */
public final class LabQualityRules {
    private LabQualityRules() {}

    /** 发布模板字段单位、物理取值范围和合格区间，均包含端点。 */
    public record Metric(String unit, BigDecimal minimum, BigDecimal maximum,
                         BigDecimal acceptMinimum, BigDecimal acceptMaximum) {}

    /** 实验室判定的稳定业务值。 */
    public enum Decision { PASS, FAIL }

    /**
     * @param rules 发布模板的全部指标及区间
     * @param metrics 实测指标，必须和规则一一匹配
     * @return 任一指标超出合格区间时为 FAIL
     * @throws IllegalStateException 模板不完整、不支持或范围反转
     * @throws IllegalArgumentException 客户端指标集合或值无效
     */
    public static Decision decide(Map<String, Metric> rules, Map<String, BigDecimal> metrics) {
        if (rules == null || rules.isEmpty() || rules.size() > 32) {
            throw new IllegalStateException("模板缺少有效实验室指标规则");
        }
        for (var entry : rules.entrySet()) {
            var rule = entry.getValue();
            if (!entry.getKey().matches("[a-z][a-zA-Z0-9_]{0,31}") || rule == null
                    || rule.unit() == null || rule.unit().isBlank() || rule.unit().length() > 24
                    || !validNumber(rule.minimum()) || !validNumber(rule.maximum())
                    || !validNumber(rule.acceptMinimum()) || !validNumber(rule.acceptMaximum())
                    || rule.minimum().compareTo(rule.acceptMinimum()) > 0
                    || rule.acceptMinimum().compareTo(rule.acceptMaximum()) > 0
                    || rule.acceptMaximum().compareTo(rule.maximum()) > 0) {
                throw new IllegalStateException("模板实验室指标范围无效");
            }
        }
        if (metrics == null || !rules.keySet().equals(metrics.keySet())) {
            throw new IllegalArgumentException("指标键与发布模板不一致");
        }
        boolean accepted = true;
        for (var entry : rules.entrySet()) {
            var rule = entry.getValue();
            var value = metrics.get(entry.getKey());
            if (!validNumber(value) || value.compareTo(rule.minimum()) < 0 || value.compareTo(rule.maximum()) > 0) {
                throw new IllegalArgumentException("实验室指标超出物理有效范围");
            }
            accepted &= value.compareTo(rule.acceptMinimum()) >= 0 && value.compareTo(rule.acceptMaximum()) <= 0;
        }
        return accepted ? Decision.PASS : Decision.FAIL;
    }

    private static boolean validNumber(BigDecimal value) {
        return value != null && value.scale() >= 0 && value.scale() <= 8 && value.precision() <= 18;
    }
}
