package com.appnexus.bulkwarehousing.api;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.util.List;

/** 出仓库存门禁。以已过账实收范围为保管身份，当前按整个范围排他配货。 */
public interface OutboundStock {
  /** 门禁结果；可发仅表示当前库存条件满足，实际配货时必须再次校验。 */
  enum Gate { AVAILABLE, PENDING_QUALITY, TITLE_UNREGISTERED, FROZEN, OCCUPIED, DISPATCHED }

  /** 独立冻结指令；冻结编号用于精确解除，凭证 UUID 由监管岗位核验。 */
  record Freeze(String id, String receiptId, String evidenceId) {}

  /** 库存身份下的一条冻结及其解除依据，解除凭证为空表示仍有效。 */
  record RestrictionEvidence(String id,String evidenceId,String releaseEvidenceId,
                             String createdBy,String createdAt,String updatedBy,String updatedAt) {}

  /** @param context 已验证租户主体 @param receiptId 库存身份 UUID @return 该身份的冻结和解除历史 */
  List<RestrictionEvidence> restrictionHistory(VerifiedTenantContext context,String receiptId);

  /** 配货占用编号及库存身份 UUID；重复请求必须沿用原编号。 */
  record Hold(String id, String receiptId) {}

  /** 请求级配货结果，数量采用库存基础单位。 */
  record Reservation(String id, String receiptId, BigDecimal quantity, String unitId) {}

  /** @param context 已验证租户主体 @param receiptId 已过账身份 UUID @return 当前门禁结果 */
  Gate check(VerifiedTenantContext context, String receiptId);

  /** @param context 已验证租户主体 @param hold 当前配货意图 @return 已提交的占用 */
  Reservation reserve(VerifiedTenantContext context, Hold hold);

  /** @param context 已验证租户主体 @param hold 原占用编号及库存身份 */
  void release(VerifiedTenantContext context, Hold hold);

  /** @param context 已验证租户主体 @param freeze 已核验冻结指令 */
  void freeze(VerifiedTenantContext context, Freeze freeze);

  /** @param context 已验证租户主体 @param freeze 原冻结编号及解除依据 */
  void lift(VerifiedTenantContext context, Freeze freeze);
}
