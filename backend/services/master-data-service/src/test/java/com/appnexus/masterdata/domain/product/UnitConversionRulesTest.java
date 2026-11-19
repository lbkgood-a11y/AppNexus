package com.appnexus.masterdata.domain.product;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 换算领域边界验证，覆盖十进制精度、往返容差与非标准输入拒绝。 */
class UnitConversionRulesTest {
  /** 精确值不因目标展示舍入丢失，允许的容差边界必须保持。 */
  @Test void preservesExactQuantityAndReciprocalTolerance() {
    assertThat(UnitConversionRules.calculate(new BigDecimal("9007199254740993.001"), new BigDecimal("0.001"), 3))
        .containsEntry("exact", "9007199254740.993001").containsEntry("display", "9007199254740.993");
    UnitConversionRules.reciprocal(BigDecimal.ONE, new BigDecimal("1.000000000001"));
    assertThatThrownBy(() -> UnitConversionRules.reciprocal(BigDecimal.ONE, new BigDecimal("1.000000000002")))
        .hasMessage("PRODUCT_CONVERSION_INVALID");
  }
  /** 系数正数、格式及维度约束不能由前端替代。 */
  @Test void rejectsInvalidFactorsAndDimensions() {
    for (String input : new String[]{"0", "-1", "1e3", "0.0000000000001", "1000000000000000000"})
      assertThatThrownBy(() -> UnitConversionRules.decimal(input, true)).hasMessage("PRODUCT_CONVERSION_INVALID");
    assertThat(UnitConversionRules.decimal("-1.25", false)).isEqualByComparingTo("-1.25");
    assertThatThrownBy(() -> UnitConversionRules.sameDimension("MASS", "TIME")).hasMessage("PRODUCT_CONVERSION_INVALID");
  }
}
