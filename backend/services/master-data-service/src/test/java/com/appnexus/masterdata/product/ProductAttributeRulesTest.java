package com.appnexus.masterdata.product;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
/** 校验声明与执行共用规则；非法边界、脚本和数值超界不能只靠前端拒绝。 */
class ProductAttributeRulesTest {
 /** JSON字符串不能冒充整数长度，浮点数不能绕过整型约束，嵌套对象与null边界失败关闭。 */
 @Test void preservesJsonTypeBoundaries(){
  for(String rule:java.util.List.of("{\"minLength\":\"2\"}","{\"minLength\":2.5}","{\"minLength\":null}","{\"minLength\":{}}"))
   assertThatThrownBy(()->ProductAttributeRules.definition("TEXT",rule)).hasMessage("PRODUCT_ATTRIBUTE_RULE_INVALID");
  assertThatThrownBy(()->ProductAttributeRules.definition("DECIMAL","{\"min\":true}")).hasMessage("PRODUCT_ATTRIBUTE_RULE_INVALID");
  ProductAttributeRules.value("TEXT","{\"minLength\":1,\"maxLength\":1}","😀",0);
 }
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
