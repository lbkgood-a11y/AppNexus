package com.appnexus.bulkwarehousing.api;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 整卷重量配货用例，数量一律使用通知的库存基础重量单位。 */
public interface CoilOutbound {
  /** 经岗位核验一条实收范围恰为一个整卷。serial 为仓内稳定卷号，最多 64 字符。 */
  record Coil(String receiptId,String serial,String unitCode) {}
  /** 通知范围固定到仓库、货主及主数据库存核算身份。 */
  record Notice(String id,String warehouseId,String ownerId,InboundReceiptReferences.Item item,
                String unitId,String unitCode,BigDecimal cap) {}
  /** 独立签发的提货授权，继承通知货物范围，截止时间为 UTC。 */
  record Authorization(String id,String noticeId,String pickupPartnerId,Instant expiresAt,BigDecimal cap) {}
  /** 车次内的授权配额行。
   * @param vehicleNumber 已核验车辆业务号码
   * @param recoveryReceiptId 仅撤配续办时传入的原卷库存 UUID；普通建车为空
   */
  record Trip(String id,String authorizationId,String vehicleNumber,BigDecimal cap,String recoveryReceiptId) {
    /** 普通建车保留原调用契约；续办建车额外绑定已撤配的原卷库存 UUID。 */
    public Trip(String id,String authorizationId,String vehicleNumber,BigDecimal cap) {
      this(id,authorizationId,vehicleNumber,cap,null);
    }
  }
  /** 修改该授权的未派车预留。expected 保证并发时旧请求无法覆盖新值。 */
  record PreReserve(String authorizationId,BigDecimal expected,BigDecimal quantity) {}
  /** 原授权标识，关闭前须完成所有车次撤配。 */
  record CloseAuthorization(String authorizationId) {}
  /** 一次配货的稳定请求键、车次及实物身份。 */
  record Allocate(String id,String tripId,String receiptId) {}
  /** 按原卷和授权投影的历史行为与所属车次的当前额度，数量使用通知单位。 */
  record RecoveryEvent(String tripId,String vehicleNumber,BigDecimal cap,BigDecimal reserved,BigDecimal issued,
                       String kind,String id,String evidenceId,String occurredAt) {}
  /** 原服务根据已提交的事实计算续办下一步；标识均指已保存业务对象。 */
  enum RecoveryStep { ALLOCATE, VEHICLE, WEIGH, HANDOVER }
  /** 当前续办车次，尚未配货或复磅时相应业务标识为空。 */
  record RecoveryTrip(String tripId,String vehicleNumber,String allocationId,String reviewId,RecoveryStep nextStep) {}
  /** 余额列表按原授权返回已验证的返仓原卷。已结束的车次只提供追溯入口。
   * @param tripReserved 该条入口所属车次的当前占用，使用授权的库存基础重量单位
   * @param tripIssued 同一车次的当前已出量，使用相同单位
   * @param replacementRequired 当前可按原授权建立替代车次，且没有在办车次
   */
  record RecoveryEntry(String receiptId,String serial,String transitId,boolean executable,String tripId,String vehicleNumber,
                       BigDecimal tripReserved,BigDecimal tripIssued,boolean replacementRequired) {}
  /** 按库存身份续查；没有下一页时游标为空。 */
  record RecoveryPage(List<RecoveryEntry> rows,String nextAfterId) {}
  /** @param c 可信租户主体 @param authorizationId 已授权提货单 UUID
   * @param afterId 可选，上一页最后一个实际可读的库存 UUID @return 可续办或仅追溯的原卷
   */
  RecoveryPage recoveryEntries(VerifiedTenantContext c,String authorizationId,String afterId);

  /** 原授权上下文及当前是否可建立替代车次；冻结仍由实时库存门禁控制。 */
  record AuthorizationRecovery(String noticeId,String authorizationId,String pickupPartnerId,boolean canPlan,
                               List<RecoveryEvent> events,RecoveryTrip activeTrip) {}
  /** @param c 已校验租户主体 @param authorizationId 原授权 UUID @param receiptId 原卷库存 UUID
   * @return 已授权的出仓历史和续办上下文 */
  AuthorizationRecovery authorizationRecovery(VerifiedTenantContext c,String authorizationId,String receiptId);

