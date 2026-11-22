package com.appnexus.bulkwarehousing.adapter.persistence;

import com.appnexus.bulkwarehousing.api.OutboundStock.Freeze;
import com.appnexus.persistence.mybatis.TenantAggregateScoped;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/** 出仓门禁固定查询及占用、冻结写入。业务命令先锁同一库存身份。 */
@Mapper
@TenantAggregateScoped(requireTenantMatch=true,
    tables={"bw_stock_receipt","bw_inbound_receipt","bw_inbound_custody","bw_inbound_notice",
        "bw_inbound_title","bw_inbound_quality_release","bw_stock_occupation","bw_stock_restriction","bw_stock_coil","bw_coil_allocation"},
    statements={"warehouse","facts","occupation","reserve","release","restore","restriction","restrictionHistory","freeze","lift"})
public interface OutboundStockMapper {
  /** 先定位实际仓库，仓及库存加锁后由适配器重验仓库一致。 */
  @Select("""
      SELECT BIN_TO_UUID(COALESCE(s.current_warehouse_id,c.warehouse_id))
      FROM bw_stock_receipt s JOIN bw_inbound_receipt r ON r.tenant_id=s.tenant_id AND r.id=s.receipt_id
      JOIN bw_inbound_custody c ON c.tenant_id=r.tenant_id AND c.id=r.custody_id
      WHERE s.tenant_id=#{__verifiedTenant} AND s.receipt_id=UUID_TO_BIN(#{id})
      """)
  String warehouse(@Param("__verifiedTenant") byte[] tenant,@Param("id") String receiptId);

  /** 读取已锁定库存的正式数量、全量权利与质量状态及当前限制。 */
  @Select("""
      SELECT BIN_TO_UUID(COALESCE(s.current_warehouse_id,c.warehouse_id)) warehouseId,BIN_TO_UUID(COALESCE(s.current_area_id,c.area_id)) areaId,
        BIN_TO_UUID(COALESCE(s.current_location_id,c.location_id)) locationId,
        COALESCE(s.current_warehouse_version,c.warehouse_version) warehouseVersion,
        COALESCE(s.current_area_version,c.area_version) areaVersion,
        COALESCE(s.current_location_version,c.location_version) locationVersion,
        BIN_TO_UUID(COALESCE(t.owner_id,n.owner_id)) ownerId,COALESCE(t.title_revision,0) titleRevision,
        BIN_TO_UUID(n.product_id) productId,
        BIN_TO_UUID(n.identity_id) identityId,BIN_TO_UUID(r.unit_id) unitId,s.inbound_quantity quantity,
        CASE WHEN s.pending_quantity=0 AND q.quantity=s.inbound_quantity THEN 1 ELSE 0 END qualityReady,
        CASE WHEN t.quantity=s.inbound_quantity
          AND t.effective_at<=CURRENT_TIMESTAMP(6) THEN 1 ELSE 0 END titleReady,
        EXISTS(SELECT 1 FROM bw_stock_restriction f
          WHERE f.tenant_id=s.tenant_id AND f.receipt_id=s.receipt_id AND f.release_evidence_id IS NULL) frozen,
        EXISTS(SELECT 1 FROM bw_stock_occupation o
          WHERE o.tenant_id=s.tenant_id AND o.active_receipt_id=s.receipt_id) occupied,
        EXISTS(SELECT 1 FROM bw_stock_coil k WHERE k.tenant_id=s.tenant_id AND k.receipt_id=s.receipt_id) coil,
        s.shipped_quantity>0 dispatched
      FROM bw_stock_receipt s
      JOIN bw_inbound_receipt r ON r.tenant_id=s.tenant_id AND r.id=s.receipt_id
      JOIN bw_inbound_custody c ON c.tenant_id=r.tenant_id AND c.id=r.custody_id
      JOIN bw_inbound_notice n ON n.tenant_id=c.tenant_id AND n.id=c.notice_id
      LEFT JOIN bw_inbound_title t ON t.tenant_id=s.tenant_id AND t.receipt_id=s.receipt_id
      LEFT JOIN bw_inbound_quality_release q ON q.tenant_id=s.tenant_id AND q.receipt_id=s.receipt_id
      WHERE s.tenant_id=#{__verifiedTenant} AND s.receipt_id=UUID_TO_BIN(#{id})
      """)
  Map<String,Object> facts(@Param("__verifiedTenant") byte[] tenant,@Param("id") String receiptId);

