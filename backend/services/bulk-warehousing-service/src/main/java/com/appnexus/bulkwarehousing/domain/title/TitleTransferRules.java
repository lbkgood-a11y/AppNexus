package com.appnexus.bulkwarehousing.domain.title;

/** 整卷货权过户的状态、监管和版本规则。调用方提供同一业务视图中的事实。 */
public final class TitleTransferRules {
  private TitleTransferRules() {}

  /** 以现行过户规则优先级报告结果。 */
  public enum Decision { NOT_TRANSFERABLE, FROZEN, OCCUPIED, VERSION_CHANGED, ALLOWED }

  /**
   * @param coil 是否为整卷身份
   * @param registered 是否已登记货权
   * @param dispatched 是否已出库
   * @param frozen 监管是否禁止过户
   * @param occupied 是否存在实物占用
   * @param revision 锁定后的货权版本
   * @param expected 请求确认的货权版本
   * @return 过户业务决定
   */
  public static Decision evaluate(boolean coil, boolean registered, boolean dispatched,
      boolean frozen, boolean occupied, long revision, long expected) {
    if (!coil || !registered || dispatched) return Decision.NOT_TRANSFERABLE;
    if (frozen) return Decision.FROZEN;
    if (occupied) return Decision.OCCUPIED;
    if (revision != expected) return Decision.VERSION_CHANGED;
    return Decision.ALLOWED;
  }

  /**
   * @param currentRevision 当前库存货权代次
   * @param authorizedWatermark 签发提货授权时的货权代次上界
   * @return 当前权利是否仍可使用原授权
   */
  public static boolean withinAuthorization(long currentRevision, long authorizedWatermark) {
    return currentRevision <= authorizedWatermark;
  }

  /** 监管凭证写入意图由原凭证和解除状态共同决定。 */
  public enum RestrictionChange { APPLY, REPLAY, CONFLICT }

  /**
   * @param sameStock 是否为原冻结记录所约束的库存
   * @param originalEvidence 原冻结凭证，首次登记为 null
   * @param requestedEvidence 本次冻结凭证
   * @param lifted 原冻结是否已解除
   * @return 冻结登记的业务决定
   */
  public static RestrictionChange freeze(boolean sameStock, String originalEvidence,
      String requestedEvidence, boolean lifted) {
    if (originalEvidence == null) return RestrictionChange.APPLY;
    return sameStock && !lifted && originalEvidence.equals(requestedEvidence)
        ? RestrictionChange.REPLAY : RestrictionChange.CONFLICT;
  }

  /**
   * @param sameStock 是否存在对应库存的原冻结
   * @param recordedRelease 已登记的解除凭证，未解除为 null
   * @param requestedRelease 本次提交的解除凭证
   * @return 解除登记的业务决定
   */
  public static RestrictionChange lift(boolean sameStock, String recordedRelease, String requestedRelease) {
    if (!sameStock) return RestrictionChange.CONFLICT;
    if (recordedRelease == null) return RestrictionChange.APPLY;
    return recordedRelease.equals(requestedRelease) ? RestrictionChange.REPLAY : RestrictionChange.CONFLICT;
  }

  /** 初始货权登记和同凭证重放的结果。 */
  public enum Registration { APPLY, REPLAY, CONFLICT }

  /**
   * @param recordedEvidence 已登记的货权证据，未登记为 null
   * @param requestedEvidence 本次提交的证据
   * @param recordedAt 已登记的权利生效时间
   * @param requestedAt 本次提交的生效时间，已归一为数据库精度
   * @return 初次登记、原事实重放或冲突
   */
  public static Registration registration(String recordedEvidence, String requestedEvidence,
      java.time.Instant recordedAt, java.time.Instant requestedAt) {
    if (recordedEvidence == null) return Registration.APPLY;
    return recordedEvidence.equals(requestedEvidence) && requestedAt.equals(recordedAt)
        ? Registration.REPLAY : Registration.CONFLICT;
  }

  /**
   * @param effectiveAt 归一到数据库微秒精度后的登记生效时间
   * @param now 本次登记使用的当前可信应用时钟
   * @return 生效时间在数据库支持的下界以上且已经生效
   */
  public static boolean registrationEffectiveAt(java.time.Instant effectiveAt,java.time.Instant now) {
    return !effectiveAt.isBefore(java.time.Instant.EPOCH.plusSeconds(1)) && !effectiveAt.isAfter(now);
  }
}