  /** 当前各级占用和剩余可配量，已确认出库量已在公式内扣除。 */
  record Balance(BigDecimal reserved,BigDecimal remaining) {}
  /** 已提交的配货以及从权威库存取得的卷号与重量。 */
  record Allocation(String id,String tripId,String receiptId,String serial,BigDecimal quantity,
                    Balance notice,Balance authorization,Balance trip) {}

  /** 经核验的车辆质量限值。两项使用通知基础单位，证据 UUID 指向现场采用凭证。 */
  record VehicleLimits(String tripId,String vehicleNumber,String unitId,String evidenceId,
                       BigDecimal payloadLimit,BigDecimal grossLimit) {}
  /** 有效磅单对当前配货明细的实测分摊。 */
  record WeighedCoil(String allocationId,BigDecimal quantity) {}
  /** 复核单 UUID 同时用作幂等键，毛重及皮重使用同一库存基础单位。 */
  record Weighing(String id,String tripId,String ticketId,String vehicleNumber,String unitId,
                  BigDecimal gross,BigDecimal tare,List<WeighedCoil> coils) {}
  /** 已登记的复核结论。HELD 时有效占用和卷锁保持原样。 */
  enum ReviewState { APPROVED, HELD }
  /** 独立的车辆载货、总重和三级额度校验结果。 */
  record Review(String id,String tripId,ReviewState state,BigDecimal net,List<String> reasons,long loadVersion) {}

  /** 受控门岗签署的实际交接。凭证标识、车辆、提货主体和实物均参与最终复核。 */
  record Handover(String id,String tripId,String reviewId,String evidenceId,String vehicleNumber,
                  String pickupPartnerId,List<String> receiptIds) {}
  /** 已记账的交出结果，核销实重与库存账面扣减分别表达。 */
  record Dispatch(String id,String tripId,BigDecimal issuedQuantity,BigDecimal stockDeduction,String unitId) {}

  /** 冲销原因，决定是否恢复原车次占用。 */
  enum ReversalKind { MISTAKEN_HANDOVER, ACTUAL_RETURN }
  /** 实际返仓的卷与库位。异库位或跨仓返仓须给出实际接收位置及各级主数据版本。 */
  record ReturnedCoil(String receiptId,String locationId,InboundReceiptReferences.Position position) {
    /** @param receiptId 库存身份 @param locationId 使用原位置时可沿用原库位 UUID */
    public ReturnedCoil(String receiptId,String locationId) {
      this(receiptId,locationId,null);
    }
  }
  /** 真实退回的接管、权利和质量凭证，须覆盖原交接的每个卷号。 */
  record AcceptedReturn(String id,String handoverId,String custodyEvidenceId,String titleEvidenceId,
                        String qualityEvidenceId,List<ReturnedCoil> coils) {}
  /** 原交接的整车冲销。真实退回必须引用已独立登记的退回验收。 */
  record ReverseHandover(String id,String handoverId,ReversalKind kind,String evidenceId,String returnId) {}
  /** 补偿确认，保留实测核销、账面恢复和原占用恢复三个不同口径。 */
  record Reversal(String id,String handoverId,ReversalKind kind,BigDecimal restoredQuota,
                  BigDecimal restoredStock,BigDecimal restoredReservation,String unitId) {}

