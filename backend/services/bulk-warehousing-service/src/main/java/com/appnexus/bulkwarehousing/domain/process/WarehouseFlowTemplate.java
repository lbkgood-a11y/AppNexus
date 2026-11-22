package com.appnexus.bulkwarehousing.domain.process;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 版本固定的仓储步骤模板。前置事实由应用在有效事务视图内取得。 */
public final class WarehouseFlowTemplate {
  /** 流程中可验证的业务步骤和证据。 */
  public enum Step { NOTICE, CUSTODY, RECEIVE, AUTHORIZATION, TRIP, ALLOCATION, VEHICLE_LIMITS, PRE_RESERVE, CLOSE, CANCEL, WEIGHING, HANDOVER,
    REGISTER_TITLE, RELEASE_QUALITY, RETURN_ACCEPT, REVERSAL, RETURN_DISPATCH, RETURN_RECEIVE,
    RETURN_REROUTE, LOSS_SETTLE, LOSS_REOPEN, RETURN_QUALITY }

  /** 历史进仓接管记录对应的固定模板版本。 */
  public static final WarehouseFlowTemplate RECEIVING_V1 = new WarehouseFlowTemplate(
      "warehouse-receiving", 1, Map.of(Step.NOTICE, Set.of(), Step.CUSTODY, Set.of(Step.NOTICE), Step.RECEIVE, Set.of(Step.CUSTODY),
          Step.REGISTER_TITLE, Set.of(Step.RECEIVE), Step.RELEASE_QUALITY, Set.of(Step.RECEIVE)));

  /** 历史钢卷汽运车次对应的固定模板版本。 */
  public static final WarehouseFlowTemplate COIL_DISPATCH_V1 = new WarehouseFlowTemplate(
      "coil-road-dispatch", 1, Map.ofEntries(
          Map.entry(Step.NOTICE, Set.of()), Map.entry(Step.AUTHORIZATION, Set.of(Step.NOTICE)),
          Map.entry(Step.TRIP, Set.of(Step.AUTHORIZATION)), Map.entry(Step.ALLOCATION, Set.of(Step.TRIP)),
          Map.entry(Step.VEHICLE_LIMITS, Set.of(Step.TRIP)),
          Map.entry(Step.PRE_RESERVE, Set.of(Step.AUTHORIZATION)), Map.entry(Step.CLOSE, Set.of(Step.AUTHORIZATION)),
          Map.entry(Step.CANCEL, Set.of(Step.ALLOCATION)), Map.entry(Step.WEIGHING, Set.of(Step.ALLOCATION)),
          Map.entry(Step.HANDOVER, Set.of(Step.WEIGHING)),
          Map.entry(Step.RETURN_ACCEPT, Set.of(Step.HANDOVER)), Map.entry(Step.REVERSAL, Set.of(Step.HANDOVER)),
          Map.entry(Step.RETURN_DISPATCH, Set.of(Step.HANDOVER)), Map.entry(Step.RETURN_RECEIVE, Set.of(Step.HANDOVER)),
          Map.entry(Step.RETURN_REROUTE, Set.of(Step.RETURN_DISPATCH)),
          Map.entry(Step.LOSS_SETTLE, Set.of(Step.RETURN_DISPATCH)), Map.entry(Step.LOSS_REOPEN, Set.of(Step.LOSS_SETTLE)),
          Map.entry(Step.RETURN_QUALITY, Set.of(Step.RETURN_RECEIVE))));

  /** 接管前置沿用原流程，实收同时明确要求已签发通知。 */
  public static final WarehouseFlowTemplate RECEIVING_V2 = RECEIVING_V1.requiring(2, Step.RECEIVE, Step.NOTICE);

  /** 交接同时要求真实配货和有效复磅。 */
  public static final WarehouseFlowTemplate COIL_DISPATCH_V2 =
      COIL_DISPATCH_V1.requiring(2, Step.HANDOVER, Step.ALLOCATION);

  /** 现有进仓与钢卷汽运流程采用的固定发布目录。 */
  public static final WarehouseFlowCatalog STEEL_CATALOG = new WarehouseFlowCatalog(List.of(
      new WarehouseFlowCatalog.Policy("receiving", null, RECEIVING_V2),
      new WarehouseFlowCatalog.Policy("dispatch", "road-coil", COIL_DISPATCH_V2)),
      List.of(RECEIVING_V1, COIL_DISPATCH_V1, RECEIVING_V2, COIL_DISPATCH_V2));

  private final String code;
  private final int version;
  private final Map<Step, Set<Step>> requirements;

  private WarehouseFlowTemplate(String code, int version, Map<Step, Set<Step>> requirements) {
    this.code = code;
    this.version = version;
    this.requirements = requirements;
  }

  /**
   * @param nextVersion 新发布版本，必须大于当前版本
   * @param step 被收紧的已登记步骤
   * @param prerequisite 新增的前置步骤，必须已登记，且不能形成循环
   * @return 保留原约束并增加前置步骤的新模板，原版本继续可用
   * @throws IllegalArgumentException 版本或步骤依赖无效时抛出
   */
  public WarehouseFlowTemplate requiring(int nextVersion, Step step, Step prerequisite) {
    if (nextVersion <= version || !requirements.containsKey(step) || !requirements.containsKey(prerequisite)
        || dependsOn(prerequisite, step)) {
      throw new IllegalArgumentException("流程版本或步骤依赖无效");
    }
    var updated = new java.util.EnumMap<Step, Set<Step>>(requirements);
    var required = new java.util.HashSet<>(requirements.get(step));
    required.add(prerequisite);
    updated.put(step, Set.copyOf(required));
    return new WarehouseFlowTemplate(code, nextVersion, Map.copyOf(updated));
  }

  private boolean dependsOn(Step start, Step target) {
    return start == target || requirements.get(start).stream().anyMatch(step -> dependsOn(step, target));
  }

  /** @return 稳定模板编码 */
  public String code() { return code; }

  /** @return 已绑定规则版本，从 1 开始 */
  public int version() { return version; }

  /**
   * @param step 待执行的业务步骤
   * @param confirmed 已确认且当前仍有效的前置事实
   * @return 模板是否登记该步骤且所有前置事实已满足
   */
  public boolean permits(Step step, Set<Step> confirmed) {
    var required = requirements.get(step);
    return required != null && confirmed.containsAll(required);
  }
}
