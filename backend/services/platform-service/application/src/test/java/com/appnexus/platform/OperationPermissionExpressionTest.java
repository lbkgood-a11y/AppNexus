package com.appnexus.platform;
import com.appnexus.platform.authorization.OperationPermissionExpression;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/** 准入与诊断共享表达式计算，空表达式和不完整 AND 分支不得意外放行。 */
class OperationPermissionExpressionTest {
 @Test void evaluatesAlternativesAndRejectsIncompleteGroups(){
  var allowed=Set.of("a:read","b:update");
  assertThat(OperationPermissionExpression.allows(List.of("a:read&a:update","b:update"),allowed::contains)).isTrue();
  assertThat(OperationPermissionExpression.allows(List.of("a:read&a:update"),allowed::contains)).isFalse();
  assertThat(OperationPermissionExpression.allows(List.of(),ignored->true)).isFalse();
  assertThat(OperationPermissionExpression.allows(List.of("a:read&"),ignored->true)).isFalse();
 }
}