  /** 按卷实际退回，数量从原出仓明细读取。接管及权利凭证由授权岗位核验，返仓先入待检。 */
  record PartialReturn(String id,String handoverId,String custodyEvidenceId,
                       String titleEvidenceId,List<ReturnedCoil> coils,String transitId) {
    /** 原直接接管格式。未曾登记在途的原卷可继续使用。 */
    public PartialReturn(String id,String handoverId,String custodyEvidenceId,String titleEvidenceId,List<ReturnedCoil> coils) {
      this(id,handoverId,custodyEvidenceId,titleEvidenceId,coils,null);
    }
  }
  /** 跨仓实际发运凭证。指定原交接、实际发运卷、计划接收仓和发运依据。 */
  record ReturnDispatch(String id,String handoverId,String receivingWarehouseId,String evidenceId,List<String> receiptIds) {}
  /** 发运卷当前处置状态。 */
  enum TransitState { IN_TRANSIT, RECEIVED, LOSS_SETTLED }
  /** 在途卷的原重量、当前状态。 接管批次与活动短损编号互斥。 */
  record TransitCoil(String receiptId,String serial,BigDecimal bookQuantity,BigDecimal measuredQuantity,
                    String receivedReturnId,String lossId,TransitState state,String receivingWarehouseId,String rerouteId) {}
  /** 原发运计划及逐卷接管进度。头部接收仓保留原计划，卷上的接收仓反映当前路径。 */
  record ReturnTransit(String id,String handoverId,String sourceWarehouseId,String receivingWarehouseId,
                       String unitId,List<TransitCoil> coils,String evidenceId,List<TransitEvidence> history,
                       ReturnQuota noticeQuota,ReturnQuota authorizationQuota,ReturnQuota tripQuota) {}
  /** 返仓确认行仅展示已批准的接管事实，不读取当前库存余额。 */
  record ReturnDocumentLine(String receiptId,String warehouseId,String transitId,String qualityEvidenceId,BigDecimal quantity) {}

  /** 已确认的返仓业务主单与原接管明细。 */
  record ReturnDocument(PartialReturnResult header,List<ReturnDocumentLine> lines) {}

  /** @param c 可信租户成员 @param id 返仓确认 UUID @return 经原出仓及接收仓权限复核的确认单 */
  ReturnDocument returnDocument(VerifiedTenantContext c,String id);

  /** 原单据额度快照，当前操作员另有授权余额读取权时提供。 */
  record ReturnQuota(String id,BigDecimal cap,BigDecimal reserved,BigDecimal issued,BigDecimal remaining) {}
  /** 逐卷实际处置凭证，来源及目标仓在返回前校验。 */
  record TransitEvidence(String kind,String id,String receiptId,String evidenceId,String titleEvidenceId,
                         String previousWarehouseId,String warehouseId,String reason,String qualityEvidenceId) {}

  /** 负责短损的业务伙伴 UUID、批准凭证 UUID 及经复核的原因，原因最长 256 字符。 */
  record SettleTransitLoss(String id,String transitId,String receiptId,String responsiblePartnerId,
                           String evidenceId,String reason) {}
  /** 已结清的短损及原账面、实测重量，仅作追溯。 */
  record TransitLoss(String id,String transitId,String receiptId,String responsiblePartnerId,String evidenceId,
                     String reason,BigDecimal bookQuantity,BigDecimal measuredQuantity) {}
  /** 补到后批准重开的原短损 UUID 及补到核实凭证。 */
  record ReopenTransitLoss(String id,String transitId,String lossId,String evidenceId) {}

  /** 将一卷从当前计划接收仓改至同租户另一仓。改仓原因最长 256 字符。 */
  record RerouteTransitCoil(String id,String transitId,String receiptId,String expectedReceivingWarehouseId,
                           String newReceivingWarehouseId,String evidenceId,String reason) {}

  /** @param c 可信改仓审批岗位 @param command 当前仓和新仓及批准依据 @return 已登记的改仓记录 */
  RerouteTransitCoil rerouteTransitCoil(VerifiedTenantContext c,RerouteTransitCoil command);

  /** @param c 可信短损审批岗位 @param command 原发运卷及责任凭证 @return 短损结清 */
  TransitLoss settleTransitLoss(VerifiedTenantContext c,SettleTransitLoss command);
  /** @param c 可信补到复核岗位 @param command 原短损及批准重开凭证 */
  void reopenTransitLoss(VerifiedTenantContext c,ReopenTransitLoss command);

