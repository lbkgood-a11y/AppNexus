package com.appnexus.bulkwarehousing.adapter.persistence;

import com.appnexus.bulkwarehousing.api.WarehouseBalances;
import com.appnexus.bulkwarehousing.api.WarehouseBalances.NoticeFilter;
import com.appnexus.bulkwarehousing.api.WarehouseBalances.OverviewFilter;
import com.appnexus.persistence.mybatis.TenantAggregateScoped;
import com.appnexus.persistence.mybatis.GuardedScope;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/** 读取历史实物库存和入仓通知，按可信租户与已授权操作限制。 */
@Mapper
@TenantAggregateScoped(requireTenantMatch=true,
    tables={"bw_inbound_notice","bw_inbound_notice_line","bw_stock_receipt"},
    statements={"overview","document","inboundNotices","inboundNoticeProducts","inboundLines"})
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
      SELECT BIN_TO_UUID(s.receipt_id) receiptId,s.serial,
        BIN_TO_UUID(COALESCE(s.current_warehouse_id,s.origin_warehouse_id)) warehouseId,
        BIN_TO_UUID(CASE WHEN s.title_effective_at&lt;=CURRENT_TIMESTAMP(6) THEN s.title_owner_id ELSE s.origin_owner_id END) ownerId,BIN_TO_UUID(s.product_id) productId,
        s.identity_kind identityKind,BIN_TO_UUID(s.identity_id) identityId,s.receipt_batch_no receiptBatchNo,
        BIN_TO_UUID(s.unit_id) unitId,s.inbound_quantity-s.shipped_quantity onHand,s.pending_quantity pending,
        BIN_TO_UUID(s.created_by) createdBy,s.created_at createdAt,BIN_TO_UUID(s.updated_by) updatedBy,s.updated_at updatedAt
      FROM bw_stock_receipt s
      WHERE s.tenant_id=#{__verifiedTenant} AND s.reversed_at IS NULL AND s.inbound_quantity&gt;s.shipped_quantity
      <if test="filter.warehouseId != null">
        AND COALESCE(s.current_warehouse_id,s.origin_warehouse_id)=UUID_TO_BIN(#{filter.warehouseId})
      </if>
      <if test="filter.ownerId != null">AND (CASE WHEN s.title_effective_at&lt;=CURRENT_TIMESTAMP(6) THEN s.title_owner_id ELSE s.origin_owner_id END)=UUID_TO_BIN(#{filter.ownerId})</if>
      <if test="filter.productId != null">AND s.product_id=UUID_TO_BIN(#{filter.productId})</if>
      <if test="afterId != null">AND s.receipt_id&gt;UUID_TO_BIN(#{afterId})</if>
      ORDER BY s.receipt_id LIMIT #{limit}
      </script>
      """)
  List<Map<String,Object>> overview(@Param("__verifiedTenant") byte[] tenant,@Param("filter") OverviewFilter filter,
      @Param("afterId") String afterId,@Param("limit") int limit);

  /** 固定租户内按主键分页扫描通知；可选条件均为绑定值。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions="warehousing.coil.stockbalance:read")
  @Select("""
      <script>
      SELECT n.notice_no documentNo,BIN_TO_UUID(n.id) noticeId,n.status,n.flow_version flowVersion,
        n.issued_at issuedAt,n.expected_arrival_at expectedArrivalAt,n.remark,
        n.appointment_no appointmentNo,n.planned_conveyance plannedConveyance,n.version,
        BIN_TO_UUID(n.warehouse_id) warehouseId,BIN_TO_UUID(n.owner_id) ownerId,
        BIN_TO_UUID(n.created_by) createdBy,n.created_at createdAt,
        BIN_TO_UUID(n.updated_by) updatedBy,n.updated_at updatedAt
      FROM bw_inbound_notice n WHERE n.tenant_id=#{__verifiedTenant}
      <choose>
        <when test="numberId != null">AND n.id=UUID_TO_BIN(#{numberId})</when>
        <when test="f.documentNo != null">AND n.notice_no=#{f.documentNo}</when>
      </choose>
      <if test="f.warehouseId != null">AND n.warehouse_id=UUID_TO_BIN(#{f.warehouseId})</if>
      <if test="f.ownerId != null">AND n.owner_id=UUID_TO_BIN(#{f.ownerId})</if>
      <if test="f.appointmentNo != null">AND n.appointment_no=#{f.appointmentNo}</if>
      <if test="afterId != null">AND n.id &lt; UUID_TO_BIN(#{afterId})</if>
      ORDER BY n.id DESC LIMIT #{limit}
      </script>
      """)
  List<Map<String,Object>> inboundNotices(@Param("__verifiedTenant") byte[] tenant,@Param("f") NoticeFilter filter,
      @Param("numberId") String numberId,@Param("afterId") String afterId,@Param("limit") int limit);

  /** 一次读取候选主单引用的去重商品，便于按整单执行与详情一致的准入。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions="warehousing.coil.stockbalance:read")
  @Select("""
      <script>
      SELECT DISTINCT BIN_TO_UUID(notice_id) noticeId,BIN_TO_UUID(product_id) productId
      FROM bw_inbound_notice_line WHERE tenant_id=#{__verifiedTenant} AND notice_id IN
      <foreach collection="ids" item="id" open="(" separator="," close=")">UUID_TO_BIN(#{id})</foreach>
      </script>
      """)
  List<Map<String,Object>> inboundNoticeProducts(@Param("__verifiedTenant") byte[] tenant,@Param("ids") List<String> ids);

  /** 精确读取入仓通知主表，签发后即可查询。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions="warehousing.coil.stockbalance:read")
  @Select("""
      <script>
        SELECT n.notice_no documentNo,BIN_TO_UUID(n.id) noticeId,n.status,n.flow_version flowVersion,
          n.issued_at issuedAt,n.expected_arrival_at expectedArrivalAt,n.remark,
          n.appointment_no appointmentNo,n.planned_conveyance plannedConveyance,n.version,
          n.driver_name driverName,n.driver_phone driverPhone,n.driver_id_number driverIdNumber,
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
      </script>
      """)
  Map<String,Object> document(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,
      @Param("uuid") String uuid);

  /** 通知头授权后读取全部明细，按创建时间和稳定标识排序；单张通知最多 100 行。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions="warehousing.coil.stockbalance:read")
  @Select("""
      SELECT BIN_TO_UUID(l.id) id,BIN_TO_UUID(l.product_id) productId,
        l.identity_kind identityKind,BIN_TO_UUID(l.identity_id) identityId,l.product_version productVersion,
        BIN_TO_UUID(l.base_unit_id) unitId,l.planned_quantity plannedQuantity,l.approved_quantity approvedQuantity,
        l.received_quantity receivedQuantity,l.remark,l.contract_no contractNo,
        BIN_TO_UUID(l.created_by) createdBy,l.created_at createdAt,
        BIN_TO_UUID(l.updated_by) updatedBy,l.updated_at updatedAt
      FROM bw_inbound_notice_line l WHERE l.tenant_id=#{__verifiedTenant} AND l.notice_id=UUID_TO_BIN(#{id})
      ORDER BY l.created_at,l.id
      """)
  List<Map<String,Object>> inboundLines(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

}
