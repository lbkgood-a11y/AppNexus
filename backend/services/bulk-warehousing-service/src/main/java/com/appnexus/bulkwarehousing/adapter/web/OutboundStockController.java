package com.appnexus.bulkwarehousing.adapter.web;

import com.appnexus.bulkwarehousing.api.OutboundStock;
import com.appnexus.bulkwarehousing.api.CoilOutbound;
import com.appnexus.bulkwarehousing.api.CoilTitleTransfer;
import com.appnexus.bulkwarehousing.api.WarehouseBalances;
import java.util.List;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.contracts.operation.OperationBinding;
import com.appnexus.masterdata.api.masterdata.MasterData;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 第一方库存校验、占用及监管命令，仅消费宿主安全上下文。 */
@RestController
public final class OutboundStockController {
  private final OutboundStock stock;
  private final CoilOutbound coils;
  private final CoilTitleTransfer titles;
  private final WarehouseBalances balances;

  /** @param stock 统一权限下的库存门禁 @param coils 三级额度及整卷配货入口 @param titles 整卷过户 @param balances 两类余额读取 */
  public OutboundStockController(OutboundStock stock,CoilOutbound coils,CoilTitleTransfer titles,WarehouseBalances balances) {
    this.stock=stock;
    this.coils=coils;
    this.titles=titles;
    this.balances=balances;
  }

  /** @param id 已过账库存身份 UUID @return 当前可发条件，不产生占用 */
  @GetMapping("/api/bulk-outbound-stock/{id}/eligibility")
  @OperationBinding(id="BulkOutboundStock.check",reference=true)
  public OutboundStock.Gate check(HttpServletRequest request,@PathVariable String id) {
    return stock.check(context(request),id);
  }

  /** @param hold 当前配货编号及库存身份 @return 提交后的占用 */
  @PostMapping("/api/bulk-stock-occupations")
  @OperationBinding(id="BulkOutboundStock.reserve",reference=true)
  public OutboundStock.Reservation reserve(HttpServletRequest request,@RequestBody OutboundStock.Hold hold) {
    return stock.reserve(context(request),hold);
  }

  /** @param hold 原占用编号及库存身份 */
  @PostMapping("/api/bulk-stock-occupations:release")
  @OperationBinding(id="BulkOutboundStock.release",reference=true)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void release(HttpServletRequest request,@RequestBody OutboundStock.Hold hold) {
    stock.release(context(request),hold);
  }

  /** @param id 已过账的库存身份 UUID @return 该库存身份的监管凭证及审计 */
  @GetMapping("/api/bulk-stock-restrictions/by-receipt/{id}")
  @OperationBinding(id="BulkStockRestriction.read",reference=true)
  public List<OutboundStock.RestrictionEvidence> restrictionHistory(HttpServletRequest request,@PathVariable String id) {
    return stock.restrictionHistory(context(request),id);
  }

  /** @param freeze 已核验的冻结凭证 */
  @PostMapping("/api/bulk-stock-restrictions")
  @OperationBinding(id="BulkStockRestriction.freeze",reference=true)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void freeze(HttpServletRequest request,@RequestBody OutboundStock.Freeze freeze) {
    stock.freeze(context(request),freeze);
  }

  /** @param freeze 原冻结及解除依据 */
  @PostMapping("/api/bulk-stock-restrictions:lift")
  @OperationBinding(id="BulkStockRestriction.lift",reference=true)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void lift(HttpServletRequest request,@RequestBody OutboundStock.Freeze freeze) {
    stock.lift(context(request),freeze);
  }

  /** @param command 核验的卷号 */
  @PostMapping("/api/bulk-stock-coils")
  @OperationBinding(id="BulkCoilOutbound.identify",reference=true)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void identify(HttpServletRequest request,@RequestBody CoilOutbound.Coil command) {
    coils.identify(context(request),command);
  }

  /** @param command 批准的重量通知 */
  @PostMapping("/api/bulk-outbound-notices")
  @OperationBinding(id="BulkCoilOutbound.issue",reference=true)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void issue(HttpServletRequest request,@RequestBody CoilOutbound.Notice command) {
    coils.issue(context(request),command);
  }

  /** @param command 提货授权 */
  @PostMapping("/api/bulk-pickup-authorizations")
  @OperationBinding(id="BulkCoilOutbound.authorize",reference=true)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void authorize(HttpServletRequest request,@RequestBody CoilOutbound.Authorization command) {
    coils.authorize(context(request),command);
  }

