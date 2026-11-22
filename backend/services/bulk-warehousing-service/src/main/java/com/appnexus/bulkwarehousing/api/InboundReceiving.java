package com.appnexus.bulkwarehousing.api;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;

/** 入仓写入入口。单条通知按基础单位控制累计实收，每笔接管对应一个可独立确认的范围。 */
public interface InboundReceiving {
  /** 已获批准的执行通知，限额为商品基础单位，严格为正。 */
  record Notice(String id, String warehouseId, String ownerPartnerId,
                InboundReceiptReferences.Item item, String baseUnitId, BigDecimal limit) {}

  /** 实际接管的范围及现场证据标识，位置版本由受控主数据提供。 */
  record Custody(String id, String noticeId, InboundReceiptReferences.Position position, String evidenceId) {}

  /** 实收请求。ID 为重试时保持不变的业务键；来源数量及单位须对应所引用的有效计量证据。 */
  record Receipt(String id, String custodyId, String measurementId, InboundReceiptReferences.Request references) {}

  /** 已确认的实收及待检库存过账结果，数量以基础单位表达。 */
  record Posting(String receiptId, String custodyId, BigDecimal quantity, String unitId) {}

  /** @param context 可信租户和成员 @param notice 已获授权的执行范围 */
  void issue(VerifiedTenantContext context, Notice notice);

  /** @param context 可信租户和成员 @param custody 实际控制范围及现场证据 */
  void takeCustody(VerifiedTenantContext context, Custody custody);

  /** @param context 可信租户和成员 @param receipt 经岗位复核的实收依据 @return 已过账结果或同键重放结果 */
  Posting receive(VerifiedTenantContext context, Receipt receipt);
}
