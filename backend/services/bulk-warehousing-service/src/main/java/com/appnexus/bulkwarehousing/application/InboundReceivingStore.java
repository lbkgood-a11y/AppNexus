package com.appnexus.bulkwarehousing.application;

import com.appnexus.bulkwarehousing.api.InboundReceiving.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.function.Supplier;

/** 固定入仓存储端口。聚合写入及审计参加同一连接上的业务事务。 */
public interface InboundReceivingStore {
  /** 加锁通知及当前累计实收。 */
  record LockedNotice(Notice notice, BigDecimal received) {}
  /** 原实收过账和幂等摘要。 */
  record Posted(Posting posting, String digest) {}

  /** 同一已过账范围的在库、待检及权利和质检证据。 */
  record Stock(String receiptId, String warehouseId, String ownerId, String unitId,
      BigDecimal inbound, BigDecimal pending, String titleEvidence, Instant titleEffectiveAt, String qualityEvidence) {}

  /** 加锁库存并返回来源与当前证据，缺失返回空。 */
  Stock lockStock(VerifiedTenantContext context, String receiptId);
  /** 追加经核验的权利登记，份额对应整条实收行。 */
  void registerTitle(VerifiedTenantContext context, Stock stock, String evidenceId, Instant effectiveAt);
  /** 追加质检证据并转出该范围待检余额。 */
  void releaseQuality(VerifiedTenantContext context, Stock stock, String evidenceId);

  /** @param work 完整用例 @return 同库提交后的结果 */
  <T> T transaction(Supplier<T> work);
  /** 存储已签发的执行范围。 */
  /** @param c 可信租户 @param id 通知标识 @return 已签发通知的固定流程版本 */
  int flowVersion(VerifiedTenantContext c,String id);

  /** @param c 可信租户 @param receiptId 已过账库存身份 @return 原入仓通知的固定流程版本 */
  int receiptFlowVersion(VerifiedTenantContext c,String receiptId);

  void issue(VerifiedTenantContext context, Notice notice,int flowVersion);
  /** 锁定可信租户内的通知，缺失返回空。 */
  LockedNotice lockNotice(VerifiedTenantContext context, String id);
  /** 保存实际接管及可信审计人。 */
  void takeCustody(VerifiedTenantContext context, Custody custody);
  /** 读取可信租户内的接管，缺失返回空。 */
  Custody custody(VerifiedTenantContext context, String id);
  /** 读取原收货结果，用于请求重试。 */
  Posted posted(VerifiedTenantContext context, String custodyId);
  /** 原子追加实收、待检余额及累计量，由调用方持有通知聚合锁。 */
  void post(VerifiedTenantContext context, Receipt receipt, Posting posting, String noticeId,
      String digest, String quantityEvidence);
}
