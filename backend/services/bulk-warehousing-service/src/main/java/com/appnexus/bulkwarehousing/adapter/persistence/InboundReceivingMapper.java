package com.appnexus.bulkwarehousing.adapter.persistence;

import com.appnexus.bulkwarehousing.api.InboundReceiving.*;
import com.appnexus.persistence.mybatis.TenantAggregateScoped;
import java.math.BigDecimal;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/** 固定仓储 SQL。所有操作使用应用可信上下文派生的租户，锁只覆盖该租户通知。 */
@Mapper
@TenantAggregateScoped(requireTenantMatch=true,
    tables={"bw_inbound_entry","bw_inbound_receipt_document","bw_inbound_receipt_document_line","bw_inbound_notice","bw_inbound_notice_line","bw_inbound_custody","bw_inbound_receipt","bw_stock_receipt","bw_inbound_title","bw_inbound_quality_release"},
    statements={"claimEntry","lockEntry","claimReceiptDocument","receiptDocumentDigest","confirmation","attachReceiptLine","receiptFlowVersion","flowVersion","issue","issueLine","startReceiving","updateHeaderProgress","lockNotice","custody","findCustody","posted","receipt","stock","increase","lockStock","stockFacts","registerTitle","qualityRelease","clearPending"})
public interface InboundReceivingMapper {
  /** 写入已签发主单；兼容列仅由旧式单行请求提供。 */
  @com.appnexus.persistence.mybatis.GuardedScope(resource="warehousing.inbound",tenant="__verifiedTenant",recordId="n.id",actions="issue")
  @Insert("""
      INSERT INTO bw_inbound_notice(tenant_id,id,notice_no,warehouse_id,owner_id,product_id,identity_kind,
        identity_id,product_version,base_unit_id,approved_quantity,received_quantity,flow_version,
        issued_by,issued_at,expected_arrival_at,remark,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{n.id}),#{n.noticeNo},UUID_TO_BIN(#{n.warehouseId}),UUID_TO_BIN(#{n.ownerPartnerId}),
        UUID_TO_BIN(#{legacyProduct,jdbcType=VARCHAR}),#{legacyKind,jdbcType=VARCHAR},
        UUID_TO_BIN(#{legacyIdentity,jdbcType=VARCHAR}),#{legacyVersion,jdbcType=BIGINT},
        UUID_TO_BIN(#{n.baseUnitId,jdbcType=VARCHAR}),#{n.limit,jdbcType=DECIMAL},
        IF(#{n.limit,jdbcType=DECIMAL} IS NULL,NULL,0),#{flowVersion},UUID_TO_BIN(#{actor}),CURRENT_TIMESTAMP(6),
        #{n.expectedArrivalAt,jdbcType=TIMESTAMP},#{n.remark,jdbcType=VARCHAR},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      """)
  void issue(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("n") Notice notice,
      @Param("flowVersion") int flowVersion,@Param("legacyProduct") String legacyProduct,
      @Param("legacyKind") String legacyKind,@Param("legacyIdentity") String legacyIdentity,
      @Param("legacyVersion") Long legacyVersion);

  /** 在主单签发许可和同一事务中保存明细。 */
  @com.appnexus.persistence.mybatis.GuardedScope(resource="warehousing.inbound",tenant="__verifiedTenant",recordId="noticeId",actions="issue")
  @Insert("""
      INSERT INTO bw_inbound_notice_line(tenant_id,id,notice_id,line_no,product_id,identity_kind,
        identity_id,product_version,base_unit_id,planned_quantity,approved_quantity,remark,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{line.id}),UUID_TO_BIN(#{noticeId}),#{lineNo},
        UUID_TO_BIN(#{line.item.productId}),#{line.item.kind},UUID_TO_BIN(#{line.item.identityId}),#{line.item.productVersion},
        UUID_TO_BIN(#{line.baseUnitId}),#{line.plannedQuantity,jdbcType=DECIMAL},#{line.limit},
        #{line.remark,jdbcType=VARCHAR},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      """)
  void issueLine(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("noticeId") String noticeId,@Param("lineNo") int lineNo,@Param("line") NoticeLine line);

