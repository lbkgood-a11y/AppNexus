package com.appnexus.masterdata.domain.product;
import static org.assertj.core.api.Assertions.*;
import java.util.List;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
/** 动态筛选边界回归，精确小数、日期范围与布尔运算不能因SQL迁移放宽。 */
class ProductFilterRulesTest {
 /** 拒绝倒置范围、超精度与不适用运算符。 */
 @Test void rejectsInvalidRangesAndOperators(){
  assertThatThrownBy(()->ProductFilterRules.scalar("INTEGER",0,"between",List.of("2","1"))).hasMessage("PRODUCT_V1_INPUT_INVALID");
  assertThatThrownBy(()->ProductFilterRules.scalar("DECIMAL",2,"eq",List.of("1.001"))).hasMessage("PRODUCT_V1_INPUT_INVALID");
  assertThatThrownBy(()->ProductFilterRules.scalar("BOOLEAN",0,"gte",List.of("true"))).hasMessage("PRODUCT_V1_INPUT_INVALID");
  assertThatThrownBy(()->ProductFilterRules.scalar("DATE",0,"gte",List.of("2026-01-01"))).hasMessage("PRODUCT_V1_INPUT_INVALID");
  assertThat(ProductFilterRules.scalar("DECIMAL",2,"eq",List.of("9007199254740993.01"))).containsExactly(new BigDecimal("9007199254740993.01"));
 }
}
