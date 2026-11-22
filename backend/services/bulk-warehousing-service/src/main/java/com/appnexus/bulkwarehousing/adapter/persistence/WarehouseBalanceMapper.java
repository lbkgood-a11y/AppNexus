package com.appnexus.bulkwarehousing.adapter.persistence;

import com.appnexus.bulkwarehousing.api.WarehouseBalances;
import com.appnexus.bulkwarehousing.api.WarehouseBalances.OverviewFilter;
import com.appnexus.bulkwarehousing.api.WarehouseBalances.Scope;
import com.appnexus.persistence.mybatis.TenantAggregateScoped;
import com.appnexus.persistence.mybatis.GuardedScope;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/** 分别读取权威实物及单据额度，同一次读取内按可信租户与已授权业务范围限制。 */
@Mapper
@TenantAggregateScoped(requireTenantMatch=true,
    tables={"bw_inbound_entry","bw_inbound_receipt_document","bw_inbound_receipt_document_line","bw_inbound_notice_line","bw_inbound_title","bw_stock_receipt","bw_inbound_receipt","bw_inbound_custody",
        "bw_inbound_notice","bw_inbound_quality_release","bw_stock_restriction","bw_stock_occupation",
        "bw_outbound_notice","bw_pickup_authorization","bw_stock_coil","bw_coil_title_transfer",
        "bw_coil_allocation","bw_outbound_trip","bw_coil_weigh_line","bw_coil_weigh_review",
        "bw_coil_handover","bw_coil_handover_line","bw_coil_partial_return_line","bw_coil_handover_reversal",
        "bw_coil_return_transit_line","bw_coil_return_transit","bw_coil_return_transit_reroute",
        "bw_coil_return_transit_loss","bw_coil_return_loss_reopening"},
    statements={"overview","physical","authorizations","coils","traceStock","traceDocuments","document","documentRelations","inboundLines","inboundCustodies","entryHeader","entryLines","receiptHeader","receiptLines","authorization"})
public interface WarehouseBalanceMapper {
  /**
   * 当前实物由库存过账扣除已发账面量；待检库存和未登记货权的实收仍属于实物。
   * @param tenant 可信租户 UUID 二进制值，由 MyBatis 守卫复核
   * @param filter 可选仓库、货主、商品范围
   * @param afterId 上一条已扫描实收 UUID，缺省从首行开始
   * @param limit 固定批次扫描容量，由应用用例控制
   */
  @GuardedScope(tenant="__verifiedTenant",operationPermissions=WarehouseBalances.OVERVIEW_PERMISSION)
  @Select("""
      <script>
      SELECT BIN_TO_UUID(s.receipt_id) receiptId,k.serial,
        BIN_TO_UUID(COALESCE(s.current_warehouse_id,c.warehouse_id)) warehouseId,
        BIN_TO_UUID(COALESCE(t.owner_id,n.owner_id)) ownerId,BIN_TO_UUID(nl.product_id) productId,
        nl.identity_kind identityKind,BIN_TO_UUID(nl.identity_id) identityId,r.receipt_batch_no receiptBatchNo,
        BIN_TO_UUID(r.unit_id) unitId,s.inbound_quantity-s.shipped_quantity onHand,s.pending_quantity pending,
        BIN_TO_UUID(s.created_by) createdBy,s.created_at createdAt,BIN_TO_UUID(s.updated_by) updatedBy,s.updated_at updatedAt
      FROM bw_stock_receipt s
      JOIN bw_inbound_receipt r ON r.tenant_id=s.tenant_id AND r.id=s.receipt_id
      JOIN bw_inbound_custody c ON c.tenant_id=r.tenant_id AND c.id=r.custody_id
      JOIN bw_inbound_notice n ON n.tenant_id=c.tenant_id AND n.id=c.notice_id
      JOIN bw_inbound_notice_line nl ON nl.tenant_id=c.tenant_id AND nl.notice_id=c.notice_id AND nl.id=c.notice_line_id
      LEFT JOIN bw_inbound_title t ON t.tenant_id=s.tenant_id AND t.receipt_id=s.receipt_id
        AND t.effective_at&lt;=CURRENT_TIMESTAMP(6)
      LEFT JOIN bw_stock_coil k ON k.tenant_id=s.tenant_id AND k.receipt_id=s.receipt_id
      WHERE s.tenant_id=#{__verifiedTenant} AND s.inbound_quantity&gt;s.shipped_quantity
      <if test="filter.warehouseId != null">
        AND COALESCE(s.current_warehouse_id,c.warehouse_id)=UUID_TO_BIN(#{filter.warehouseId})
      </if>
      <if test="filter.ownerId != null">AND COALESCE(t.owner_id,n.owner_id)=UUID_TO_BIN(#{filter.ownerId})</if>
      <if test="filter.productId != null">AND nl.product_id=UUID_TO_BIN(#{filter.productId})</if>
      <if test="afterId != null">AND s.receipt_id&gt;UUID_TO_BIN(#{afterId})</if>
      ORDER BY s.receipt_id LIMIT #{limit}
      </script>
      """)
  List<Map<String,Object>> overview(@Param("__verifiedTenant") byte[] tenant,@Param("filter") OverviewFilter filter,
      @Param("afterId") String afterId,@Param("limit") int limit);

