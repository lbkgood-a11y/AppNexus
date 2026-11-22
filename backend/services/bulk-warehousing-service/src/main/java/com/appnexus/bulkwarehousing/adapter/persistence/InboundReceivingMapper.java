package com.appnexus.bulkwarehousing.adapter.persistence;

import com.appnexus.bulkwarehousing.api.InboundReceiving.*;
import com.appnexus.persistence.mybatis.TenantAggregateScoped;
import java.math.BigDecimal;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/** 固定仓储 SQL。所有操作使用应用可信上下文派生的租户，锁只覆盖该租户通知。 */
@Mapper
@TenantAggregateScoped(requireTenantMatch=true,
    tables={"bw_inbound_notice","bw_inbound_custody","bw_inbound_receipt","bw_stock_receipt","bw_inbound_title","bw_inbound_quality_release"},
    statements={"receiptFlowVersion","flowVersion","issue","lockNotice","custody","findCustody","posted","receipt","stock","increase","lockStock","stockFacts","registerTitle","qualityRelease","clearPending"})
public interface InboundReceivingMapper {
  /** 写入单行已签发通知及审计主体。 */
  @com.appnexus.persistence.mybatis.GuardedScope(resource="warehousing.inbound",tenant="__verifiedTenant",recordId="n.id",actions="issue")
  @Insert("""
      INSERT INTO bw_inbound_notice(tenant_id,id,warehouse_id,owner_id,product_id,identity_kind,
        identity_id,product_version,base_unit_id,approved_quantity,flow_version,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{n.id}),UUID_TO_BIN(#{n.warehouseId}),UUID_TO_BIN(#{n.ownerPartnerId}),
        UUID_TO_BIN(#{n.item.productId}),#{n.item.kind},UUID_TO_BIN(#{n.item.identityId}),#{n.item.productVersion},
        UUID_TO_BIN(#{n.baseUnitId}),#{n.limit},#{flowVersion},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      """)
  void issue(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("n") Notice notice,
      @Param("flowVersion") int flowVersion);

  /** 当前已授权事务内读取通知绑定，续办不重新匹配发布策略。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.inbound:take_custody","warehousing.inbound:receive"})
  @Select("SELECT flow_version FROM bw_inbound_notice WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) FOR UPDATE")
  int flowVersion(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

  /** 当前读并锁定额度所有者，使并发收货按最新已收量串行。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.inbound:take_custody","warehousing.inbound:receive"})
  @Select("""
      SELECT BIN_TO_UUID(warehouse_id) warehouseId,BIN_TO_UUID(owner_id) ownerId,
        BIN_TO_UUID(product_id) productId,identity_kind kind,BIN_TO_UUID(identity_id) identityId,
        product_version productVersion,BIN_TO_UUID(base_unit_id) unitId,approved_quantity lim,received_quantity received
      FROM bw_inbound_notice WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) FOR UPDATE
      """)
  Map<String,Object> lockNotice(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

  /** 已获准的处置读取原通知版本，版本签发后不可改。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={
      "warehousing.inbound.title:register","warehousing.inbound.quality:release"})
  @Select("""
      SELECT n.flow_version FROM bw_inbound_receipt r
      JOIN bw_inbound_custody c ON c.tenant_id=r.tenant_id AND c.id=r.custody_id
      JOIN bw_inbound_notice n ON n.tenant_id=c.tenant_id AND n.id=c.notice_id
      WHERE r.tenant_id=#{__verifiedTenant} AND r.id=UUID_TO_BIN(#{id})
      """)
  int receiptFlowVersion(@Param("__verifiedTenant") byte[] tenant,@Param("id") String receiptId);

  /** 接管独立提交，净重未知时只保存位置及现场证据。 */
  @com.appnexus.persistence.mybatis.GuardedScope(resource="warehousing.inbound",tenant="__verifiedTenant",recordId="c.id",actions="take_custody")
  @Insert("""
      INSERT INTO bw_inbound_custody(tenant_id,id,notice_id,warehouse_id,area_id,location_id,
        warehouse_version,area_version,location_version,evidence_id,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{c.id}),UUID_TO_BIN(#{c.noticeId}),
        UUID_TO_BIN(#{c.position.warehouseId}),UUID_TO_BIN(#{c.position.areaId}),UUID_TO_BIN(#{c.position.locationId}),
        #{c.position.warehouseVersion},#{c.position.areaVersion},#{c.position.locationVersion},
        UUID_TO_BIN(#{c.evidenceId}),UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      """)
  void custody(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("c") Custody custody);

  /** 仅返回同租户的接管位置和证据。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.inbound:take_custody","warehousing.inbound:receive"})
  @Select("""
      SELECT BIN_TO_UUID(notice_id) noticeId,BIN_TO_UUID(warehouse_id) warehouseId,
        BIN_TO_UUID(area_id) areaId,BIN_TO_UUID(location_id) locationId,warehouse_version warehouseVersion,
        area_version areaVersion,location_version locationVersion,BIN_TO_UUID(evidence_id) evidenceId
      FROM bw_inbound_custody WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id})
      """)
  Map<String,Object> findCustody(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

  /** 通知加锁后使用当前读处理重放，避免使用加锁前的快照。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.inbound:receive"})
  @Select("""
      SELECT BIN_TO_UUID(id) id,quantity,BIN_TO_UUID(unit_id) unitId,request_digest digest
      FROM bw_inbound_receipt WHERE tenant_id=#{__verifiedTenant} AND custody_id=UUID_TO_BIN(#{id}) FOR UPDATE
      """)
  Map<String,Object> posted(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

  /** 保存确认及采用的数量证据，唯一约束防止重复消费独立计量范围。 */
  @com.appnexus.persistence.mybatis.GuardedScope(resource="warehousing.inbound",tenant="__verifiedTenant",recordId="p.receiptId",actions="receive")
  @Insert("""
      INSERT INTO bw_inbound_receipt(tenant_id,id,custody_id,measurement_id,quantity,unit_id,
        request_digest,quantity_evidence,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{p.receiptId}),UUID_TO_BIN(#{p.custodyId}),UUID_TO_BIN(#{measurement}),
        #{p.quantity},UUID_TO_BIN(#{p.unitId}),#{digest},#{evidence},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      """)
  void receipt(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("p") Posting posting,
      @Param("measurement") String measurement,@Param("digest") String digest,@Param("evidence") String evidence);

