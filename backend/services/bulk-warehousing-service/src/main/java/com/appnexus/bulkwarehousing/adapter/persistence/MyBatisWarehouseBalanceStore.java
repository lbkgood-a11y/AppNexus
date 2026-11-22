package com.appnexus.bulkwarehousing.adapter.persistence;

import static com.appnexus.bulkwarehousing.adapter.persistence.MyBatisOutboundStockStore.*;

import com.appnexus.bulkwarehousing.api.InboundReceiptReferences.Position;
import com.appnexus.bulkwarehousing.api.WarehouseBalances.*;
import com.appnexus.bulkwarehousing.application.WarehouseBalanceStore;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 库存、授权余额和单据查询的只读类型映射。 */
public final class MyBatisWarehouseBalanceStore implements WarehouseBalanceStore {
  private final WarehouseBalanceMapper mapper;

  /** @param mapper 已受可信租户约束的余额 SQL */
  public MyBatisWarehouseBalanceStore(WarehouseBalanceMapper mapper) { this.mapper=mapper; }

  /** {@inheritDoc} */
  @Override public List<OverviewSlice> overview(VerifiedTenantContext c,OverviewFilter filter,String afterId,int limit) {
    return mapper.overview(tenant(c),filter,afterId,limit).stream().map(row->new OverviewSlice(
        text(row,"receiptId"),text(row,"serial"),text(row,"warehouseId"),text(row,"ownerId"),
        text(row,"productId"),text(row,"identityKind"),text(row,"identityId"),text(row,"receiptBatchNo"),text(row,"unitId"),decimal(row,"onHand"),decimal(row,"pending"),
        new OverviewAudit(text(row,"createdBy"),((java.sql.Timestamp)row.get("createdAt")).toInstant(),
            text(row,"updatedBy"),((java.sql.Timestamp)row.get("updatedAt")).toInstant()))).toList();
  }

  /** {@inheritDoc} */
  @Override public List<StockSlice> physical(VerifiedTenantContext c,Scope scope) {
    return mapper.physical(tenant(c),scope).stream().map(row -> new StockSlice(
        new Position(text(row,"warehouseId"),text(row,"areaId"),text(row,"locationId"),
            version(row,"warehouseVersion"),version(row,"areaVersion"),version(row,"locationVersion")),
        text(row,"identityId"),text(row,"unitId"),decimal(row,"title"),decimal(row,"eligible"))).toList();
  }

  /** {@inheritDoc} */
  @Override public List<AuthorizationBalance> authorizations(VerifiedTenantContext c,Scope scope,String afterId,int limit) {
    return mapper.authorizations(tenant(c),scope,afterId,limit).stream()
        .map(MyBatisWarehouseBalanceStore::authorizationBalance).toList();
  }

  private static AuthorizationBalance authorizationBalance(Map<String,Object> row) {
    return new AuthorizationBalance(text(row,"authorizationId"),text(row,"noticeId"),text(row,"identityId"),
        text(row,"unitId"),decimal(row,"cap"),decimal(row,"reserved"),decimal(row,"issued"),
        decimal(row,"unassigned"),decimal(row,"remaining"),text(row,"status"));
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
  @Override public AuthorizationDocument authorization(VerifiedTenantContext c,String id) {
    var row=mapper.authorization(tenant(c),id);
    if (row==null) return null;
    var scope=new Scope(text(row,"ownerId"),text(row,"warehouseId"),text(row,"productId"),text(row,"identityId"));
    return new AuthorizationDocument(scope,authorizationBalance(row));
  }

  /** {@inheritDoc} */
  @Override public DocumentHeader document(VerifiedTenantContext c,DocumentKind kind,String id) {
    String uuid=id.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}") ? id : null;
    var row=mapper.document(tenant(c),kind.name(),id,uuid);
    if (row==null) return null;
    return new DocumentHeader(text(row,"documentNo"),text(row,"parentId"),text(row,"warehouseId"),
        text(row,"ownerId"),text(row,"productId"),text(row,"otherOwnerId"),text(row,"unitId"),
        (BigDecimal)row.get("quantity"),(BigDecimal)row.get("completedQuantity"),(BigDecimal)row.get("reserved"),
        text(row,"evidenceId"),text(row,"vehicleNumber"),
        new OverviewAudit(text(row,"createdBy"),((java.sql.Timestamp)row.get("createdAt")).toInstant(),
            text(row,"updatedBy"),((java.sql.Timestamp)row.get("updatedAt")).toInstant()),
        text(row,"noticeId"),text(row,"status"),(Integer)row.get("flowVersion"),
        instant(row,"issuedAt"),instant(row,"expectedArrivalAt"),text(row,"remark"));
  }

