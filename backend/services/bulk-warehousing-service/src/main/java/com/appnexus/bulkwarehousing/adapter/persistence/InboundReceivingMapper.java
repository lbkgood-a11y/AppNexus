package com.appnexus.bulkwarehousing.adapter.persistence;

import com.appnexus.bulkwarehousing.api.InboundReceiving.*;
import com.appnexus.persistence.mybatis.TenantAggregateScoped;
import java.math.BigDecimal;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/** 固定仓储 SQL。所有操作使用应用可信上下文派生的租户，锁只覆盖该租户通知。 */
@Mapper
@TenantAggregateScoped(requireTenantMatch=true,
    tables={"bw_inbound_notice","bw_inbound_notice_line"},
    statements={"issue","issueLine","lockHeader","lockLines","updateHeader","updateLine","deleteLine"})
public interface InboundReceivingMapper {
  /** 写入已签发主单；兼容列仅由旧式单行请求提供。 */
  @com.appnexus.persistence.mybatis.GuardedScope(resource="warehousing.inbound",tenant="__verifiedTenant",recordId="n.id",actions="issue")
  @Insert("""
      INSERT INTO bw_inbound_notice(tenant_id,id,notice_no,warehouse_id,owner_id,product_id,identity_kind,
        identity_id,product_version,base_unit_id,approved_quantity,received_quantity,flow_version,
        issued_by,issued_at,expected_arrival_at,remark,appointment_no,planned_conveyance,driver_name,driver_phone,driver_id_number,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{n.id}),#{n.noticeNo},UUID_TO_BIN(#{n.warehouseId}),UUID_TO_BIN(#{n.ownerPartnerId}),
        UUID_TO_BIN(#{legacyProduct,jdbcType=VARCHAR}),#{legacyKind,jdbcType=VARCHAR},
        UUID_TO_BIN(#{legacyIdentity,jdbcType=VARCHAR}),#{legacyVersion,jdbcType=BIGINT},
        UUID_TO_BIN(#{n.baseUnitId,jdbcType=VARCHAR}),#{n.limit,jdbcType=DECIMAL},
        IF(#{n.limit,jdbcType=DECIMAL} IS NULL,NULL,0),#{flowVersion},UUID_TO_BIN(#{actor}),CURRENT_TIMESTAMP(6),
        #{n.expectedArrivalAt,jdbcType=TIMESTAMP},#{n.remark,jdbcType=VARCHAR},#{n.appointmentNo,jdbcType=VARCHAR},
        #{n.plannedConveyance,jdbcType=VARCHAR},#{n.driver.driverName,jdbcType=VARCHAR},#{n.driver.driverPhone,jdbcType=VARCHAR},#{n.driver.driverIdNumber,jdbcType=VARCHAR},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      """)
  void issue(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("n") Notice notice,
      @Param("flowVersion") int flowVersion,@Param("legacyProduct") String legacyProduct,
      @Param("legacyKind") String legacyKind,@Param("legacyIdentity") String legacyIdentity,
      @Param("legacyVersion") Long legacyVersion);

  /** 在主单签发许可和同一事务中保存明细。 */
  @com.appnexus.persistence.mybatis.GuardedScope(resource="warehousing.inbound",tenant="__verifiedTenant",recordId="noticeId",actions={"issue","update"})
  @Insert("""
      INSERT INTO bw_inbound_notice_line(tenant_id,id,notice_id,product_id,identity_kind,
        identity_id,product_version,base_unit_id,planned_quantity,approved_quantity,remark,contract_no,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{line.id}),UUID_TO_BIN(#{noticeId}),
        UUID_TO_BIN(#{line.item.productId}),#{line.item.kind},UUID_TO_BIN(#{line.item.identityId}),#{line.item.productVersion},
        UUID_TO_BIN(#{line.baseUnitId}),#{line.plannedQuantity,jdbcType=DECIMAL},#{line.limit},
        #{line.remark,jdbcType=VARCHAR},#{line.contractNo,jdbcType=VARCHAR},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      """)
  void issueLine(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("noticeId") String noticeId,@Param("line") NoticeLine line);