  /** 当前已授权事务内读取通知绑定，续办不重新匹配发布策略。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.inbound:take_custody","warehousing.inbound:receive"})
  @Select("SELECT flow_version FROM bw_inbound_notice WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) FOR UPDATE")
  int flowVersion(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

  /** 当前读并锁定额度所有者，使并发收货按最新已收量串行。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.inbound:take_custody","warehousing.inbound:receive"})
  @Select("""
      SELECT BIN_TO_UUID(n.warehouse_id) warehouseId,BIN_TO_UUID(n.owner_id) ownerId,n.status,
        BIN_TO_UUID(l.id) lineId,BIN_TO_UUID(l.product_id) productId,l.identity_kind kind,
        BIN_TO_UUID(l.identity_id) identityId,l.product_version productVersion,BIN_TO_UUID(l.base_unit_id) unitId,
        l.approved_quantity lim,l.received_quantity received
      FROM bw_inbound_notice n
      JOIN bw_inbound_notice_line l ON l.tenant_id=n.tenant_id AND l.notice_id=n.id
      WHERE n.tenant_id=#{__verifiedTenant} AND n.id=UUID_TO_BIN(#{id})
        AND (l.id=UUID_TO_BIN(#{lineId,jdbcType=VARCHAR}) OR (#{lineId,jdbcType=VARCHAR} IS NULL AND l.line_no=1
          AND NOT EXISTS(SELECT 1 FROM bw_inbound_notice_line other WHERE other.tenant_id=n.tenant_id
            AND other.notice_id=n.id AND other.line_no>1))) FOR UPDATE
      """)
  Map<String,Object> lockNotice(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("lineId") String lineId);

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
      INSERT INTO bw_inbound_custody(tenant_id,id,notice_id,notice_line_id,arrival_batch_no,entry_id,expected_quantity,warehouse_id,area_id,location_id,
        warehouse_version,area_version,location_version,evidence_id,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{c.id}),UUID_TO_BIN(#{c.noticeId}),UUID_TO_BIN(#{c.noticeLineId}),#{c.arrivalBatchNo,jdbcType=VARCHAR},UUID_TO_BIN(#{c.entryId,jdbcType=VARCHAR}),#{c.expectedQuantity,jdbcType=DECIMAL},
        UUID_TO_BIN(#{c.position.warehouseId}),UUID_TO_BIN(#{c.position.areaId}),UUID_TO_BIN(#{c.position.locationId}),
        #{c.position.warehouseVersion},#{c.position.areaVersion},#{c.position.locationVersion},
        UUID_TO_BIN(#{c.evidenceId}),UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      """)
  void custody(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("c") Custody custody);

  /** 仅返回同租户的接管位置和证据。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.inbound:take_custody","warehousing.inbound:receive"})
  @Select("""
      SELECT BIN_TO_UUID(notice_id) noticeId,BIN_TO_UUID(notice_line_id) lineId,arrival_batch_no arrivalBatchNo,
        BIN_TO_UUID(entry_id) entryId,expected_quantity expectedQuantity,
        BIN_TO_UUID(warehouse_id) warehouseId,
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

  /** 入场头由同一接管操作认领，重复标识先保留原事实，再锁定校验摘要。 */
  @com.appnexus.persistence.mybatis.GuardedScope(resource="warehousing.inbound",tenant="__verifiedTenant",recordId="e.id",actions="take_custody")
  @Insert("""
      INSERT INTO bw_inbound_entry(tenant_id,id,notice_id,vehicle_number,carrier,arrived_at,remark,request_digest,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{e.id}),UUID_TO_BIN(#{e.noticeId}),#{e.vehicleNumber},
        #{e.carrier,jdbcType=VARCHAR},#{e.arrivedAt},#{e.remark,jdbcType=VARCHAR},#{digest},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      ON DUPLICATE KEY UPDATE id=id
      """)
  void claimEntry(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("e") Entry entry,@Param("digest") String digest);

  /** 同一车次的实收主从事务先锁原入场头。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.inbound:take_custody","warehousing.inbound:receive"})
  @Select("""
      SELECT BIN_TO_UUID(notice_id) noticeId,request_digest digest FROM bw_inbound_entry
      WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) FOR UPDATE
      """)
  Map<String,Object> lockEntry(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

  /** 按原实收写许可认领主单，失败随现有过账一起回滚。 */
  @com.appnexus.persistence.mybatis.GuardedScope(resource="warehousing.inbound",tenant="__verifiedTenant",recordId="r.id",actions="receive")
  @Insert("""
      INSERT INTO bw_inbound_receipt_document(tenant_id,id,entry_id,measurement_ref,remark,request_digest,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{r.id}),UUID_TO_BIN(#{r.entryId}),
        #{r.measurementRef,jdbcType=VARCHAR},#{r.remark,jdbcType=VARCHAR},#{digest},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      ON DUPLICATE KEY UPDATE id=id
      """)
  void claimReceiptDocument(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("r") ReceiptDocument receipt,@Param("digest") String digest);

