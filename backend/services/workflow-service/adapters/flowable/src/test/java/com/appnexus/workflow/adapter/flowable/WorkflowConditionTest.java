package com.appnexus.workflow.adapter.flowable;

import static org.assertj.core.api.Assertions.*;
import com.appnexus.workflow.core.WorkflowCondition;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 业务条件的边界及失败关闭验证；实际路由另由请假真实引擎集成测试覆盖。 */
class WorkflowConditionTest {
  @Test void decimalComparisonDoesNotUseLexicalOrFloatingPointOrdering() {
    assertThat(WorkflowCondition.test(Map.of("days", "10"), "days", "GT", "3")).isTrue();
    assertThat(WorkflowCondition.test(Map.of("days", "3"), "days", "GT", "3")).isFalse();
    assertThat(WorkflowCondition.test(Map.of("days", "3.00"), "days", "GE", "3")).isTrue();
    assertThat(WorkflowCondition.test(Map.of("days", "3"), "days", "NUM_EQ", "3.00")).isTrue();
    assertThat(WorkflowCondition.test(Map.of("days", "3"), "days", "NUM_NE", "3.00")).isFalse();
    assertThat(WorkflowCondition.test(Map.of("days", "2.99999999999999999999"), "days", "LT", "3")).isTrue();
  }
  @Test void missingOrInvalidValuesNeverMatchEvenNotEquals() {
    assertThat(WorkflowCondition.test(Map.of(), "days", "NE", "3")).isFalse();
    for (String value : new String[]{"NaN", "Infinity", "1e1000000", "3 + 1", "${days}"}) {
      assertThat(WorkflowCondition.test(Map.of("days", value), "days", "GT", "3")).isFalse();
      assertThat(WorkflowCondition.valid("GT", value)).isFalse();
    }
    assertThat(WorkflowCondition.valid("CUSTOM", "3")).isFalse();
  }
  @Test void historicalEqualityRemainsStringEquality() {
    assertThat(WorkflowCondition.test(Map.of("flag", "true"), "flag", null, "true")).isTrue();
    assertThat(WorkflowCondition.test(Map.of("flag", "true"), "flag", "NE", "false")).isTrue();
  }
}
