package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.GroundWeighingUseCase.Ticket;
import com.appnexus.platform.authorization.AccessPlan;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

/** 钢铁双计量只在持有作业锁的受控事务内读写。 */
public interface SteelMeasurementRepository {
    /** 来源作业、当前质检与已发布差率规则。 */
    record Task(long version,String status,UUID inspectionId,BigDecimal maxVariance,boolean bothRequired,
                Instant arrivedAt,Instant now) {}
    /** 确认结果及可核验源证据。 */
    record Measurement(UUID id,UUID taskId,UUID inspectionId,String number,String method,
                       BigDecimal physical,BigDecimal theoretical,BigDecimal ratio,int packages,Ticket gross,Ticket tare) {}

    /** @param plan 统一操作计划 @param work 同事务工作 @param <T> 结果类型 @return 已提交结果 */
    <T> T transaction(AccessPlan plan,Supplier<T> work);
    /** @param tenant 可信租户 @param task 作业 UUID @return 作业锁及确认事实 */
    Task lock(UUID tenant,UUID task);
    /** @param tenant 可信租户 @param actor 可信账号 @param measurement 确认计量及可选双票 */
    void insert(UUID tenant,UUID actor,Measurement measurement);
    /** @param tenant 可信租户 @param actor 可信账号 @param task 作业 UUID @param version 当前作业版本 @return 修改行数 */
    int advance(UUID tenant,UUID actor,UUID task,long version);
}
