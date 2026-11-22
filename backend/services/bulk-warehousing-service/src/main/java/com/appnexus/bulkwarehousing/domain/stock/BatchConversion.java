package com.appnexus.bulkwarehousing.domain.stock;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashSet;

/** 批次转换的实物守恒和来源约束，调用方先按有效计量规则统一基础单位。 */
public final class BatchConversion {
  private BatchConversion() {}

  /**
   * @param stockId 稳定库存身份，调用方按可信业务范围解析
   * @param quantity 同一基础单位下的实物数量
   */
  public record Line(String stockId, BigDecimal quantity) {}

  /**
   * @param inputs 实际消耗的来源库存，至少一行
   * @param outputs 新生成库存，包含可回收余料，至少一行
   * @param approvedLoss 已批准损耗，使用相同基础单位，无损耗传零
   * @return 各行有效、身份不重复且实物数量守恒时返回真
   */
  public static boolean reconciles(Collection<Line> inputs, Collection<Line> outputs, BigDecimal approvedLoss) {
    if (inputs.isEmpty() || outputs.isEmpty() || approvedLoss.signum() < 0) return false;
    var identities = new HashSet<String>();
    for (var line : inputs) {
      if (line.stockId().isBlank() || line.quantity().signum() <= 0 || !identities.add(line.stockId())) return false;
    }
    for (var line : outputs) {
      if (line.stockId().isBlank() || line.quantity().signum() <= 0 || !identities.add(line.stockId())) return false;
    }
    return total(inputs).compareTo(total(outputs).add(approvedLoss)) == 0;
  }

  private static BigDecimal total(Collection<Line> lines) {
    return lines.stream().map(Line::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
