package com.appnexus.masterdata.domain.product;
import static org.assertj.core.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
/** 完整性状态规则验证；历史待处理不能因读取结果而被自动转换。 */
class ProductCompletenessRulesTest {
 /** 启用且不完整必须拒绝，历史状态保持，草稿按事实判断。 */
 @Test void preservesLegacyAndRejectsIncompleteEnabledProduct(){
  assertThatThrownBy(()->ProductCompletenessRules.state("ENABLED","COMPLETE",List.of("unit"))).hasMessage("PRODUCT_COMPLETION_REQUIRED");
  assertThat(ProductCompletenessRules.state("ENABLED","LEGACY_PENDING",List.of("unit"))).isEqualTo("LEGACY_PENDING");
  assertThat(ProductCompletenessRules.state("DRAFT","INCOMPLETE",List.of())).isEqualTo("COMPLETE");
 }
}
