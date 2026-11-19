package com.appnexus.printing.core;

import java.util.Set;

/** 代理回执纯状态规则；状态不能证明纸张是否已出，不允许用自动重试掩盖未知打印结果。 */
public final class PrintJobLifecycle {
  private PrintJobLifecycle() {}
  /** @param state 代理目标状态，可空 @return 是否为受控回执状态，不接受任意文本 */
  public static boolean validReceipt(String state) {
    return state != null && Set.of("SUBMITTED", "SUCCEEDED", "FAILED", "UNKNOWN").contains(state);
  }
  /**
   * 校验非幂等回执的状态转换；相同结果重放在应用完成代理身份和结构一致性核验后处理。
   * @param current 数据库锁定的当前状态，不接受客户端当前状态
   * @param next 代理提交的已验证目标状态
   * @param leaseExpired 数据库 UTC 判断的领取租约是否过期；仅约束 CLAIMED 阶段
   * @return 是否允许转换；未提交队列不能直达成功，已提交后不允许声称安全失败重印
   */
  public static boolean permitsReceipt(String current, String next, boolean leaseExpired) {
    return "CLAIMED".equals(current) && !leaseExpired
        && Set.of("SUBMITTED", "FAILED", "UNKNOWN").contains(next)
        || "SUBMITTED".equals(current) && Set.of("SUCCEEDED", "UNKNOWN").contains(next);
  }
}