  /** 在编辑操作许可下锁定原单，核对原归属和版本。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions="warehousing.inbound:update")
  @Select("SELECT BIN_TO_UUID(warehouse_id) warehouseId,BIN_TO_UUID(owner_id) ownerId,version,status,driver_name driverName,driver_phone driverPhone,driver_id_number driverIdNumber FROM bw_inbound_notice WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) FOR UPDATE")
  Map<String,Object> lockHeader(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

  /** 锁定原单明细，阻止编辑覆盖已实收事实。 */
  @com.appnexus.persistence.mybatis.GuardedScope(tenant="__verifiedTenant",operationPermissions="warehousing.inbound:update")
  @Select("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(product_id) productId,identity_kind kind,BIN_TO_UUID(identity_id) identityId,BIN_TO_UUID(base_unit_id) unitId,received_quantity received FROM bw_inbound_notice_line WHERE tenant_id=#{__verifiedTenant} AND notice_id=UUID_TO_BIN(#{id}) FOR UPDATE")
  java.util.List<Map<String,Object>> lockLines(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

  /** 版本比较、审计与主单修改一起提交，创建审计不覆盖。 */
  @com.appnexus.persistence.mybatis.GuardedScope(resource="warehousing.inbound",tenant="__verifiedTenant",recordId="n.id",actions="update")
  @org.apache.ibatis.annotations.Update("""
    UPDATE bw_inbound_notice SET notice_no=#{n.noticeNo},warehouse_id=UUID_TO_BIN(#{n.warehouseId}),
      owner_id=UUID_TO_BIN(#{n.ownerPartnerId}),expected_arrival_at=#{n.expectedArrivalAt,jdbcType=TIMESTAMP},
      remark=#{n.remark,jdbcType=VARCHAR},appointment_no=#{n.appointmentNo,jdbcType=VARCHAR},
      planned_conveyance=#{n.plannedConveyance,jdbcType=VARCHAR},driver_name=#{n.driver.driverName,jdbcType=VARCHAR},
      driver_phone=#{n.driver.driverPhone,jdbcType=VARCHAR},driver_id_number=#{n.driver.driverIdNumber,jdbcType=VARCHAR},version=version+1,
      updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
    WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{n.id}) AND version=#{version}
    """)
  int updateHeader(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("n") Notice notice,@Param("version") long version);

  /** 按原单与稳定行 ID 更新，保留累计实收量及创建审计。 */
  @com.appnexus.persistence.mybatis.GuardedScope(resource="warehousing.inbound",tenant="__verifiedTenant",recordId="noticeId",actions="update")
  @org.apache.ibatis.annotations.Update("""
    UPDATE bw_inbound_notice_line SET product_id=UUID_TO_BIN(#{line.item.productId}),identity_kind=#{line.item.kind},
      identity_id=UUID_TO_BIN(#{line.item.identityId}),product_version=#{line.item.productVersion},
      base_unit_id=UUID_TO_BIN(#{line.baseUnitId}),planned_quantity=#{line.plannedQuantity,jdbcType=DECIMAL},
      approved_quantity=#{line.limit},remark=#{line.remark,jdbcType=VARCHAR},contract_no=#{line.contractNo,jdbcType=VARCHAR},
      updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
    WHERE tenant_id=#{__verifiedTenant} AND notice_id=UUID_TO_BIN(#{noticeId}) AND id=UUID_TO_BIN(#{line.id})
    """)
  int updateLine(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("noticeId") String noticeId,@Param("line") NoticeLine line);

  /** 只有从未实收的原行才能删除。 */
  @com.appnexus.persistence.mybatis.GuardedScope(resource="warehousing.inbound",tenant="__verifiedTenant",recordId="noticeId",actions="update")
  @Delete("DELETE FROM bw_inbound_notice_line WHERE tenant_id=#{__verifiedTenant} AND notice_id=UUID_TO_BIN(#{noticeId}) AND id=UUID_TO_BIN(#{id}) AND received_quantity=0")
  int deleteLine(@Param("__verifiedTenant") byte[] tenant,@Param("noticeId") String noticeId,@Param("id") String id);
}
