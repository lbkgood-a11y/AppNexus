package com.appnexus.bulkwarehousing.adapter.web;

import com.appnexus.contracts.operation.ResponseReferenceNames;
import com.appnexus.contracts.operation.ResponseReferenceNames.Binding;
import com.appnexus.bulkwarehousing.api.WarehouseBalances;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.contracts.operation.OperationBinding;
import com.appnexus.masterdata.api.masterdata.MasterData;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 入仓通知和库存总览读取入口，使用宿主提供的可信租户上下文。 */
@RestController
public final class WarehouseQueryController {
  private final WarehouseBalances balances;

  /** @param balances 经过统一权限校验的仓储查询 */
  public WarehouseQueryController(WarehouseBalances balances) {
    this.balances=balances;
  }

  /**
   * @param request 宿主验证的租户会话
   * @param warehouseId 可选当前仓库 UUID
   * @param ownerId 可选货主 UUID
   * @param productId 可选商品 UUID
   * @param afterId 可选续查实收 UUID
   * @param limit 每页 1–100 行，缺省 20 行
   * @return 授权范围内的当前实物库存
   */
  @com.appnexus.contracts.operation.RecordAuditNames(paths={"rows.audit"})
  @GetMapping("/api/bulk-warehouse-inventory")
  @OperationBinding(id="BulkWarehouseStockBalance.read_overview",reference=true)
  public WarehouseBalances.OverviewPage inventory(HttpServletRequest request,
      @RequestParam(required=false) String warehouseId,@RequestParam(required=false) String ownerId,
      @RequestParam(required=false) String productId,@RequestParam(required=false) String afterId,
      @RequestParam(defaultValue="20") int limit) {
    return balances.overview(context(request),new WarehouseBalances.OverviewFilter(warehouseId,ownerId,productId),
        afterId,limit);
  }

  /**
   * 查询当前有权入仓通知的主单，总数与明细仅计算授权范围。
   * @param request 服务端认证后建立的可信仓储请求
   * @param documentNo 可选本单号或原通知 UUID，业务单号格式最多 64 字符
   * @param warehouseId 可选仓库 UUID，为空时不限制仓库条件
   * @param ownerId 可选货主 UUID，为空时不限制货主条件
   * @param appointmentNo 可选上游业务单号，最多 64 字符，精确匹配
   * @param page 从 1 开始的页码，默认 1
   * @param pageSize 每页条数，1–100，默认 20
   * @return 当前授权页及同一筛选条件下的授权总数
   */
  @com.appnexus.contracts.operation.RecordAuditNames(paths={"rows.audit"})
  @ResponseReferenceNames({
      @Binding(path="rows",type="warehouse",id="warehouseId",name="warehouseName"),
      @Binding(path="rows",type="partner",id="ownerId",name="ownerName")})
  @GetMapping("/api/bulk-inbound-notices")
  @OperationBinding(id="BulkWarehouseStockBalance.list_inbound_notices",reference=true)
  public WarehouseBalances.NoticePage inboundNotices(HttpServletRequest request,
      @RequestParam(required=false) String documentNo,@RequestParam(required=false) String warehouseId,
      @RequestParam(required=false) String ownerId,@RequestParam(required=false) String appointmentNo,
      @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) {
    return balances.inboundNotices(context(request),
        new WarehouseBalances.NoticeFilter(documentNo,warehouseId,ownerId,appointmentNo),page,pageSize);
  }

  /** @param request 可信仓储请求 @param documentNo 入仓通知单号或历史通知 UUID
   * @return 已授权入仓通知及其明细 */
  @com.appnexus.contracts.operation.RecordAuditNames(paths={"header.audit","lines.audit"})
  @ResponseReferenceNames({
      @Binding(path="header",type="warehouse",id="warehouseId",name="warehouseName"),
      @Binding(path="header",type="partner",id="ownerId",name="ownerName"),
      @Binding(path="header",type="partner",id="otherOwnerId",name="otherOwnerName"),
      @Binding(path="header",type="product",id="productId",name="productName"),
      @Binding(path="header",type="product-unit",id="unitId",name="unitName"),
      @Binding(path="lines",type="product",id="productId",name="productName"),
      @Binding(path="lines",type="product",id="identityId",name="identityName",when="identityKind",equalsValue="PRODUCT"),
      @Binding(path="lines",type="product-specification",id="identityId",name="identityName",when="identityKind",equalsValue="SPECIFICATION"),
      @Binding(path="lines",type="product-sku",id="identityId",name="identityName",when="identityKind",equalsValue="SKU"),
      @Binding(path="lines",type="product-unit",id="unitId",name="unitName")})
  @GetMapping("/api/bulk-warehouse-documents/INBOUND_NOTICE/{documentNo}")
  @OperationBinding(id="BulkWarehouseStockBalance.read_document",reference=true)
  public WarehouseBalances.DocumentDetail warehouseDocument(HttpServletRequest request,
      @PathVariable String documentNo) {
    return balances.document(context(request),WarehouseBalances.DocumentKind.INBOUND_NOTICE,documentNo);
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
