package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.platform.authorization.AccessPlan;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** 入库过账和冲红的仓储边界，所有访问须绑定同一数据源和统一权限计划。 */
public interface ReceiptExecutionRepository {
    /** 过账或冲红。 */
    enum Action { POST, REVERSE }
    /** 允许执行的入库单状态。 */
    enum State { DRAFT, POSTED, VOIDED, REVERSED }

    /** 作业和目标入库单的锁内状态。 */
    record LockedReceipt(long taskVersion, long receiptVersion, State state) {}

    /** 已提交回执，摘要以 64 位十六进制表示。 */
    record Execution(UUID receiptId, String digest, long resultingVersion) {}

    /**
     * @param work 受控数据库事务内执行的业务操作
     * @param <T> 业务操作结果
     * @return 提交结果
     * @throws RuntimeException 操作或提交失败并传播；操作失败时一并回滚库存、回执和事件
     */
    <T> T transaction(Supplier<T> work);

    /**
     * 按锁顺序先锁作业，随后按同一统一权限计划校验记录范围并锁定目标入库单。
     * @param plan 当前操作的统一权限计划
     * @param taskId 作业 UUID
     * @param receiptId 同作业目标单据 UUID
     * @return 已锁定的可访问记录；无权或不存在时为空
     */
    Optional<LockedReceipt> lock(AccessPlan plan, UUID taskId, UUID receiptId);

    /**
     * 只能在复核授权且持有作业锁时查询。
     * @param context 可信租户主体
     * @param taskId 已加锁作业 UUID
     * @param action 固定操作
     * @param key 本次幂等键，1 至 96 ASCII 字节
     * @return 在相同键下已记录的结果
     */
    Optional<Execution> findExecution(VerifiedTenantContext context, UUID taskId, Action action, String key);

    /**
     * @param context 可信实际账号与租户
     * @param taskId 已加锁作业 UUID
     * @param action 固定操作
     * @param key 本次幂等键，1 至 96 ASCII 字节
     * @param execution 同事务完成的单据版本和请求摘要
     */
    void insertExecution(VerifiedTenantContext context, UUID taskId, Action action, String key, Execution execution);
}