  /** {@inheritDoc} */
  @Override public BatchDocument inboundBatch(VerifiedTenantContext context,String id,boolean receipt) {
    var tenant=tenant(context);
    var header=receipt ? mapper.receiptHeader(tenant,id) : mapper.entryHeader(tenant,id);
    if (header==null) return null;
    var facts=receipt ? mapper.receiptLines(tenant,id) : mapper.entryLines(tenant,id);
    var lines=facts.stream().map(row->new BatchLine(text(row,"id"),text(row,"noticeLineId"),(int)version(row,"lineNo"),
        text(row,"productId"),text(row,"identityKind"),text(row,"identityId"),text(row,"unitId"),
        decimal(row,"approvedQuantity"),decimal(row,"receivedQuantity"),text(row,"custodyId"),
        receipt ? null : new Position(text(row,"warehouseId"),text(row,"areaId"),text(row,"locationId"),
            version(row,"warehouseVersion"),version(row,"areaVersion"),version(row,"locationVersion")),
        (BigDecimal)row.get("expectedQuantity"),(BigDecimal)row.get("rejectionQuantity"),text(row,"rejectionReason"),
        text(row,"evidenceId"),text(row,"measurementId"),text(row,"receiptId"),processed(row),text(row,"stockStatus"))).toList();
    return new BatchDocument(id,text(header,"noticeId"),text(header,"warehouseId"),text(header,"ownerId"),
        text(header,"entryId"),text(header,"vehicleNumber"),text(header,"carrier"),instant(header,"arrivedAt"),
        text(header,"measurementRef"),text(header,"remark"),
        new OverviewAudit(text(header,"createdBy"),instant(header,"createdAt"),text(header,"updatedBy"),instant(header,"updatedAt")),lines);
  }

  private static boolean processed(Map<String,Object> row) {
    var value=row.get("processed");
    return Boolean.TRUE.equals(value) || value instanceof Number count && count.intValue()!=0;
  }

  /** {@inheritDoc} */
  @Override public List<NoticeLineView> inboundLines(VerifiedTenantContext c,String id) {
    return mapper.inboundLines(tenant(c),id).stream().map(row->new NoticeLineView(
        text(row,"id"),(int)version(row,"lineNo"),text(row,"productId"),text(row,"identityKind"),text(row,"identityId"),
        version(row,"productVersion"),text(row,"unitId"),(BigDecimal)row.get("plannedQuantity"),
        decimal(row,"approvedQuantity"),decimal(row,"receivedQuantity"),
        decimal(row,"receivedQuantity").compareTo(decimal(row,"approvedQuantity"))<0,text(row,"remark"))).toList();
  }

  /** {@inheritDoc} */
  @Override public List<NoticeCustodyView> inboundCustodies(VerifiedTenantContext c,String id) {
    return mapper.inboundCustodies(tenant(c),id).stream().map(row->new NoticeCustodyView(
        text(row,"id"),text(row,"lineId"),text(row,"arrivalBatchNo"),
        new Position(text(row,"warehouseId"),text(row,"areaId"),text(row,"locationId"),
            version(row,"warehouseVersion"),version(row,"areaVersion"),version(row,"locationVersion")),
        text(row,"receiptId"),text(row,"entryId"))).toList();
  }

  /** {@inheritDoc} */
  @Override public List<DocumentRelation> documentRelations(VerifiedTenantContext c,DocumentKind kind,String id) {
    String source=switch(kind) {
      case INBOUND_NOTICE,OUTBOUND_TRIP -> id;
      default -> null;
    };
    if (source==null) return List.of();
    return mapper.documentRelations(tenant(c),kind.name(),source).stream().map(row->new DocumentRelation(
        text(row,"kind"),text(row,"id"),text(row,"parentId"),text(row,"evidenceId"),(BigDecimal)row.get("quantity"))).toList();
  }

  /** {@inheritDoc} */
  @Override public TraceStock traceStock(VerifiedTenantContext c,String id) {
    var row=mapper.traceStock(tenant(c),id);
    if (row==null) return null;
    return new TraceStock(id,text(row,"serial"),text(row,"noticeId"),text(row,"custodyId"),text(row,"measurementId"),text(row,"receiptBatchNo"),
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

  private static java.time.Instant instant(Map<String,Object> row,String key) {
    var timestamp=(java.sql.Timestamp)row.get(key);
    return timestamp==null ? null : timestamp.toInstant();
  }

  private static long version(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }

  private static BigDecimal decimal(Map<String,Object> row,String key) {
    return ((BigDecimal)row.get(key)).stripTrailingZeros();
  }
}
