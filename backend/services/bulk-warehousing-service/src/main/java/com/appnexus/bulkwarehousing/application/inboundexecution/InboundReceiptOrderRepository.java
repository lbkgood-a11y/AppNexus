package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.bulkwarehousing.api.InboundReceiptReferences;
import com.appnexus.platform.authorization.AccessPlan;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** 散货草稿的受控快照读取和事务写入。 */
public interface InboundReceiptOrderRepository {
    /** 已锁作业和来源通知事实。 */
    record Task(long rowVersion, String status, UUID noticeId, UUID warehouseId, UUID ownerId,
                String noticeStatus, boolean bulkMeteringEnabled) {}
    /** 已锁通知行商品、单位与剩余额度。 */
    record NoticeLine(InboundReceiptReferences.Item item, UUID baseUnitId, BigDecimal remaining) {}
    /** 已确认计量证据。 */
    record Meter(UUID id, long rowVersion, BigDecimal physicalNetKg, String method) {}
    /** 现行采样及判定。 */
    record Sample(UUID id, int version, boolean sampled, String decision) {}
    /** 由锁内事实和授权主数据生成的入库草稿。 */
    record Draft(UUID id, UUID lineId, UUID taskId, UUID noticeId, UUID noticeLineId, UUID meteringId,
                 UUID qualityId, String receiptNo, String batchNo, String qualityState, BigDecimal netKg,
                 InboundReceiptReferences.Resolved references, UUID replacesReceiptId, String method) {}
    /** 当前锁内钢铁计量与合格炉批。 */
    record SteelMeter(UUID id,long version,UUID qualityId,String heatNo,String method,BigDecimal physicalNetKg,
                      BigDecimal theoreticalKg,int packages) {}
    /** 每卷或捆的计量、核验及库存位置信息。 */
    record SteelLine(UUID id,UUID noticeLineId,String lpn,String heatNo,String specification,String material,String batch,
                     BigDecimal theoreticalKg,BigDecimal accountingKg,InboundReceiptReferences.Resolved references) {}
    /** 钢铁多行入库草稿。 */
    record SteelDraft(UUID id,UUID taskId,UUID noticeId,UUID meteringId,UUID qualityId,String number,String method,
                      BigDecimal physicalKg,BigDecimal accountingKg,List<SteelLine> lines) {}

    /** @param plan 真实操作许可 @param work 同库事务回调 @param <T> 结果类型 @return 已提交结果 */
    <T> T transaction(AccessPlan plan, Supplier<T> work);
    /** @param tenant 可信租户 @param task 作业 UUID @return 首先加锁的作业及其来源 */
    Task lockTask(UUID tenant, UUID task);
    /** @param tenant 可信租户 @param notice 已锁作业的来源 @param line 请求行 UUID @return 匹配通知的当前明细 */
    NoticeLine lockNoticeLine(UUID tenant, UUID notice, UUID line);
    /** @param tenant 可信租户 @param task 已锁作业 @return 现行质检样品 */
    Sample lockSample(UUID tenant, UUID task);
    /** @param tenant 可信租户 @param task 已锁作业 @return 已确认的物理净重 */
    Meter lockMeter(UUID tenant, UUID task);
    /** @param tenant 可信租户 @param task 已锁作业 @return 最近完成冲红的原单，无历史时为空 */
    UUID lockLatestReversed(UUID tenant, UUID task);
    /** @param tenant 可信租户 @param task 已锁作业 @return 有效双计量及当前合格质检 */
    SteelMeter lockSteelMeter(UUID tenant,UUID task);
    /** @param tenant 可信租户 @param actor 实际账号 @param draft 已校验单头和单行 */
    void insertDraft(UUID tenant, UUID actor, Draft draft, boolean existing);

    /** 已占用槽位的草稿及其要素收集状态。 */
    record ActiveDraft(UUID id, String number, long version, boolean collecting) {}

    /** 作业锁内读取活动草稿。 */
    ActiveDraft lockActiveDraft(UUID tenant, UUID task);

    /** 在同作业锁内创建等待收集要素的头记录。 */
    void openDraft(UUID tenant, UUID actor, UUID task, UUID notice, UUID id, String number);
    /** @param tenant 可信租户 @param actor 实际账号 @param draft 已校验钢铁头及行 */
    void insertSteelDraft(UUID tenant,UUID actor,SteelDraft draft, boolean existing);
    /** @param tenant 可信租户 @param actor 实际账号 @param task 已锁作业 @param version 原作业版本 @return 更新行数 */
    int advanceTask(UUID tenant, UUID actor, UUID task, long version);
}
