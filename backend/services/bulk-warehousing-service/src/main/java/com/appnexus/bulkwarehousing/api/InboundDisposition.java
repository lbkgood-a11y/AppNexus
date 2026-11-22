package com.appnexus.bulkwarehousing.api;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.time.Instant;

/** 已过账收货范围的货权登记与质量放行。两类证据独立生效。 */
public interface InboundDisposition {
  /** 已过账实收 UUID，以及经岗位核验的对应权利或质检凭证 UUID。 */
  record Decision(String receiptId, String evidenceId) {}

  /** 权利依据及其生效时点，独立于数据库生成的仓库登记时点。 */
  record TitleDecision(String receiptId, String evidenceId, Instant effectiveAt) {}

  /** 对应收货范围的账务进度，数量使用实收基础单位。 */
  record Status(String receiptId, BigDecimal inboundQuantity, BigDecimal pendingQuantity,
                BigDecimal registeredQuantity, String unitId) {}

  /** @param context 可信租户和成员 @param decision 核验后的货权证据 @return 账务进度 */
  Status registerTitle(VerifiedTenantContext context, TitleDecision decision);

  /** @param context 可信租户和成员 @param decision 覆盖该实收范围的合格证据 @return 账务进度 */
  Status releaseQuality(VerifiedTenantContext context, Decision decision);
}
