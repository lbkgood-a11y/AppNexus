package com.appnexus.bulkwarehousing.adapter.persistence;

import java.nio.ByteBuffer;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.appnexus.bulkwarehousing.api.WarehouseBalances.*;
import com.appnexus.bulkwarehousing.application.WarehouseBalanceStore;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 库存、授权余额和单据查询的只读类型映射。 */
public final class MyBatisWarehouseBalanceStore implements WarehouseBalanceStore {
  private final WarehouseBalanceMapper mapper;
  private final TransactionTemplate transaction;

  /** @param manager 同一数据源的事务管理器 @param mapper 已受可信租户约束的余额 SQL */
  public MyBatisWarehouseBalanceStore(WarehouseBalanceMapper mapper,PlatformTransactionManager manager) {
    this.mapper=mapper;
    this.transaction=new TransactionTemplate(manager);
    // 公共 MyBatis 操作许可要求可写事务；本适配器仅执行 SELECT。
  }

  /** {@inheritDoc} */
  @Override public <T> T transaction(Supplier<T> work) {
    return transaction.execute(status -> work.get());
  }

  /** {@inheritDoc} */
  @Override public List<OverviewSlice> overview(VerifiedTenantContext c,OverviewFilter filter,String afterId,int limit) {
    return mapper.overview(tenant(c),filter,afterId,limit).stream().map(row->new OverviewSlice(
        text(row,"receiptId"),text(row,"serial"),text(row,"warehouseId"),text(row,"ownerId"),
        text(row,"productId"),text(row,"identityKind"),text(row,"identityId"),text(row,"receiptBatchNo"),text(row,"unitId"),decimal(row,"onHand"),decimal(row,"pending"),
        new OverviewAudit(text(row,"createdBy"),((java.sql.Timestamp)row.get("createdAt")).toInstant(),
            text(row,"updatedBy"),((java.sql.Timestamp)row.get("updatedAt")).toInstant()))).toList();
  }

  /** {@inheritDoc} */
  @Override public DocumentHeader document(VerifiedTenantContext c,String id) {
    String uuid=id.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}") ? id : null;
    var row=mapper.document(tenant(c),id,uuid);
    return row==null ? null : documentHeader(row);
  }

  /** {@inheritDoc} */
  @Override public List<DocumentHeader> inboundNotices(VerifiedTenantContext c,NoticeFilter filter,String afterId,int limit) {
    String number=filter.documentNo();
    String numberId=number!=null&&number.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")?number:null;
    return mapper.inboundNotices(tenant(c),filter,numberId,afterId,limit).stream()
        .map(MyBatisWarehouseBalanceStore::documentHeader).toList();
  }

  /** {@inheritDoc} */
  @Override public Map<String,List<String>> inboundNoticeProducts(VerifiedTenantContext c,List<String> noticeIds) {
    return mapper.inboundNoticeProducts(tenant(c),noticeIds).stream().collect(java.util.stream.Collectors.groupingBy(
        row->text(row,"noticeId"),java.util.stream.Collectors.mapping(row->text(row,"productId"),java.util.stream.Collectors.toList())));
  }

  private static DocumentHeader documentHeader(Map<String,Object> row) {
    return new DocumentHeader(text(row,"documentNo"),text(row,"parentId"),text(row,"warehouseId"),
        text(row,"ownerId"),text(row,"productId"),text(row,"otherOwnerId"),text(row,"unitId"),
        (BigDecimal)row.get("quantity"),(BigDecimal)row.get("completedQuantity"),(BigDecimal)row.get("reserved"),
        text(row,"evidenceId"),text(row,"vehicleNumber"),
        new OverviewAudit(text(row,"createdBy"),((java.sql.Timestamp)row.get("createdAt")).toInstant(),
            text(row,"updatedBy"),((java.sql.Timestamp)row.get("updatedAt")).toInstant()),
        text(row,"noticeId"),text(row,"status"),(Integer)row.get("flowVersion"),
        instant(row,"issuedAt"),instant(row,"expectedArrivalAt"),text(row,"remark"),
        text(row,"appointmentNo"),text(row,"plannedConveyance"),version(row,"version"),new com.appnexus.bulkwarehousing.api.InboundReceiving.Driver(
            text(row,"driverName"),text(row,"driverPhone"),text(row,"driverIdNumber")));
  }

  /** {@inheritDoc} */
  @Override public List<NoticeLineView> inboundLines(VerifiedTenantContext c,String id) {
    return mapper.inboundLines(tenant(c),id).stream().map(row->new NoticeLineView(
        text(row,"id"),text(row,"productId"),text(row,"identityKind"),text(row,"identityId"),
        version(row,"productVersion"),text(row,"unitId"),(BigDecimal)row.get("plannedQuantity"),
        decimal(row,"approvedQuantity"),decimal(row,"receivedQuantity"),
        decimal(row,"receivedQuantity").compareTo(decimal(row,"approvedQuantity"))<0,text(row,"remark"),text(row,"contractNo"),
        new OverviewAudit(text(row,"createdBy"),instant(row,"createdAt"),
            text(row,"updatedBy"),instant(row,"updatedAt")))).toList();
  }

  private static java.time.Instant instant(Map<String,Object> row,String key) {
    var timestamp=(java.sql.Timestamp)row.get(key);
    return timestamp==null ? null : timestamp.toInstant();
  }

  private static long version(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }

  private static BigDecimal decimal(Map<String,Object> row,String key) {
    return ((BigDecimal)row.get(key)).stripTrailingZeros();
  }
  private static byte[] tenant(VerifiedTenantContext context) {
    var id=context.tenantId();
    return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
  }

  private static String text(Map<String,Object> row,String key) { return (String) row.get(key); }
}
