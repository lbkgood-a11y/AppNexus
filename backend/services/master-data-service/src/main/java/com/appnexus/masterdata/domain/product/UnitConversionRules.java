package com.appnexus.masterdata.domain.product;

import com.appnexus.masterdata.MasterData.Rejection;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 单位换算纯规则；精度、往返误差及维度判定不依赖存储、时钟或 Spring。 */
public final class UnitConversionRules {
  private UnitConversionRules() {}
  /** @param value 最多18位整数与12位小数的十进制文本 @param positive true 为系数，要求严格正数 @return 精确十进制值；不接受指数文本或浮点近似 */
  public static BigDecimal decimal(String value, boolean positive) {
    if (value == null || !value.matches("-?(0|[1-9][0-9]{0,17})([.][0-9]{1,12})?")) throw invalid();
    var result = new BigDecimal(value);
    if (positive && result.signum() <= 0) throw invalid();
    return result;
  }
  /** @param value 小写规范 UUID 文本，不允许其他格式 @throws Rejection 标识不合法时拒绝 */
  public static void identifier(String value) {
    try { if (!UUID.fromString(value).toString().equals(value)) throw invalid(); }
    catch (IllegalArgumentException | NullPointerException error) { throw invalid(); }
  }
  /** @param source 源维度 @param target 目标维度 @throws Rejection 不同维度拒绝直连换算 */
  public static void sameDimension(String source, String target) {
    if (!Objects.equals(source, target)) throw invalid();
  }
  /** @param factor 当前系数 @param reverse 已启用反向系数，可空 @throws Rejection 往返误差大于 0.000000000001 时拒绝 */
  public static void reciprocal(BigDecimal factor, BigDecimal reverse) {
    if (reverse != null && reverse.multiply(factor).subtract(BigDecimal.ONE).abs()
        .compareTo(new BigDecimal("0.000000000001")) > 0) throw invalid();
  }
  /** @param quantity 原精确数量 @param factor 原精确系数 @param scale 目标单位展示精度 @return exact 不舍入，display 按 HALF_UP 展示；均保留文本精度 */
  public static Map<String, String> calculate(BigDecimal quantity, BigDecimal factor, int scale) {
    var exact = quantity.multiply(factor);
    return Map.of("exact", exact.stripTrailingZeros().toPlainString(),
        "display", exact.setScale(scale, RoundingMode.HALF_UP).toPlainString());
  }
  /** @return 原协议的稳定输入错误，不能附带存储或敏感内容 */
  public static Rejection invalid() { return new Rejection(400, "PRODUCT_CONVERSION_INVALID"); }
}