  /** @param command 车次配额 */
  @PostMapping("/api/bulk-outbound-trips")
  @OperationBinding(id="BulkCoilOutbound.plan",reference=true)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void plan(HttpServletRequest request,@RequestBody CoilOutbound.Trip command) {
    coils.plan(context(request),command);
  }

  /** @param command 授权未派车占用 */
  @PostMapping("/api/bulk-pickup-authorizations:pre-reserve")
  @OperationBinding(id="BulkCoilOutbound.pre_reserve",reference=true)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void preReserve(HttpServletRequest request,@RequestBody CoilOutbound.PreReserve command) {
    coils.preReserve(context(request),command);
  }

  /** @param request 经安全链请求 @param command 原授权 UUID */
  @PostMapping("/api/bulk-pickup-authorizations:close")
  @OperationBinding(id="BulkCoilOutbound.close",reference=true)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void close(HttpServletRequest request,@RequestBody CoilOutbound.CloseAuthorization command) {
    coils.close(context(request),command);
  }

  /** @param command 配货范围 */
  @PostMapping("/api/bulk-coil-allocations")
  @OperationBinding(id="BulkCoilOutbound.allocate",reference=true)
  public CoilOutbound.Allocation allocate(HttpServletRequest request,@RequestBody CoilOutbound.Allocate command) {
    return coils.allocate(context(request),command);
  }

  /** @param command 原配货 */
  @PostMapping("/api/bulk-coil-allocations:cancel")
  @OperationBinding(id="BulkCoilOutbound.cancel",reference=true)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancel(HttpServletRequest request,@RequestBody CoilOutbound.Allocate command) {
    coils.cancel(context(request),command);
  }

  /** @param request 已认证请求 @param command 经核验的车辆载货与总重限制 */
  @PostMapping("/api/bulk-outbound-trips:verify-vehicle-limits")
  @OperationBinding(id="BulkCoilOutbound.vehicle_limits",reference=true)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void verifyVehicleLimits(HttpServletRequest request,@RequestBody CoilOutbound.VehicleLimits command) {
    coils.verifyVehicleLimits(context(request),command);
  }

  /** @param request 已认证请求 @param command 复磅及分摊证据 @return 已提交的复核结论 */
  @PostMapping("/api/bulk-outbound-trips:review-weighing")
  @OperationBinding(id="BulkCoilOutbound.review_weighing",reference=true)
  public CoilOutbound.Review reviewWeighing(HttpServletRequest request,@RequestBody CoilOutbound.Weighing command) {
    return coils.reviewWeighing(context(request),command);
  }

  /** @param request 已认证门岗请求 @param command 实际交接范围 @return 原子提交的交出确认 */
  @PostMapping("/api/bulk-outbound-trips:handover")
  @OperationBinding(id="BulkCoilOutbound.handover",reference=true)
  public CoilOutbound.Dispatch handover(HttpServletRequest request,@RequestBody CoilOutbound.Handover command) {
    return coils.handover(context(request),command);
  }

  /** @param request 已认证退回验收请求 @param command 已实际复接管的原卷及凭证 */
  @PostMapping("/api/bulk-coil-returns")
  @OperationBinding(id="BulkCoilOutbound.accept_return",reference=true)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void acceptReturn(HttpServletRequest request,@RequestBody CoilOutbound.AcceptedReturn command) {
    coils.acceptReturn(context(request),command);
  }

  /** @param request 已认证冲销请求 @param command 原交接及冲销凭证 @return 已提交补偿 */
  @PostMapping("/api/bulk-coil-handover-reversals")
  @OperationBinding(id="BulkCoilOutbound.reverse_handover",reference=true)
  public CoilOutbound.Reversal reverseHandover(HttpServletRequest request,@RequestBody CoilOutbound.ReverseHandover command) {
    return coils.reverseHandover(context(request),command);
  }

  /** @param request 已认证退回请求 @param command 本批实际退回整卷 @return 已记账恢复量 */
  @PostMapping("/api/bulk-coil-returns:post-coils")
  @OperationBinding(id="BulkCoilOutbound.return_coils",reference=true)
  public CoilOutbound.PartialReturnResult returnCoils(HttpServletRequest request,@RequestBody CoilOutbound.PartialReturn command) {
    return coils.returnCoils(context(request),command);
  }

