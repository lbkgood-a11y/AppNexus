package com.appnexus.masterdata.adapter.codec;
import com.appnexus.masterdata.domain.product.AttributeRulePolicy;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
/** 校验声明与执行共用规则；非法边界、脚本和数值超界不能只靠前端拒绝。 */
class ProductAttributeRulesTest {
 /** JSON字符串不能冒充整数长度，浮点数不能绕过整型约束，嵌套对象与null边界失败关闭。 */
 @Test void preservesJsonTypeBoundaries(){
  for(String rule:java.util.List.of("{\"minLength\":\"2\"}","{\"minLength\":2.5}","{\"minLength\":null}","{\"minLength\":{}}"))
   assertThatThrownBy(()->definition("TEXT",rule)).hasMessage("PRODUCT_ATTRIBUTE_RULE_INVALID");
  assertThatThrownBy(()->definition("DECIMAL","{\"min\":true}")).hasMessage("PRODUCT_ATTRIBUTE_RULE_INVALID");
  value("TEXT","{\"minLength\":1,\"maxLength\":1}","😀",0);
 }
 @Test void validatesFiniteRulesAndBoundaries(){
  value("DECIMAL","{\"min\":\"1.25\",\"max\":\"2.50\"}","1.25",0);
  assertThatThrownBy(()->value("DECIMAL","{\"min\":\"1.25\"}","1.24",0)).hasMessage("PRODUCT_ATTRIBUTE_RULE_INVALID");
  value("TEXT","{\"minLength\":2,\"maxLength\":3}","商品",0);
  assertThatThrownBy(()->value("TEXT","{\"maxLength\":2}","商品名",0)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->definition("TEXT","{\"script\":\"x\"}")).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->definition("DATE","{\"minDate\":\"2026-12-01\",\"maxDate\":\"2026-01-01\"}")).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->value("MULTI_SELECT","{\"maxItems\":2}",null,3)).isInstanceOf(RuntimeException.class);
 }
 private static void definition(String type,String rules){AttributeRulePolicy.definition(type,new JacksonAttributeRuleContent().decode(rules));}
 private static void value(String type,String rules,String value,int count){AttributeRulePolicy.value(type,new JacksonAttributeRuleContent().decode(rules),value,count);}
}
