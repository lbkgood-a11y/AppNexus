package com.appnexus.bulkwarehousing.api;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 货权库存、提货授权和仓储单据的读取契约。 */
public interface WarehouseBalances {
  String OBJECT="BulkWarehouseStockBalance";
  String RESOURCE="warehousing.coil.stockbalance";
  String READ_PERMISSION=RESOURCE+":read";

  String OVERVIEW_PERMISSION=RESOURCE+":read_overview";

  /**
   * @param warehouseId 可选当前仓库 UUID
   * @param ownerId 可选当前货主 UUID，货权未生效时采用入仓通知货主
   * @param productId 可选商品 UUID
   */
  record OverviewFilter(String warehouseId,String ownerId,String productId) {}

  /** 单个实收身份的当前实物余额和待检量，数量均使用该次实收的基础计量单位。 */
  record OverviewRow(String receiptId,String serial,String warehouse,String owner,String product,
      String specification,String skuCode,String receiptBatchNo,
      String unit,BigDecimal onHand,BigDecimal pending,OverviewAudit audit,
      String warehouseId,String ownerId,String productId,String identityId,String unitId) {}

  /** 原库存行的只读审计。无可信操作账号时账号字段为空，时间使用 UTC。 */
  record OverviewAudit(String createdBy,Instant createdAt,String updatedBy,Instant updatedAt) {}

  /** 本页授权库存；以最后一条可见库存 UUID 续查，末页不返回游标。 */
  record OverviewPage(List<OverviewRow> rows,String nextAfterId) {}

  /**
   * @param c 可信租户及成员
   * @param filter 可选范围，不填写时按当前权限查询全部在库实收
   * @param afterId 可选续查实收 UUID
   * @param limit 每页行数，范围 1–100
   * @return 当前仓储实物分页，不包含已全部出仓的实收
   */
  OverviewPage overview(VerifiedTenantContext c,OverviewFilter filter,String afterId,int limit);

  /** 货主、仓库、商品 UUID 必填。identityId 为可选的具体库存品规 UUID。 */
  record Scope(String ownerId,String warehouseId,String productId,String identityId) {}

  /** 品规和库存单位分别聚合。可提量已扣除待检、冻结、占用和位置不可执行范围。 */
  record Physical(String identityId,String unitId,BigDecimal onHandTitle,BigDecimal pickable) {}

  /** 单张提货授权的单据额度及状态；已关闭或到期时可用单据额度为零。 */
  record AuthorizationBalance(String authorizationId,String noticeId,String identityId,String unitId,
      BigDecimal cap,BigDecimal reserved,BigDecimal issued,BigDecimal unassigned,BigDecimal remaining,String status) {}

  /** 以授权 UUID 续查。没有后续数据时 nextAfterId 为空。 */
  record AuthorizationPage(List<AuthorizationBalance> rows,String nextAfterId) {}

  /** 可见的实物整卷及用于过户的权利版本；状态反映冻结、占用、待检和库位可执行性。 */
  record Coil(String receiptId,String serial,String identityId,String unitId,BigDecimal quantity,
              long titleRevision,String status,boolean transferable) {}
  /** 在库整卷按库存 UUID 续查。 */
  record CoilPage(List<Coil> rows,String nextAfterId) {}

  /** 库存身份当前账面、待检和已登记货权，用库存单位表达。 */
  record TraceStock(String receiptId,String serial,String noticeId,String custodyId,String measurementId,String receiptBatchNo,
      String warehouseId,String locationId,String ownerId,String productId,String identityId,String unitId,
      BigDecimal onHand,BigDecimal pending,BigDecimal title,String titleEvidenceId,String qualityEvidenceId) {}
  /** 一张可追溯的业务单据及其上游。仓和货主均为权限范围。 */
  record TraceDocument(String kind,String id,String parentId,String warehouseId,String ownerId,
                       String otherOwnerId,BigDecimal quantity) {}
  /** 按真实库存身份读取历史，不依赖浏览器保存的表单。 */
  record CoilTrace(TraceStock stock,List<TraceDocument> documents) {}

  /** 现有追溯权限覆盖的固定单据种类；用于选择构建内查询分支。 */
  enum DocumentKind { INBOUND_NOTICE, INBOUND_RECEIPT, INBOUND_TITLE, INBOUND_QUALITY, TITLE_TRANSFER, OUTBOUND_NOTICE, OUTBOUND_TRIP, OUTBOUND_CONFIRMATION, HANDOVER_REVERSAL }

