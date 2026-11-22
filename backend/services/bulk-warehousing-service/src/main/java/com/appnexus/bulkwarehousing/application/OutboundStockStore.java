package com.appnexus.bulkwarehousing.application;

import com.appnexus.bulkwarehousing.api.InboundReceiptReferences.Position;
import com.appnexus.bulkwarehousing.api.OutboundStock.Freeze;
import com.appnexus.bulkwarehousing.api.OutboundStock.RestrictionEvidence;
import java.util.List;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.util.function.Supplier;

/** 查询与写入都受库存身份锁保护，容量与权利读取来自已过账收货范围。 */
public interface OutboundStockStore {
  /** 已加锁库存的权威计量、质检、货权和现场状态。 */
  record Facts(String receiptId, Position position, String ownerId, String productId, String identityId,
               String unitId, BigDecimal quantity, boolean qualityReady, boolean titleReady,
               boolean frozen, boolean occupied, boolean coil, boolean dispatched,long titleRevision) {}
  /** 原占用标识及是否已终结。 */
  record Occupation(String receiptId, boolean released, boolean quotaManaged) {}
  /** 原冻结及解除凭证。 */
  record Restriction(String receiptId, String evidenceId, String releaseEvidence) {}

  /** @param work 单次命令或校验 @return 同库事务结果 */
  <T> T transaction(Supplier<T> work);
  /** 锁定身份后读取完整事实，只有事务内允许调用。 */
  Facts lock(VerifiedTenantContext context, String receiptId);
  /** 在已获取的指定仓库栅栏内锁定库存，外仓身份在加锁前拒绝。 */
  Facts lockInWarehouse(VerifiedTenantContext context,String receiptId,String warehouseId);
  /** 锁内按编号查重。 */
  Occupation occupation(VerifiedTenantContext context, String id);
  /** 生成当前唯一的完整库存占用。 */
  void reserve(VerifiedTenantContext context, String id, String receiptId);
  /** 按原配货编号关闭占用并保留历史。 */
  void release(VerifiedTenantContext context, String id);
  /** 从原交接耗用恢复同一把有效卷锁。 */
  void restore(VerifiedTenantContext context,String id,String receiptId);
  /** 读取已在同事务验证实际仓和货主范围的库存身份下的监管记录。 */
  List<RestrictionEvidence> restrictionHistory(VerifiedTenantContext context,String receiptId);
  /** 读取冻结及其状态。 */
  Restriction restriction(VerifiedTenantContext context, String id);
  /** 新增独立冻结。 */
  void freeze(VerifiedTenantContext context, Freeze freeze);
  /** 解除指定冻结，保留原证据。 */
  void lift(VerifiedTenantContext context, Freeze freeze);
}