  /** @param request 已认证质检请求 @param command 返仓卷合格依据 */
  @PostMapping("/api/bulk-coil-returns:release-quality")
  @OperationBinding(id="BulkCoilOutbound.release_returned_quality",reference=true)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void releaseReturnedQuality(HttpServletRequest request,@RequestBody CoilOutbound.ReturnedQuality command) {
    coils.releaseReturnedQuality(context(request),command);
  }

  /** @param request 可信发运请求 @param command 实际发运范围 @return 在途清单 */
  @PostMapping("/api/bulk-coil-return-transits")
  @OperationBinding(id="BulkCoilOutbound.dispatch_return",reference=true)
  public CoilOutbound.ReturnTransit dispatchReturn(HttpServletRequest request,@RequestBody CoilOutbound.ReturnDispatch command) {
    return coils.dispatchReturn(context(request),command);
  }

  /** @param request 可信查询请求 @param id 在途批次 UUID @return 已接管及缺卷范围 */
  @GetMapping("/api/bulk-coil-return-transits/{id}")
  @OperationBinding(id="BulkCoilOutbound.read_return_transit",reference=true)
  public CoilOutbound.ReturnTransit returnTransit(HttpServletRequest request,@PathVariable String id) {
    return coils.returnTransit(context(request),id);
  }

  /** @param request 可信审批请求 @param command 短损责任与批准凭证 @return 已批准结清 */
  @PostMapping("/api/bulk-coil-return-transits:settle-loss")
  @OperationBinding(id="BulkCoilOutbound.settle_transit_loss",reference=true)
  public CoilOutbound.TransitLoss settleTransitLoss(HttpServletRequest request,@RequestBody CoilOutbound.SettleTransitLoss command) {
    return coils.settleTransitLoss(context(request),command);
  }

  /** @param request 可信复核请求 @param command 补到及重开凭证 */
  @PostMapping("/api/bulk-coil-return-transits:reopen-loss")
  @OperationBinding(id="BulkCoilOutbound.reopen_transit_loss",reference=true)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void reopenTransitLoss(HttpServletRequest request,@RequestBody CoilOutbound.ReopenTransitLoss command) {
    coils.reopenTransitLoss(context(request),command);
  }

  /** @param request 可信改仓审批请求 @param command 原在途卷及批准依据 @return 已记载改仓 */
  @PostMapping("/api/bulk-coil-return-transits:reroute-coil")
  @OperationBinding(id="BulkCoilOutbound.reroute_transit_coil",reference=true)
  public CoilOutbound.RerouteTransitCoil rerouteTransitCoil(HttpServletRequest request,
      @RequestBody CoilOutbound.RerouteTransitCoil command) {
    return coils.rerouteTransitCoil(context(request),command);
  }

  /** @param request 可信过户请求 @param command 已核验权利依据 @return 已提交货权划转 */
  @PostMapping("/api/bulk-coil-title-transfers")
  @OperationBinding(id="BulkCoilTitleTransfer.transfer",reference=true)
  public CoilTitleTransfer.Transfer transferTitle(HttpServletRequest request,@RequestBody CoilTitleTransfer.Command command) {
    return titles.transfer(context(request),command);
  }

  /** @param ownerId 货主 UUID @param warehouseId 实际库存仓 UUID @param productId 商品 UUID
   * @param identityId 可选品规 UUID @return 按库存单位的在库货权及可提量 */
  @GetMapping("/api/bulk-warehouse-stock-balances")
  @OperationBinding(id="BulkWarehouseStockBalance.read",reference=true)
  public List<WarehouseBalances.Physical> physicalBalances(HttpServletRequest request,
      @RequestParam String ownerId,@RequestParam String warehouseId,@RequestParam String productId,
      @RequestParam(required=false) String identityId) {
    return balances.physical(context(request),new WarehouseBalances.Scope(ownerId,warehouseId,productId,identityId));
  }

  /** @param ownerId 货主 UUID @param warehouseId 实际库存仓 UUID @param productId 商品 UUID
   * @param identityId 可选品规 UUID @param afterId 可选上一页最后库存 UUID
   * @param limit 1–100 行，默认 50 @return 可见整卷及过户所需的权利版本 */
  @GetMapping("/api/bulk-warehouse-stock-balances/coils")
  @OperationBinding(id="BulkWarehouseStockBalance.read_coils",reference=true)
  public WarehouseBalances.CoilPage coilBalances(HttpServletRequest request,
      @RequestParam String ownerId,@RequestParam String warehouseId,@RequestParam String productId,
      @RequestParam(required=false) String identityId,@RequestParam(required=false) String afterId,
      @RequestParam(defaultValue="50") int limit) {
    return balances.coils(context(request),new WarehouseBalances.Scope(ownerId,warehouseId,productId,identityId),
        afterId,limit);
  }

