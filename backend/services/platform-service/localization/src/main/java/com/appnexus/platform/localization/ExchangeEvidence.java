package com.appnexus.platform.localization;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/**
 * 历史汇率依据契约；仅提供不可变证据模型，不联网获取、自动选率或执行币种折算服务。
 * @param id 不可变汇率依据 UUID，业务记录引用后不得修改
 * @param sourceCurrency 原始金额 ISO 4217 币种，不能由展示偏好覆盖
 * @param targetCurrency 折算目标 ISO 4217 币种
 * @param rate 每 1 单位原币对应目标币金额，必须为正的精确十进制数
 * @param effectiveDate 汇率业务日期，不作为 UTC 时间点进行时区偏移
 * @param source 已审核来源标识，1–100 字符，不是任意抓取 URL 或认证凭据
 * @param capturedAt 服务端采集或审核 UTC 时间点，不能以显示时区修改
 * @param resultScale 结果小数位数，0–8，由业务币种及账务规则明确指定
 * @param rounding 已确认舍入规则，不允许缺省依赖浮点行为
 */
public record ExchangeEvidence(UUID id,String sourceCurrency,String targetCurrency,BigDecimal rate,
        LocalDate effectiveDate,String source,Instant capturedAt,int resultScale,RoundingMode rounding) {
    /** 校验并保留原始十进制精度，不在模型构造时隐式四舍五入。 */
    public ExchangeEvidence {
        Objects.requireNonNull(id);Objects.requireNonNull(rate);Objects.requireNonNull(effectiveDate);Objects.requireNonNull(capturedAt);Objects.requireNonNull(rounding);
        if(sourceCurrency==null||targetCurrency==null||!sourceCurrency.matches("[A-Z]{3}")||!targetCurrency.matches("[A-Z]{3}"))throw new IllegalArgumentException("ISO currency required");
        Currency.getInstance(sourceCurrency);Currency.getInstance(targetCurrency);
        if(rate.signum()<=0||rate.precision()>38||rate.scale()>18||resultScale<0||resultScale>8||source==null||source.isBlank()||source.length()>100||source.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid exchange evidence");
        if(sourceCurrency.equals(targetCurrency)&&rate.compareTo(BigDecimal.ONE)!=0)throw new IllegalArgumentException("Same-currency rate must be one");
    }
}
