package com.appnexus.bulkwarehousing.adapter.persistence;

import com.appnexus.bulkwarehousing.api.CoilOutbound.*;
import com.appnexus.persistence.mybatis.TenantAggregateScoped;
import java.math.BigDecimal;
import java.util.Map;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** 受可信租户拦截的整卷事务语句，调用方固定锁定仓库、通知、授权和车次。 */
@Mapper
@TenantAggregateScoped(requireTenantMatch=true,
    tables={"bw_outbound_warehouse_guard","bw_stock_coil","bw_outbound_notice","bw_pickup_authorization",
      "bw_outbound_trip","bw_coil_allocation","bw_stock_occupation","bw_coil_weigh_review","bw_coil_weigh_line",
      "bw_coil_handover","bw_coil_handover_line","bw_stock_receipt","bw_inbound_title",
      "bw_coil_return_acceptance","bw_coil_return_line","bw_coil_handover_reversal",
      "bw_coil_partial_return","bw_coil_partial_return_line","bw_inbound_quality_release",
      "bw_coil_return_transit","bw_coil_return_transit_line","bw_coil_return_transit_loss","bw_coil_return_loss_reopening","bw_coil_return_transit_reroute","bw_coil_partial_return"},
    statements={"flowVersion","ensureGuard","lockGuard","links","notice","authorization","trip","serial",
      "identify","issue","authorize","increaseCommitment","close","plan","noticeDelta","authorizationDelta",
      "tripDelta","allocated","allocate","recoveryEvents","recoveryOrigins","activeRecoveryTrip","closeRecoveryTrip","activeAllocations","invalidateReview","vehicleLimits",
      "findReview","insertReview","insertWeighLine","currentReview","applyMeasured","otherVehicleLoad","findDispatch","postDispatch","postDispatchLine",
      "deductStock","deductTitle","issueNotice","issueAuthorization","issueTrip","issuedCoils","findReturn","returnFor","insertReturn","insertReturnLine",
      "findReversal","insertReversal","restoreStock","restoreTitle","reverseLines","reverseHeader",
      "restoreNoticeQuota","restoreAuthorizationQuota","restoreTripQuota","findPartialReturn","activeIssuedCoils",
      "insertPartialReturn","insertPartialReturnLine","moveCoilWarehouse","restorePendingStock","reverseSingleLine","subtractTripIssued",
      "findReturnedStock","historicalQualityEvidence","markReturnedQuality","clearReturnedPending","findTransit","transitCoils","findTransitLink",
      "hasTransit","insertTransit","insertTransitLine","receiveTransitLine",
      "findTransitLoss","insertTransitLoss","markTransitLoss","findTransitReopening","insertTransitReopening","clearTransitLoss","findTransitReroute","insertTransitReroute","applyTransitReroute","transitEvidence"})
public interface CoilOutboundMapper {
  /** 栅栏不存在时建立记录。并发创建时锁住已存在记录，无业务变更。 */
  @Insert("""
      INSERT INTO bw_outbound_warehouse_guard(tenant_id,warehouse_id,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{warehouse}),UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      ON DUPLICATE KEY UPDATE warehouse_id=warehouse_id
      """)
  void ensureGuard(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("warehouse") String warehouseId);

  /** 获取该仓库的串行控制点，无栅栏时返回空。 */
  @Select("""
      SELECT BIN_TO_UUID(warehouse_id) FROM bw_outbound_warehouse_guard
      WHERE tenant_id=#{__verifiedTenant} AND warehouse_id=UUID_TO_BIN(#{warehouse}) FOR UPDATE
      """)
  String lockGuard(@Param("__verifiedTenant") byte[] tenant,@Param("warehouse") String warehouseId);

  /** 仅定位不可变的父引用，SQL 路径由本侧固定枚举选择。 */
  @Select("""
      <script><choose>
      <when test="level == 'notice'">
        SELECT BIN_TO_UUID(n.id) noticeId,BIN_TO_UUID(n.warehouse_id) warehouseId
        FROM bw_outbound_notice n WHERE n.tenant_id=#{__verifiedTenant} AND n.id=UUID_TO_BIN(#{id})
      </when>
      <when test="level == 'authorization'">
        SELECT BIN_TO_UUID(n.id) noticeId,BIN_TO_UUID(a.id) authorizationId,BIN_TO_UUID(n.warehouse_id) warehouseId
        FROM bw_pickup_authorization a JOIN bw_outbound_notice n ON n.tenant_id=a.tenant_id AND n.id=a.notice_id
        WHERE a.tenant_id=#{__verifiedTenant} AND a.id=UUID_TO_BIN(#{id})
      </when>
      <otherwise>
        SELECT BIN_TO_UUID(n.id) noticeId,BIN_TO_UUID(a.id) authorizationId,BIN_TO_UUID(n.warehouse_id) warehouseId
        FROM bw_outbound_trip v
        JOIN bw_pickup_authorization a ON a.tenant_id=v.tenant_id AND a.id=v.authorization_id
        JOIN bw_outbound_notice n ON n.tenant_id=a.tenant_id AND n.id=a.notice_id
        WHERE v.tenant_id=#{__verifiedTenant} AND v.id=UUID_TO_BIN(#{id})
      </otherwise>
      </choose></script>
      """)
  Map<String,Object> links(@Param("__verifiedTenant") byte[] tenant,@Param("level") String level,@Param("id") String id);