  /** 同一 UUID 再提交时复核原单摘要。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions="warehousing.inbound:receive")
  @Select("""
      SELECT request_digest FROM bw_inbound_receipt_document
      WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) FOR UPDATE
      """)
  String receiptDocumentDigest(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

  /** 同一接管范围最多一次确认，重复请求沿用原确认行。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions="warehousing.inbound:receive")
  @Select("""
      SELECT BIN_TO_UUID(document_id) documentId,BIN_TO_UUID(id) lineId FROM bw_inbound_receipt_document_line
      WHERE tenant_id=#{__verifiedTenant} AND custody_id=UUID_TO_BIN(#{id}) FOR UPDATE
      """)
  Map<String,Object> confirmation(@Param("__verifiedTenant") byte[] tenant,@Param("id") String custodyId);

  /** 拒收量仅登记在确认明细；已有库存流水永远引用正实收行。 */
  @com.appnexus.persistence.mybatis.GuardedScope(resource="warehousing.inbound",tenant="__verifiedTenant",recordId="r.id",actions="receive")
  @Insert("""
      INSERT INTO bw_inbound_receipt_document_line(tenant_id,id,document_id,entry_id,custody_id,receipt_id,
        received_quantity,rejected_quantity,rejection_reason,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{l.id}),UUID_TO_BIN(#{r.id}),UUID_TO_BIN(#{r.entryId}),
        UUID_TO_BIN(#{l.custodyId}),UUID_TO_BIN(#{receiptId,jdbcType=VARCHAR}),#{l.quantity},#{l.rejectedQuantity},
        #{l.rejectionReason,jdbcType=VARCHAR},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      """)
  void attachReceiptLine(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("r") ReceiptDocument document,@Param("l") ReceiptLine line,@Param("receiptId") String receiptId);

  /** 保存确认及采用的数量证据，唯一约束防止重复消费独立计量范围。 */
  @com.appnexus.persistence.mybatis.GuardedScope(resource="warehousing.inbound",tenant="__verifiedTenant",recordId="p.receiptId",actions="receive")
  @Insert("""
      INSERT INTO bw_inbound_receipt(tenant_id,id,custody_id,measurement_id,quantity,unit_id,
        request_digest,quantity_evidence,receipt_batch_no,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{p.receiptId}),UUID_TO_BIN(#{p.custodyId}),UUID_TO_BIN(#{measurement}),
        #{p.quantity},UUID_TO_BIN(#{p.unitId}),#{digest},#{evidence},#{batch,jdbcType=VARCHAR},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      """)
  void receipt(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("p") Posting posting,
      @Param("measurement") String measurement,@Param("digest") String digest,@Param("evidence") String evidence,
      @Param("batch") String batch);

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
      UPDATE bw_inbound_notice_line SET received_quantity=received_quantity+#{quantity},
        updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
      WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id})
        AND received_quantity+#{quantity}<=approved_quantity
      """)
  int increase(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("id") String id,
      @Param("quantity") BigDecimal quantity);

  /** 首次接管推进通知状态；原成功请求的幂等重放不更新审计。 */
  @com.appnexus.persistence.mybatis.GuardedScope(resource="warehousing.inbound",tenant="__verifiedTenant",recordId="id",actions="take_custody")
  @Update("""
      UPDATE bw_inbound_notice SET status='RECEIVING',version=version+1,
        updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
      WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) AND status='ISSUED'
      """)
  void startReceiving(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("id") String id);

  /** 多单位不混加；历史单行兼容累计量单独维护。全行收足时通知完成。 */
  @com.appnexus.persistence.mybatis.GuardedScope(resource="warehousing.inbound",tenant="__verifiedTenant",recordId="id",actions="receive")
  @Update("""
      UPDATE bw_inbound_notice n SET n.received_quantity=n.received_quantity+#{quantity},
        n.status=CASE WHEN EXISTS(SELECT 1 FROM bw_inbound_notice_line l WHERE l.tenant_id=n.tenant_id
          AND l.notice_id=n.id AND l.received_quantity<l.approved_quantity) THEN 'RECEIVING' ELSE 'COMPLETED' END,
        n.version=n.version+1,n.updated_by=UUID_TO_BIN(#{actor}),n.updated_at=CURRENT_TIMESTAMP(6)
      WHERE n.tenant_id=#{__verifiedTenant} AND n.id=UUID_TO_BIN(#{id})
      """)
  void updateHeaderProgress(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("id") String id,
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
