package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.RegisterExternalMeasurementUseCase.Source;
import com.appnexus.platform.authorization.AccessPlan;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Supplier;

/** 外部计量登记的事务、授权和同作业事实读取端口。 */
public interface InboundMeteringOrderRepository {
    /** 已加锁作业、通知及采用的模板事实。 */
    record Task(long version, String status, String noticeStatus, String meteringStrategy, String qualityStrategy) {}
    /** 同作业当前采样和已有结论。 */
    record Sample(UUID id, int version, String decision, boolean sampled) {}
    /** 新计量单事实及实际使用的采样版本。 */
    record Confirmation(UUID id, UUID taskId, UUID sampleId, String meteringNo, Source source,
                        String externalReference, BigDecimal netKg) {}

    /** @param plan 本操作统一授权 @param work 同作业的完整事务 @param <T> 提交结果类型 @return 已提交结果 */
    <T> T transaction(AccessPlan plan, Supplier<T> work);
    /** @param tenant 可信租户 @param task 作业 UUID @return 先锁作业再读取归属事实，不存在时为空 */
    Task lockTask(UUID tenant, UUID task);
    /** @param tenant 可信租户 @param task 已锁作业 UUID @return 已锁现行质检或空 */
    Sample lockSample(UUID tenant, UUID task);
    /** @param tenant 可信租户 @param actor 可信账号 @param confirmation 已验证且已持有作业锁的确认事实 */
    void insertExternal(UUID tenant, UUID actor, Confirmation confirmation);
    /** @param tenant 可信租户 @param actor 可信账号 @param task 已锁作业 UUID @param version 原作业版本 @return 更新行数 */
    int advanceTask(UUID tenant, UUID actor, UUID task, long version);
}
