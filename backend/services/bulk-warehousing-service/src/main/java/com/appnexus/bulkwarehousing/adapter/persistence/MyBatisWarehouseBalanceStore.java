package com.appnexus.bulkwarehousing.adapter.persistence;

import static com.appnexus.bulkwarehousing.adapter.persistence.MyBatisOutboundStockStore.*;

import com.appnexus.bulkwarehousing.api.InboundReceiptReferences.Position;
import com.appnexus.bulkwarehousing.api.WarehouseBalances.*;
import com.appnexus.bulkwarehousing.application.WarehouseBalanceStore;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 两类余额的只读类型映射。 */
public final class MyBatisWarehouseBalanceStore implements WarehouseBalanceStore {
  private final WarehouseBalanceMapper mapper;

  /** @param mapper 已受可信租户约束的余额 SQL */
  public MyBatisWarehouseBalanceStore(WarehouseBalanceMapper mapper) { this.mapper=mapper; }

  /** {@inheritDoc} */
  @Override public List<StockSlice> physical(VerifiedTenantContext c,Scope scope) {
    return mapper.physical(tenant(c),scope).stream().map(row -> new StockSlice(
        new Position(text(row,"warehouseId"),text(row,"areaId"),text(row,"locationId"),
            version(row,"warehouseVersion"),version(row,"areaVersion"),version(row,"locationVersion")),
        text(row,"identityId"),text(row,"unitId"),decimal(row,"title"),decimal(row,"eligible"))).toList();
  }

  /** {@inheritDoc} */
  @Override public List<AuthorizationBalance> authorizations(VerifiedTenantContext c,Scope scope,String afterId,int limit) {
    return mapper.authorizations(tenant(c),scope,afterId,limit).stream().map(row -> new AuthorizationBalance(
        text(row,"authorizationId"),text(row,"noticeId"),text(row,"identityId"),text(row,"unitId"),
        decimal(row,"cap"),decimal(row,"reserved"),decimal(row,"issued"),decimal(row,"unassigned"),
        decimal(row,"remaining"),text(row,"status"))).toList();
  }

  /** {@inheritDoc} */
  @Override public List<CoilSlice> coils(VerifiedTenantContext c,Scope scope,String afterId,int limit) {
    return mapper.coils(tenant(c),scope,afterId,limit).stream().map(row -> new CoilSlice(
        new Position(text(row,"warehouseId"),text(row,"areaId"),text(row,"locationId"),
            version(row,"warehouseVersion"),version(row,"areaVersion"),version(row,"locationVersion")),
        text(row,"receiptId"),text(row,"serial"),text(row,"identityId"),text(row,"unitId"),
        decimal(row,"quantity"),version(row,"titleRevision"),text(row,"status"))).toList();
  }

  /** {@inheritDoc} */
  @Override public TraceStock traceStock(VerifiedTenantContext c,String id) {
    var row=mapper.traceStock(tenant(c),id);
    if (row==null) return null;
    return new TraceStock(id,text(row,"serial"),text(row,"noticeId"),text(row,"custodyId"),text(row,"measurementId"),
        text(row,"warehouseId"),text(row,"locationId"),text(row,"ownerId"),text(row,"productId"),text(row,"identityId"),
        text(row,"unitId"),decimal(row,"onHand"),decimal(row,"pending"),decimal(row,"title"),
        text(row,"titleEvidenceId"),text(row,"qualityEvidenceId"));
  }

  /** {@inheritDoc} */
  /** {@inheritDoc} */
  @Override public List<TraceEvent> traceDocuments(VerifiedTenantContext c,String id) {
    return mapper.traceDocuments(tenant(c),id).stream().map(row -> {
      var kind = text(row,"kind");
      var relatedPartner = text(row,"otherOwnerId");
      boolean loss = "RETURN_LOSS".equals(kind) || "RETURN_LOSS_REOPENED".equals(kind);
      var document = new TraceDocument(kind,text(row,"id"),text(row,"parentId"),text(row,"warehouseId"),
          text(row,"ownerId"),loss ? null : relatedPartner,decimal(row,"quantity"));
      return new TraceEvent(document,text(row,"previousWarehouseId"),loss ? relatedPartner : null);
    }).toList();
  }

  private static long version(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }

  private static BigDecimal decimal(Map<String,Object> row,String key) {
    return ((BigDecimal)row.get(key)).stripTrailingZeros();
  }
}
