package com.appnexus.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.appnexus.bulkwarehousing.domain.stock.BatchConversion;
import java.math.BigDecimal;
import com.appnexus.bulkwarehousing.domain.stock.BatchLineage;
import com.appnexus.bulkwarehousing.domain.process.WarehouseFlowTemplate;
import com.appnexus.bulkwarehousing.domain.process.WarehouseFlowCatalog;
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
  @Test void fixedTemplatesRequireCurrentEvidence() {
    var template = WarehouseFlowTemplate.COIL_DISPATCH_V1;
    assertEquals(1, template.version());
    assertFalse(template.permits(WarehouseFlowTemplate.Step.HANDOVER, Set.of()));
    assertTrue(template.permits(WarehouseFlowTemplate.Step.HANDOVER, Set.of(WarehouseFlowTemplate.Step.WEIGHING)));
    assertFalse(template.permits(WarehouseFlowTemplate.Step.RECEIVE, Set.of(WarehouseFlowTemplate.Step.CUSTODY)));
  }

  /** 精确策略优先，历史引用始终按原编码和版本续办。 */
  @Test void resolvesPublishedStrategyAndRetainedVersionIndependently() {
    var receiving = WarehouseFlowTemplate.RECEIVING_V1;
    var original = WarehouseFlowTemplate.COIL_DISPATCH_V1;
    var dispatch = original.requiring(2, WarehouseFlowTemplate.Step.HANDOVER, WarehouseFlowTemplate.Step.ALLOCATION);
    var exact = new WarehouseFlowCatalog.Policy("operation", "road", dispatch);
    var fallback = new WarehouseFlowCatalog.Policy("operation", null, receiving);
    var catalog = new WarehouseFlowCatalog(List.of(fallback, exact), List.of(receiving, original, dispatch));
    assertEquals(dispatch, catalog.select("operation", "road"));
    assertEquals(receiving, catalog.select("operation", "rail"));
    var confirmed = Set.of(WarehouseFlowTemplate.Step.WEIGHING);
    assertTrue(catalog.resume(original.code(), 1).permits(WarehouseFlowTemplate.Step.HANDOVER, confirmed));
    assertFalse(catalog.resume(original.code(), 2).permits(WarehouseFlowTemplate.Step.HANDOVER, confirmed));
    assertThrows(IllegalStateException.class, () -> catalog.resume(original.code(), 3));
    assertThrows(IllegalStateException.class, () -> catalog.select("unknown", "road"));
    var ambiguous = new WarehouseFlowCatalog(List.of(exact, exact), List.of(dispatch));
    assertThrows(IllegalStateException.class, () -> ambiguous.select("operation", "road"));
  }

  private static BatchConversion.Line line(String stockId, String quantity) {
    return new BatchConversion.Line(stockId, amount(quantity));
  }

  private static BigDecimal amount(String value) {
    return new BigDecimal(value);
  }
}
