package com.appnexus.bulkwarehousing.domain.title;

import java.math.BigDecimal;
import java.util.Map;

/** 校验没有独立货权转移的转换中，各货主份额均可对账。 */
public final class TitleShareBalance {
  private TitleShareBalance() {}

  /**
   * @param inputs 各货主投入份额，使用同一实物基础单位
   * @param outputs 各货主产出份额，缺省为零
   * @param losses 各货主承担的已批准损耗，缺省为零
   * @return 各货主份额均守恒且所有数量非负
   */
  public static boolean reconciles(Map<String, BigDecimal> inputs, Map<String, BigDecimal> outputs,
      Map<String, BigDecimal> losses) {
    if (inputs.isEmpty() || !inputs.keySet().containsAll(outputs.keySet())
        || !inputs.keySet().containsAll(losses.keySet())) return false;
    for (var input : inputs.entrySet()) {
      var output = outputs.getOrDefault(input.getKey(), BigDecimal.ZERO);
      var loss = losses.getOrDefault(input.getKey(), BigDecimal.ZERO);
      if (output.signum() < 0 || loss.signum() < 0 || input.getValue().compareTo(output.add(loss)) != 0) return false;
    }
    return true;
  }
}
