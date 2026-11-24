package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.GroundWeighingUseCase.Ticket;
import com.appnexus.bulkwarehousing.domain.inboundexecution.UnloadingFact;
import com.appnexus.platform.authorization.AccessPlan;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

/** 作业锁内地磅证据和受控状态存储。 */
public interface GroundWeighingRepository {
    /** 已锁作业、通知和固定模板。 */
    record Task(long version, String status, String noticeStatus, String meteringStrategy,
                String qualityStrategy, UnloadingFact unloading, Instant arrivedAt, Instant databaseNow) {}
    /** 已登记的毛重及版本化证据。 */
    record Meter(UUID id, long version, String status, BigDecimal grossKg, String station, Instant grossAt) {}
    /** 现行采样和判定。 */
    record Quality(UUID id, int version, String decision, boolean sampled) {}
    /** 阶段转换，仅用例可选择任务和队列值。 */
    record Advance(String state, String queue) {}

    /** @param plan 当前动作权限 @param work 业务事务 @param <T> 结果类型 @return 已提交结果 */
    <T> T transaction(AccessPlan plan, Supplier<T> work);
    /** @param tenant 可信租户 @param task 来源作业 @return 作业优先加锁后的执行事实 */
    Task lockTask(UUID tenant, UUID task);
    /** @param tenant 可信租户 @param task 持有锁的作业 @return 已登记计量或空 */
    Meter lockMeter(UUID tenant, UUID task);
    /** @param tenant 可信租户 @param task 持有锁的作业 @return 已锁现行采样或空 */
    Quality lockQuality(UUID tenant, UUID task);
    /** 保存计量单及唯一毛重票证，来源由前端人工复核操作声明。 */
    void saveGross(UUID tenant, UUID actor, UUID task, UUID meter, String number, Ticket ticket);
    /** 保存监卸与可信审计，原作业版本仍需匹配。 */
    int saveUnload(UUID tenant, UUID actor, UUID task, long version, UnloadingFact fact, Advance advance);
    /** 保存皮重、采用的质检版本及可空的实收净重。 */
    int saveTare(UUID tenant, UUID actor, UUID task, Meter meter, Ticket ticket, UUID quality, BigDecimal net);
    /** 原状态已由持有作业锁的用例验证。 */
    int advance(UUID tenant, UUID actor, UUID task, long version, Advance advance);
}
