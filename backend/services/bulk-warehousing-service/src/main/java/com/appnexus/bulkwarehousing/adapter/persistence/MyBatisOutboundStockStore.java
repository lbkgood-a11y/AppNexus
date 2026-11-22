package com.appnexus.bulkwarehousing.adapter.persistence;

import com.appnexus.bulkwarehousing.api.InboundReceiptReferences.Position;
import com.appnexus.bulkwarehousing.api.OutboundStock.Freeze;
import com.appnexus.bulkwarehousing.api.OutboundStock.RestrictionEvidence;
import java.util.List;
import com.appnexus.bulkwarehousing.application.OutboundStockStore;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 现有库存行锁与出仓门禁 SQL 的同库事务适配。 */
public final class MyBatisOutboundStockStore implements OutboundStockStore {
  private final InboundReceivingMapper inbound;
  private final OutboundStockMapper mapper;
  private final CoilOutboundMapper coils;
  private final TransactionTemplate transaction;

  /** @param inbound 现有库存锁 @param mapper 出仓查询与写入 @param coils 仓库控制栅栏 @param manager 同一数据源事务 */
  public MyBatisOutboundStockStore(InboundReceivingMapper inbound,OutboundStockMapper mapper,
      CoilOutboundMapper coils,PlatformTransactionManager manager) {
    this.inbound=inbound;
    this.mapper=mapper;
    this.coils=coils;
    transaction=new TransactionTemplate(manager);
    transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  /** {@inheritDoc} */
  @Override public <T> T transaction(Supplier<T> work) {
    try {
      return transaction.execute(status -> work.get());
    } catch (DuplicateKeyException error) {
      throw new Rejection(409,"OUTBOUND_REQUEST_CONFLICT");
    }
  }

  /** {@inheritDoc} */
  @Override public Facts lock(VerifiedTenantContext c,String id) {
    var warehouse=mapper.warehouse(tenant(c),id);
    if (warehouse==null) return null;
    coils.lockGuard(tenant(c),warehouse);
    return lockFacts(c,id,warehouse);
  }

  /** {@inheritDoc} */
  @Override public Facts lockInWarehouse(VerifiedTenantContext c,String id,String warehouseId) {
    if (!warehouseId.equals(mapper.warehouse(tenant(c),id))) return null;
    return lockFacts(c,id,warehouseId);
  }

  private Facts lockFacts(VerifiedTenantContext c,String id,String warehouseId) {
    if (inbound.lockStock(tenant(c),id)==null) return null;
    var row=mapper.facts(tenant(c),id);
    // 跨仓退回可能在库存锁等待期间提交，归属变化时必须由调用方按新仓库重新开始。
    if (!warehouseId.equals(text(row,"warehouseId"))) throw new Rejection(409,"OUTBOUND_STOCK_WAREHOUSE_CHANGED");
    var position=new Position(text(row,"warehouseId"),text(row,"areaId"),text(row,"locationId"),
        number(row,"warehouseVersion"),number(row,"areaVersion"),number(row,"locationVersion"));
    return new Facts(id,position,text(row,"ownerId"),text(row,"productId"),text(row,"identityId"),text(row,"unitId"),
        (BigDecimal)row.get("quantity"),flag(row,"qualityReady"),flag(row,"titleReady"),flag(row,"frozen"),
        flag(row,"occupied"),flag(row,"coil"),flag(row,"dispatched"),number(row,"titleRevision"));
  }

  /** {@inheritDoc} */
  @Override public Occupation occupation(VerifiedTenantContext c,String id) {
    var row=mapper.occupation(tenant(c),id);
    return row==null ? null : new Occupation(text(row,"receiptId"),flag(row,"released"),flag(row,"quotaManaged"));
  }

  /** {@inheritDoc} */
  @Override public void reserve(VerifiedTenantContext c,String id,String receiptId) {
    mapper.reserve(tenant(c),actor(c),id,receiptId);
  }

  /** {@inheritDoc} */
  @Override public void release(VerifiedTenantContext c,String id) {
    mapper.release(tenant(c),actor(c),id);
  }

  /** {@inheritDoc} */
  @Override public void restore(VerifiedTenantContext c,String id,String receiptId) {
    if (mapper.restore(tenant(c),actor(c),id,receiptId)!=1) {
      throw new Rejection(409,"OUTBOUND_REVERSAL_CONFLICT");
    }
  }

  /** {@inheritDoc} */
  @Override public List<RestrictionEvidence> restrictionHistory(VerifiedTenantContext c,String receiptId) {
    return mapper.restrictionHistory(tenant(c),receiptId).stream().map(row -> new RestrictionEvidence(
        (String)row.get("id"),(String)row.get("evidenceId"),(String)row.get("releaseEvidenceId"),
        (String)row.get("createdBy"),(String)row.get("createdAt"),(String)row.get("updatedBy"),(String)row.get("updatedAt"))).toList();
  }

  /** {@inheritDoc} */
  @Override public Restriction restriction(VerifiedTenantContext c,String id) {
    var row=mapper.restriction(tenant(c),id);
    return row==null ? null : new Restriction(text(row,"receiptId"),text(row,"evidenceId"),text(row,"releaseEvidence"));
  }

  /** {@inheritDoc} */
  @Override public void freeze(VerifiedTenantContext c,Freeze freeze) {
    mapper.freeze(tenant(c),actor(c),freeze);
  }

  /** {@inheritDoc} */
  @Override public void lift(VerifiedTenantContext c,Freeze freeze) {
    mapper.lift(tenant(c),actor(c),freeze);
  }

  static byte[] tenant(VerifiedTenantContext c) {
    var id=c.tenantId();
    return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
  }

  static String actor(VerifiedTenantContext c) { return c.accountId().toString(); }
  static String text(Map<String,Object> row,String key) { return (String)row.get(key); }
  private static long number(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
  static boolean flag(Map<String,Object> row,String key) {
    Object value=row.get(key);
    return Boolean.TRUE.equals(value) || value instanceof Number flag && flag.intValue()==1;
  }
}
