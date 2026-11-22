package com.appnexus.bulkwarehousing.adapter.persistence;

import com.appnexus.bulkwarehousing.api.WarehouseBalances.Scope;
import com.appnexus.persistence.mybatis.TenantAggregateScoped;
import com.appnexus.persistence.mybatis.GuardedScope;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/** 分别读取权威实物及单据额度，同一次读取内按可信租户与已授权业务范围限制。 */
@Mapper
@TenantAggregateScoped(requireTenantMatch=true,
    tables={"bw_inbound_title","bw_stock_receipt","bw_inbound_receipt","bw_inbound_custody",
        "bw_inbound_notice","bw_inbound_quality_release","bw_stock_restriction","bw_stock_occupation",
        "bw_outbound_notice","bw_pickup_authorization","bw_stock_coil","bw_coil_title_transfer",
        "bw_coil_allocation","bw_outbound_trip","bw_coil_weigh_line","bw_coil_weigh_review",
        "bw_coil_handover_line","bw_coil_partial_return_line","bw_coil_handover_reversal",
        "bw_coil_return_transit_line","bw_coil_return_transit","bw_coil_return_transit_reroute",
        "bw_coil_return_transit_loss","bw_coil_return_loss_reopening"},
    statements={"physical","authorizations","coils","traceStock","traceDocuments"})
public interface WarehouseBalanceMapper {
  /** 按库位聚合权利余额及通过库存实时状态校验的持有量，库位有效性由应用复核。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions="warehousing.coil.stockbalance:read")
  @Select("""
      <script>
      SELECT BIN_TO_UUID(n.identity_id) identityId,BIN_TO_UUID(r.unit_id) unitId,
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
      LEFT JOIN bw_inbound_quality_release q ON q.tenant_id=s.tenant_id AND q.receipt_id=s.receipt_id
      WHERE t.tenant_id=#{__verifiedTenant} AND t.owner_id=UUID_TO_BIN(#{scope.ownerId})
        AND COALESCE(s.current_warehouse_id,c.warehouse_id)=UUID_TO_BIN(#{scope.warehouseId})
        AND n.product_id=UUID_TO_BIN(#{scope.productId}) AND t.quantity&gt;t.issued_quantity
        AND s.inbound_quantity&gt;s.shipped_quantity AND t.effective_at&lt;=CURRENT_TIMESTAMP(6)
      <if test="scope.identityId != null">AND n.identity_id=UUID_TO_BIN(#{scope.identityId})</if>
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
      SELECT BIN_TO_UUID(s.receipt_id) receiptId,k.serial,BIN_TO_UUID(n.identity_id) identityId,BIN_TO_UUID(r.unit_id) unitId,
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
      LEFT JOIN bw_inbound_quality_release q ON q.tenant_id=s.tenant_id AND q.receipt_id=s.receipt_id
      WHERE t.tenant_id=#{__verifiedTenant} AND t.owner_id=UUID_TO_BIN(#{scope.ownerId})
        AND COALESCE(s.current_warehouse_id,c.warehouse_id)=UUID_TO_BIN(#{scope.warehouseId})
        AND n.product_id=UUID_TO_BIN(#{scope.productId}) AND t.issued_quantity=0
        AND s.shipped_quantity=0 AND t.quantity=s.inbound_quantity AND t.effective_at&lt;=CURRENT_TIMESTAMP(6)
      <if test="scope.identityId != null">AND n.identity_id=UUID_TO_BIN(#{scope.identityId})</if>
      <if test="afterId != null">AND s.receipt_id&gt;UUID_TO_BIN(#{afterId})</if>
      ORDER BY s.receipt_id LIMIT #{limit}
      </script>
      """)
  List<Map<String,Object>> coils(@Param("__verifiedTenant") byte[] tenant,@Param("scope") Scope scope,
      @Param("afterId") String afterId,@Param("limit") int limit);

  /** 原库存身份、当前账面及权利折减，已发出后仍可查该身份。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions="warehousing.coil.stockbalance:read")
  @Select("""
      SELECT k.serial,BIN_TO_UUID(n.id) noticeId,BIN_TO_UUID(c.id) custodyId,
        BIN_TO_UUID(r.measurement_id) measurementId,BIN_TO_UUID(COALESCE(s.current_warehouse_id,c.warehouse_id)) warehouseId,
        BIN_TO_UUID(COALESCE(s.current_location_id,c.location_id)) locationId,
        BIN_TO_UUID(COALESCE(t.owner_id,n.owner_id)) ownerId,BIN_TO_UUID(n.product_id) productId,
        BIN_TO_UUID(n.identity_id) identityId,BIN_TO_UUID(r.unit_id) unitId,s.inbound_quantity-s.shipped_quantity onHand,
        s.pending_quantity pending,COALESCE(t.quantity-t.issued_quantity,0) title,
        BIN_TO_UUID(t.evidence_id) titleEvidenceId,BIN_TO_UUID(q.evidence_id) qualityEvidenceId
      FROM bw_stock_receipt s
      JOIN bw_inbound_receipt r ON r.tenant_id=s.tenant_id AND r.id=s.receipt_id
      JOIN bw_inbound_custody c ON c.tenant_id=r.tenant_id AND c.id=r.custody_id
      JOIN bw_inbound_notice n ON n.tenant_id=c.tenant_id AND n.id=c.notice_id
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
