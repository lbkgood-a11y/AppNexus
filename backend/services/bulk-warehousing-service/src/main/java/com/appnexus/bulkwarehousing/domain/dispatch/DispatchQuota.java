package com.appnexus.bulkwarehousing.domain.dispatch;

import java.math.BigDecimal;

/** 出仓额度增减和交接核销规则。调用方提供同一业务视图中的事实。 */
public final class DispatchQuota {
  private DispatchQuota() {}

  /**
   * @param parentDelta 通知与授权的预留变化
   * @param tripDelta 车次的预留变化
   * @param unassignedDelta 授权未分配额度变化，三者均使用授权单位
   */
  public record ReservationChange(BigDecimal parentDelta, BigDecimal tripDelta, BigDecimal unassignedDelta) {}

  /**
   * @param tripDelta 配货或复磅造成的车次占用变化，使用授权单位
   * @param unassigned 授权现有未分配占用量，使用相同单位
   * @return 先消费未分配额度的三级占用变更；减配释放额度而不生成未分配占用
   */
  public static ReservationChange assign(BigDecimal tripDelta, BigDecimal unassigned) {
    BigDecimal consumed = tripDelta.signum() > 0 ? tripDelta.min(unassigned) : BigDecimal.ZERO;
    return new ReservationChange(tripDelta.subtract(consumed), tripDelta, consumed.negate());
  }

  /**
   * @param committed 通知已承诺额度，使用授权单位
   * @param requested 新授权申请量，使用相同单位
   * @param cap 通知批准上限，使用相同单位
   * @return 是否允许签发新授权
   */
  public static boolean allowsCommitment(BigDecimal committed, BigDecimal requested, BigDecimal cap) {
    return committed.add(requested).compareTo(cap) <= 0;
  }

  /**
   * @param reserved 当前占用量，使用授权计量单位
   * @param remaining 可新增占用量，使用同一单位
   * @param delta 占用变化量，负数表示释放
   * @return 额度能否覆盖该变化且占用不会为负
   */
  public static boolean allowsChange(BigDecimal reserved, BigDecimal remaining, BigDecimal delta) {
    return delta.compareTo(remaining) <= 0 && reserved.add(delta).signum() >= 0;
  }

  /**
   * @param issued 本次交接核销量
   * @param approvedNet 有效复磅确认净重
   * @param tripReserved 车次占用量
   * @param authorizationAssigned 授权已分配占用量
   * @param noticeReserved 通知占用量，以上参数均使用授权计量单位
   * @return 各层占用和计量是否支持本次核销
   */
  public static boolean allowsIssue(BigDecimal issued, BigDecimal approvedNet,
      BigDecimal tripReserved, BigDecimal authorizationAssigned, BigDecimal noticeReserved) {
    return issued.compareTo(approvedNet) == 0 && issued.compareTo(tripReserved) == 0
        && issued.compareTo(authorizationAssigned) <= 0 && issued.compareTo(noticeReserved) <= 0;
  }

  /**
   * @param reserved 授权占用量，使用授权单位
   * @param unassigned 尚未分配至车次的占用量，使用相同单位
   * @return 是否没有车次占用并允许短结
   */
  public static boolean canClose(BigDecimal reserved, BigDecimal unassigned) {
    return reserved.compareTo(unassigned) == 0;
  }
  /**
   * @param itemUnit 品规的权威基础计量单位
   * @param noticeUnit 签发通知使用的计量单位
   * @return 通知是否采用品规基础单位
   */
  public static boolean acceptsNoticeUnit(String itemUnit, String noticeUnit) {
    return itemUnit.equals(noticeUnit);
  }

  /**
   * @param current 当前未分配授权预留，使用授权计量单位
   * @param expected 请求依据的未分配预留量
   * @param requested 本次希望保留的未分配量
   * @return 实际增量，已有预留与请求前置条件冲突时返回空
   */
  public static java.util.Optional<BigDecimal> reviseUnassigned(
      BigDecimal current,BigDecimal expected,BigDecimal requested) {
    return current.compareTo(expected)==0
        ? java.util.Optional.of(requested.subtract(current)) : java.util.Optional.empty();
  }

  /**
   * @param recorded 已锁定装载明细中的标识集合
   * @param reported 复磅或交接实报的标识
   * @return 当前装载是否与实报的有效范围完全相同
   */
  public static boolean matchesLoad(java.util.Collection<String> recorded,java.util.Set<String> reported) {
    return !recorded.isEmpty() && recorded.size()==reported.size() && reported.containsAll(recorded);
  }
}
