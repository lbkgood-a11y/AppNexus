package com.appnexus.platform.businessobject.internal.domain.configuration;

import static com.appnexus.contracts.businessobject.BusinessObjects.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 条件匹配与事实白名单验证，覆盖精度、缺失值及类型拒绝；不以数据库替代纯规则验证。 */
class ConfigurationRulesTest {
  private final Descriptor descriptor = new Descriptor("RuleTest", "规则测试", "Rule test", "test", 1,
      Category.CONFIGURATION, List.of(
          new Field("amount", "金额", "Amount", "NUMBER", "元", true),
          new Field("enabled", "启用", "Enabled", "BOOLEAN", "", false)), List.of());

  /** 大整数及不同小数尺度按精确十进制比较，禁止转为 double 造成选错绑定。 */
  @Test void preservesDecimalPrecisionAndScaleIndependence() {
    var facts = Map.of("amount", "9007199254740993.00");
    ConfigurationRules.checkFacts(descriptor, facts);
    assertThat(ConfigurationRules.test(descriptor, new Condition("amount", "GT", "9007199254740992"), facts)).isTrue();
    assertThat(ConfigurationRules.test(descriptor, new Condition("amount", "EQ", "9007199254740993.0"), facts)).isTrue();
    assertThat(ConfigurationRules.test(descriptor, new Condition("enabled", "EQ", "true"), facts)).isFalse();
  }

  /** 未声明事实、必填缺失、超长及布尔非标准文本都必须拒绝。 */
  @Test void rejectsUnknownMissingAndMalformedFacts() {
    assertThatThrownBy(() -> ConfigurationRules.checkFacts(descriptor, Map.of("amount", "1", "secret", "x")))
        .isInstanceOf(Rejection.class).hasMessage("BUSINESS_OBJECT_FACTS_INVALID");
    assertThatThrownBy(() -> ConfigurationRules.checkFacts(descriptor, Map.of()))
        .hasMessage("BUSINESS_OBJECT_FACTS_INVALID");
    assertThatThrownBy(() -> ConfigurationRules.checkFacts(descriptor, Map.of("amount", "x")))
        .hasMessage("BUSINESS_OBJECT_NUMBER_INVALID");
    assertThatThrownBy(() -> ConfigurationRules.checkFacts(descriptor, Map.of("amount", "1", "enabled", "TRUE")))
        .hasMessage("BUSINESS_OBJECT_BOOLEAN_INVALID");
    assertThatThrownBy(() -> ConfigurationRules.checkFacts(descriptor, Map.of("amount", "1".repeat(201))))
        .hasMessage("BUSINESS_OBJECT_FACTS_INVALID");
  }
}
