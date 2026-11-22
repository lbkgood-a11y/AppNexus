package com.appnexus.bulkwarehousing.adapter.persistence;

import com.appnexus.bulkwarehousing.api.CoilTitleTransfer.Transfer;
import com.appnexus.persistence.mybatis.TenantAggregateScoped;
import java.math.BigDecimal;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/** 固定货权 SQL。过户和签发在仓库控制锁之后获取同一租户代次锁。 */
@Mapper
@TenantAggregateScoped(requireTenantMatch=true,
    tables={"bw_title_watermark","bw_coil_title_transfer","bw_coil_title_entry","bw_inbound_title"},
    statements={"ensure","watermark","advance","transfer","insertTransfer","entry","assignOwner"})
public interface CoilTitleMapper {
  /** 确保租户代次已初始化，存量权利及授权为零。 */
  @Insert("""
      INSERT INTO bw_title_watermark(tenant_id,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      ON DUPLICATE KEY UPDATE tenant_id=tenant_id
      """)
  void ensure(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor);

  /** 串行化所有需比较的货权转移与授权签发快照。 */
  @Select("SELECT revision FROM bw_title_watermark WHERE tenant_id=#{__verifiedTenant} FOR UPDATE")
  Long watermark(@Param("__verifiedTenant") byte[] tenant);

  /** 同一事务推进单调代次，失败时连同该次代次一起回滚。 */
  @Update("""
      UPDATE bw_title_watermark SET revision=revision+1,updated_by=UUID_TO_BIN(#{actor}),
        updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=#{__verifiedTenant}
      """)
  void advance(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor);

  /** 读取原已提交的过户及其快照。 */
  @Select("""
      SELECT BIN_TO_UUID(receipt_id) receiptId,BIN_TO_UUID(warehouse_id) warehouseId,
        BIN_TO_UUID(location_id) locationId,BIN_TO_UUID(from_owner_id) fromOwnerId,
        BIN_TO_UUID(to_owner_id) toOwnerId,BIN_TO_UUID(unit_id) unitId,quantity,title_revision revision,request_digest digest
      FROM bw_coil_title_transfer WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id})
      """)
  Map<String,Object> transfer(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

  /** 保存经核验的转出转入凭证及保持原位的实物信息。 */
  @com.appnexus.persistence.mybatis.GuardedScope(resource="warehousing.coil.title",tenant="__verifiedTenant",recordId="t.id",actions="transfer",target="t.toOwnerId")
  @Insert("""
      INSERT INTO bw_coil_title_transfer(tenant_id,id,receipt_id,warehouse_id,location_id,from_owner_id,to_owner_id,
        unit_id,quantity,title_revision,evidence_id,request_digest,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{t.id}),UUID_TO_BIN(#{t.receiptId}),UUID_TO_BIN(#{t.warehouseId}),
        UUID_TO_BIN(#{t.locationId}),UUID_TO_BIN(#{t.fromOwnerId}),UUID_TO_BIN(#{t.toOwnerId}),
        UUID_TO_BIN(#{t.unitId}),#{t.quantity},#{t.titleRevision},UUID_TO_BIN(#{evidence}),#{digest},
        UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      """)
  void insertTransfer(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("t") Transfer result,@Param("evidence") String evidenceId,@Param("digest") String digest);

  /** 由应用受控方向分别写入等量转出和转入分录。 */
  @com.appnexus.persistence.mybatis.GuardedScope(resource="warehousing.coil.title",tenant="__verifiedTenant",recordId="id",actions="transfer")
  @Insert("""
      INSERT INTO bw_coil_title_entry(tenant_id,transfer_id,direction,owner_id,signed_quantity,created_by,updated_by)
      VALUES(#{__verifiedTenant},UUID_TO_BIN(#{id}),#{direction},UUID_TO_BIN(#{owner}),#{quantity},
        UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))
      """)
  void entry(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,@Param("id") String transferId,
      @Param("direction") String direction,@Param("owner") String ownerId,@Param("quantity") BigDecimal signedQuantity);

  /** 原库存与初始登记依据保持，只有现行货主和权利代次变化。 */
  @com.appnexus.persistence.mybatis.GuardedScope(resource="warehousing.coil.title",tenant="__verifiedTenant",recordId="t.receiptId",actions="transfer",target="t.toOwnerId")
  @Update("""
      UPDATE bw_inbound_title SET owner_id=UUID_TO_BIN(#{t.toOwnerId}),title_revision=#{t.titleRevision},
        updated_at=CURRENT_TIMESTAMP(6),updated_by=UUID_TO_BIN(#{actor})
      WHERE tenant_id=#{__verifiedTenant} AND receipt_id=UUID_TO_BIN(#{t.receiptId})
        AND owner_id=UUID_TO_BIN(#{t.fromOwnerId}) AND title_revision=#{expected}
        AND issued_quantity=0 AND quantity=#{t.quantity}
      """)
  int assignOwner(@Param("__verifiedTenant") byte[] tenant,@Param("actor") String actor,
      @Param("t") Transfer result,@Param("expected") long expectedRevision);
}