  /** 原交接实际已出且可登记发运的整卷，数量以发运原单位表示。 */
  record ReturnCandidate(String receiptId,String serial,BigDecimal bookQuantity,BigDecimal measuredQuantity) {}
  /** 用原交接准备新发运，实际接收仓和发运凭证仍由岗位确认。 */
  record ReturnPreparation(String handoverId,String sourceWarehouseId,List<ReturnCandidate> coils) {}

  /** @param c 可信发运主体 @param handoverId 原交接 UUID @return 当前仍可登记发运的原卷 */
  ReturnPreparation prepareReturn(VerifiedTenantContext c,String handoverId);

  /** @param c 可信发运岗位 @param dispatch 经核验的实际发运凭证 @return 在途清单 */
  ReturnTransit dispatchReturn(VerifiedTenantContext c,ReturnDispatch dispatch);
  /** @param c 可信主体 @param id 原发运 UUID @return 原发运及各卷当前接管进度 */
  ReturnTransit returnTransit(VerifiedTenantContext c,String id);
  /** 一批退回的实测冲回和账面恢复。重量使用原交接基础单位。 */
  record PartialReturnResult(String id,String handoverId,BigDecimal restoredQuota,BigDecimal restoredStock,
                             String unitId) {}
  /** 授权质检岗位按批及卷确认返仓质检，引用新的合格证据。 */
  record ReturnedQuality(String returnId,String receiptId,String evidenceId) {}

  /** @param c 可信退回岗位 @param request 实际复接管的部分原卷 @return 已过账的本批退回 */
  PartialReturnResult returnCoils(VerifiedTenantContext c,PartialReturn request);
  /** @param c 可信质检岗位 @param request 本次返仓卷的新质检证据 */
  void releaseReturnedQuality(VerifiedTenantContext c,ReturnedQuality request);

  /** @param c 可信退回验收岗位 @param request 已核实实际退回、质检和权利的整卷明细 */
  void acceptReturn(VerifiedTenantContext c,AcceptedReturn request);
  /** @param c 可信冲销批准人 @param request 原交接与经核验的冲销依据 @return 已提交补偿 */
  Reversal reverseHandover(VerifiedTenantContext c,ReverseHandover request);

  /** @param c 可信门岗账号 @param handover 受控交接证据 @return 原子提交的交出确认 */
  Dispatch handover(VerifiedTenantContext c,Handover handover);

  /** @param c 可信核验岗位 @param limits 实际车辆限值及权威凭证 */
  void verifyVehicleLimits(VerifiedTenantContext c,VehicleLimits limits);
  /** @param c 可信计量复核岗位 @param weighing 最终称量和各卷分摊 @return 已提交的复核决定 */
  Review reviewWeighing(VerifiedTenantContext c,Weighing weighing);

  /** @param c 可信主体 @param coil 已核验整卷身份 */
  void identify(VerifiedTenantContext c,Coil coil);
  /** @param c 可信主体 @param notice 已批准出仓范围及上限 */
  void issue(VerifiedTenantContext c,Notice notice);
  /** @param c 可信主体 @param authorization 提货人、范围及额度 */
  void authorize(VerifiedTenantContext c,Authorization authorization);
  /** @param c 可信主体 @param trip 授权下的车次及作业上限 */
  void plan(VerifiedTenantContext c,Trip trip);
  /** @param c 可信主体 @param reservation 预留的原值及新值 */
  void preReserve(VerifiedTenantContext c,PreReserve reservation);
  /** @param c 可信主体 @param command 关闭授权并成对释放未用承诺 */
  void close(VerifiedTenantContext c,CloseAuthorization command);
  /** @param c 可信主体 @param command 整卷配货 @return 权威占用和额度 */
  Allocation allocate(VerifiedTenantContext c,Allocate command);
  /** @param c 可信主体 @param command 原配货范围，经现场确认可撤配 */
  void cancel(VerifiedTenantContext c,Allocate command);
}
