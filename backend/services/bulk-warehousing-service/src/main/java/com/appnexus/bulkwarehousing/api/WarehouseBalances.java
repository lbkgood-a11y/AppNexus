package com.appnexus.bulkwarehousing.api;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 入仓通知和库存总览的读取契约。 */
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

  /** 通知详情的固定业务类别。 */
  enum DocumentKind { INBOUND_NOTICE }

  /** 入仓通知主单的只读快照。单号缺省时使用历史通知 UUID。 */
  record DocumentHeader(String documentNo,String parentId,String warehouseId,String ownerId,String productId,
      String otherOwnerId,String unitId,BigDecimal quantity,BigDecimal completedQuantity,BigDecimal reserved,
      String evidenceId,String vehicleNumber,OverviewAudit audit,String noticeId,String status,Integer flowVersion,Instant issuedAt,Instant expectedArrivalAt,String remark,
      String appointmentNo,String plannedConveyance,long version,InboundReceiving.Driver driver) {
    /** @param protectedDriver 公共字段投影后的司机信息 @return 保留主单其他事实的只读副本 */
    public DocumentHeader withDriver(InboundReceiving.Driver protectedDriver) {
      return new DocumentHeader(documentNo,parentId,warehouseId,ownerId,productId,otherOwnerId,unitId,
          quantity,completedQuantity,reserved,evidenceId,vehicleNumber,audit,noticeId,status,flowVersion,
          issuedAt,expectedArrivalAt,remark,appointmentNo,plannedConveyance,version,protectedDriver);
    }
  }

  /** 通过稳定 UUID 关联的签发明细，数量沿用基础单位，审计来自数据库。 */
  record NoticeLineView(String id,String productId,String identityKind,String identityId,
      long productVersion,String unitId,BigDecimal plannedQuantity,BigDecimal approvedQuantity,
      BigDecimal receivedQuantity,boolean hasCapacity,String remark,String contractNo,OverviewAudit audit) {}

  /** 入仓通知主单及其签发明细。 */
  record DocumentDetail(DocumentHeader header,List<NoticeLineView> lines,
      java.util.Map<String,com.appnexus.platform.authorization.FieldSecurity.Mode> driverRestrictions) {}

  /**
   * @param c 服务端认证的租户主体
   * @param kind 固定单据类别
   * @param documentNo 入仓通知的业务单号或 UUID
   * @return 经仓储读取权限和主数据引用范围校验的通知
   */
  DocumentDetail document(VerifiedTenantContext c,DocumentKind kind,String documentNo);

  /** 通知列表的可选精确条件；空值表示不限制，仓库与货主使用稳定 UUID。 */
  record NoticeFilter(String documentNo,String warehouseId,String ownerId,String appointmentNo) {}
  /** 仅返回授权通知，total 为符合筛选条件且可见的单据总数。 */
  record NoticePage(List<DocumentHeader> rows,long total) {}

  /**
   * @param c 可信租户及成员
   * @param filter 可选业务精确条件；编号最多 64 字符
   * @param page 从 1 开始的页码，最大为 Java int 上限
   * @param pageSize 页容量，范围 1–100
   * @return 经过单据和主数据授权的通知主表页
   */
  NoticePage inboundNotices(VerifiedTenantContext c,NoticeFilter filter,int page,int pageSize);

}
