package com.appnexus.workflow.core;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/** 条件预演与真实运行共用的受控比较；缺失字段或非法数值不匹配，不解释表达式或函数名。 */
public final class WorkflowCondition {
  private WorkflowCondition() {}

  /** 缺省为历史字符串等值比较；数值运算只支持固定白名单，不接收脚本。 */
  public static String operator(String value) { return value == null ? "EQ" : value; }

  /** 大小比较只允许十进制常量；最多 100 字符，不接收指数、NaN、Infinity 或计算式。 */
  public static boolean numeric(String value) {
    return value != null && value.length() <= 100 && value.matches("-?[0-9]+(?:\\.[0-9]+)?");
  }

  /** 发布及预演前检查操作符和值，保持历史 EQ 语义。 */
  public static boolean valid(String op, String value) {
    String operator = operator(op);
    return Set.of("EQ", "NE", "NUM_EQ", "NUM_NE", "GT", "GE", "LT", "LE").contains(operator)
        && value != null && value.length() <= 100
        && (Set.of("EQ", "NE").contains(operator) || numeric(value));
  }

  /**
   * @param fields 可信非敏感业务快照或显式输入的预演样本
   * @param field 已校验的字段白名单标识，不支持属性路径
   * @param op 固定比较符，缺省 EQ；未知比较符拒绝
   * @param expected 条件常量，数值比较不使用浮点运算
   * @return 缺字段、非法样本或条件不满足时为 false
   */
  public static boolean test(Map<String, String> fields, String field, String op, String expected) {
    if (fields == null || !valid(op, expected)) return false;
    String actual = fields.get(field);
    if (actual == null) return false;
    String operator = operator(op);
    if (operator.equals("EQ")) return actual.equals(expected);
    if (operator.equals("NE")) return !actual.equals(expected);
    if (!numeric(actual)) return false;
    int compared = new BigDecimal(actual).compareTo(new BigDecimal(expected));
    return switch (operator) {
      case "NUM_EQ" -> compared == 0;
      case "NUM_NE" -> compared != 0;
      case "GT" -> compared > 0;
      case "GE" -> compared >= 0;
      case "LT" -> compared < 0;
      case "LE" -> compared <= 0;
      default -> false;
    };
  }
}
