package com.appnexus.bulkwarehousing.application;

import com.appnexus.bulkwarehousing.api.CoilOutbound.*;
import com.appnexus.bulkwarehousing.api.InboundReceiptReferences.Position;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.util.List;

/** 整卷配货事务存储端口。栅栏优先于通知、授权、车次和库存的锁。 */
public interface CoilOutboundStore {
  /** 额度已出、占用和上限。 */
  record Quota(BigDecimal cap,BigDecimal issued,BigDecimal reserved) {
    /** @return 本级剩余可配量 */
    public BigDecimal remaining() { return cap.subtract(issued).subtract(reserved); }
    /** @return 当前额度投影 @param delta 本次占用增量 */
    public Balance after(BigDecimal delta) {
      return new Balance(reserved.add(delta).stripTrailingZeros(),remaining().subtract(delta).stripTrailingZeros());
    }
  }
  /** 通知范围及已签发的子授权额度。 */
  record NoticeState(String id,String warehouseId,String ownerId,String productId,String identityId,
                     String unitId,Quota quota,BigDecimal committed) {}
  /** 授权及未派车预留；有效期按数据库当前时点计算。 */
  record AuthorizationState(String id,String noticeId,String pickupPartnerId,boolean valid,boolean closed,Quota quota,BigDecimal unassigned,long titleWatermark) {}
  /** 单个授权下的车次配额。 */
  record TripState(String id,String authorizationId,String vehicleNumber,Quota quota,long loadVersion,
                   String reviewId,String limitsEvidenceId,BigDecimal payloadLimit,BigDecimal grossLimit,boolean retired,String recoveryReceiptId,boolean recoveryClosed) {}
  /** 复核历史及其不可变请求摘要。 */
  record RecordedReview(Review result,String digest,BigDecimal gross) {}
  /** 配货记录，关联唯一实物占用。 */
  record Allocated(String id,String tripId,String receiptId,String serial,BigDecimal quantity,boolean released) {}

  /** 原交接凭证和请求摘要，可按业务键安全重放。 */
  record PostedDispatch(Dispatch result,String digest,boolean reversed) {}
  /** 装车配货、账面重量与实发重量的映射。 */
  record DispatchedCoil(Allocated allocation,BigDecimal bookQuantity,String sourceWarehouseId,String sourceLocationId) {}

  /** 读取已记账交接。 */
  PostedDispatch dispatch(VerifiedTenantContext c,String id);
  /** 同事务保存确认、实物和权利扣减及三级额度核销。 */
  void dispatch(VerifiedTenantContext c,Handover request,Dispatch result,String digest,
      NoticeState notice,AuthorizationState authorization,List<DispatchedCoil> coils);

  /** 同仓同车存在另一有货车次时，禁止分拆同一车辆的限载口径。 */
  boolean otherVehicleLoad(VerifiedTenantContext c,String warehouseId,TripState trip);
  /** 发运清单的不可变归属及请求摘要。 */
  record Transit(String id,String handoverId,String warehouseId,String evidenceId,String digest) {}
  /** 原交接卷对应的在途归属，其接管批次为空时尚未核销。 */
  record TransitLink(String transitId,String receivedReturnId,String lossId,String receivingWarehouseId,String rerouteId) {}
  /** 历史改仓及重放摘要。 */
  record PostedReroute(RerouteTransitCoil result,String digest) {}

  /** 读取原改仓事实。 */
  PostedReroute transitReroute(VerifiedTenantContext c,String id);
  /** 同事务追加已批准依据与更新该卷当前接收仓。 */
  void rerouteTransitCoil(VerifiedTenantContext c,RerouteTransitCoil request,String digest,String expectedRerouteId);

  /** 读取原发运头。 */
  Transit transit(VerifiedTenantContext c,String id);
  /** 读取整票发运的逐卷实际处置历史。 */
  List<TransitEvidence> transitEvidence(VerifiedTenantContext c,String id);
  /** 查在途卷及接管批次。 */
  List<TransitCoil> transitCoils(VerifiedTenantContext c,String id);
  /** 根据原交接和卷查发运归属。 */
  TransitLink transitLink(VerifiedTenantContext c,String handoverId,String receiptId);
  /** 判断原交接是否已有在途管理，禁止改走整车旧流程。 */
  boolean hasTransit(VerifiedTenantContext c,String handoverId);
  /** 登记实际发运，全量保留原实发账。 */
  void dispatchReturn(VerifiedTenantContext c,ReturnDispatch request,String digest,List<IssuedCoil> coils);

