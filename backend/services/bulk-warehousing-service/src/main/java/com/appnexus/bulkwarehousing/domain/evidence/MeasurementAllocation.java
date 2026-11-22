package com.appnexus.bulkwarehousing.domain.evidence;

import java.math.BigDecimal;
import java.util.Collection;

/** 计量采用量和分摊量守恒验证。调用方提供同一业务视图中的事实。 */
public final class MeasurementAllocation {
  private MeasurementAllocation() {}

  /**
   * @param portions 分摊量，使用采用量的计量单位
   * @param adopted 有效计量采用量，使用相同单位
   * @return 分摊总量是否等于有效计量采用量
   */
  public static boolean balances(Collection<BigDecimal> portions, BigDecimal adopted) {
    return portions.stream().reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(adopted) == 0;
  }

  /**
   * @param payload 车辆载货上限，使用授权计量单位
   * @param gross 车货总重上限，使用相同单位
   * @return 车货总重上限是否覆盖载货上限
   */
  public static boolean validVehicleLimits(BigDecimal payload, BigDecimal gross) {
    return gross.compareTo(payload) >= 0;
  }

  /** 原实收入仓质检命令可执行的决定。 */
  public enum IntakeQuality { APPLY, REPLAY, RETURN_REVIEW_REQUIRED, CONFLICT }

  /**
   * @param intakeEvidence 原进仓放行证据，尚未放行为 null
   * @param requestedEvidence 本次申请证据
   * @param pending 当前待检数量，使用库存基础单位
   * @return 初次放行、重放、返仓独立质检或凭证冲突
   */
  public static IntakeQuality intakeQuality(String intakeEvidence, String requestedEvidence, BigDecimal pending) {
    if (intakeEvidence == null) return IntakeQuality.APPLY;
    if (pending.signum() > 0) return IntakeQuality.RETURN_REVIEW_REQUIRED;
    return intakeEvidence.equals(requestedEvidence) ? IntakeQuality.REPLAY : IntakeQuality.CONFLICT;
  }

  /** 载货及车货总重分别独立验证，可同时命中。 */
  public enum VehicleIssue { LIMITS_REQUIRED, PAYLOAD_LIMIT, GROSS_LIMIT }

  /**
   * @param payload 载货上限，缺失为 null
   * @param grossLimit 车货总重上限，缺失为 null
   * @param net 实测净重
   * @param gross 实测总重，所有重量使用同一授权单位
   * @return 按现行拒绝优先级排序的车辆约束结果
   */
  public static java.util.List<VehicleIssue> vehicleIssues(BigDecimal payload, BigDecimal grossLimit,
      BigDecimal net, BigDecimal gross) {
    if (payload == null || grossLimit == null) return java.util.List.of(VehicleIssue.LIMITS_REQUIRED);
    var issues = new java.util.ArrayList<VehicleIssue>();
    if (net.compareTo(payload) > 0) issues.add(VehicleIssue.PAYLOAD_LIMIT);
    if (gross.compareTo(grossLimit) > 0) issues.add(VehicleIssue.GROSS_LIMIT);
    return java.util.List.copyOf(issues);
  }

  /**
   * @param approved 复磅记录是否已批准
   * @param reviewedTrip 复磅面向的车次
   * @param actualTrip 本次交接车次
   * @param reviewedLoadVersion 采用复磅时的装载版本
   * @param actualLoadVersion 锁定后的当前装载版本
   * @return 是否仍可使用该复磅结果完成交接
   */
  public static boolean matchesHandover(boolean approved,String reviewedTrip,String actualTrip,
      long reviewedLoadVersion,long actualLoadVersion) {
    return approved && reviewedTrip.equals(actualTrip) && reviewedLoadVersion==actualLoadVersion;
  }

  /**
   * @param currentPayload 已登记载货上限，使用授权单位
   * @param currentGross 已登记总重上限
   * @param requestedPayload 请求载货上限
   * @param requestedGross 请求总重上限
   * @return 重放同一车辆证据时其两个数量是否一致
   */
  public static boolean sameVehicleLimits(BigDecimal currentPayload,BigDecimal currentGross,
      BigDecimal requestedPayload,BigDecimal requestedGross) {
    return requestedPayload.compareTo(currentPayload)==0 && requestedGross.compareTo(currentGross)==0;
  }
}
