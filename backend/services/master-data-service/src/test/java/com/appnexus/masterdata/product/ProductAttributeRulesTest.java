package com.appnexus.masterdata.product;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
/** 校验声明与执行共用规则；非法边界、脚本和数值超界不能只靠前端拒绝。 */
class ProductAttributeRulesTest {
 @Test void validatesFiniteRulesAndBoundaries(){
  ProductAttributeRules.value("DECIMAL","{\"min\":\"1.25\",\"max\":\"2.50\"}","1.25",0);
  assertThatThrownBy(()->ProductAttributeRules.value("DECIMAL","{\"min\":\"1.25\"}","1.24",0)).hasMessage("PRODUCT_ATTRIBUTE_RULE_INVALID");
  ProductAttributeRules.value("TEXT","{\"minLength\":2,\"maxLength\":3}","商品",0);
  assertThatThrownBy(()->ProductAttributeRules.value("TEXT","{\"maxLength\":2}","商品名",0)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->ProductAttributeRules.definition("TEXT","{\"script\":\"x\"}")).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->ProductAttributeRules.definition("DATE","{\"minDate\":\"2026-12-01\",\"maxDate\":\"2026-01-01\"}")).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->ProductAttributeRules.value("MULTI_SELECT","{\"maxItems\":2}",null,3)).isInstanceOf(RuntimeException.class);
 }
}