  /** 已记载短损及稳定请求摘要。 */
  record PostedLoss(TransitLoss result,String digest) {}
  /** 原短损的补到重开关联。 */
  record PostedReopening(String lossId,String transitId,String evidenceId) {}

  /** 读取已批准短损。 */
  PostedLoss transitLoss(VerifiedTenantContext c,String id);
  /** 附加短损证据并关闭对应在途卷，不进行任何库存或额度恢复。 */
  void settleTransitLoss(VerifiedTenantContext c,TransitLoss loss,String digest);
  /** 查询已经批准的补到重开。 */
  PostedReopening transitReopening(VerifiedTenantContext c,String id);
  /** 保留原短损历史，记录批准依据并恢复待接管状态。 */
  void reopenTransitLoss(VerifiedTenantContext c,ReopenTransitLoss command,String receiptId);

  /** 当前已冲回的批次及摘要。 */
  record PostedPartialReturn(PartialReturnResult result,String digest) {}
  /** 返仓卷账面量及新质量凭证。 */
  record ReturnedStock(BigDecimal book,String qualityEvidenceId,String warehouseId,String locationId) {}

  /** 查询原批次，用于重放与质检。 */
  PostedPartialReturn partialReturn(VerifiedTenantContext c,String id);
  /** 按旧式整车退回编号读取确认结果，缺失返回空。 */
  PartialReturnResult legacyReturnDocument(VerifiedTenantContext c,String id);

  /** 读取确认单的全部接收仓及接管行，用于复用原返仓准入。 */
  List<ReturnDocumentLine> returnDocumentLines(VerifiedTenantContext c,String id);

  /** 查询原交接尚未冲回的卷。 */
  List<IssuedCoil> activeIssuedCoils(VerifiedTenantContext c,String handoverId);
  /** 原交接行与经主数据解析的实际返仓位置。 */
  record ReturnedLine(IssuedCoil issued,Position destination) {}
  /** 按实际卷同步恢复实物、货权和三级额度。 */
  void returnCoils(VerifiedTenantContext c,PartialReturn request,PartialReturnResult result,String digest,
      NoticeState notice,AuthorizationState authorization,TripState trip,List<ReturnedLine> coils);
  /** 按返仓批次和卷定位待检依据。 */
  ReturnedStock returnedStock(VerifiedTenantContext c,String returnId,String receiptId);
  /** 记录新的合格证据并转出相同范围的待检量。 */
  void releaseReturnedQuality(VerifiedTenantContext c,ReturnedQuality request,ReturnedStock stock);

  /** 原交接明细，两个重量从已过账流水读取。 */
  record IssuedCoil(String allocationId,String receiptId,BigDecimal issued,BigDecimal book) {}
  /** 保留已经独立接管验收的原交接关联。 */
  record ReturnEvidence(String handoverId,String digest) {}
  /** 原冲销及稳定请求摘要。 */
  record PostedReversal(Reversal result,String digest) {}

  /** 读取原交接全部流水，按库存身份排序。 */
  List<IssuedCoil> issuedCoils(VerifiedTenantContext c,String handoverId);
  /** 查询已经登记的退回接收。 */
  ReturnEvidence acceptedReturn(VerifiedTenantContext c,String id);
  /** 附加退回凭证及逐卷接管证据。 */
  void acceptReturn(VerifiedTenantContext c,AcceptedReturn request,String digest);
  /** 根据当前出仓凭证查已生效的退回接收编号。 */
  String acceptedReturnFor(VerifiedTenantContext c,String handoverId);
  /** 根据补偿编号读取历史结果。 */
  PostedReversal reversal(VerifiedTenantContext c,String id);
  /** 同事务恢复库存、权利及三层额度，并记录补偿。 */
  void reverse(VerifiedTenantContext c,ReverseHandover request,Reversal result,String digest,
      NoticeState notice,AuthorizationState authorization,TripState trip,List<IssuedCoil> coils);

