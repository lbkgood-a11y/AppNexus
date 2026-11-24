package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.platform.authorization.AccessPlan;
import com.appnexus.bulkwarehousing.domain.inboundexecution.LabQualityRules.Metric;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** 质检登记的固定仓储和事务边界。 */
public interface InboundQualityOrderRepository {
    /** 作业状态和来源通知状态，须在作业锁内读取。 */
    record Task(long rowVersion, String status, String noticeStatus, UUID templateId) {}
    /** 已锁定的当前质检单身份和版本。 */
    record Active(UUID id, int qualityVersion, long rowVersion, String status) {}
    /** 新质检单插入事实。编号和条码来自已校验命令。 */
    record Sample(UUID id, UUID taskId, int version, String number, String barcode) {}
    /** 作业固定的实验室指标模板。 */
    record LabTemplate(int version, String mode, Map<String, Metric> metrics) {}
    /** 追溯判定的不可变数值、规则和版本。 */
    record LabDecision(UUID templateId, int templateVersion, String decision,
                       Map<String, BigDecimal> metrics, Map<String, Metric> rules) {}
    /** 钢厂报告及实物核验的可追溯快照。 */
    record SteelEvidence(UUID templateId,String heatNo,String certificateNo,UUID certificateId,UUID appearanceId,
                         boolean mtcVerified,boolean appearanceVerified) {}

    /**
     * @param plan 当前统一授权；绑定到同一数据库事务
     * @param work 按作业、质检的顺序锁定并完整写入的操作
     * @param <T> 提交结果类型
     * @return 已提交结果；失败回滚全部改动
     */
    <T> T transaction(AccessPlan plan, Supplier<T> work);

    /** @param tenant 可信租户 @param task 作业 UUID @return 加锁记录或空 */
    Task lockTask(UUID tenant, UUID task);
    /** @param tenant 可信租户 @param task 已加锁作业 UUID @return 加锁的现行质检版本或空 */
    Active lockActive(UUID tenant, UUID task);
    /** @param tenant 可信租户 @param actor 可信账号 @param task 已加锁作业 UUID @param active 原现行版本 @return 修改行数 */
    int deactivate(UUID tenant, UUID actor, UUID task, Active active);
    /** @param tenant 可信租户 @param actor 可信账号 @param sample 新版本事实 */
    void insert(UUID tenant, UUID actor, Sample sample);
    /** @param tenant 可信租户 @param actor 可信账号 @param task 作业 UUID @param version 锁内原版本 @return 修改行数 */
    int advanceTask(UUID tenant, UUID actor, UUID task, long version);
    /** @param tenant 可信租户 @param template 作业固定模板 UUID @return 数值规则，无规则时为空 */
    LabTemplate readLabTemplate(UUID tenant, UUID template);
    /** @param tenant 可信租户 @param actor 可信账号 @param task 作业 UUID @param active 当前质检 @param decision 判定结果快照 @return 更新行数 */
    int decide(UUID tenant, UUID actor, UUID task, Active active, LabDecision decision);
    /** @param tenant 可信租户 @param actor 可信账号 @param task 作业 UUID @param version 作业原版本 @param failed 是否需要重新卡控 @return 更新行数 */
    int advanceAfterDecision(UUID tenant, UUID actor, UUID task, long version, boolean failed);
    /** @param tenant 可信租户 @param task 已锁作业 @param quality 当前样品 UUID @return 已过账且仍待化验 */
    boolean hasPendingPostedSample(UUID tenant, UUID task, UUID quality);
    /** @param tenant 可信租户 @param template 作业固定模板 @return 允许钢铁文档核验 */
    boolean supportsSteelInspection(UUID tenant, UUID template);
    /** 保存已授权的初次质保书和实物核验记录。 */
    void insertSteelInspection(UUID tenant,UUID actor,UUID task,UUID quality,String qualityNo,String decision,
                               SteelEvidence evidence);
    /** 已加锁作业只推进核验待办及版本。 */
    int advanceAfterSteelInspection(UUID tenant,UUID actor,UUID task,long taskVersion,String decision);
}