  /** 写入待检余额及固定入仓数量，必须和来源确认同事务。 */
  @com.appnexus.persistence.mybatis.GuardedScope(resource="warehousing.inbound",tenant="__verifiedTenant",recordId="p.receiptId",actions="receive")
  @Insert("""
      INSERT INTO bw_stock_receipt(tenant_id,receipt_id,inbound_quantity,pending_quantity,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{p.receiptId}),#{p.quantity},#{p.quantity},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      """)
  void stock(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("p") Posting posting);

  /** 加锁通知的额度结算，数据库再次防止超过批准数量。 */
  @com.appnexus.persistence.mybatis.GuardedScope(resource="warehousing.inbound",tenant="__verifiedTenant",recordId="id",actions="receive")
  @Update("""
      UPDATE bw_inbound_notice SET received_quantity=received_quantity+#{quantity},
        updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
      WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id})
        AND received_quantity+#{quantity}<=approved_quantity
      """)
  int increase(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("id") String id,
      @Param("quantity") BigDecimal quantity);

  /** 先以库存身份为锁，确保权利、质量及其重试串行处置同一范围。 */
  @Select("""
      SELECT BIN_TO_UUID(receipt_id) FROM bw_stock_receipt WHERE tenant_id=#{__verifiedTenant}
        AND receipt_id=UUID_TO_BIN(#{id}) FOR UPDATE
      """)
  String lockStock(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

  /** 只读取已加锁库存范围的来源及独立判定事实。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.inbound.title:register","warehousing.inbound.quality:release"})
  @Select("""
      SELECT BIN_TO_UUID(COALESCE(s.current_warehouse_id,c.warehouse_id)) warehouseId,BIN_TO_UUID(COALESCE(t.owner_id,n.owner_id)) ownerId,
        BIN_TO_UUID(r.unit_id) unitId,s.inbound_quantity inboundQuantity,s.pending_quantity pendingQuantity,
        BIN_TO_UUID(t.evidence_id) titleEvidence,CAST(UNIX_TIMESTAMP(t.effective_at)*1000000 AS SIGNED) titleEffectiveMicros,
        BIN_TO_UUID(q.evidence_id) qualityEvidence
      FROM bw_stock_receipt s
      JOIN bw_inbound_receipt r ON s.tenant_id=r.tenant_id AND s.receipt_id=r.id
      JOIN bw_inbound_custody c ON c.tenant_id=r.tenant_id AND c.id=r.custody_id
      JOIN bw_inbound_notice n ON n.tenant_id=c.tenant_id AND n.id=c.notice_id
      LEFT JOIN bw_inbound_title t ON t.tenant_id=s.tenant_id AND t.receipt_id=s.receipt_id
      LEFT JOIN bw_inbound_quality_release q ON q.tenant_id=s.tenant_id AND q.receipt_id=s.receipt_id
      WHERE s.tenant_id=#{__verifiedTenant} AND s.receipt_id=UUID_TO_BIN(#{id})
      """)
  Map<String,Object> stockFacts(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

  /** 登记该实收范围的权利份额，保持实物与质量数量。 */
  @com.appnexus.persistence.mybatis.GuardedScope(resource="warehousing.inbound.title",tenant="__verifiedTenant",recordId="s.receiptId",actions="register")
  @Insert("""
      INSERT INTO bw_inbound_title(tenant_id,receipt_id,owner_id,evidence_id,effective_at,quantity,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{s.receiptId}),UUID_TO_BIN(#{s.ownerId}),
        UUID_TO_BIN(#{evidence}),#{effectiveAt},#{s.inbound},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      """)
  void registerTitle(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("s") com.appnexus.bulkwarehousing.application.InboundReceivingStore.Stock stock,@Param("evidence") String evidence,
      @Param("effectiveAt") java.time.Instant effectiveAt);

  /** 追加独立的质量放行凭证。 */
  @com.appnexus.persistence.mybatis.GuardedScope(resource="warehousing.inbound.quality",tenant="__verifiedTenant",recordId="s.receiptId",actions="release")
  @Insert("""
      INSERT INTO bw_inbound_quality_release(tenant_id,receipt_id,evidence_id,quantity,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{s.receiptId}),UUID_TO_BIN(#{evidence}),
        #{s.pending},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      """)
  void qualityRelease(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("s") com.appnexus.bulkwarehousing.application.InboundReceivingStore.Stock stock,@Param("evidence") String evidence);

  /** 同事务转出已批准范围的待检量，入库流水保持原值。 */
  @com.appnexus.persistence.mybatis.GuardedScope(resource="warehousing.inbound.quality",tenant="__verifiedTenant",recordId="id",actions="release")
  @Update("""
      UPDATE bw_stock_receipt SET pending_quantity=0,updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
      WHERE tenant_id=#{__verifiedTenant} AND receipt_id=UUID_TO_BIN(#{id}) AND pending_quantity>0
      """)
  int clearPending(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("id") String id);
}