  /** 唯一库存与历史返仓发运关联，出域前必须复核实时权限。 */
  record RecoveryOrigin(String receiptId,String transitId) {}
  /** @param limit 授权内原卷的候选批次大小，服务端固定，按库存 UUID 续查 */
  List<RecoveryOrigin> recoveryOrigins(VerifiedTenantContext c,String authorizationId,String warehouseId,String afterId,int limit);

  /** 按已受控锁定的授权与库存身份读取车次、配货、复磅和交接历史。 */
  List<RecoveryEvent> recoveryEvents(VerifiedTenantContext c,String authorizationId,String receiptId);

  /** 在仓控制锁内查找该授权和原卷尚可执行的续办车次。 */
  String activeRecoveryTrip(VerifiedTenantContext c,String authorizationId,String receiptId);
  /** 撤配时结束本车次的续办关联，原记录保留以供追溯。 */
  void closeRecoveryTrip(VerifiedTenantContext c,String tripId);

  /** 当前仍有效的配货集合，顺序由库存身份确定。 */
  List<Allocated> activeAllocations(VerifiedTenantContext c,String tripId);
  /** 作业货物发生改变，既有复核不能再用于交接。 */
  void invalidateReview(VerifiedTenantContext c,String tripId);
  /** 保存已核验限值及依据，并推进复核版本。 */
  void vehicleLimits(VerifiedTenantContext c,VehicleLimits limits);
  /** 按复核业务键读取已保存结论。 */
  RecordedReview review(VerifiedTenantContext c,String id);
  /** 保存复核报告及分摊，并设置车次当前复核。 */
  void review(VerifiedTenantContext c,Weighing input,Review result,String digest);
  /** 已批准分摊替换配货有效吨额，原始配货计划重量保留。 */
  void applyMeasured(VerifiedTenantContext c,WeighedCoil coil);

  /** 懒建立并获取该仓控制栅栏。 */
  void guard(VerifiedTenantContext c,String warehouseId);
  /** 不可变的父引用。 */
  record Links(String noticeId,String authorizationId,String warehouseId) {}
  /** 获取额度链父引用用于排序加锁。 */
  Links links(VerifiedTenantContext c,String level,String id);
  /** 按受信租户及 ID 锁定父通知。 */
  NoticeState notice(VerifiedTenantContext c,String id);
  /** 按受信租户及 ID 锁定授权。 */
  AuthorizationState authorization(VerifiedTenantContext c,String id);
  /** 按受信租户及 ID 锁定车次。 */
  TripState trip(VerifiedTenantContext c,String id);
  /** 读取货物稳定卷号。 */
  String serial(VerifiedTenantContext c,String receiptId);
  /** 写入经岗位核验的仓内唯一卷号。 */
  void identify(VerifiedTenantContext c,String warehouseId,Coil coil);
  /** 签发已校验通知。 */
  /** @param c 可信租户 @param id 通知标识 @return 已签发通知的固定流程版本 */
  int flowVersion(VerifiedTenantContext c,String id);

  void issue(VerifiedTenantContext c,Notice notice,int flowVersion);
  /** 签发授权并增加父通知已签承诺。 */
  void authorize(VerifiedTenantContext c,Authorization authorization);
  /** 关闭授权并回收剩余承诺。 */
  void close(VerifiedTenantContext c,AuthorizationState authorization);
  /** 建立车次配额。 */
  void plan(VerifiedTenantContext c,Trip trip);
  /** 同事务修改三个层级的占用及未派车额。 */
  void adjust(VerifiedTenantContext c,String noticeId,String authorizationId,String tripId,
      BigDecimal parentDelta,BigDecimal tripDelta,BigDecimal unassignedDelta);
  /** 获取原配货用于重放及撤配。 */
  Allocated allocated(VerifiedTenantContext c,String id);
  /** 写入卷号占用对应的配货明细。 */
  void allocate(VerifiedTenantContext c,Allocate request,String serial,BigDecimal quantity);
}
