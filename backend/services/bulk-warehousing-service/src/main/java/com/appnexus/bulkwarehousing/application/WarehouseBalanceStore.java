package com.appnexus.bulkwarehousing.application;

import com.appnexus.bulkwarehousing.api.InboundReceiptReferences.Position;
import com.appnexus.bulkwarehousing.api.WarehouseBalances.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.util.List;

/** 实物库存、货权、授权额度及仓储单据的只读存储投影。 */
public interface WarehouseBalanceStore {
  /** 尚未应用主数据引用可见性的在库实收事实，仅限查询用例内部使用。 */
  record OverviewSlice(String receiptId,String serial,String warehouseId,String ownerId,String productId,
      String identityKind,String identityId,String receiptBatchNo,
      String unitId,BigDecimal onHand,BigDecimal pending,OverviewAudit audit) {}

  /**
   * @param c 可信租户
   * @param filter 可选当前仓库、货主及商品 UUID
   * @param afterId 可选实收 UUID，按数据库主键顺序排除该行
   * @param limit 本次扫描量，应用以固定批次读取以避免一次加载全部库存
   * @return 原始在库切片，必须复核引用后才可返回
   */
  List<OverviewSlice> overview(VerifiedTenantContext c,OverviewFilter filter,String afterId,int limit);

  /** 单据按可信租户、固定类型和主键读取，应用返回前复核仓库、货主和商品。 */
  DocumentHeader document(VerifiedTenantContext c,DocumentKind kind,String id);

  /** 在原查询事务中读取批次头与明细，应用层执行仓库、货主与各品规可见性。 */
  BatchDocument inboundBatch(VerifiedTenantContext context,String id,boolean receipt);

  /** 已授权通知下的批准明细。 */
  List<NoticeLineView> inboundLines(VerifiedTenantContext context,String noticeId);

  /** 已授权通知下的接管及实收确认关联。 */
  List<NoticeCustodyView> inboundCustodies(VerifiedTenantContext context,String noticeId);

  /** 读取已授权主单的关联凭证，最多 101 条，用于提示截断。 */
  List<DocumentRelation> documentRelations(VerifiedTenantContext c,DocumentKind kind,String id);

  /** 读取原实收及当前仓储事实。缺失返回空。 */
  TraceStock traceStock(VerifiedTenantContext c,String id);
  /**
   * @param document 现有追溯响应所需的业务事件
   * @param previousWarehouseId 改仓前的仓库，用于响应前授权校验，其他事件为 null
   * @param responsiblePartnerId 短损责任方，仅用于权限核对，其他事件为 null
   */
  record TraceEvent(TraceDocument document, String previousWarehouseId, String responsiblePartnerId) {}

  /** 读取真实业务流水及其权限事实。权限核对由应用在响应前执行。 */
  List<TraceEvent> traceDocuments(VerifiedTenantContext c,String id);

  /** 一处同单位品规的账面余额及通过库存状态检查的数量，仍需复核位置。 */
  record StockSlice(Position position,String identityId,String unitId,BigDecimal title,BigDecimal eligible) {}

  /** 与现有库存位置解析复核所需的整卷快照。 */
  record CoilSlice(Position position,String receiptId,String serial,String identityId,String unitId,
                   BigDecimal quantity,long titleRevision,String status) {}
  /** 按实际库存身份、现行货权和指定范围读取整卷。 */
  List<CoilSlice> coils(VerifiedTenantContext c,Scope scope,String afterId,int limit);

  /** 读取货主当前在库的货权，按库存位置分组。 */
  List<StockSlice> physical(VerifiedTenantContext c,Scope scope);

  /** 授权主键读取所需的业务范围及原额度投影。 */
  record AuthorizationDocument(Scope scope,AuthorizationBalance balance) {}

  /** 按授权 UUID 查询单条额度及范围。 */
  AuthorizationDocument authorization(VerifiedTenantContext c,String id);

  /** 读取定向授权额度，独立于实物库存。 */
  List<AuthorizationBalance> authorizations(VerifiedTenantContext c,Scope scope,String afterId,int limit);
}
