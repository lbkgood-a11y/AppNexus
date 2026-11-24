package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.bulkwarehousing.api.InboundReceiptReferences;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** 受锁散货过账的仓储事实及库存写入边界。所有调用均使用同一业务事务。 */
public interface BulkReceiptPostingRepository {
    /** 经固定 SQL 检查同作业计量、当前合格样品、来源通知及散货模板后得到的归属和数量。 */
    record StockInput(UUID receiptLineId, UUID noticeId, UUID noticeLineId, String ownerId,
                      InboundReceiptReferences.Position position, InboundReceiptReferences.Item item,
                      String baseUnitId, String batch, BigDecimal quantity, String qualityState,
                      Integer qualityVersion, String qualitySnapshot,String lpn) {}

    /** @param tenant 可信租户 @param task 已锁作业 @param receipt 已锁草稿 @return 支持的合格单行事实或空 */
    List<StockInput> lockPostableDraft(UUID tenant, UUID task, UUID receipt);

    /** @param tenant 可信租户 @param actor 可信账号 @param input 锁内同通知明细及数量 @return 更新明细数 */
    int consumeNoticeLine(UUID tenant, UUID actor, StockInput input);

    /** @param tenant 可信租户 @param actor 可信账号 @param input 锁内来源通知 @return 更新通知数 */
    int advanceNotice(UUID tenant, UUID actor, StockInput input);
    /** @param tenant 可信租户 @param actor 可信账号 @param notice 多行来源通知 @return 更新通知数 */
    int advanceMultiLineNotice(UUID tenant,UUID actor,UUID notice);

    /** @param tenant 可信租户 @param actor 可信账号 @param input 已复核的库存行和位置 */
    void insertStock(UUID tenant, UUID actor, StockInput input);

    /** @param tenant 可信租户 @param actor 可信账号 @param task 作业 @param receipt 单据 @param version 原版本 @return 更新单数 */
    int markReceiptPosted(UUID tenant, UUID actor, UUID task, UUID receipt, long version, String qualityState);

    /** @param tenant 可信租户 @param actor 可信账号 @param task 作业 @param version 原版本 @return 更新作业数 */
    int markTaskReceived(UUID tenant, UUID actor, UUID task, long version);
}
