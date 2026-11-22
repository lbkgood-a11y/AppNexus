package com.appnexus.bulkwarehousing.domain.returns;

import java.math.BigDecimal;

/** 返仓对原交接剩余可退额度的校验。调用方提供同一业务视图中的事实。 */
public final class ReturnBalance {
  private ReturnBalance() {}

  /**
   * @param remainingNet 各未退交接行的核销量合计
   * @param issued 当前车次已核销量，使用相同授权单位
   * @param reserved 当前车次剩余占用量，使用相同单位
   * @return 可退明细是否与原车次当前账面一致
   */
  public static boolean reconciles(BigDecimal remainingNet, BigDecimal issued, BigDecimal reserved) {
    return remainingNet.compareTo(issued) == 0 && reserved.signum() == 0;
  }

  /**
   * @param actualReturn 是否为实物退回后的冲销
   * @param requestedAcceptance 请求引用的接收记录标识
   * @param currentAcceptance 已登记的接收记录标识，未登记为 null
   * @return 冲销方式是否与原实物接收事实一致
   */
  public static boolean matchesReversal(boolean actualReturn, String requestedAcceptance, String currentAcceptance) {
    return actualReturn ? requestedAcceptance.equals(currentAcceptance) : currentAcceptance == null;
  }

  /**
   * @param linkedTransit 已关联的返仓在途标识，未关联为 null
   * @param requestedTransit 当前操作的返仓在途标识
   * @param received 是否已接管入账
   * @return 当前卷是否可以继续在途操作
   */
  public static boolean isOpenTransit(String linkedTransit, String requestedTransit, boolean received) {
    return requestedTransit.equals(linkedTransit) && !received;
  }

  /**
   * @param currentWarehouse 当前有效接收仓
   * @param expectedWarehouse 操作引用的接收仓
   * @return 当前操作是否使用有效仓路
   */
  public static boolean matchesRoute(String currentWarehouse, String expectedWarehouse) {
    return currentWarehouse.equals(expectedWarehouse);
  }

  /**
   * @param issuedIdentities 原交接有效库存身份
   * @param acceptedIdentities 本次声明接收的身份
   * @return 整笔退回是否与原交接覆盖完全一致
   */
  public static boolean coversOriginal(java.util.Collection<String> issuedIdentities,
      java.util.Set<String> acceptedIdentities) {
    return issuedIdentities.size() == acceptedIdentities.size()
        && acceptedIdentities.containsAll(issuedIdentities);
  }

  /**
   * @param mistakenHandover 是否为误确认冲销
   * @param authorizationClosed 原提货授权是否已关闭
   * @return 是否恢复原配货占用，已经关闭的授权保持关闭
   */
  public static boolean restoresOccupation(boolean mistakenHandover, boolean authorizationClosed) {
    return mistakenHandover && !authorizationClosed;
  }

  /** 在途改单及接管必须按当前有效仓路、短损状态判断。 */
  public enum RouteDecision { ALLOWED, ROUTE_CHANGED, LOSS_SETTLED }

  /**
   * @param currentWarehouse 锁定后的有效接收仓
   * @param expectedWarehouse 操作请求所依据的接收仓，未指定时为 null
   * @param lossSettled 是否有活动短损
   * @param rerouting 是否为改仓；改仓优先报告活动短损，接管优先报告仓路不符
   * @return 对该仓路操作的业务决定
   */
  public static RouteDecision routeDecision(String currentWarehouse,String expectedWarehouse,
      boolean lossSettled,boolean rerouting) {
    if (rerouting && lossSettled) return RouteDecision.LOSS_SETTLED;
    if (!matchesRoute(currentWarehouse,expectedWarehouse)) return RouteDecision.ROUTE_CHANGED;
    return lossSettled ? RouteDecision.LOSS_SETTLED : RouteDecision.ALLOWED;
  }

  /**
   * @param activeLossId 当前生效的短损记录，未结清或已重开为 null
   * @param requestedLossId 本次重开指向的短损记录
   * @return 指定短损是否仍可重开
   */
  public static boolean canReopenLoss(String activeLossId,String requestedLossId) {
    return requestedLossId.equals(activeLossId);
  }
}
