package com.appnexus.bulkwarehousing.adapter.persistence;

import com.appnexus.bulkwarehousing.api.InboundReceiptReferences.*;
import com.appnexus.bulkwarehousing.api.InboundReceiving.*;
import com.appnexus.bulkwarehousing.application.InboundReceivingStore;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 仓储 MyBatis 适配器。事务回滚同时覆盖实收确认、待检余额和通知累计量。 */
public final class MyBatisInboundReceivingStore implements InboundReceivingStore {
  private final InboundReceivingMapper mapper;
  private final TransactionTemplate transaction;

  /** @param mapper 固定仓储语句 @param manager 组合应用共用同一数据源的事务管理器 */
  public MyBatisInboundReceivingStore(InboundReceivingMapper mapper, PlatformTransactionManager manager) {
    this.mapper=mapper;
    this.transaction=new TransactionTemplate(manager);
  }

  /** {@inheritDoc} */
  @Override public <T> T transaction(Supplier<T> work) {
    try {
      return transaction.execute(status -> work.get());
    } catch (DuplicateKeyException error) {
      throw new Rejection(409,"INBOUND_REQUEST_CONFLICT");
    }
  }

  /** {@inheritDoc} */
  @Override public void issue(VerifiedTenantContext c,Notice notice,int flowVersion) {
    mapper.issue(tenant(c),actor(c),notice,flowVersion);
  }

  /** {@inheritDoc} */
  @Override public int flowVersion(VerifiedTenantContext c,String id) {
    return mapper.flowVersion(tenant(c),id);
  }

  /** {@inheritDoc} */
  @Override public int receiptFlowVersion(VerifiedTenantContext context,String receiptId) {
    return mapper.receiptFlowVersion(tenant(context),receiptId);
  }

  /** {@inheritDoc} */
  @Override public LockedNotice lockNotice(VerifiedTenantContext c,String id) {
    var row=mapper.lockNotice(tenant(c),id);
    if (row==null) return null;
    var item=new Item(string(row,"productId"),IdentityKind.valueOf(string(row,"kind")),
        string(row,"identityId"),version(row,"productVersion"));
    var notice=new Notice(id,string(row,"warehouseId"),string(row,"ownerId"),item,string(row,"unitId"),
        (BigDecimal) row.get("lim"));
    return new LockedNotice(notice,(BigDecimal) row.get("received"));
  }

  /** {@inheritDoc} */
  @Override public void takeCustody(VerifiedTenantContext c,Custody custody) {
    mapper.custody(tenant(c),actor(c),custody);
  }

  /** {@inheritDoc} */
  @Override public Custody custody(VerifiedTenantContext c,String id) {
    var row=mapper.findCustody(tenant(c),id);
    if (row==null) return null;
    var position=new Position(string(row,"warehouseId"),string(row,"areaId"),string(row,"locationId"),
        version(row,"warehouseVersion"),version(row,"areaVersion"),version(row,"locationVersion"));
    return new Custody(id,string(row,"noticeId"),position,string(row,"evidenceId"));
  }

  /** {@inheritDoc} */
  @Override public Posted posted(VerifiedTenantContext c,String id) {
    var row=mapper.posted(tenant(c),id);
    if (row==null) return null;
    return new Posted(new Posting(string(row,"id"),id,((BigDecimal) row.get("quantity")).stripTrailingZeros(),
        string(row,"unitId")),string(row,"digest"));
  }

  /** {@inheritDoc} */
  @Override public void post(VerifiedTenantContext c,Receipt receipt,Posting posting,String noticeId,
      String digest,String evidence) {
    mapper.receipt(tenant(c),actor(c),posting,receipt.measurementId(),digest,evidence);
    mapper.stock(tenant(c),actor(c),posting);
    if (mapper.increase(tenant(c),actor(c),noticeId,posting.quantity())!=1) {
      throw new Rejection(409,"INBOUND_LIMIT_EXCEEDED");
    }
  }

  /** {@inheritDoc} */
  @Override public Stock lockStock(VerifiedTenantContext c,String receiptId) {
    if (mapper.lockStock(tenant(c),receiptId)==null) return null;
    var row=mapper.stockFacts(tenant(c),receiptId);
    return new Stock(receiptId,string(row,"warehouseId"),string(row,"ownerId"),string(row,"unitId"),
        (BigDecimal) row.get("inboundQuantity"),(BigDecimal) row.get("pendingQuantity"),
        string(row,"titleEvidence"),titleEffectiveAt(row),string(row,"qualityEvidence"));
  }

  /** {@inheritDoc} */
  @Override public void registerTitle(VerifiedTenantContext c,Stock stock,String evidenceId,java.time.Instant effectiveAt) {
    mapper.registerTitle(tenant(c),actor(c),stock,evidenceId,effectiveAt);
  }

  /** {@inheritDoc} */
  @Override public void releaseQuality(VerifiedTenantContext c,Stock stock,String evidenceId) {
    mapper.qualityRelease(tenant(c),actor(c),stock,evidenceId);
    if (mapper.clearPending(tenant(c),actor(c),stock.receiptId())!=1) {
      throw new Rejection(409,"INBOUND_QUALITY_CONFLICT");
    }
  }

  private static java.time.Instant titleEffectiveAt(Map<String,Object> row) {
    var stored=(Number) row.get("titleEffectiveMicros");
    if (stored==null) return null;
    long micros=stored.longValue();
    return java.time.Instant.ofEpochSecond(micros/1_000_000,(micros%1_000_000)*1_000);
  }

  private static byte[] tenant(VerifiedTenantContext c) {
    var id=c.tenantId();
    return java.nio.ByteBuffer.allocate(16).putLong(id.getMostSignificantBits())
        .putLong(id.getLeastSignificantBits()).array();
  }
  private static String actor(VerifiedTenantContext c) { return c.accountId().toString(); }
  private static String string(Map<String,Object> row,String key) { return (String) row.get(key); }
  private static long version(Map<String,Object> row,String key) { return ((Number) row.get(key)).longValue(); }
}