  /** 按库位聚合权利余额及通过库存实时状态校验的持有量，库位有效性由应用复核。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions="warehousing.coil.stockbalance:read")
  @Select("""
      <script>
      SELECT BIN_TO_UUID(nl.identity_id) identityId,BIN_TO_UUID(r.unit_id) unitId,
        BIN_TO_UUID(COALESCE(s.current_warehouse_id,c.warehouse_id)) warehouseId,
        BIN_TO_UUID(COALESCE(s.current_area_id,c.area_id)) areaId,
        BIN_TO_UUID(COALESCE(s.current_location_id,c.location_id)) locationId,
        COALESCE(s.current_warehouse_version,c.warehouse_version) warehouseVersion,
        COALESCE(s.current_area_version,c.area_version) areaVersion,
        COALESCE(s.current_location_version,c.location_version) locationVersion,
        SUM(t.quantity-t.issued_quantity) title,
        SUM(CASE WHEN s.pending_quantity=0 AND s.shipped_quantity=0 AND t.issued_quantity=0
          AND t.quantity=s.inbound_quantity AND q.quantity=s.inbound_quantity
          AND NOT EXISTS(SELECT 1 FROM bw_stock_restriction f WHERE f.tenant_id=s.tenant_id
              AND f.receipt_id=s.receipt_id AND f.release_evidence_id IS NULL)
          AND NOT EXISTS(SELECT 1 FROM bw_stock_occupation o WHERE o.tenant_id=s.tenant_id
              AND o.active_receipt_id=s.receipt_id) THEN t.quantity ELSE 0 END) eligible
      FROM bw_inbound_title t
      JOIN bw_stock_receipt s ON s.tenant_id=t.tenant_id AND s.receipt_id=t.receipt_id
      JOIN bw_inbound_receipt r ON r.tenant_id=s.tenant_id AND r.id=s.receipt_id
      JOIN bw_inbound_custody c ON c.tenant_id=r.tenant_id AND c.id=r.custody_id
      JOIN bw_inbound_notice n ON n.tenant_id=c.tenant_id AND n.id=c.notice_id
      JOIN bw_inbound_notice_line nl ON nl.tenant_id=c.tenant_id AND nl.notice_id=c.notice_id AND nl.id=c.notice_line_id
      LEFT JOIN bw_inbound_quality_release q ON q.tenant_id=s.tenant_id AND q.receipt_id=s.receipt_id
      WHERE t.tenant_id=#{__verifiedTenant} AND t.owner_id=UUID_TO_BIN(#{scope.ownerId})
        AND COALESCE(s.current_warehouse_id,c.warehouse_id)=UUID_TO_BIN(#{scope.warehouseId})
        AND nl.product_id=UUID_TO_BIN(#{scope.productId}) AND t.quantity&gt;t.issued_quantity
        AND s.inbound_quantity&gt;s.shipped_quantity AND t.effective_at&lt;=CURRENT_TIMESTAMP(6)
      <if test="scope.identityId != null">AND nl.identity_id=UUID_TO_BIN(#{scope.identityId})</if>
      GROUP BY identityId,unitId,warehouseId,areaId,locationId,warehouseVersion,areaVersion,locationVersion
      ORDER BY identityId,unitId,locationId
      </script>
      """)
  List<Map<String,Object>> physical(@Param("__verifiedTenant") byte[] tenant,@Param("scope") Scope scope);

  /** 只查单据范围内授权，关闭或过期的单据不提供有效剩余额度。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions="warehousing.outbound.coil:read_authorization_balances")
  @Select("""
      <script>
      SELECT BIN_TO_UUID(a.id) authorizationId,BIN_TO_UUID(n.id) noticeId,
        BIN_TO_UUID(n.identity_id) identityId,BIN_TO_UUID(n.unit_id) unitId,
        a.cap,a.reserved,a.issued,a.unassigned,
        CASE WHEN a.closed_at IS NULL AND a.expires_at&gt;CURRENT_TIMESTAMP(6)
          THEN a.cap-a.issued-a.reserved ELSE 0 END remaining,
        CASE WHEN a.closed_at IS NOT NULL THEN 'CLOSED'
          WHEN a.expires_at&lt;=CURRENT_TIMESTAMP(6) THEN 'EXPIRED' ELSE 'ACTIVE' END status
      FROM bw_outbound_notice n
      JOIN bw_pickup_authorization a ON a.tenant_id=n.tenant_id AND a.notice_id=n.id
      WHERE n.tenant_id=#{__verifiedTenant} AND n.warehouse_id=UUID_TO_BIN(#{scope.warehouseId})
        AND n.owner_id=UUID_TO_BIN(#{scope.ownerId}) AND n.product_id=UUID_TO_BIN(#{scope.productId})
      <if test="scope.identityId != null">AND n.identity_id=UUID_TO_BIN(#{scope.identityId})</if>
      <if test="afterId != null">AND a.id&gt;UUID_TO_BIN(#{afterId})</if>
      ORDER BY a.id LIMIT #{limit}
      </script>
      """)
  List<Map<String,Object>> authorizations(@Param("__verifiedTenant") byte[] tenant,@Param("scope") Scope scope,
      @Param("afterId") String afterId,@Param("limit") int limit);

  /** 按过户范围读取整卷、权利代次及当前门禁事实；同仓已发卷不会进入列表。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions="warehousing.coil.stockbalance:read")
  @Select("""
      <script>
      SELECT BIN_TO_UUID(s.receipt_id) receiptId,k.serial,BIN_TO_UUID(nl.identity_id) identityId,BIN_TO_UUID(r.unit_id) unitId,
        BIN_TO_UUID(COALESCE(s.current_warehouse_id,c.warehouse_id)) warehouseId,
        BIN_TO_UUID(COALESCE(s.current_area_id,c.area_id)) areaId,
        BIN_TO_UUID(COALESCE(s.current_location_id,c.location_id)) locationId,
        COALESCE(s.current_warehouse_version,c.warehouse_version) warehouseVersion,
        COALESCE(s.current_area_version,c.area_version) areaVersion,
        COALESCE(s.current_location_version,c.location_version) locationVersion,
        t.quantity,t.title_revision titleRevision,
        CASE WHEN EXISTS(SELECT 1 FROM bw_stock_restriction f WHERE f.tenant_id=s.tenant_id
              AND f.receipt_id=s.receipt_id AND f.release_evidence_id IS NULL) THEN 'FROZEN'
          WHEN EXISTS(SELECT 1 FROM bw_stock_occupation o WHERE o.tenant_id=s.tenant_id
              AND o.active_receipt_id=s.receipt_id) THEN 'OCCUPIED'
          WHEN s.pending_quantity&gt;0 OR q.quantity IS NULL OR q.quantity&lt;&gt;s.inbound_quantity THEN 'PENDING_QUALITY'
          ELSE 'AVAILABLE' END status
      FROM bw_inbound_title t
      JOIN bw_stock_receipt s ON s.tenant_id=t.tenant_id AND s.receipt_id=t.receipt_id
      JOIN bw_stock_coil k ON k.tenant_id=s.tenant_id AND k.receipt_id=s.receipt_id
      JOIN bw_inbound_receipt r ON r.tenant_id=s.tenant_id AND r.id=s.receipt_id
      JOIN bw_inbound_custody c ON c.tenant_id=r.tenant_id AND c.id=r.custody_id
      JOIN bw_inbound_notice n ON n.tenant_id=c.tenant_id AND n.id=c.notice_id
      JOIN bw_inbound_notice_line nl ON nl.tenant_id=c.tenant_id AND nl.notice_id=c.notice_id AND nl.id=c.notice_line_id
      LEFT JOIN bw_inbound_quality_release q ON q.tenant_id=s.tenant_id AND q.receipt_id=s.receipt_id
      WHERE t.tenant_id=#{__verifiedTenant} AND t.owner_id=UUID_TO_BIN(#{scope.ownerId})
        AND COALESCE(s.current_warehouse_id,c.warehouse_id)=UUID_TO_BIN(#{scope.warehouseId})
        AND nl.product_id=UUID_TO_BIN(#{scope.productId}) AND t.issued_quantity=0
        AND s.shipped_quantity=0 AND t.quantity=s.inbound_quantity AND t.effective_at&lt;=CURRENT_TIMESTAMP(6)
      <if test="scope.identityId != null">AND nl.identity_id=UUID_TO_BIN(#{scope.identityId})</if>
      <if test="afterId != null">AND s.receipt_id&gt;UUID_TO_BIN(#{afterId})</if>
      ORDER BY s.receipt_id LIMIT #{limit}
      </script>
      """)
  List<Map<String,Object>> coils(@Param("__verifiedTenant") byte[] tenant,@Param("scope") Scope scope,
      @Param("afterId") String afterId,@Param("limit") int limit);

  /** 使用授权主键精确读取，与原余额查询使用同一额度状态公式。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions="warehousing.outbound.coil:read_authorization_balances")
  @Select("""
      SELECT BIN_TO_UUID(a.id) authorizationId,BIN_TO_UUID(n.id) noticeId,
        BIN_TO_UUID(n.identity_id) identityId, BIN_TO_UUID(n.owner_id) ownerId,
        BIN_TO_UUID(n.warehouse_id) warehouseId,BIN_TO_UUID(n.product_id) productId,BIN_TO_UUID(n.unit_id) unitId,
        a.cap,a.reserved,a.issued,a.unassigned,
        CASE WHEN a.closed_at IS NULL AND a.expires_at>CURRENT_TIMESTAMP(6)
          THEN a.cap-a.issued-a.reserved ELSE 0 END remaining,
        CASE WHEN a.closed_at IS NOT NULL THEN 'CLOSED'
          WHEN a.expires_at<=CURRENT_TIMESTAMP(6) THEN 'EXPIRED' ELSE 'ACTIVE' END status
      FROM bw_outbound_notice n
      JOIN bw_pickup_authorization a ON a.tenant_id=n.tenant_id AND a.notice_id=n.id
      WHERE a.tenant_id=#{__verifiedTenant} AND a.id=UUID_TO_BIN(#{id})
      """)
  Map<String,Object> authorization(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

  /** 精确读取单据主表；通知与车次无须库存或配货关联。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions="warehousing.coil.stockbalance:read")
  @Select("""
      <script><choose>
      <when test="kind == 'INBOUND_NOTICE'">
        SELECT n.notice_no documentNo,BIN_TO_UUID(n.id) noticeId,n.status,n.flow_version flowVersion,
          n.issued_at issuedAt,n.expected_arrival_at expectedArrivalAt,n.remark,
          BIN_TO_UUID(NULL) parentId,
          BIN_TO_UUID(n.warehouse_id) warehouseId,BIN_TO_UUID(n.owner_id) ownerId,BIN_TO_UUID(n.product_id) productId,
          BIN_TO_UUID(NULL) otherOwnerId,BIN_TO_UUID(n.base_unit_id) unitId,n.approved_quantity quantity,
          n.received_quantity completedQuantity,NULL reserved,NULL evidenceId,
          NULL vehicleNumber,BIN_TO_UUID(n.created_by) createdBy,n.created_at createdAt,
          BIN_TO_UUID(n.updated_by) updatedBy,n.updated_at updatedAt
        FROM bw_inbound_notice n
        WHERE n.tenant_id=#{__verifiedTenant}
        <choose>
          <when test="uuid != null">AND n.id=UUID_TO_BIN(#{uuid})</when>
          <otherwise>AND n.notice_no=#{id}</otherwise>
        </choose>
      </when>
      <when test="kind == 'INBOUND_RECEIPT'">
        SELECT BIN_TO_UUID(r.id) documentNo,BIN_TO_UUID(n.id) parentId,
          BIN_TO_UUID(c.warehouse_id) warehouseId,BIN_TO_UUID(n.owner_id) ownerId,BIN_TO_UUID(nl.product_id) productId,
          BIN_TO_UUID(NULL) otherOwnerId,BIN_TO_UUID(r.unit_id) unitId,r.quantity quantity,
          NULL completedQuantity,NULL reserved,BIN_TO_UUID(r.measurement_id) evidenceId,
          NULL vehicleNumber,BIN_TO_UUID(r.created_by) createdBy,r.created_at createdAt,
          BIN_TO_UUID(r.updated_by) updatedBy,r.updated_at updatedAt
        FROM bw_inbound_receipt r
        JOIN bw_inbound_custody c ON c.tenant_id=r.tenant_id AND c.id=r.custody_id
        JOIN bw_inbound_notice n ON n.tenant_id=c.tenant_id AND n.id=c.notice_id
        JOIN bw_inbound_notice_line nl ON nl.tenant_id=c.tenant_id AND nl.notice_id=c.notice_id AND nl.id=c.notice_line_id
        WHERE r.tenant_id=#{__verifiedTenant} AND r.id=UUID_TO_BIN(#{id})
      </when>
      <when test="kind == 'INBOUND_TITLE'">
        SELECT BIN_TO_UUID(d.receipt_id) documentNo,BIN_TO_UUID(r.id) parentId,
          BIN_TO_UUID(c.warehouse_id) warehouseId,BIN_TO_UUID(n.owner_id) ownerId,BIN_TO_UUID(nl.product_id) productId,
          BIN_TO_UUID(d.owner_id) otherOwnerId,BIN_TO_UUID(r.unit_id) unitId,d.quantity quantity,
          NULL completedQuantity,NULL reserved,BIN_TO_UUID(d.evidence_id) evidenceId,
          NULL vehicleNumber,BIN_TO_UUID(d.created_by) createdBy,d.created_at createdAt,
          BIN_TO_UUID(d.updated_by) updatedBy,d.updated_at updatedAt
        FROM bw_inbound_title d
        JOIN bw_inbound_receipt r ON r.tenant_id=d.tenant_id AND r.id=d.receipt_id
        JOIN bw_inbound_custody c ON c.tenant_id=r.tenant_id AND c.id=r.custody_id
        JOIN bw_inbound_notice n ON n.tenant_id=c.tenant_id AND n.id=c.notice_id
        JOIN bw_inbound_notice_line nl ON nl.tenant_id=c.tenant_id AND nl.notice_id=c.notice_id AND nl.id=c.notice_line_id
        WHERE d.tenant_id=#{__verifiedTenant} AND d.receipt_id=UUID_TO_BIN(#{id})
      </when>
      <when test="kind == 'INBOUND_QUALITY'">
        SELECT BIN_TO_UUID(d.receipt_id) documentNo,BIN_TO_UUID(r.id) parentId,
          BIN_TO_UUID(c.warehouse_id) warehouseId,BIN_TO_UUID(n.owner_id) ownerId,BIN_TO_UUID(nl.product_id) productId,
          BIN_TO_UUID(NULL) otherOwnerId,BIN_TO_UUID(r.unit_id) unitId,d.quantity quantity,
          NULL completedQuantity,NULL reserved,BIN_TO_UUID(d.evidence_id) evidenceId,
          NULL vehicleNumber,BIN_TO_UUID(d.created_by) createdBy,d.created_at createdAt,
          BIN_TO_UUID(d.updated_by) updatedBy,d.updated_at updatedAt
        FROM bw_inbound_quality_release d
        JOIN bw_inbound_receipt r ON r.tenant_id=d.tenant_id AND r.id=d.receipt_id
        JOIN bw_inbound_custody c ON c.tenant_id=r.tenant_id AND c.id=r.custody_id
        JOIN bw_inbound_notice n ON n.tenant_id=c.tenant_id AND n.id=c.notice_id
        JOIN bw_inbound_notice_line nl ON nl.tenant_id=c.tenant_id AND nl.notice_id=c.notice_id AND nl.id=c.notice_line_id
        WHERE d.tenant_id=#{__verifiedTenant} AND d.receipt_id=UUID_TO_BIN(#{id})
      </when>
      <when test="kind == 'TITLE_TRANSFER'">
        SELECT BIN_TO_UUID(d.id) documentNo,BIN_TO_UUID(r.id) parentId,
          BIN_TO_UUID(d.warehouse_id) warehouseId,BIN_TO_UUID(d.from_owner_id) ownerId,BIN_TO_UUID(nl.product_id) productId,
          BIN_TO_UUID(d.to_owner_id) otherOwnerId,BIN_TO_UUID(r.unit_id) unitId,d.quantity quantity,
          NULL completedQuantity,NULL reserved,NULL evidenceId,
          NULL vehicleNumber,BIN_TO_UUID(d.created_by) createdBy,d.created_at createdAt,
          BIN_TO_UUID(d.updated_by) updatedBy,d.updated_at updatedAt
        FROM bw_coil_title_transfer d
        JOIN bw_inbound_receipt r ON r.tenant_id=d.tenant_id AND r.id=d.receipt_id
        JOIN bw_inbound_custody c ON c.tenant_id=r.tenant_id AND c.id=r.custody_id
        JOIN bw_inbound_notice n ON n.tenant_id=c.tenant_id AND n.id=c.notice_id
        JOIN bw_inbound_notice_line nl ON nl.tenant_id=c.tenant_id AND nl.notice_id=c.notice_id AND nl.id=c.notice_line_id
        WHERE d.tenant_id=#{__verifiedTenant} AND d.id=UUID_TO_BIN(#{id})
      </when>
      <when test="kind == 'OUTBOUND_NOTICE'">
        SELECT BIN_TO_UUID(n.id) documentNo,BIN_TO_UUID(NULL) parentId,
          BIN_TO_UUID(n.warehouse_id) warehouseId,BIN_TO_UUID(n.owner_id) ownerId,BIN_TO_UUID(n.product_id) productId,
          BIN_TO_UUID(NULL) otherOwnerId,BIN_TO_UUID(n.unit_id) unitId,n.cap quantity,
          n.issued completedQuantity,n.reserved reserved,NULL evidenceId,
          NULL vehicleNumber,BIN_TO_UUID(n.created_by) createdBy,n.created_at createdAt,
          BIN_TO_UUID(n.updated_by) updatedBy,n.updated_at updatedAt
        FROM bw_outbound_notice n

        WHERE n.tenant_id=#{__verifiedTenant} AND n.id=UUID_TO_BIN(#{id})
      </when>
      <when test="kind == 'OUTBOUND_TRIP'">
        SELECT BIN_TO_UUID(t.id) documentNo,BIN_TO_UUID(a.id) parentId,
          BIN_TO_UUID(n.warehouse_id) warehouseId,BIN_TO_UUID(n.owner_id) ownerId,BIN_TO_UUID(n.product_id) productId,
          BIN_TO_UUID(a.pickup_partner_id) otherOwnerId,BIN_TO_UUID(n.unit_id) unitId,t.cap quantity,
          t.issued completedQuantity,t.reserved reserved,NULL evidenceId,
          t.vehicle_number vehicleNumber,BIN_TO_UUID(t.created_by) createdBy,t.created_at createdAt,
          BIN_TO_UUID(t.updated_by) updatedBy,t.updated_at updatedAt
        FROM bw_outbound_trip t
        JOIN bw_pickup_authorization a ON a.tenant_id=t.tenant_id AND a.id=t.authorization_id
        JOIN bw_outbound_notice n ON n.tenant_id=a.tenant_id AND n.id=a.notice_id
        WHERE t.tenant_id=#{__verifiedTenant} AND t.id=UUID_TO_BIN(#{id})
      </when>
      <when test="kind == 'OUTBOUND_CONFIRMATION'">
        SELECT BIN_TO_UUID(h.id) documentNo,BIN_TO_UUID(t.id) parentId,
          BIN_TO_UUID(n.warehouse_id) warehouseId,BIN_TO_UUID(n.owner_id) ownerId,BIN_TO_UUID(n.product_id) productId,
          BIN_TO_UUID(h.pickup_partner_id) otherOwnerId,BIN_TO_UUID(h.unit_id) unitId,h.issued_quantity quantity,
          NULL completedQuantity,NULL reserved,BIN_TO_UUID(h.evidence_id) evidenceId,
          NULL vehicleNumber,BIN_TO_UUID(h.created_by) createdBy,h.created_at createdAt,
          BIN_TO_UUID(h.updated_by) updatedBy,h.updated_at updatedAt
        FROM bw_coil_handover h
        JOIN bw_outbound_trip t ON t.tenant_id=h.tenant_id AND t.id=h.trip_id
        JOIN bw_pickup_authorization a ON a.tenant_id=t.tenant_id AND a.id=t.authorization_id
        JOIN bw_outbound_notice n ON n.tenant_id=a.tenant_id AND n.id=a.notice_id
        WHERE h.tenant_id=#{__verifiedTenant} AND h.id=UUID_TO_BIN(#{id})
      </when>
      <when test="kind == 'HANDOVER_REVERSAL'">
        SELECT BIN_TO_UUID(d.id) documentNo,BIN_TO_UUID(h.id) parentId,
          BIN_TO_UUID(n.warehouse_id) warehouseId,BIN_TO_UUID(n.owner_id) ownerId,BIN_TO_UUID(n.product_id) productId,
          BIN_TO_UUID(h.pickup_partner_id) otherOwnerId,BIN_TO_UUID(d.unit_id) unitId,d.restored_stock quantity,
          NULL completedQuantity,NULL reserved,BIN_TO_UUID(d.evidence_id) evidenceId,
          NULL vehicleNumber,BIN_TO_UUID(d.created_by) createdBy,d.created_at createdAt,
          BIN_TO_UUID(d.updated_by) updatedBy,d.updated_at updatedAt
        FROM bw_coil_handover_reversal d
        JOIN bw_coil_handover h ON h.tenant_id=d.tenant_id AND h.id=d.handover_id
        JOIN bw_outbound_trip t ON t.tenant_id=h.tenant_id AND t.id=h.trip_id
        JOIN bw_pickup_authorization a ON a.tenant_id=t.tenant_id AND a.id=t.authorization_id
        JOIN bw_outbound_notice n ON n.tenant_id=a.tenant_id AND n.id=a.notice_id
        WHERE d.tenant_id=#{__verifiedTenant} AND d.id=UUID_TO_BIN(#{id})
      </when>
      <otherwise>SELECT NULL WHERE #{__verifiedTenant} IS NULL AND FALSE</otherwise>
      </choose></script>
      """)
  Map<String,Object> document(@Param("__verifiedTenant") byte[] tenant,@Param("kind") String kind,@Param("id") String id,
      @Param("uuid") String uuid);

  /** 通知头授权后按稳定行号读取全部明细；单张通知最多 100 行。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions="warehousing.coil.stockbalance:read")
  @Select("""
      SELECT BIN_TO_UUID(l.id) id,l.line_no lineNo,BIN_TO_UUID(l.product_id) productId,
        l.identity_kind identityKind,BIN_TO_UUID(l.identity_id) identityId,l.product_version productVersion,
        BIN_TO_UUID(l.base_unit_id) unitId,l.planned_quantity plannedQuantity,l.approved_quantity approvedQuantity,
        l.received_quantity receivedQuantity,l.remark
      FROM bw_inbound_notice_line l WHERE l.tenant_id=#{__verifiedTenant} AND l.notice_id=UUID_TO_BIN(#{id})
      ORDER BY l.line_no
      """)
  List<Map<String,Object>> inboundLines(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

  /** 接管来源和状态用原确认关联确认，不依赖前端推断或当前库存。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions="warehousing.coil.stockbalance:read")
  @Select("""
      SELECT BIN_TO_UUID(c.id) id,BIN_TO_UUID(c.notice_line_id) lineId,c.arrival_batch_no arrivalBatchNo,
        BIN_TO_UUID(c.warehouse_id) warehouseId,BIN_TO_UUID(c.area_id) areaId,BIN_TO_UUID(c.location_id) locationId,
        c.warehouse_version warehouseVersion,c.area_version areaVersion,c.location_version locationVersion,
        BIN_TO_UUID(r.id) receiptId,BIN_TO_UUID(c.entry_id) entryId
      FROM bw_inbound_custody c LEFT JOIN bw_inbound_receipt r ON r.tenant_id=c.tenant_id AND r.custody_id=c.id
      WHERE c.tenant_id=#{__verifiedTenant} AND c.notice_id=UUID_TO_BIN(#{id})
      ORDER BY c.created_at,c.id LIMIT 101
      """)
  List<Map<String,Object>> inboundCustodies(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

  /** 读取入场业务头，仓库及货主以原通知为准。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions="warehousing.coil.stockbalance:read")
  @Select("""
      SELECT BIN_TO_UUID(e.id) id,BIN_TO_UUID(e.notice_id) noticeId,
        BIN_TO_UUID(n.warehouse_id) warehouseId,BIN_TO_UUID(n.owner_id) ownerId,
        e.vehicle_number vehicleNumber,e.carrier,e.arrived_at arrivedAt,e.remark,
        BIN_TO_UUID(e.created_by) createdBy,e.created_at createdAt,BIN_TO_UUID(e.updated_by) updatedBy,e.updated_at updatedAt
      FROM bw_inbound_entry e JOIN bw_inbound_notice n ON n.tenant_id=e.tenant_id AND n.id=e.notice_id
      WHERE e.tenant_id=#{__verifiedTenant} AND e.id=UUID_TO_BIN(#{id})
      """)
  Map<String,Object> entryHeader(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

  /** 按真实接管读取明细及确认状态，拒收也属于已确认。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions="warehousing.coil.stockbalance:read")
  @Select("""
      SELECT BIN_TO_UUID(c.id) id,BIN_TO_UUID(c.id) custodyId,BIN_TO_UUID(l.id) noticeLineId,l.line_no lineNo,
        BIN_TO_UUID(l.product_id) productId,l.identity_kind identityKind,BIN_TO_UUID(l.identity_id) identityId,
        BIN_TO_UUID(l.base_unit_id) unitId,l.approved_quantity approvedQuantity,l.received_quantity receivedQuantity,
        c.expected_quantity expectedQuantity,BIN_TO_UUID(c.evidence_id) evidenceId,
        BIN_TO_UUID(c.warehouse_id) warehouseId,BIN_TO_UUID(c.area_id) areaId,BIN_TO_UUID(c.location_id) locationId,
        c.warehouse_version warehouseVersion,c.area_version areaVersion,c.location_version locationVersion,
        BIN_TO_UUID(d.receipt_id) receiptId,d.id IS NOT NULL processed
      FROM bw_inbound_custody c
      JOIN bw_inbound_notice_line l ON l.tenant_id=c.tenant_id AND l.notice_id=c.notice_id AND l.id=c.notice_line_id
      LEFT JOIN bw_inbound_receipt_document_line d ON d.tenant_id=c.tenant_id AND d.custody_id=c.id
      WHERE c.tenant_id=#{__verifiedTenant} AND c.entry_id=UUID_TO_BIN(#{id})
      ORDER BY l.line_no,c.id
      """)
  List<Map<String,Object>> entryLines(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

  /** 锁定来源只用于写；只读头以同一事务读取原入场和通知信息。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions="warehousing.coil.stockbalance:read")
  @Select("""
      SELECT BIN_TO_UUID(d.id) id,BIN_TO_UUID(e.notice_id) noticeId,BIN_TO_UUID(d.entry_id) entryId,
        BIN_TO_UUID(n.warehouse_id) warehouseId,BIN_TO_UUID(n.owner_id) ownerId,d.measurement_ref measurementRef,d.remark,
        BIN_TO_UUID(d.created_by) createdBy,d.created_at createdAt,BIN_TO_UUID(d.updated_by) updatedBy,d.updated_at updatedAt
      FROM bw_inbound_receipt_document d JOIN bw_inbound_entry e ON e.tenant_id=d.tenant_id AND e.id=d.entry_id
      JOIN bw_inbound_notice n ON n.tenant_id=e.tenant_id AND n.id=e.notice_id
      WHERE d.tenant_id=#{__verifiedTenant} AND d.id=UUID_TO_BIN(#{id})
      """)
  Map<String,Object> receiptHeader(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

  /** 已确认行按原品规及单位展示；全拒收无过账键和计量证据。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions="warehousing.coil.stockbalance:read")
  @Select("""
      SELECT BIN_TO_UUID(d.id) id,BIN_TO_UUID(d.custody_id) custodyId,BIN_TO_UUID(l.id) noticeLineId,l.line_no lineNo,
        BIN_TO_UUID(l.product_id) productId,l.identity_kind identityKind,BIN_TO_UUID(l.identity_id) identityId,
        BIN_TO_UUID(l.base_unit_id) unitId,l.approved_quantity approvedQuantity,
        d.received_quantity receivedQuantity,d.rejected_quantity rejectionQuantity,d.rejection_reason rejectionReason,
        BIN_TO_UUID(r.measurement_id) measurementId,BIN_TO_UUID(d.receipt_id) receiptId,TRUE processed,
        CASE WHEN d.receipt_id IS NULL THEN 'REJECTED' WHEN s.pending_quantity>0 THEN 'PENDING_QUALITY'
          ELSE 'POSTED' END stockStatus
      FROM bw_inbound_receipt_document_line d JOIN bw_inbound_custody c ON c.tenant_id=d.tenant_id AND c.id=d.custody_id
      JOIN bw_inbound_notice_line l ON l.tenant_id=c.tenant_id AND l.notice_id=c.notice_id AND l.id=c.notice_line_id
      LEFT JOIN bw_inbound_receipt r ON r.tenant_id=d.tenant_id AND r.id=d.receipt_id
      LEFT JOIN bw_stock_receipt s ON s.tenant_id=r.tenant_id AND s.receipt_id=r.id
      WHERE d.tenant_id=#{__verifiedTenant} AND d.document_id=UUID_TO_BIN(#{id})
      ORDER BY l.line_no,d.id
      """)
  List<Map<String,Object>> receiptLines(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

  /** 关联查询只以已授权的入仓通知或车次为锚点，不返回关联卷的库存位置及货权字段。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions="warehousing.coil.stockbalance:read")
  @Select("""
      <script>
      SELECT e.kind,BIN_TO_UUID(e.id) id,BIN_TO_UUID(e.parent_id) parentId,
          BIN_TO_UUID(e.evidence_id) evidenceId,e.quantity
      FROM (
        <choose>
        <when test="kind == 'INBOUND_NOTICE'">
          SELECT 'CUSTODY' kind,c.id,c.notice_id parent_id,c.evidence_id,NULL quantity
          FROM bw_inbound_custody c WHERE c.tenant_id=#{__verifiedTenant} AND c.notice_id=UUID_TO_BIN(#{id})
          UNION ALL
          SELECT 'RECEIPT',r.id,c.id,r.measurement_id,r.quantity
          FROM bw_inbound_custody c JOIN bw_inbound_receipt r ON r.tenant_id=c.tenant_id AND r.custody_id=c.id
          WHERE c.tenant_id=#{__verifiedTenant} AND c.notice_id=UUID_TO_BIN(#{id})
          UNION ALL
          SELECT 'ENTRY',e.id,e.notice_id,NULL,NULL FROM bw_inbound_entry e
          WHERE e.tenant_id=#{__verifiedTenant} AND e.notice_id=UUID_TO_BIN(#{id})
          UNION ALL
          SELECT 'RECEIPT_DOCUMENT',x.id,x.entry_id,NULL,NULL FROM bw_inbound_receipt_document x
          JOIN bw_inbound_entry e ON e.tenant_id=x.tenant_id AND e.id=x.entry_id
          WHERE e.tenant_id=#{__verifiedTenant} AND e.notice_id=UUID_TO_BIN(#{id})
        </when>
        <otherwise>
          SELECT 'ALLOCATION' kind,a.id,a.trip_id parent_id,NULL evidence_id,a.quantity
          FROM bw_coil_allocation a WHERE a.tenant_id=#{__verifiedTenant} AND a.trip_id=UUID_TO_BIN(#{id})
          UNION ALL
          SELECT 'WEIGHING',w.id,w.trip_id,w.ticket_id,w.net
          FROM bw_coil_weigh_review w WHERE w.tenant_id=#{__verifiedTenant} AND w.trip_id=UUID_TO_BIN(#{id})
        </otherwise>
        </choose>
      ) e ORDER BY e.kind,e.id LIMIT 101
      </script>
      """)
  List<Map<String,Object>> documentRelations(@Param("__verifiedTenant") byte[] tenant,
      @Param("kind") String kind,@Param("id") String id);

  /** 原库存身份、当前账面及权利折减，已发出后仍可查该身份。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions="warehousing.coil.stockbalance:read")
  @Select("""
      SELECT k.serial,BIN_TO_UUID(n.id) noticeId,BIN_TO_UUID(c.id) custodyId,
        BIN_TO_UUID(r.measurement_id) measurementId,r.receipt_batch_no receiptBatchNo,BIN_TO_UUID(COALESCE(s.current_warehouse_id,c.warehouse_id)) warehouseId,
        BIN_TO_UUID(COALESCE(s.current_location_id,c.location_id)) locationId,
        BIN_TO_UUID(COALESCE(t.owner_id,n.owner_id)) ownerId,BIN_TO_UUID(nl.product_id) productId,
        BIN_TO_UUID(nl.identity_id) identityId,BIN_TO_UUID(r.unit_id) unitId,s.inbound_quantity-s.shipped_quantity onHand,
        s.pending_quantity pending,COALESCE(t.quantity-t.issued_quantity,0) title,
        BIN_TO_UUID(t.evidence_id) titleEvidenceId,BIN_TO_UUID(q.evidence_id) qualityEvidenceId
      FROM bw_stock_receipt s
      JOIN bw_inbound_receipt r ON r.tenant_id=s.tenant_id AND r.id=s.receipt_id
      JOIN bw_inbound_custody c ON c.tenant_id=r.tenant_id AND c.id=r.custody_id
      JOIN bw_inbound_notice n ON n.tenant_id=c.tenant_id AND n.id=c.notice_id
      JOIN bw_inbound_notice_line nl ON nl.tenant_id=c.tenant_id AND nl.notice_id=c.notice_id AND nl.id=c.notice_line_id
      LEFT JOIN bw_stock_coil k ON k.tenant_id=s.tenant_id AND k.receipt_id=s.receipt_id
      LEFT JOIN bw_inbound_title t ON t.tenant_id=s.tenant_id AND t.receipt_id=s.receipt_id
      LEFT JOIN bw_inbound_quality_release q ON q.tenant_id=s.tenant_id AND q.receipt_id=s.receipt_id
      WHERE s.tenant_id=#{__verifiedTenant} AND s.receipt_id=UUID_TO_BIN(#{id})
      """)
  Map<String,Object> traceStock(@Param("__verifiedTenant") byte[] tenant,@Param("id") String receiptId);

  /** 历史单据键来自固定账表，仓库及权利双方在响应前统一核验。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions="warehousing.coil.stockbalance:read")
  @Select("""
      SELECT events.kind,BIN_TO_UUID(events.doc) id,BIN_TO_UUID(events.parent) parentId,
        BIN_TO_UUID(events.wh) warehouseId,BIN_TO_UUID(events.owner) ownerId,
        BIN_TO_UUID(events.other_owner) otherOwnerId,events.qty quantity,BIN_TO_UUID(events.previous_wh) previousWarehouseId
      FROM (
        SELECT 'INBOUND' kind,n.id doc,c.id parent,c.warehouse_id wh,n.owner_id owner,
          NULL other_owner,r.quantity qty,r.created_at happened,NULL previous_wh
        FROM bw_inbound_receipt r
        JOIN bw_inbound_custody c ON c.tenant_id=r.tenant_id AND c.id=r.custody_id
        JOIN bw_inbound_notice n ON n.tenant_id=c.tenant_id AND n.id=c.notice_id
        WHERE r.tenant_id=#{__verifiedTenant} AND r.id=UUID_TO_BIN(#{id})
        UNION ALL
        SELECT 'TRANSFER',t.id,t.receipt_id,t.warehouse_id,t.from_owner_id,t.to_owner_id,t.quantity,t.created_at,NULL
        FROM bw_coil_title_transfer t WHERE t.tenant_id=#{__verifiedTenant} AND t.receipt_id=UUID_TO_BIN(#{id})
        UNION ALL
        SELECT 'OUTBOUND',n.id,a.id,n.warehouse_id,n.owner_id,NULL,n.cap,n.created_at,NULL
        FROM bw_coil_allocation l JOIN bw_outbound_trip v ON v.tenant_id=l.tenant_id AND v.id=l.trip_id
        JOIN bw_pickup_authorization a ON a.tenant_id=v.tenant_id AND a.id=v.authorization_id
        JOIN bw_outbound_notice n ON n.tenant_id=a.tenant_id AND n.id=a.notice_id
        WHERE l.tenant_id=#{__verifiedTenant} AND l.receipt_id=UUID_TO_BIN(#{id})
        UNION ALL
        SELECT 'ALLOCATION',l.id,v.id,n.warehouse_id,n.owner_id,NULL,l.quantity,l.created_at,NULL
        FROM bw_coil_allocation l JOIN bw_outbound_trip v ON v.tenant_id=l.tenant_id AND v.id=l.trip_id
        JOIN bw_pickup_authorization a ON a.tenant_id=v.tenant_id AND a.id=v.authorization_id
        JOIN bw_outbound_notice n ON n.tenant_id=a.tenant_id AND n.id=a.notice_id
        WHERE l.tenant_id=#{__verifiedTenant} AND l.receipt_id=UUID_TO_BIN(#{id})
        UNION ALL
        SELECT 'WEIGHING',w.id,w.ticket_id,n.warehouse_id,n.owner_id,NULL,d.quantity,w.created_at,NULL
        FROM bw_coil_allocation l JOIN bw_coil_weigh_line d ON d.tenant_id=l.tenant_id AND d.allocation_id=l.id
        JOIN bw_coil_weigh_review w ON w.tenant_id=d.tenant_id AND w.id=d.review_id
        JOIN bw_outbound_trip v ON v.tenant_id=l.tenant_id AND v.id=l.trip_id
        JOIN bw_pickup_authorization a ON a.tenant_id=v.tenant_id AND a.id=v.authorization_id
        JOIN bw_outbound_notice n ON n.tenant_id=a.tenant_id AND n.id=a.notice_id
        WHERE l.tenant_id=#{__verifiedTenant} AND l.receipt_id=UUID_TO_BIN(#{id})
        UNION ALL
        SELECT 'HANDOVER',h.handover_id,h.allocation_id,h.source_warehouse_id,n.owner_id,NULL,h.issued_quantity,h.created_at,NULL
        FROM bw_coil_handover_line h JOIN bw_coil_allocation l ON l.tenant_id=h.tenant_id AND l.id=h.allocation_id
        JOIN bw_outbound_trip v ON v.tenant_id=l.tenant_id AND v.id=l.trip_id
        JOIN bw_pickup_authorization a ON a.tenant_id=v.tenant_id AND a.id=v.authorization_id
        JOIN bw_outbound_notice n ON n.tenant_id=a.tenant_id AND n.id=a.notice_id
        WHERE h.tenant_id=#{__verifiedTenant} AND h.receipt_id=UUID_TO_BIN(#{id})
        UNION ALL
        SELECT 'RETURN',p.return_id,p.handover_id,p.receiving_warehouse_id,n.owner_id,NULL,p.restored_stock,p.created_at,NULL
        FROM bw_coil_partial_return_line p JOIN bw_coil_allocation l ON l.tenant_id=p.tenant_id AND l.id=p.allocation_id
        JOIN bw_outbound_trip v ON v.tenant_id=l.tenant_id AND v.id=l.trip_id
        JOIN bw_pickup_authorization a ON a.tenant_id=v.tenant_id AND a.id=v.authorization_id
        JOIN bw_outbound_notice n ON n.tenant_id=a.tenant_id AND n.id=a.notice_id
        WHERE p.tenant_id=#{__verifiedTenant} AND p.receipt_id=UUID_TO_BIN(#{id})
        UNION ALL
        SELECT 'REVERSAL',rv.id,h.handover_id,h.source_warehouse_id,n.owner_id,NULL,h.stock_deduction,
          rv.created_at,NULL
        FROM bw_coil_handover_line h
        JOIN bw_coil_handover_reversal rv ON rv.tenant_id=h.tenant_id AND rv.handover_id=h.handover_id
        JOIN bw_coil_allocation l ON l.tenant_id=h.tenant_id AND l.id=h.allocation_id
        JOIN bw_outbound_trip v ON v.tenant_id=l.tenant_id AND v.id=l.trip_id
        JOIN bw_pickup_authorization a ON a.tenant_id=v.tenant_id AND a.id=v.authorization_id
        JOIN bw_outbound_notice n ON n.tenant_id=a.tenant_id AND n.id=a.notice_id
        WHERE h.tenant_id=#{__verifiedTenant} AND h.receipt_id=UUID_TO_BIN(#{id})
        UNION ALL
        SELECT evt.kind,evt.doc,evt.parent,evt.wh,n.owner_id,evt.partner,evt.qty,evt.happened,evt.previous_wh
        FROM bw_coil_return_transit_line tl
        JOIN bw_coil_allocation l ON l.tenant_id=tl.tenant_id AND l.id=tl.allocation_id
        JOIN bw_outbound_trip v ON v.tenant_id=l.tenant_id AND v.id=l.trip_id
        JOIN bw_pickup_authorization a ON a.tenant_id=v.tenant_id AND a.id=v.authorization_id
        JOIN bw_outbound_notice n ON n.tenant_id=a.tenant_id AND n.id=a.notice_id
        JOIN LATERAL (
          SELECT 'RETURN_DISPATCH' kind,t.id doc,t.handover_id parent,t.receiving_warehouse_id wh,
            NULL partner,h.stock_deduction qty,t.created_at happened,NULL previous_wh
          FROM bw_coil_return_transit t
          JOIN bw_coil_handover_line h ON h.tenant_id=tl.tenant_id AND h.handover_id=tl.handover_id
            AND h.allocation_id=tl.allocation_id
          WHERE t.tenant_id=tl.tenant_id AND t.id=tl.transit_id
          UNION ALL
          SELECT 'RETURN_REROUTE',rr.id,rr.transit_id,rr.receiving_warehouse_id,NULL,
            h.stock_deduction,rr.created_at,rr.previous_warehouse_id
          FROM bw_coil_return_transit_reroute rr
          JOIN bw_coil_handover_line h ON h.tenant_id=tl.tenant_id AND h.handover_id=tl.handover_id
            AND h.allocation_id=tl.allocation_id
          WHERE rr.tenant_id=tl.tenant_id AND rr.transit_id=tl.transit_id AND rr.receipt_id=tl.receipt_id
          UNION ALL
          SELECT 'RETURN_LOSS',ls.id,ls.transit_id,h.source_warehouse_id,ls.responsible_partner_id,
            ls.book_quantity,ls.created_at,NULL
          FROM bw_coil_return_transit_loss ls
          JOIN bw_coil_handover_line h ON h.tenant_id=tl.tenant_id AND h.handover_id=tl.handover_id
            AND h.allocation_id=tl.allocation_id
          WHERE ls.tenant_id=tl.tenant_id AND ls.transit_id=tl.transit_id AND ls.receipt_id=tl.receipt_id
          UNION ALL
          SELECT 'RETURN_LOSS_REOPENED',ro.id,ro.loss_id,h.source_warehouse_id,ls.responsible_partner_id,
            ls.book_quantity,ro.created_at,NULL
          FROM bw_coil_return_loss_reopening ro
          JOIN bw_coil_return_transit_loss ls ON ls.tenant_id=ro.tenant_id AND ls.id=ro.loss_id
          JOIN bw_coil_handover_line h ON h.tenant_id=tl.tenant_id AND h.handover_id=tl.handover_id
            AND h.allocation_id=tl.allocation_id
          WHERE ro.tenant_id=tl.tenant_id AND ro.transit_id=tl.transit_id AND ro.receipt_id=tl.receipt_id
        ) evt ON TRUE
        WHERE tl.tenant_id=#{__verifiedTenant} AND tl.receipt_id=UUID_TO_BIN(#{id})
      ) events ORDER BY events.happened,events.kind,events.doc
      """)
  @GuardedScope(tenant="__verifiedTenant",operationPermissions="warehousing.coil.stockbalance:read")
  List<Map<String,Object>> traceDocuments(@Param("__verifiedTenant") byte[] tenant,@Param("id") String receiptId);
}
