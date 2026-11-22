package com.appnexus.bulkwarehousing.domain.stock;

/** 库存可分配状态及现行准出结果优先级。调用方提供同一业务视图中的事实。 */
public final class StockAvailability {
  private StockAvailability() {}

  /** 可分配状态；多个拒绝原因并存时按现行准出顺序选取。 */
  public enum Status { DISPATCHED, FROZEN, PENDING_QUALITY, TITLE_UNREGISTERED, OCCUPIED, AVAILABLE }

  /**
   * @param dispatched 是否已交接出库
   * @param frozen 当前监管规则是否冻结该库存
   * @param qualityReady 是否已取得适用质量放行
   * @param titleReady 是否已登记可用货权
   * @param occupied 是否有有效实物占用
   * @return 依据当前库存及外部裁决事实计算的准出结果
   */
  public static Status evaluate(boolean dispatched, boolean frozen, boolean qualityReady,
      boolean titleReady, boolean occupied) {
    if (dispatched) return Status.DISPATCHED;
    if (frozen) return Status.FROZEN;
    if (!qualityReady) return Status.PENDING_QUALITY;
    if (!titleReady) return Status.TITLE_UNREGISTERED;
    return occupied ? Status.OCCUPIED : Status.AVAILABLE;
  }

  /**
   * @param returnedLocation 返仓原接管位置
   * @param currentLocation 锁定后库存当前的位置
   * @param dispatched 当前是否已发货
   * @param occupied 当前是否已占用
   * @param qualityReady 返仓质检是否已放行
   * @return 该返仓记录是否仍具有可放行的待检库存
   */
  public static boolean canReleaseReturnedQuality(String returnedLocation, String currentLocation,
      boolean dispatched, boolean occupied, boolean qualityReady) {
    return returnedLocation.equals(currentLocation) && !dispatched && !occupied && !qualityReady;
  }

  /**
   * @param coil 库存是否作为整卷管理
   * @return 通用预占入口能否处理该库存
   */
  public static boolean supportsStandaloneReservation(boolean coil) {
    return !coil;
  }

  /**
   * @param serial 拟登记的钢卷外部编号
   * @return 是否符合稳定卷号格式及长度要求
   */
  public static boolean validCoilSerial(String serial) {
    return serial != null && serial.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
  }

  /**
   * @param dispatched 当前是否已发出
   * @param qualityReady 是否达到质检放行条件
   * @param titleReady 是否有已生效货权
   * @param occupied 是否仍持有有效占用
   * @return 是否满足交接库存前置条件；冻结规则独立核验
   */
  public static boolean canHandover(boolean dispatched,boolean qualityReady,boolean titleReady,boolean occupied) {
    return !dispatched && qualityReady && titleReady && occupied;
  }

  /**
   * @param dispatched 当前是否已发出
   * @param occupied 当前是否被占用
   * @param currentBook 当前账面量，使用原交接账面单位
   * @param originalBook 原交接扣减重量
   * @return 库存状态是否允许原交接的补偿计算
   */
  public static boolean matchesDispatched(boolean dispatched,boolean occupied,
      java.math.BigDecimal currentBook,java.math.BigDecimal originalBook) {
    return dispatched && !occupied && currentBook.compareTo(originalBook)==0;
  }

  /** 有效占用判断决定是否写入、重放或拒绝。 */
  public enum OccupationChange { APPLY, REPLAY, CONFLICT, QUOTA_REQUIRED }

  /**
   * @param recordedReceipt 原占用对象，尚无占用记录时为 null
   * @param requestedReceipt 本次请求的库存对象
   * @param released 原占用是否已释放
   * @return 通用预占的业务处置
   */
  public static OccupationChange reserve(String recordedReceipt,String requestedReceipt,boolean released) {
    if (recordedReceipt == null) return OccupationChange.APPLY;
    return !released && recordedReceipt.equals(requestedReceipt)
        ? OccupationChange.REPLAY : OccupationChange.CONFLICT;
  }

  /**
   * @param recordedReceipt 原占用对象，尚无占用记录时为 null
   * @param requestedReceipt 本次请求的库存对象
   * @param quotaManaged 是否绑定三级提货额度
   * @param released 原占用是否已释放
   * @return 通用解除的业务处置，额度配货须走对应撤配
   */
  public static OccupationChange release(String recordedReceipt,String requestedReceipt,
      boolean quotaManaged,boolean released) {
    if (!requestedReceipt.equals(recordedReceipt)) return OccupationChange.CONFLICT;
    if (quotaManaged) return OccupationChange.QUOTA_REQUIRED;
    return released ? OccupationChange.REPLAY : OccupationChange.APPLY;
  }

  /** 登记卷号的业务决定，已登记卷号保持稳定。 */
  public enum Identification { APPLY, REPLAY, OCCUPIED, CONFLICT }

  /**
   * @param currentSerial 已登记卷号，首次登记时为 null
   * @param proposedSerial 本次登记的卷号
   * @param occupied 原库存是否存在有效配货
   * @return 初次登记、重放或业务冲突
   */
  public static Identification identify(String currentSerial,String proposedSerial,boolean occupied) {
    if (currentSerial != null) return currentSerial.equals(proposedSerial)
        ? Identification.REPLAY : Identification.CONFLICT;
    return occupied ? Identification.OCCUPIED : Identification.APPLY;
  }
}
