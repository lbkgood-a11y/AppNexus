package com.appnexus.platform.businessobject.internal.domain.configuration;

import com.appnexus.contracts.businessobject.BusinessObjects.Condition;
import com.appnexus.contracts.businessobject.BusinessObjects.Descriptor;
import com.appnexus.contracts.businessobject.BusinessObjects.Field;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/** 配置条件与业务事实的纯领域规则；不读取发布配置、身份、数据库或系统环境。 */
public final class ConfigurationRules {
  private ConfigurationRules() {}

  /**
   * 校验事实白名单与类型，防止调用方注入未声明字段或无界文本。
   * @param descriptor 唯一代码声明，包含字段类型与必填约束
   * @param facts 授权后重读的业务事实；字段文本最多 200 字，不包含业务正文或凭据
   * @throws Rejection 未声明字段、缺失必填或类型错误时拒绝
   */
  public static void checkFacts(Descriptor descriptor, Map<String, String> facts) {
    if (facts == null || facts.keySet().stream().anyMatch(key -> descriptor.fields().stream()
        .noneMatch(field -> field.key().equals(key)))) fail("BUSINESS_OBJECT_FACTS_INVALID");
    for (var field : descriptor.fields()) {
      String value = facts.get(field.key());
      if (value == null) {
        if (field.required()) fail("BUSINESS_OBJECT_FACTS_INVALID");
        continue;
      }
      if (value.length() > 200) fail("BUSINESS_OBJECT_FACTS_INVALID");
      scalar(field, value);
    }
  }

  /**
   * 校验固定标量，不使用浮点近似比较金额或数量。
   * @param field 声明字段，类型 TEXT、NUMBER 或 BOOLEAN
   * @param value 已检查长度的非空文本；布尔仅允许小写 true/false
   * @throws Rejection 数字或布尔格式不合法时拒绝
   */
  public static void scalar(Field field, String value) {
    if (field.type().equals("NUMBER")) {
      try { new BigDecimal(value); }
      catch (NumberFormatException error) { fail("BUSINESS_OBJECT_NUMBER_INVALID"); }
    }
    if (field.type().equals("BOOLEAN") && !Set.of("true", "false").contains(value))
      fail("BUSINESS_OBJECT_BOOLEAN_INVALID");
  }

  /**
   * 对已验证条件执行确定性匹配，未知运算符和缺失事实不匹配。
   * @param descriptor 唯一对象字段声明
   * @param condition 已通过配置校验的条件，数值使用精确十进制比较
   * @param facts 已通过 checkFacts 校验的事实，不替代执行授权
   * @return 条件是否匹配；应用必须拒绝零匹配及多匹配
   */
  public static boolean test(Descriptor descriptor, Condition condition, Map<String, String> facts) {
    String value = facts.get(condition.field());
    if (value == null) return false;
    var field = descriptor.fields().stream().filter(item -> item.key().equals(condition.field()))
        .findFirst().orElseThrow();
    int compare = field.type().equals("NUMBER")
        ? new BigDecimal(value).compareTo(new BigDecimal(condition.value()))
        : value.compareTo(condition.value());
    return switch (condition.operator()) {
      case "EQ" -> compare == 0;
      case "NE" -> compare != 0;
      case "GT" -> compare > 0;
      case "GE" -> compare >= 0;
      case "LT" -> compare < 0;
      case "LE" -> compare <= 0;
      default -> false;
    };
  }

  private static void fail(String code) { throw new Rejection(400, code); }
}
