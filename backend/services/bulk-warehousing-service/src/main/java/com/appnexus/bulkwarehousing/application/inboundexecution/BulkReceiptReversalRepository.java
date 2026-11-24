package com.appnexus.bulkwarehousing.application.inboundexecution;

import java.math.BigDecimal;
import java.util.UUID;

/** 已过账散货冲红的逆向库存事务端口。 */
public interface BulkReceiptReversalRepository {
    /** 锁内核对原入库单明细及库存余额后的冲红依据。 */
    record ReversalInput(UUID stockId, UUID noticeId, UUID noticeLineId, BigDecimal quantity) {}

    /** @param tenant 可信租户 @param task 已锁作业 @param receipt 已锁原单 @return 当前可整笔冲红的库存事实或空 */
    ReversalInput lockReversibleStock(UUID tenant, UUID task, UUID receipt);
    /** @param tenant 可信租户 @param actor 可信账号 @param input 可冲红库存 @return 修改库存行数 */
    int reverseStock(UUID tenant, UUID actor, ReversalInput input);
    /** @param tenant 可信租户 @param actor 可信账号 @param input 来源通知行 @return 修改明细数 */
    int restoreNoticeLine(UUID tenant, UUID actor, ReversalInput input);
    /** @param tenant 可信租户 @param actor 可信账号 @param input 来源通知头 @return 修改通知数 */
    int restoreNotice(UUID tenant, UUID actor, ReversalInput input);
    /** @param tenant 可信租户 @param actor 可信账号 @param task 原作业 @param receipt 原单 @param version 原单行版本 @return 修改单数 */
    int markReceiptReversed(UUID tenant, UUID actor, UUID task, UUID receipt, long version);
    /** @param tenant 可信租户 @param actor 可信账号 @param task 原作业 @param version 作业行版本 @return 修改作业数 */
    int markTaskCorrectionRequired(UUID tenant, UUID actor, UUID task, long version);
}
