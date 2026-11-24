package com.appnexus.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.appnexus.bulkwarehousing.domain.stock.BatchConversion;
import java.math.BigDecimal;
import com.appnexus.bulkwarehousing.domain.stock.BatchLineage;
import com.appnexus.bulkwarehousing.domain.title.TitleShareBalance;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 用多品类夹具验证加工批次转换约束，无生产入口或数据库写入。 */
class BulkWarehouseDomainTest {
  /** 覆盖烘干损耗、分级拆批、多批配煤及来源身份误复用。 */
  @Test void batchConversionsReconcileInputsOutputsAndApprovedLoss() {
    assertTrue(BatchConversion.reconciles(List.of(line("湿玉米", "100")),
        List.of(line("干玉米", "96")), amount("4")));
    assertTrue(BatchConversion.reconciles(List.of(line("原废钢", "10")),
        List.of(line("一级", "6"), line("二级", "3"), line("余料", "0.8")), amount("0.2")));
    assertTrue(BatchConversion.reconciles(List.of(line("煤批甲", "60"), line("煤批乙", "40")),
        List.of(line("配煤批", "100")), BigDecimal.ZERO));
    assertFalse(BatchConversion.reconciles(List.of(line("湿玉米", "100")),
        List.of(line("干玉米", "96")), BigDecimal.ZERO));
    assertFalse(BatchConversion.reconciles(List.of(line("煤批甲", "60"), line("煤批乙", "40")),
        List.of(line("煤批甲", "100")), BigDecimal.ZERO));
  }

  /** 拆批后的产出参与另一批混配，双向追溯并拒绝循环。 */
  @Test void tracesSplitAndBlendInBothDirections() {
    var edges = List.of(new BatchLineage.Edge("原批", "一级"), new BatchLineage.Edge("原批", "二级"),
        new BatchLineage.Edge("一级", "混合批"), new BatchLineage.Edge("另一批", "混合批"));
    var lineage = new BatchLineage(edges);
    assertEquals(Set.of("一级", "二级", "混合批"), lineage.outputs("原批"));
    assertEquals(Set.of("原批", "一级", "另一批"), lineage.sources("混合批"));
    assertThrows(IllegalArgumentException.class, () -> new BatchLineage(List.of(
        new BatchLineage.Edge("原批", "一级"), new BatchLineage.Edge("一级", "原批"))));
  }

  /** 混配总量相同但货主份额错配时拒绝。 */
  @Test void reconcilesPerOwnerAfterBlendAndLoss() {
    var inputs = Map.of("甲", amount("60"), "乙", amount("40"));
    var losses = Map.of("甲", amount("2"));
    assertTrue(TitleShareBalance.reconciles(inputs, Map.of("甲", amount("58"), "乙", amount("40")), losses));
    assertFalse(TitleShareBalance.reconciles(inputs, Map.of("甲", amount("57"), "乙", amount("41")), losses));
  }

  /** 固定模板只接受该版本声明的步骤和有效前置事实。 */

  private static BatchConversion.Line line(String stockId, String quantity) {
    return new BatchConversion.Line(stockId,amount(quantity));
  }

  /** 构造规则测试用的精确十进制数量。 */
  private static BigDecimal amount(String value) { return new BigDecimal(value); }
}