  /** 按操作编号查重。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.stock:reserve","warehousing.outbound.stock:release","warehousing.outbound.coil:handover"})
  @Select("""
      SELECT BIN_TO_UUID(o.receipt_id) receiptId,o.released_at IS NOT NULL released,
        EXISTS(SELECT 1 FROM bw_coil_allocation a WHERE a.tenant_id=o.tenant_id AND a.id=o.id) quotaManaged
      FROM bw_stock_occupation o WHERE o.tenant_id=#{__verifiedTenant} AND o.id=UUID_TO_BIN(#{id})
      """)
  Map<String,Object> occupation(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

  /** 创建当前有效整笔占用，数据库唯一约束保护排他性。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.stock:reserve","warehousing.outbound.coil:allocate"})
  @Insert("""
      INSERT INTO bw_stock_occupation(tenant_id,id,receipt_id,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{id}),UUID_TO_BIN(#{receiptId}),UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      """)
  void reserve(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("id") String id,@Param("receiptId") String receiptId);

  /** 按原编号释放并保留历史。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.stock:release","warehousing.outbound.coil:cancel","warehousing.outbound.coil:handover"})
  @Update("""
      UPDATE bw_stock_occupation SET released_at=CURRENT_TIMESTAMP(6),updated_at=CURRENT_TIMESTAMP(6),
        updated_by=UUID_TO_BIN(#{actor})
      WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) AND released_at IS NULL
      """)
  void release(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("id") String id);

  /** 只恢复原已耗用占用，数据库仍约束同库存唯一活动卷锁。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:reverse_handover"})
  @Update("""
      UPDATE bw_stock_occupation SET released_at=NULL,updated_at=CURRENT_TIMESTAMP(6),updated_by=UUID_TO_BIN(#{actor})
      WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) AND receipt_id=UUID_TO_BIN(#{receiptId})
        AND released_at IS NOT NULL
      """)
  int restore(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("id") String id,@Param("receiptId") String receiptId);

  /** 按租户及已授权库存身份读取全部独立限制，保留解除凭证及原审计。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.stock.restriction:read"})
  @Select("""
      SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(evidence_id) evidenceId,BIN_TO_UUID(release_evidence_id) releaseEvidenceId,
        BIN_TO_UUID(created_by) createdBy,DATE_FORMAT(created_at,'%Y-%m-%dT%H:%i:%s.%fZ') createdAt,
        BIN_TO_UUID(updated_by) updatedBy,DATE_FORMAT(updated_at,'%Y-%m-%dT%H:%i:%s.%fZ') updatedAt
      FROM bw_stock_restriction WHERE tenant_id=#{__verifiedTenant} AND receipt_id=UUID_TO_BIN(#{id})
      ORDER BY created_at DESC,id DESC
      """)
  List<Map<String,Object>> restrictionHistory(@Param("__verifiedTenant") byte[] tenant,@Param("id") String receiptId);

  /** 按冻结编号读取原依据。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.stock.restriction:freeze","warehousing.stock.restriction:lift"})
  @Select("""
      SELECT BIN_TO_UUID(receipt_id) receiptId,BIN_TO_UUID(evidence_id) evidenceId,
        BIN_TO_UUID(release_evidence_id) releaseEvidence FROM bw_stock_restriction
      WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id})
      """)
  Map<String,Object> restriction(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

  /** 新增独立限制，保留其他限制及原占用。 */
  @com.appnexus.persistence.mybatis.GuardedScope(resource="warehousing.stock.restriction",tenant="__verifiedTenant",recordId="f.id",actions="freeze",target="f.receiptId")
  @Insert("""
      INSERT INTO bw_stock_restriction(tenant_id,id,receipt_id,evidence_id,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{f.id}),UUID_TO_BIN(#{f.receiptId}),UUID_TO_BIN(#{f.evidenceId}),
        UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      """)
  void freeze(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("f") Freeze freeze);

  /** 只解除引用的冻结。 */
  @com.appnexus.persistence.mybatis.GuardedScope(resource="warehousing.stock.restriction",tenant="__verifiedTenant",recordId="f.id",actions="lift",target="f.receiptId")
  @Update("""
      UPDATE bw_stock_restriction SET release_evidence_id=UUID_TO_BIN(#{f.evidenceId}),
        updated_at=CURRENT_TIMESTAMP(6),updated_by=UUID_TO_BIN(#{actor})
      WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{f.id}) AND receipt_id=UUID_TO_BIN(#{f.receiptId}) AND release_evidence_id IS NULL
      """)
  void lift(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("f") Freeze freeze);
}
