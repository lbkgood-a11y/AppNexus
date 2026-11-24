package com.appnexus.bulkwarehousing.application;

import com.appnexus.bulkwarehousing.api.WarehouseBalances.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.util.List;

/** 入仓通知及实物库存总览的只读投影。 */
public interface WarehouseBalanceStore {
  /** @param work 在同一查询事务中执行授权后的读取 @return 查询结果 */
  <T> T transaction(java.util.function.Supplier<T> work);

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

  /** 入仓通知按可信租户、单号或历史 UUID 读取，应用返回前复核仓库、货主和商品。 */
  DocumentHeader document(VerifiedTenantContext c,String id);

  /** 固定租户和游标范围内的通知档案，返回前须复核仓库、货主和所有商品。 */
  List<DocumentHeader> inboundNotices(VerifiedTenantContext c,NoticeFilter filter,String afterId,int limit);

  /** 每批通知的商品引用集合，按通知 UUID 分组。 */
  java.util.Map<String,List<String>> inboundNoticeProducts(VerifiedTenantContext c,List<String> noticeIds);

  /** 已授权通知下的批准明细。 */
  List<NoticeLineView> inboundLines(VerifiedTenantContext context,String noticeId);

}
