package com.appnexus.bulkwarehousing.application;

import com.appnexus.bulkwarehousing.api.InboundReceiving.Notice;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.function.Supplier;

/** 入仓通知的持久化端口，主单和明细须在同一写事务内保存。 */
public interface InboundReceivingStore {
  /** @param work 同一数据源事务内执行的签发操作 @return 操作结果 */
  <T> T transaction(Supplier<T> work);

  /** 已锁原单的授权范围及版本。 */
  record Existing(String warehouseId,String ownerId,long version,String status,java.util.List<String> productIds,com.appnexus.bulkwarehousing.api.InboundReceiving.Driver driver) {}
  /** @param context 可信租户 @param id 通知 UUID @return 锁定原单，不存在时返回空 */
  Existing lock(VerifiedTenantContext context,String id);
  /** @param context 可信租户 @param notice 已验证的新主从内容 @param version 原单版本 */
  void update(VerifiedTenantContext context,Notice notice,long version);

  /** @param context 可信租户主体 @param notice 已验证通知 @param flowVersion 签发时的流程版本 */
  void issue(VerifiedTenantContext context,Notice notice,int flowVersion);
}
