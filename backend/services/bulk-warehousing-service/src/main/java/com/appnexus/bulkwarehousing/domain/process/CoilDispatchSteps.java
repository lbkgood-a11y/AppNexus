package com.appnexus.bulkwarehousing.domain.process;

import java.math.BigDecimal;

/** 现有钢卷出仓车次的可执行步骤。调用方提供同一业务视图中的事实。 */
public final class CoilDispatchSteps {
  private CoilDispatchSteps() {}

  /** 车次阶段用于确定配货等步骤能否继续。 */
  public enum Stage { ALREADY_ISSUED, RECOVERY_CLOSED, EXECUTABLE }

  /**
   * @param retired 车次是否已退役
   * @param issued 已核销数量，使用授权计量单位
   * @param recoveryClosed 续办车次是否已关闭
   * @return 现行钢卷流程下车次的可执行阶段
   */
  public static Stage stage(boolean retired, BigDecimal issued, boolean recoveryClosed) {
    if (retired || issued.signum() != 0) return Stage.ALREADY_ISSUED;
    return recoveryClosed ? Stage.RECOVERY_CLOSED : Stage.EXECUTABLE;
  }
}