  /** @param id 提货授权 UUID @param afterId 可选库存游标 @return 实际授权下可续办或追溯的返仓卷 */
  @GetMapping("/api/bulk-pickup-authorizations/{id}/recovery-entries")
  @OperationBinding(id="BulkCoilOutbound.list_recovery_entries",reference=true)
  public CoilOutbound.RecoveryPage recoveryEntries(HttpServletRequest request,
      @PathVariable String id,@RequestParam(required=false) String afterId) {
    return coils.recoveryEntries(context(request),id,afterId);
  }

  /** @param id 已签发授权 UUID @param receiptId 所选原卷库存 UUID @return 受控续办上下文与车次历史 */
  @GetMapping("/api/bulk-pickup-authorizations/{id}/coil-recovery")
  @OperationBinding(id="BulkCoilOutbound.recover_authorization",reference=true)
  public CoilOutbound.AuthorizationRecovery authorizationRecovery(HttpServletRequest request,
      @PathVariable String id,@RequestParam String receiptId) {
    return coils.authorizationRecovery(context(request),id,receiptId);
  }

  /** @param ownerId 货主 UUID @param warehouseId 通知仓 UUID @param productId 商品 UUID
   * @param identityId 可选品规 UUID @param afterId 可选上一页最后授权 UUID
   * @param limit 1–100 行，默认 50 @return 不合并实物库存的授权单据余额 */
  @GetMapping("/api/bulk-pickup-authorization-balances")
  @OperationBinding(id="BulkCoilOutbound.read_authorization_balances",reference=true)
  public WarehouseBalances.AuthorizationPage authorizationBalances(HttpServletRequest request,
      @RequestParam String ownerId,@RequestParam String warehouseId,@RequestParam String productId,
      @RequestParam(required=false) String identityId,@RequestParam(required=false) String afterId,
      @RequestParam(defaultValue="50") int limit) {
    return balances.authorizations(context(request),new WarehouseBalances.Scope(ownerId,warehouseId,productId,identityId),
        afterId,limit);
  }

  /** @param request 可信仓储请求 @param id 原实收库存身份 UUID @return 实物及有权历史单据 */
  @GetMapping("/api/bulk-warehouse-stock-balances/coils/{id}/trace")
  @OperationBinding(id="BulkWarehouseStockBalance.trace",reference=true)
  public WarehouseBalances.CoilTrace coilTrace(HttpServletRequest request,@PathVariable String id) {
    return balances.trace(context(request),id);
  }

  /** @param request 已认证仓储请求 @param handoverId 原交接 UUID @return 当前可登记发运的原卷 */
  @GetMapping("/api/bulk-coil-return-transits/eligible-coils/{handoverId}")
  @OperationBinding(id="BulkCoilOutbound.prepare_return",reference=true)
  public CoilOutbound.ReturnPreparation prepareReturn(HttpServletRequest request,@PathVariable String handoverId) {
    return coils.prepareReturn(context(request),handoverId);
  }

  static VerifiedTenantContext context(HttpServletRequest request) {
    if (request.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext c) return c;
    throw new Rejection(403,"OUTBOUND_ACCESS_DENIED");
  }

  /** @param error 稳定状态拒绝 @return 统一问题响应 */
  @ExceptionHandler(Rejection.class)
  public ResponseEntity<ProblemDetail> reject(Rejection error) { return problem(error.status(),error.getMessage()); }

  /** @param error 主数据拒绝 @return 统一问题响应 */
  @ExceptionHandler(MasterData.Rejection.class)
  public ResponseEntity<ProblemDetail> rejectReference(MasterData.Rejection error) {
    return problem(error.status,error.getMessage());
  }

  /** @return 无权错误 */
  @ExceptionHandler(SecurityException.class)
  public ResponseEntity<ProblemDetail> denied() { return problem(403,"OUTBOUND_ACCESS_DENIED"); }

  private static ResponseEntity<ProblemDetail> problem(int status,String code) {
    var body=ProblemDetail.forStatus(status);
    body.setProperty("code",code);
    return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
  }
}