  /** 主单据只读快照。单号复用业务 UUID；确权和质检以实收 UUID 为登记主键。 */
  record DocumentHeader(String documentNo,String parentId,String warehouseId,String ownerId,String productId,
      String otherOwnerId,String unitId,BigDecimal quantity,BigDecimal completedQuantity,BigDecimal reserved,
      String evidenceId,String vehicleNumber,OverviewAudit audit,String noticeId,String status,Integer flowVersion,Instant issuedAt,Instant expectedArrivalAt,String remark) {}

  /** 附属凭证，数量沿用主单计量单位。 */
  record DocumentRelation(String kind,String id,String parentId,String evidenceId,BigDecimal quantity) {}

  /** 签发快照和该行独立的计划、批准及实收数量。 */
  record NoticeLineView(String id,int lineNo,String productId,String identityKind,String identityId,
      long productVersion,String unitId,BigDecimal plannedQuantity,BigDecimal approvedQuantity,
      BigDecimal receivedQuantity,boolean hasCapacity,String remark) {}

  /** 接管绑定的行及批次。尚未确认时 receiptId 为空。 */
  record NoticeCustodyView(String id,String lineId,String arrivalBatchNo,InboundReceiptReferences.Position position,String receiptId,String entryId) {}

  /** 单据及有界关联；明细和接管范围仅在入仓通知查询返回。 */
  record DocumentDetail(DocumentHeader header,List<DocumentRelation> related,boolean relatedTruncated,
      List<NoticeLineView> lines,List<NoticeCustodyView> custodies) {}

  /**
   * @param c 服务端认证的租户主体
   * @param kind 固定单据类别
   * @param documentNo 入仓通知支持业务单号或原 UUID，其他类别仍以业务 UUID 查询
   * @return 经原追溯权限和引用范围校验的单据
   */
  DocumentDetail document(VerifiedTenantContext c,DocumentKind kind,String documentNo);

  /** 接管或确认行，合格与拒收数量按同一通知行基础单位表达。 */
  record BatchLine(String id,String noticeLineId,int lineNo,String productId,String identityKind,String identityId,
      String unitId,BigDecimal approvedQuantity,BigDecimal receivedQuantity,String custodyId,
      InboundReceiptReferences.Position position,BigDecimal expectedQuantity,BigDecimal rejectionQuantity,
      String rejectionReason,String evidenceId,String measurementId,String receiptId,boolean processed,String stockStatus) {}

  /** 原签发范围内的批次主单及明细。入场与实收按各自业务字段填充。 */
  record BatchDocument(String id,String noticeId,String warehouseId,String ownerId,String entryId,
      String vehicleNumber,String carrier,Instant arrivedAt,String measurementRef,String remark,OverviewAudit audit,
      List<BatchLine> lines) {}

  /** @param context 可信租户成员 @param id 入场 UUID @return 已授权主单及行 */
  BatchDocument inboundEntry(VerifiedTenantContext context,String id);

  /** @param context 可信租户成员 @param id 实收确认 UUID @return 已授权主单及行 */
  BatchDocument receiptDocument(VerifiedTenantContext context,String id);

  /** @param c 可信租户成员 @param id 提货授权的业务 UUID @return 经原权限校验的授权额度 */
  AuthorizationBalance authorization(VerifiedTenantContext c,String id);

  /** @param c 可信主体 @param receiptId 原实收库存身份 @return 当前实物及授权范围内的历史单据 */
  CoilTrace trace(VerifiedTenantContext c,String receiptId);

  /** @param c 可信主体 @param scope 授权查询范围 @param afterId 可选续查 UUID
   * @param limit 单页 1–100 行 @return 当前货主在库整卷及过户所需版本 */
  CoilPage coils(VerifiedTenantContext c,Scope scope,String afterId,int limit);

  /** @param c 可信主体 @param scope 授权查询范围 @return 按品规及单位分行的实物货权 */
  List<Physical> physical(VerifiedTenantContext c,Scope scope);

  /** @param c 可信主体 @param scope 授权查询范围 @param afterId 可选续查 UUID
   * @param limit 单页 1–100 行 @return 单据额度分页 */
  AuthorizationPage authorizations(VerifiedTenantContext c,Scope scope,String afterId,int limit);
}
