package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.InboundSiteCommands.Action;
import java.time.Instant;
import java.util.UUID;

/** 受信设备通道，按不可变命令 UUID 去重。 */
public interface InboundSiteCommandChannel {
    /** 数据来源已授权。票据只含作业号，不发送司机、供应商、车牌或质检内容。 */
    record Job(UUID tenantId,UUID taskId,UUID commandId,Action action,String taskNumber,
               UUID createdBy,Instant createdAt) {}
    /** 设备明确成功或失败后的回执。 */
    record Acknowledgement(String state,Instant acknowledgedAt) {}
    /** @return 是否已配置工作目录 */
    boolean configured();
    /** @return 当前通道是否为模拟模式 */
    boolean simulated();
    /** @return 当前范围内的原始指令，无记录则为空 */
    Job find(UUID tenant,UUID task,UUID command);
    /** 原子通知设备，首次与重试都采用同一指令 UUID。 */
    void offer(Job job,UUID actor,Instant attemptedAt,Instant expiresAt,Instant expectedPreviousAttempt);
    /** @return 上一次已发出通知的时刻 */
    Instant attemptedAt(Job job);
    /** @return 与原指令严格匹配的终态回执 */
    Acknowledgement acknowledgement(Job job);
}