  /** 当前读，锁定通知及其可执行范围。 */
  @Select("""
      SELECT BIN_TO_UUID(warehouse_id) warehouseId,BIN_TO_UUID(owner_id) ownerId,
        BIN_TO_UUID(product_id) productId,BIN_TO_UUID(identity_id) identityId,BIN_TO_UUID(unit_id) unitId,
        cap,issued,reserved,committed FROM bw_outbound_notice
      WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) FOR UPDATE
      """)
  Map<String,Object> notice(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

  /** 当前读，授权期限使用数据库时钟。 */
  @Select("""
      SELECT BIN_TO_UUID(notice_id) noticeId,BIN_TO_UUID(pickup_partner_id) pickupPartnerId,
        closed_at IS NULL AND expires_at>CURRENT_TIMESTAMP(6) valid,closed_at IS NOT NULL closed,cap,issued,reserved,unassigned,title_watermark titleWatermark
      FROM bw_pickup_authorization WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) FOR UPDATE
      """)
  Map<String,Object> authorization(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

  /** 当前读，锁定配额行。 */
  @Select("""
      SELECT BIN_TO_UUID(authorization_id) authorizationId,vehicle_number vehicleNumber,cap,issued,reserved,
        load_version loadVersion,BIN_TO_UUID(review_id) reviewId,
        BIN_TO_UUID(vehicle_limits_evidence_id) limitsEvidenceId,payload_limit payloadLimit,gross_limit grossLimit,retired_at IS NOT NULL retired,
        BIN_TO_UUID(recovery_receipt_id) recoveryReceiptId,recovery_closed_at IS NOT NULL recoveryClosed
      FROM bw_outbound_trip
      WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) FOR UPDATE
      """)
  Map<String,Object> trip(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

  /** 读取加锁库存的卷号。 */
  @Select("""
      SELECT serial FROM bw_stock_coil WHERE tenant_id=#{__verifiedTenant} AND receipt_id=UUID_TO_BIN(#{id})
      """)
  String serial(@Param("__verifiedTenant") byte[] tenant,@Param("id") String receiptId);

  /** 建立库存和仓内唯一卷号的受控关联。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:identify"})
  @Insert("""
      INSERT INTO bw_stock_coil(tenant_id,receipt_id,warehouse_id,serial,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{c.receiptId}),UUID_TO_BIN(#{warehouse}),#{c.serial},
        UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      """)
  void identify(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("warehouse") String warehouseId,@Param("c") Coil coil);

  /** 写入已核验的范围与批准上限。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:issue"})
  @Insert("""
      INSERT INTO bw_outbound_notice(tenant_id,id,warehouse_id,owner_id,product_id,identity_id,unit_id,cap,flow_version,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{n.id}),UUID_TO_BIN(#{n.warehouseId}),UUID_TO_BIN(#{n.ownerId}),
        UUID_TO_BIN(#{n.item.productId}),UUID_TO_BIN(#{n.item.identityId}),UUID_TO_BIN(#{n.unitId}),#{n.cap},
        #{flowVersion},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      """)
  void issue(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("n") Notice notice,
      @Param("flowVersion") int flowVersion);

  /** 当前已授权事务内读取通知绑定，续办不重新匹配发布策略。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={
      "warehousing.outbound.coil:authorize","warehousing.outbound.coil:plan","warehousing.outbound.coil:allocate",
      "warehousing.outbound.coil:vehicle_limits","warehousing.outbound.coil:review_weighing","warehousing.outbound.coil:handover",
      "warehousing.outbound.coil:pre_reserve","warehousing.outbound.coil:close","warehousing.outbound.coil:cancel",
      "warehousing.outbound.coil:accept_return","warehousing.outbound.coil:reverse_handover",
      "warehousing.outbound.coil:dispatch_return","warehousing.outbound.coil:return_coils",
      "warehousing.outbound.coil:release_returned_quality","warehousing.outbound.coil:reroute_transit_coil",
      "warehousing.outbound.coil:settle_transit_loss","warehousing.outbound.coil:reopen_transit_loss"})
  @Select("SELECT flow_version FROM bw_outbound_notice WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) FOR UPDATE")
  int flowVersion(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

  /** 签发具有独立期限及提货人的授权。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:authorize"})
  @Insert("""
      INSERT INTO bw_pickup_authorization(tenant_id,id,notice_id,pickup_partner_id,expires_at,cap,title_watermark,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{a.id}),UUID_TO_BIN(#{a.noticeId}),UUID_TO_BIN(#{a.pickupPartnerId}),
        #{a.expiresAt},#{a.cap},#{titleWatermark},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      """)
  void authorize(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("a") Authorization auth,@Param("titleWatermark") long titleWatermark);

  /** 已签授权承诺只能在父通知额度内累加。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:authorize","warehousing.outbound.coil:close"})
  @Update("""
      UPDATE bw_outbound_notice SET committed=committed+#{cap},updated_by=UUID_TO_BIN(#{actor}),
        updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id})
      """)
  void increaseCommitment(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("id") String noticeId,@Param("cap") BigDecimal cap);

  /** 关闭记录保留原签发内容及未过账历史。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:close"})
  @Update("""
      UPDATE bw_pickup_authorization SET closed_at=CURRENT_TIMESTAMP(6),updated_at=CURRENT_TIMESTAMP(6),
        updated_by=UUID_TO_BIN(#{actor})
      WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) AND reserved=0 AND closed_at IS NULL
      """)
  int close(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("id") String authorizationId);

  /** 写入车次局部作业上限。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:plan"})
  @Insert("""
      INSERT INTO bw_outbound_trip(tenant_id,id,authorization_id,vehicle_number,cap,recovery_receipt_id,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{v.id}),UUID_TO_BIN(#{v.authorizationId}),#{v.vehicleNumber},#{v.cap},
        UUID_TO_BIN(#{v.recoveryReceiptId,jdbcType=VARCHAR}),UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      """)
  void plan(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("v") Trip trip);

  /** 与授权和车次同步调整通知占用。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:pre_reserve","warehousing.outbound.coil:close","warehousing.outbound.coil:allocate","warehousing.outbound.coil:cancel","warehousing.outbound.coil:review_weighing"})
  @Update("""
      UPDATE bw_outbound_notice SET reserved=reserved+#{delta},updated_by=UUID_TO_BIN(#{actor}),
        updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id})
      """)
  void noticeDelta(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("id") String id,@Param("delta") BigDecimal delta);

  /** 更新授权总占用及授权未派车部分。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:pre_reserve","warehousing.outbound.coil:close","warehousing.outbound.coil:allocate","warehousing.outbound.coil:cancel","warehousing.outbound.coil:review_weighing"})
  @Update("""
      UPDATE bw_pickup_authorization SET reserved=reserved+#{delta},unassigned=unassigned+#{unassigned},
        updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
      WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id})
      """)
  void authorizationDelta(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("id") String id,@Param("delta") BigDecimal delta,@Param("unassigned") BigDecimal unassignedDelta);

  /** 整卷占用或撤配影响目标车次。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:allocate","warehousing.outbound.coil:cancel","warehousing.outbound.coil:review_weighing"})
  @Update("""
      UPDATE bw_outbound_trip SET reserved=reserved+#{delta},updated_by=UUID_TO_BIN(#{actor}),
        updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id})
      """)
  void tripDelta(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("id") String id,@Param("delta") BigDecimal delta);

  /** 重放读取具体配货，不从申请重复推算重量。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:allocate","warehousing.outbound.coil:cancel"})
  @Select("""
      SELECT BIN_TO_UUID(a.trip_id) tripId,BIN_TO_UUID(a.receipt_id) receiptId,a.serial,COALESCE(a.measured_quantity,a.quantity) quantity,
        o.released_at IS NOT NULL released
      FROM bw_coil_allocation a JOIN bw_stock_occupation o ON o.tenant_id=a.tenant_id AND o.id=a.id
      WHERE a.tenant_id=#{__verifiedTenant} AND a.id=UUID_TO_BIN(#{id})
      """)
  Map<String,Object> allocated(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

  /** 保存配货范围及权威重量，与库存占用及三级额度同事务。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:allocate"})
  @Insert("""
      INSERT INTO bw_coil_allocation(tenant_id,id,trip_id,receipt_id,serial,quantity,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{a.id}),UUID_TO_BIN(#{a.tripId}),UUID_TO_BIN(#{a.receiptId}),
        #{serial},#{qty},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      """)
  void allocate(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("a") Allocate allocation,@Param("serial") String serial,@Param("qty") BigDecimal quantity);

  /** 同仓一个实体车辆当前只允许一个有占用的车次范围，避免拆分车辆限载。 */
  @Select("""
      SELECT EXISTS(
        SELECT 1 FROM bw_outbound_trip v
        JOIN bw_pickup_authorization a ON a.tenant_id=v.tenant_id AND a.id=v.authorization_id
        JOIN bw_outbound_notice n ON n.tenant_id=a.tenant_id AND n.id=a.notice_id
        WHERE v.tenant_id=#{__verifiedTenant} AND n.warehouse_id=UUID_TO_BIN(#{warehouse})
          AND v.vehicle_number=#{vehicle} AND v.id<>UUID_TO_BIN(#{tripId}) AND v.reserved>0
      )
      """)
  boolean otherVehicleLoad(@Param("__verifiedTenant") byte[] tenant,@Param("warehouse") String warehouseId,
      @Param("vehicle") String vehicleNumber,@Param("tripId") String tripId);

  /** 从原授权的撤配历史定位上次实际接管发运。已交接导致的占用释放不计作撤配。 */
  @Select("""
      WITH withdrawals AS (
        SELECT a.tenant_id,a.receipt_id,MIN(a.created_at) first_allocation
        FROM bw_outbound_trip v JOIN bw_coil_allocation a ON a.tenant_id=v.tenant_id AND a.trip_id=v.id
        JOIN bw_stock_occupation o ON o.tenant_id=a.tenant_id AND o.id=a.id
        WHERE v.tenant_id=#{__verifiedTenant} AND v.authorization_id=UUID_TO_BIN(#{authorizationId})
          AND (#{afterId,jdbcType=VARCHAR} IS NULL OR a.receipt_id>UUID_TO_BIN(#{afterId,jdbcType=VARCHAR}))
          AND o.released_at IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM bw_coil_handover_line h WHERE h.tenant_id=a.tenant_id AND h.allocation_id=a.id)
        GROUP BY a.tenant_id,a.receipt_id
      ), received AS (
        SELECT w.receipt_id,t.transit_id,ROW_NUMBER() OVER (
          PARTITION BY w.receipt_id ORDER BY p.created_at DESC,p.return_id DESC) position_rank
        FROM withdrawals w JOIN bw_coil_partial_return_line p ON p.tenant_id=w.tenant_id AND p.receipt_id=w.receipt_id
        JOIN bw_coil_return_transit_line t ON t.tenant_id=p.tenant_id AND t.receipt_id=p.receipt_id
          AND t.received_return_id=p.return_id
        WHERE p.receiving_warehouse_id=UUID_TO_BIN(#{warehouseId}) AND p.created_at<=w.first_allocation
          AND p.quality_evidence_id IS NOT NULL
      )
      SELECT BIN_TO_UUID(receipt_id) receiptId,BIN_TO_UUID(transit_id) transitId FROM received
      WHERE position_rank=1 ORDER BY receipt_id LIMIT #{limit}
      """)
  List<Map<String,Object>> recoveryOrigins(@Param("__verifiedTenant") byte[] tenant,@Param("authorizationId") String authorizationId,
      @Param("warehouseId") String warehouseId,@Param("afterId") String afterId,@Param("limit") int limit);

  /** 仓控制锁下，同一授权和原卷只查尚未撤配、退役或交接的续办车次。 */
  @Select("""
      SELECT BIN_TO_UUID(id) FROM bw_outbound_trip
      WHERE tenant_id=#{__verifiedTenant} AND authorization_id=UUID_TO_BIN(#{authorizationId})
        AND recovery_receipt_id=UUID_TO_BIN(#{receiptId}) AND recovery_closed_at IS NULL
        AND retired_at IS NULL AND issued=0
      """)
  String activeRecoveryTrip(@Param("__verifiedTenant") byte[] tenant,@Param("authorizationId") String authorizationId,
      @Param("receiptId") String receiptId);

  /** 只关闭续办车次关联，原车次、复核与审计仍可查阅。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:cancel"})
  @Update("""
      UPDATE bw_outbound_trip SET recovery_closed_at=CURRENT_TIMESTAMP(6),
        updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
      WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id})
        AND recovery_receipt_id IS NOT NULL AND recovery_closed_at IS NULL
      """)
  void closeRecoveryTrip(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("id") String tripId);

  /** 仅从指定授权和卷展开事件。已交接的占用释放不能显示为撤销。 */
  @Select("""
      WITH scoped AS (
        SELECT a.tenant_id,a.id allocation_id,v.id trip_id,v.vehicle_number,v.cap,v.reserved,v.issued,v.review_id,
          a.created_at allocation_at,o.released_at
        FROM bw_outbound_trip v
        JOIN bw_coil_allocation a ON a.tenant_id=v.tenant_id AND a.trip_id=v.id
        JOIN bw_stock_occupation o ON o.tenant_id=a.tenant_id AND o.id=a.id
        WHERE v.tenant_id=#{__verifiedTenant} AND v.authorization_id=UUID_TO_BIN(#{authorizationId})
          AND a.receipt_id=UUID_TO_BIN(#{receiptId})
      ), events AS (
        SELECT v.id trip_id,v.vehicle_number,v.cap,v.reserved,v.issued,'PLANNED' kind,
          v.id,NULL evidence_id,v.created_at at_time
        FROM bw_outbound_trip v WHERE v.tenant_id=#{__verifiedTenant}
          AND v.authorization_id=UUID_TO_BIN(#{authorizationId}) AND v.recovery_receipt_id=UUID_TO_BIN(#{receiptId})
        UNION ALL
        SELECT s.trip_id,s.vehicle_number,s.cap,s.reserved,s.issued,'ALLOCATED',
          s.allocation_id id,NULL evidence_id,s.allocation_at at_time
        FROM scoped s
        UNION ALL
        SELECT s.trip_id,s.vehicle_number,s.cap,s.reserved,s.issued,'CANCELLED',s.allocation_id,NULL,s.released_at
        FROM scoped s WHERE s.released_at IS NOT NULL AND NOT EXISTS (
          SELECT 1 FROM bw_coil_handover_line h WHERE h.tenant_id=s.tenant_id AND h.allocation_id=s.allocation_id)
        UNION ALL
        SELECT s.trip_id,s.vehicle_number,s.cap,s.reserved,s.issued,
          CASE WHEN w.state='HELD' THEN 'HELD_REVIEW' WHEN s.review_id=w.id THEN 'REVIEW' ELSE 'INVALID_REVIEW' END,
          w.id,w.ticket_id,w.created_at
        FROM scoped s JOIN bw_coil_weigh_line l ON l.tenant_id=s.tenant_id AND l.allocation_id=s.allocation_id
        JOIN bw_coil_weigh_review w ON w.tenant_id=l.tenant_id AND w.id=l.review_id
        UNION ALL
        SELECT s.trip_id,s.vehicle_number,s.cap,s.reserved,s.issued,
          CASE WHEN h.reversed_at IS NULL THEN 'HANDOVER' ELSE 'REVERSED_HANDOVER' END,
          h.handover_id,g.evidence_id,h.created_at
        FROM scoped s JOIN bw_coil_handover_line h ON h.tenant_id=s.tenant_id AND h.allocation_id=s.allocation_id
        JOIN bw_coil_handover g ON g.tenant_id=h.tenant_id AND g.id=h.handover_id
      )
      SELECT BIN_TO_UUID(trip_id) tripId,vehicle_number vehicleNumber,cap,reserved,issued,kind,BIN_TO_UUID(id) id,
        BIN_TO_UUID(evidence_id) evidenceId,DATE_FORMAT(at_time,'%Y-%m-%dT%H:%i:%s.%fZ') occurredAt
      FROM events ORDER BY at_time,kind,id
      """)
  List<Map<String,Object>> recoveryEvents(@Param("__verifiedTenant") byte[] tenant,
      @Param("authorizationId") String authorizationId,@Param("receiptId") String receiptId);

  /** 获取当前车次有效配货，按库存身份排序后逐卷加锁。 */
  @Select("""
      SELECT BIN_TO_UUID(a.id) id,BIN_TO_UUID(a.trip_id) tripId,BIN_TO_UUID(a.receipt_id) receiptId,
        a.serial,COALESCE(a.measured_quantity,a.quantity) quantity
      FROM bw_coil_allocation a JOIN bw_stock_occupation o ON o.tenant_id=a.tenant_id AND o.id=a.id
      WHERE a.tenant_id=#{__verifiedTenant} AND a.trip_id=UUID_TO_BIN(#{tripId}) AND o.released_at IS NULL
      ORDER BY a.receipt_id
      """)
  List<Map<String,Object>> activeAllocations(@Param("__verifiedTenant") byte[] tenant,@Param("tripId") String tripId);

  /** 作业范围变化只清除当前指针，历史磅单与失败原因继续保留。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:allocate","warehousing.outbound.coil:cancel"})
  @Update("""
      UPDATE bw_outbound_trip SET load_version=load_version+1,review_id=NULL,
        updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
      WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{tripId})
      """)
  void invalidateReview(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("tripId") String tripId);

  /** 保存核验凭证及限值，之前的复核结论失去当前效力。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:vehicle_limits"})
  @Update("""
      UPDATE bw_outbound_trip SET vehicle_limits_evidence_id=UUID_TO_BIN(#{l.evidenceId}),
        payload_limit=#{l.payloadLimit},gross_limit=#{l.grossLimit},load_version=load_version+1,review_id=NULL,
        updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
      WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{l.tripId})
      """)
  void vehicleLimits(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("l") VehicleLimits limits);

  /** 根据可信租户和复核编号读取历史结论。 */
  @Select("""
      SELECT BIN_TO_UUID(trip_id) tripId,state,net,gross,reasons,load_version loadVersion,request_digest digest
      FROM bw_coil_weigh_review WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id})
      """)
  Map<String,Object> findReview(@Param("__verifiedTenant") byte[] tenant,@Param("id") String reviewId);

  /** 插入复核，采用已经锁住的车次限值凭证。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:review_weighing"})
  @Insert("""
      INSERT INTO bw_coil_weigh_review(tenant_id,id,trip_id,ticket_id,load_version,limits_evidence_id,
        gross,tare,net,state,reasons,request_digest,created_by,updated_by)
      SELECT #{__verifiedTenant},UUID_TO_BIN(#{w.id}),id,UUID_TO_BIN(#{w.ticketId}),#{r.loadVersion},
        vehicle_limits_evidence_id,#{w.gross},#{w.tare},#{r.net},#{r.state},#{reasons},#{digest},
        UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor})
      FROM bw_outbound_trip WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{w.tripId})
      """)
  void insertReview(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("w") Weighing input,@Param("r") Review result,@Param("reasons") String reasons,
      @Param("digest") String digest);

  /** 保留复核所用的每卷实际分摊，含未通过复核的分摊证据。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:review_weighing"})
  @Insert("""
      INSERT INTO bw_coil_weigh_line(tenant_id,review_id,allocation_id,quantity,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{reviewId}),UUID_TO_BIN(#{c.allocationId}),#{c.quantity},
        UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      """)
  void insertWeighLine(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("reviewId") String reviewId,@Param("c") WeighedCoil coil);

  /** 写入当前复核，包括不通过时阻止使用旧的通过结论。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:review_weighing"})
  @Update("""
      UPDATE bw_outbound_trip SET review_id=UUID_TO_BIN(#{r.id}),updated_by=UUID_TO_BIN(#{actor}),
        updated_at=CURRENT_TIMESTAMP(6)
      WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{r.tripId}) AND load_version=#{r.loadVersion}
      """)
  int currentReview(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("r") Review result);

  /** 只有通过复核才更新已占用重量；初始配货重量保留。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:review_weighing"})
  @Update("""
      UPDATE bw_coil_allocation SET measured_quantity=#{c.quantity},updated_by=UUID_TO_BIN(#{actor}),
        updated_at=CURRENT_TIMESTAMP(6)
      WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{c.allocationId})
      """)
  void applyMeasured(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("c") WeighedCoil coil);

  /** 读取已经提交的门岗交接，用于同键重放。 */
  @Select("""
      SELECT BIN_TO_UUID(trip_id) tripId,issued_quantity issued,stock_deduction deduction,
        BIN_TO_UUID(unit_id) unitId,request_digest digest,reversed_at IS NOT NULL reversed FROM bw_coil_handover
      WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id})
      """)
  Map<String,Object> findDispatch(@Param("__verifiedTenant") byte[] tenant,@Param("id") String handoverId);

  /** 保存实际交接事实和采用的复核、单位及受控门岗凭证。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:handover"})
  @Insert("""
      INSERT INTO bw_coil_handover(tenant_id,id,trip_id,review_id,evidence_id,pickup_partner_id,
        issued_quantity,stock_deduction,unit_id,request_digest,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{h.id}),UUID_TO_BIN(#{h.tripId}),UUID_TO_BIN(#{h.reviewId}),
        UUID_TO_BIN(#{h.evidenceId}),UUID_TO_BIN(#{h.pickupPartnerId}),#{d.issuedQuantity},#{d.stockDeduction},
        UUID_TO_BIN(#{d.unitId}),#{digest},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      """)
  void postDispatch(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("h") Handover handover,@Param("d") Dispatch result,@Param("digest") String digest);

  /** 分开记录同一卷的实测核销量和账面扣减量。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:handover"})
  @Insert("""
      INSERT INTO bw_coil_handover_line(tenant_id,handover_id,allocation_id,receipt_id,
        source_warehouse_id,source_location_id,issued_quantity,stock_deduction,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{id}),UUID_TO_BIN(#{allocationId}),UUID_TO_BIN(#{receiptId}),
        UUID_TO_BIN(#{warehouse}),UUID_TO_BIN(#{location}),#{actual},#{book},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      """)
  void postDispatchLine(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("id") String handoverId,@Param("allocationId") String allocationId,@Param("receiptId") String receiptId,
      @Param("warehouse") String sourceWarehouseId,@Param("location") String sourceLocationId,
      @Param("actual") BigDecimal actual,@Param("book") BigDecimal book);

  /** 整卷交出扣全量账面库存，只可成功一次。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:handover"})
  @Update("""
      UPDATE bw_stock_receipt SET shipped_quantity=#{book},updated_by=UUID_TO_BIN(#{actor}),
        updated_at=CURRENT_TIMESTAMP(6)
      WHERE tenant_id=#{__verifiedTenant} AND receipt_id=UUID_TO_BIN(#{id})
        AND shipped_quantity=0 AND pending_quantity=0 AND inbound_quantity=#{book}
      """)
  int deductStock(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("id") String receiptId,@Param("book") BigDecimal book);

  /** 同步扣减在库货权份额，保留原登记和依据。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:handover"})
  @Update("""
      UPDATE bw_inbound_title SET issued_quantity=#{book},updated_by=UUID_TO_BIN(#{actor}),
        updated_at=CURRENT_TIMESTAMP(6)
      WHERE tenant_id=#{__verifiedTenant} AND receipt_id=UUID_TO_BIN(#{id}) AND owner_id=UUID_TO_BIN(#{owner})
        AND quantity=#{book} AND issued_quantity=0 AND effective_at<=CURRENT_TIMESTAMP(6)
      """)
  int deductTitle(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("id") String receiptId,@Param("owner") String ownerId,@Param("book") BigDecimal book);

  /** 实发吨额从通知有效占用转为已出。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:handover"})
  @Update("""
      UPDATE bw_outbound_notice SET reserved=reserved-#{qty},issued=issued+#{qty},
        updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
      WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) AND reserved>=#{qty}
      """)
  int issueNotice(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("id") String noticeId,@Param("qty") BigDecimal quantity);

  /** 实发只扣已派车授权占用，不消费未派车预留。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:handover"})
  @Update("""
      UPDATE bw_pickup_authorization SET reserved=reserved-#{qty},issued=issued+#{qty},
        updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
      WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) AND reserved-unassigned>=#{qty}
        AND closed_at IS NULL AND expires_at>CURRENT_TIMESTAMP(6)
      """)
  int issueAuthorization(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("id") String authorizationId,@Param("qty") BigDecimal quantity);

  /** 已完成整车交接时车次有效占用全量转实发。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:handover"})
  @Update("""
      UPDATE bw_outbound_trip SET reserved=0,issued=#{qty},
        updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
      WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) AND issued=0 AND reserved=#{qty}
      """)
  int issueTrip(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("id") String tripId,@Param("qty") BigDecimal quantity);

  /** 读取原交接的实测、账面明细，库存锁按稳定 UUID 顺序获取。 */
  @Select("""
      SELECT BIN_TO_UUID(allocation_id) allocationId,BIN_TO_UUID(receipt_id) receiptId,issued_quantity issued,
        stock_deduction deduction FROM bw_coil_handover_line
      WHERE tenant_id=#{__verifiedTenant} AND handover_id=UUID_TO_BIN(#{id}) ORDER BY receipt_id
      """)
  List<Map<String,Object>> issuedCoils(@Param("__verifiedTenant") byte[] tenant,@Param("id") String handoverId);

  /** 查询已受理的退回请求。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:accept_return"})
  @Select("""
      SELECT BIN_TO_UUID(handover_id) handoverId,request_digest digest FROM bw_coil_return_acceptance
      WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id})
      """)
  Map<String,Object> findReturn(@Param("__verifiedTenant") byte[] tenant,@Param("id") String returnId);

  /** 查找原交接唯一的实际退回验收。 */
  @Select("""
      SELECT BIN_TO_UUID(id) FROM bw_coil_return_acceptance
      WHERE tenant_id=#{__verifiedTenant} AND handover_id=UUID_TO_BIN(#{id})
      """)
  String returnFor(@Param("__verifiedTenant") byte[] tenant,@Param("id") String handoverId);

  /** 记录真实退回验收凭证，不提前恢复出仓账。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:accept_return"})
  @Insert("""
      INSERT INTO bw_coil_return_acceptance(tenant_id,id,handover_id,custody_evidence_id,title_evidence_id,
        quality_evidence_id,request_digest,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{r.id}),UUID_TO_BIN(#{r.handoverId}),UUID_TO_BIN(#{r.custodyEvidenceId}),
        UUID_TO_BIN(#{r.titleEvidenceId}),UUID_TO_BIN(#{r.qualityEvidenceId}),#{digest},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      """)
  void insertReturn(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("r") AcceptedReturn request,@Param("digest") String digest);

  /** 保存逐卷实际复接管位置。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:accept_return"})
  @Insert("""
      INSERT INTO bw_coil_return_line(tenant_id,return_id,receipt_id,location_id,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{id}),UUID_TO_BIN(#{c.receiptId}),UUID_TO_BIN(#{c.locationId}),
        UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      """)
  void insertReturnLine(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("id") String returnId,@Param("c") ReturnedCoil coil);

  /** 读取原补偿，重放不再恢复库存或额度。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:reverse_handover"})
  @Select("""
      SELECT BIN_TO_UUID(handover_id) handoverId,kind,restored_quota quota,restored_stock stock,
        restored_reservation reservation,BIN_TO_UUID(unit_id) unitId,request_digest digest
      FROM bw_coil_handover_reversal WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id})
      """)
  Map<String,Object> findReversal(@Param("__verifiedTenant") byte[] tenant,@Param("id") String reversalId);

  /** 附加单独补偿记录，保留原交接重量和审批证据。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:reverse_handover"})
  @Insert("""
      INSERT INTO bw_coil_handover_reversal(tenant_id,id,handover_id,kind,evidence_id,return_id,restored_quota,
        restored_stock,restored_reservation,unit_id,request_digest,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{r.id}),UUID_TO_BIN(#{r.handoverId}),#{r.kind},
        UUID_TO_BIN(#{r.evidenceId}),UUID_TO_BIN(#{r.returnId,jdbcType=VARCHAR}),#{v.restoredQuota},
        #{v.restoredStock},#{v.restoredReservation},UUID_TO_BIN(#{v.unitId}),#{digest},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      """)
  void insertReversal(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("r") ReverseHandover request,@Param("v") Reversal result,@Param("digest") String digest);

  /** 恢复原冲销库存，要求现存出账量等于交接流水的账面量。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:reverse_handover"})
  @Update("""
      UPDATE bw_stock_receipt SET shipped_quantity=0,updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
      WHERE tenant_id=#{__verifiedTenant} AND receipt_id=UUID_TO_BIN(#{id})
        AND shipped_quantity=#{book} AND inbound_quantity=#{book} AND pending_quantity=0
      """)
  int restoreStock(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("id") String id,@Param("book") BigDecimal book);

  /** 复原原货主的账面权利。冻结通过独立记录控制，无需变动。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:reverse_handover","warehousing.outbound.coil:return_coils"})
  @Update("""
      UPDATE bw_inbound_title SET issued_quantity=0,updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
      WHERE tenant_id=#{__verifiedTenant} AND receipt_id=UUID_TO_BIN(#{id}) AND owner_id=UUID_TO_BIN(#{owner})
        AND issued_quantity=#{book} AND quantity=#{book}
      """)
  int restoreTitle(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("id") String id,@Param("owner") String owner,@Param("book") BigDecimal book);

  /** 仅关闭旧交接明细的活动标记，保留原各项重量及审核主体。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:reverse_handover"})
  @Update("""
      UPDATE bw_coil_handover_line SET reversed_at=CURRENT_TIMESTAMP(6),updated_at=CURRENT_TIMESTAMP(6),
        updated_by=UUID_TO_BIN(#{actor})
      WHERE tenant_id=#{__verifiedTenant} AND handover_id=UUID_TO_BIN(#{id}) AND reversed_at IS NULL
      """)
  int reverseLines(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("id") String handoverId);

  /** 原凭证取消活动效力但保留历史。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:reverse_handover","warehousing.outbound.coil:return_coils"})
  @Update("""
      UPDATE bw_coil_handover SET reversed_at=CURRENT_TIMESTAMP(6),updated_at=CURRENT_TIMESTAMP(6),
        updated_by=UUID_TO_BIN(#{actor})
      WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) AND reversed_at IS NULL
      """)
  int reverseHeader(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("id") String handoverId);

  /** 反向变更实际已出量；已关闭授权的承诺中原先保留的已出量一并转回。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:reverse_handover","warehousing.outbound.coil:return_coils"})
  @Update("""
      UPDATE bw_outbound_notice SET issued=issued-#{qty},reserved=reserved+#{hold},committed=committed-#{commitment},
        updated_at=CURRENT_TIMESTAMP(6),updated_by=UUID_TO_BIN(#{actor})
      WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) AND issued>=#{qty} AND committed>=#{commitment}
      """)
  int restoreNoticeQuota(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("id") String id,@Param("qty") BigDecimal quantity,@Param("hold") BigDecimal hold,
      @Param("commitment") BigDecimal commitment);

  /** 撤销原已出核销，不影响其他车次和未派车预留。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:reverse_handover","warehousing.outbound.coil:return_coils"})
  @Update("""
      UPDATE bw_pickup_authorization SET issued=issued-#{qty},reserved=reserved+#{hold},
        updated_at=CURRENT_TIMESTAMP(6),updated_by=UUID_TO_BIN(#{actor})
      WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) AND issued>=#{qty}
      """)
  int restoreAuthorizationQuota(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("id") String id,@Param("qty") BigDecimal quantity,@Param("hold") BigDecimal hold);

  /** 误确认恢复旧装载并作废旧复核，实际退回终止旧车次。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:reverse_handover"})
  @Update("""
      UPDATE bw_outbound_trip SET issued=0,reserved=#{hold},review_id=NULL,load_version=load_version+1,
        retired_at=CASE WHEN #{hold}=0 THEN CURRENT_TIMESTAMP(6) ELSE NULL END,
        updated_at=CURRENT_TIMESTAMP(6),updated_by=UUID_TO_BIN(#{actor})
      WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) AND issued=#{qty} AND reserved=0 AND retired_at IS NULL
      """)
  int restoreTripQuota(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("id") String id,@Param("qty") BigDecimal quantity,@Param("hold") BigDecimal hold);

  /** 读取已记账的退回批次，重放不再过账。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:return_coils","warehousing.outbound.coil:release_returned_quality"})
  @Select("""
      SELECT BIN_TO_UUID(handover_id) handoverId,restored_quota quota,restored_stock stock,
        BIN_TO_UUID(unit_id) unitId,request_digest digest FROM bw_coil_partial_return
      WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id})
      """)
  Map<String,Object> findPartialReturn(@Param("__verifiedTenant") byte[] tenant,@Param("id") String returnId);

  /** 只读取未完成退回的原卷，按库存键保持加锁顺序。 */
  @Select("""
      SELECT BIN_TO_UUID(allocation_id) allocationId,BIN_TO_UUID(receipt_id) receiptId,issued_quantity issued,
        stock_deduction deduction FROM bw_coil_handover_line
      WHERE tenant_id=#{__verifiedTenant} AND handover_id=UUID_TO_BIN(#{id}) AND reversed_at IS NULL
      ORDER BY receipt_id
      """)
  List<Map<String,Object>> activeIssuedCoils(@Param("__verifiedTenant") byte[] tenant,@Param("id") String handoverId);

  /** 固定的本批退回及两种原量。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:return_coils"})
  @Insert("""
      INSERT INTO bw_coil_partial_return(tenant_id,id,handover_id,custody_evidence_id,title_evidence_id,
        restored_quota,restored_stock,unit_id,request_digest,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{r.id}),UUID_TO_BIN(#{r.handoverId}),UUID_TO_BIN(#{r.custodyEvidenceId}),
        UUID_TO_BIN(#{r.titleEvidenceId}),#{v.restoredQuota},#{v.restoredStock},UUID_TO_BIN(#{v.unitId}),#{digest},
        UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      """)
  void insertPartialReturn(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("r") PartialReturn request,@Param("v") PartialReturnResult result,@Param("digest") String digest);

  /** 追加唯一原交接卷的补偿明细。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:return_coils"})
  @Insert("""
      INSERT INTO bw_coil_partial_return_line(tenant_id,return_id,handover_id,allocation_id,receipt_id,
        receiving_warehouse_id,location_id,restored_quota,restored_stock,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{r.id}),UUID_TO_BIN(#{r.handoverId}),UUID_TO_BIN(#{line.allocationId}),
        UUID_TO_BIN(#{line.receiptId}),UUID_TO_BIN(#{warehouse}),UUID_TO_BIN(#{location}),#{line.issued},#{line.book},
        UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      """)
  void insertPartialReturnLine(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("r") PartialReturn request,@Param("line") com.appnexus.bulkwarehousing.application.CoilOutboundStore.IssuedCoil line,
      @Param("warehouse") String warehouseId,@Param("location") String locationId);

  /** 卷号仓内唯一键随库存迁移；目标仓同卷号冲突时整个返仓事务回滚。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:return_coils"})
  @Update("""
      UPDATE bw_stock_coil SET warehouse_id=UUID_TO_BIN(#{destination}),updated_by=UUID_TO_BIN(#{actor}),
        updated_at=CURRENT_TIMESTAMP(6)
      WHERE tenant_id=#{__verifiedTenant} AND receipt_id=UUID_TO_BIN(#{id})
        AND warehouse_id=UUID_TO_BIN(#{source})
      """)
  int moveCoilWarehouse(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("id") String receiptId,@Param("source") String sourceWarehouseId,
      @Param("destination") String receivingWarehouseId);

  /** 真实退回按原账面恢复并全量进入本次待检。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:return_coils"})
  @Update("""
      UPDATE bw_stock_receipt SET shipped_quantity=0,pending_quantity=#{book},
        current_warehouse_id=UUID_TO_BIN(#{p.warehouseId}),
        current_area_id=UUID_TO_BIN(#{p.areaId}),current_location_id=UUID_TO_BIN(#{p.locationId}),
        current_warehouse_version=#{p.warehouseVersion},current_area_version=#{p.areaVersion},
        current_location_version=#{p.locationVersion},updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
      WHERE tenant_id=#{__verifiedTenant} AND receipt_id=UUID_TO_BIN(#{id})
        AND shipped_quantity=#{book} AND inbound_quantity=#{book} AND pending_quantity=0
      """)
  int restorePendingStock(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("id") String receiptId,@Param("book") BigDecimal book,
      @Param("p") com.appnexus.bulkwarehousing.api.InboundReceiptReferences.Position position);

  /** 仅失效所选原卷，其他卷仍保留活动的已出仓状态。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:return_coils"})
  @Update("""
      UPDATE bw_coil_handover_line SET reversed_at=CURRENT_TIMESTAMP(6),updated_at=CURRENT_TIMESTAMP(6),
        updated_by=UUID_TO_BIN(#{actor})
      WHERE tenant_id=#{__verifiedTenant} AND handover_id=UUID_TO_BIN(#{id})
        AND allocation_id=UUID_TO_BIN(#{allocation}) AND reversed_at IS NULL
      """)
  int reverseSingleLine(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("id") String handoverId,@Param("allocation") String allocationId);

  /** 只减本批实测量，保留未退实发。原车不再派货。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:return_coils"})
  @Update("""
      UPDATE bw_outbound_trip SET issued=issued-#{quantity},review_id=NULL,
        retired_at=COALESCE(retired_at,CURRENT_TIMESTAMP(6)),updated_at=CURRENT_TIMESTAMP(6),
        updated_by=UUID_TO_BIN(#{actor})
      WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) AND issued>=#{quantity} AND reserved=0
      """)
  int subtractTripIssued(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("id") String tripId,@Param("quantity") BigDecimal quantity);

  /** 查本批返仓卷账面量及是否已经放行。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:release_returned_quality"})
  @Select("""
      SELECT restored_stock book,BIN_TO_UUID(quality_evidence_id) qualityEvidence,
        BIN_TO_UUID(receiving_warehouse_id) warehouseId,BIN_TO_UUID(location_id) locationId
      FROM bw_coil_partial_return_line WHERE tenant_id=#{__verifiedTenant}
        AND return_id=UUID_TO_BIN(#{returnId}) AND receipt_id=UUID_TO_BIN(#{receiptId})
      """)
  Map<String,Object> findReturnedStock(@Param("__verifiedTenant") byte[] tenant,@Param("returnId") String returnId,
      @Param("receiptId") String receiptId);

  /** 同卷不能沿用初次入仓或此前返仓已使用的合格凭证。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:release_returned_quality"})
  @Select("""
      SELECT EXISTS(SELECT 1 FROM bw_inbound_quality_release q
        WHERE q.tenant_id=#{__verifiedTenant} AND q.receipt_id=UUID_TO_BIN(#{q.receiptId})
          AND q.evidence_id=UUID_TO_BIN(#{q.evidenceId}))
        OR EXISTS(SELECT 1 FROM bw_coil_partial_return_line r
        WHERE r.tenant_id=#{__verifiedTenant} AND r.receipt_id=UUID_TO_BIN(#{q.receiptId})
          AND r.quality_evidence_id=UUID_TO_BIN(#{q.evidenceId}))
        OR EXISTS(SELECT 1 FROM bw_coil_return_line l JOIN bw_coil_return_acceptance a
          ON a.tenant_id=l.tenant_id AND a.id=l.return_id
        WHERE l.tenant_id=#{__verifiedTenant} AND l.receipt_id=UUID_TO_BIN(#{q.receiptId})
          AND a.quality_evidence_id=UUID_TO_BIN(#{q.evidenceId}))
      """)
  boolean historicalQualityEvidence(@Param("__verifiedTenant") byte[] tenant,@Param("q") ReturnedQuality request);

  /** 本次返仓独立留存质检证据，原入仓记录继续保留。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:release_returned_quality"})
  @Update("""
      UPDATE bw_coil_partial_return_line SET quality_evidence_id=UUID_TO_BIN(#{q.evidenceId}),
        quality_released_at=CURRENT_TIMESTAMP(6),quality_released_by=UUID_TO_BIN(#{actor}),
        updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
      WHERE tenant_id=#{__verifiedTenant} AND return_id=UUID_TO_BIN(#{q.returnId})
        AND receipt_id=UUID_TO_BIN(#{q.receiptId}) AND quality_evidence_id IS NULL
      """)
  int markReturnedQuality(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("q") ReturnedQuality request);

  /** 确切转出本批整卷的待检量，监管限制保持独立。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:release_returned_quality"})
  @Update("""
      UPDATE bw_stock_receipt SET pending_quantity=0,updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
      WHERE tenant_id=#{__verifiedTenant} AND receipt_id=UUID_TO_BIN(#{id})
        AND shipped_quantity=0 AND pending_quantity=#{book}
      """)
  int clearReturnedPending(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("id") String receiptId,@Param("book") BigDecimal book);

  /** 当前租户原发运凭证。 */
  @Select("""
      SELECT BIN_TO_UUID(handover_id) handoverId,BIN_TO_UUID(receiving_warehouse_id) warehouseId,
        BIN_TO_UUID(evidence_id) evidenceId,request_digest digest
      FROM bw_coil_return_transit WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id})
      """)
  Map<String,Object> findTransit(@Param("__verifiedTenant") byte[] tenant,@Param("id") String transitId);

  /** 按原交接读取两种重量和卷号快照。未接管的发运卷仍返回空接管批次。 */
  @Select("""
      SELECT BIN_TO_UUID(t.receipt_id) receiptId,a.serial,h.stock_deduction book,h.issued_quantity measured,
        BIN_TO_UUID(t.received_return_id) returnId,BIN_TO_UUID(t.active_loss_id) lossId,
        BIN_TO_UUID(COALESCE(rr.receiving_warehouse_id,d.receiving_warehouse_id)) warehouseId,
        BIN_TO_UUID(t.active_reroute_id) rerouteId
      FROM bw_coil_return_transit_line t
      JOIN bw_coil_handover_line h ON h.tenant_id=t.tenant_id AND h.handover_id=t.handover_id
        AND h.allocation_id=t.allocation_id
      JOIN bw_coil_allocation a ON a.tenant_id=t.tenant_id AND a.id=t.allocation_id
      JOIN bw_coil_return_transit d ON d.tenant_id=t.tenant_id AND d.id=t.transit_id
      LEFT JOIN bw_coil_return_transit_reroute rr ON rr.tenant_id=t.tenant_id AND rr.id=t.active_reroute_id
      WHERE t.tenant_id=#{__verifiedTenant} AND t.transit_id=UUID_TO_BIN(#{id})
      ORDER BY t.receipt_id
      """)
  List<Map<String,Object>> transitCoils(@Param("__verifiedTenant") byte[] tenant,@Param("id") String transitId);

  /** 从原交接的唯一卷键找在途批次及接管结果。 */
  @Select("""
      SELECT BIN_TO_UUID(t.transit_id) transitId,BIN_TO_UUID(t.received_return_id) returnId,
        BIN_TO_UUID(t.active_loss_id) lossId,BIN_TO_UUID(t.active_reroute_id) rerouteId,
        BIN_TO_UUID(COALESCE(rr.receiving_warehouse_id,d.receiving_warehouse_id)) warehouseId
      FROM bw_coil_return_transit_line t
      JOIN bw_coil_return_transit d ON d.tenant_id=t.tenant_id AND d.id=t.transit_id
      LEFT JOIN bw_coil_return_transit_reroute rr ON rr.tenant_id=t.tenant_id AND rr.id=t.active_reroute_id
      WHERE t.tenant_id=#{__verifiedTenant}
        AND t.handover_id=UUID_TO_BIN(#{handoverId}) AND t.receipt_id=UUID_TO_BIN(#{receiptId})
      """)
  Map<String,Object> findTransitLink(@Param("__verifiedTenant") byte[] tenant,@Param("handoverId") String handoverId,
      @Param("receiptId") String receiptId);

  /** 一旦登记过在途，原交接不能改由整车旧流程处理。 */
  @Select("""
      SELECT EXISTS(SELECT 1 FROM bw_coil_return_transit WHERE tenant_id=#{__verifiedTenant}
        AND handover_id=UUID_TO_BIN(#{id}))
      """)
  boolean hasTransit(@Param("__verifiedTenant") byte[] tenant,@Param("id") String handoverId);

  /** 保存真实发运，不增加库存或恢复额度。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:dispatch_return"})
  @Insert("""
      INSERT INTO bw_coil_return_transit(tenant_id,id,handover_id,receiving_warehouse_id,
        evidence_id,request_digest,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{d.id}),UUID_TO_BIN(#{d.handoverId}),UUID_TO_BIN(#{d.receivingWarehouseId}),
        UUID_TO_BIN(#{d.evidenceId}),#{digest},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      """)
  void insertTransit(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("d") ReturnDispatch request,@Param("digest") String digest);

  /** 发运范围只能来自已出原卷，同一原交接卷只登记一次。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:dispatch_return"})
  @Insert("""
      INSERT INTO bw_coil_return_transit_line(tenant_id,transit_id,handover_id,allocation_id,receipt_id,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{d.id}),UUID_TO_BIN(#{d.handoverId}),
        UUID_TO_BIN(#{allocation}),UUID_TO_BIN(#{receipt}),UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      """)
  void insertTransitLine(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("d") ReturnDispatch request,@Param("allocation") String allocationId,@Param("receipt") String receiptId);

  /** 接管过账末尾核销本批实际到货卷，必须和位置、待检、货权及额度一并提交。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:return_coils"})
  @Update("""
      UPDATE bw_coil_return_transit_line SET received_return_id=UUID_TO_BIN(#{r.id}),
        received_at=CURRENT_TIMESTAMP(6),updated_at=CURRENT_TIMESTAMP(6),updated_by=UUID_TO_BIN(#{actor})
      WHERE tenant_id=#{__verifiedTenant} AND transit_id=UUID_TO_BIN(#{r.transitId})
        AND handover_id=UUID_TO_BIN(#{r.handoverId}) AND receipt_id=UUID_TO_BIN(#{receipt})
        AND received_return_id IS NULL AND active_loss_id IS NULL
      """)
  int receiveTransitLine(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("r") PartialReturn request,@Param("receipt") String receiptId);

  /** 回读原短损及责任凭证，支持幂等重放。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:settle_transit_loss","warehousing.outbound.coil:reopen_transit_loss"})
  @Select("""
      SELECT BIN_TO_UUID(transit_id) transitId,BIN_TO_UUID(receipt_id) receiptId,
        BIN_TO_UUID(responsible_partner_id) responsiblePartnerId,BIN_TO_UUID(evidence_id) evidenceId,
        reason,book_quantity book,measured_quantity measured,request_digest digest
      FROM bw_coil_return_transit_loss WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id})
      """)
  Map<String,Object> findTransitLoss(@Param("__verifiedTenant") byte[] tenant,@Param("id") String lossId);

  /** 追加经批准的责任及原交接重量，历史始终保留。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:settle_transit_loss"})
  @Insert("""
      INSERT INTO bw_coil_return_transit_loss(tenant_id,id,transit_id,receipt_id,responsible_partner_id,
        evidence_id,reason,book_quantity,measured_quantity,request_digest,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{l.id}),UUID_TO_BIN(#{l.transitId}),UUID_TO_BIN(#{l.receiptId}),
        UUID_TO_BIN(#{l.responsiblePartnerId}),UUID_TO_BIN(#{l.evidenceId}),#{l.reason},
        #{l.bookQuantity},#{l.measuredQuantity},#{digest},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      """)
  void insertTransitLoss(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("l") TransitLoss loss,@Param("digest") String digest);

  /** 仅结清尚未接管、尚无有效短损的原发运卷。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:settle_transit_loss"})
  @Update("""
      UPDATE bw_coil_return_transit_line SET active_loss_id=UUID_TO_BIN(#{l.id}),
        updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
      WHERE tenant_id=#{__verifiedTenant} AND transit_id=UUID_TO_BIN(#{l.transitId}) AND receipt_id=UUID_TO_BIN(#{l.receiptId})
        AND received_return_id IS NULL AND active_loss_id IS NULL
      """)
  int markTransitLoss(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("l") TransitLoss loss);

  /** 查询已记录的补到复核。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:reopen_transit_loss"})
  @Select("""
      SELECT BIN_TO_UUID(loss_id) lossId,BIN_TO_UUID(transit_id) transitId,BIN_TO_UUID(evidence_id) evidenceId
      FROM bw_coil_return_loss_reopening WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id})
      """)
  Map<String,Object> findTransitReopening(@Param("__verifiedTenant") byte[] tenant,@Param("id") String reopeningId);

  /** 以独立凭证记录实际补到复核，不修改原短损或仓储账。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:reopen_transit_loss"})
  @Insert("""
      INSERT INTO bw_coil_return_loss_reopening(tenant_id,id,loss_id,transit_id,receipt_id,evidence_id,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{r.id}),UUID_TO_BIN(#{r.lossId}),UUID_TO_BIN(#{r.transitId}),UUID_TO_BIN(#{receipt}),
        UUID_TO_BIN(#{r.evidenceId}),UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      """)
  void insertTransitReopening(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("r") ReopenTransitLoss command,@Param("receipt") String receiptId);

  /** 解除对应的活动短损，卷重新等待接管。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:reopen_transit_loss"})
  @Update("""
      UPDATE bw_coil_return_transit_line SET active_loss_id=NULL,updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
      WHERE tenant_id=#{__verifiedTenant} AND transit_id=UUID_TO_BIN(#{r.transitId}) AND receipt_id=UUID_TO_BIN(#{receipt})
        AND received_return_id IS NULL AND active_loss_id=UUID_TO_BIN(#{r.lossId})
      """)
  int clearTransitLoss(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("r") ReopenTransitLoss command,@Param("receipt") String receiptId);

  /** 读取原改单，不受后续改单或接管影响。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:reroute_transit_coil"})
  @Select("""
      SELECT BIN_TO_UUID(transit_id) transitId,BIN_TO_UUID(receipt_id) receiptId,
        BIN_TO_UUID(previous_warehouse_id) previousId,BIN_TO_UUID(receiving_warehouse_id) receivingId,
        BIN_TO_UUID(evidence_id) evidenceId,reason,request_digest digest
      FROM bw_coil_return_transit_reroute WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id})
      """)
  Map<String,Object> findTransitReroute(@Param("__verifiedTenant") byte[] tenant,@Param("id") String rerouteId);

  /** 追加审核事实，保留原发运头和先前改仓记录。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:reroute_transit_coil"})
  @Insert("""
      INSERT INTO bw_coil_return_transit_reroute(tenant_id,id,transit_id,receipt_id,previous_warehouse_id,
        receiving_warehouse_id,evidence_id,reason,request_digest,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{r.id}),UUID_TO_BIN(#{r.transitId}),UUID_TO_BIN(#{r.receiptId}),
        UUID_TO_BIN(#{r.expectedReceivingWarehouseId}),UUID_TO_BIN(#{r.newReceivingWarehouseId}),
        UUID_TO_BIN(#{r.evidenceId}),#{r.reason},#{digest},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      """)
  void insertTransitReroute(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("r") RerouteTransitCoil command,@Param("digest") String digest);

  /** 仅移动尚未接管、未结清短损、并且仍使用前一规则的卷。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions={"warehousing.outbound.coil:reroute_transit_coil"})
  @Update("""
      UPDATE bw_coil_return_transit_line SET active_reroute_id=UUID_TO_BIN(#{r.id}),
        updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
      WHERE tenant_id=#{__verifiedTenant} AND transit_id=UUID_TO_BIN(#{r.transitId}) AND receipt_id=UUID_TO_BIN(#{r.receiptId})
        AND received_return_id IS NULL AND active_loss_id IS NULL
        AND active_reroute_id <=> UUID_TO_BIN(#{expected,jdbcType=VARCHAR})
      """)
  int applyTransitReroute(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("r") RerouteTransitCoil command,@Param("expected") String expectedRerouteId);

  /** 权威在途处置历史，保留接管凭证和每次异常的独立依据。 */
  @Select("""
      SELECT e.kind,BIN_TO_UUID(e.id) id,BIN_TO_UUID(e.receipt_id) receiptId,
        BIN_TO_UUID(e.evidence_id) evidenceId,BIN_TO_UUID(e.title_id) titleEvidenceId,
        BIN_TO_UUID(e.previous_id) previousWarehouseId,BIN_TO_UUID(e.warehouse_id) warehouseId,e.reason,
        BIN_TO_UUID(e.quality_evidence_id) qualityEvidenceId
      FROM (
        SELECT 'REROUTE' kind,r.id,r.receipt_id,r.evidence_id,NULL title_id,
          r.previous_warehouse_id previous_id,r.receiving_warehouse_id warehouse_id,r.reason,r.created_at,NULL quality_evidence_id
        FROM bw_coil_return_transit_reroute r WHERE r.tenant_id=#{__verifiedTenant} AND r.transit_id=UUID_TO_BIN(#{id})
        UNION ALL
        SELECT 'LOSS',l.id,l.receipt_id,l.evidence_id,NULL,NULL,NULL,l.reason,l.created_at,NULL
        FROM bw_coil_return_transit_loss l WHERE l.tenant_id=#{__verifiedTenant} AND l.transit_id=UUID_TO_BIN(#{id})
        UNION ALL
        SELECT 'REOPEN',x.id,x.receipt_id,x.evidence_id,NULL,NULL,NULL,NULL,x.created_at,NULL
        FROM bw_coil_return_loss_reopening x WHERE x.tenant_id=#{__verifiedTenant} AND x.transit_id=UUID_TO_BIN(#{id})
        UNION ALL
        SELECT 'RECEIVE',p.id,t.receipt_id,p.custody_evidence_id,p.title_evidence_id,NULL,r.receiving_warehouse_id,NULL,p.created_at,r.quality_evidence_id
        FROM bw_coil_return_transit_line t JOIN bw_coil_partial_return p
          ON p.tenant_id=t.tenant_id AND p.id=t.received_return_id
        JOIN bw_coil_partial_return_line r ON r.tenant_id=p.tenant_id AND r.return_id=p.id AND r.receipt_id=t.receipt_id
        WHERE t.tenant_id=#{__verifiedTenant} AND t.transit_id=UUID_TO_BIN(#{id})
      ) e ORDER BY e.created_at,e.kind,e.id,e.receipt_id
      """)
  List<Map<String,Object>> transitEvidence(@Param("__verifiedTenant") byte[] tenant,@Param("id") String transitId);
}
