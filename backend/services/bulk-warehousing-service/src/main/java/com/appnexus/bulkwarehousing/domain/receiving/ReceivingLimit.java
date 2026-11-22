package com.appnexus.bulkwarehousing.domain.receiving;

import java.math.BigDecimal;

/** 进仓累计实收约束。调用方提供同一业务视图中的事实。 */
public final class ReceivingLimit {
  private ReceivingLimit() {}

  /**
   * @param received 已确认实收量，使用通知基础单位
   * @param incoming 本次实收量，使用相同单位
   * @param limit 已批准累计上限，使用相同单位
   * @return 本次实收是否在上限内
   */
  public static boolean allows(BigDecimal received, BigDecimal incoming, BigDecimal limit) {
    return received.add(incoming).compareTo(limit) <= 0;
  }

  /**
   * @param notifiedWarehouse 签发时指定的接收仓
   * @param actualWarehouse 接管实际位置所在仓
   * @return 接管是否属于本次通知
   */
  public static boolean allowsCustody(String notifiedWarehouse, String actualWarehouse) {
    return notifiedWarehouse.equals(actualWarehouse);
  }
  /**
   * @param itemUnit 品规的权威基础计量单位
   * @param noticeUnit 签发通知使用的计量单位
   * @return 通知是否采用品规基础单位
   */
  public static boolean acceptsNoticeUnit(String itemUnit, String noticeUnit) {
    return itemUnit.equals(noticeUnit);
  }
}
