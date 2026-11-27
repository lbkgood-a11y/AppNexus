package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.platform.authorization.AccessPlan;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** 聚合读取的只读查询端口。每个卡片使用自己的授权计划。 */
public interface InboundWorkbenchDetailQueries {
    /** 公共作业票读取的最小授权快照；不含车牌、司机或货主信息。 */
    record Ticket(long version,String status,String taskNo) {}

    /** @param plan 原打印操作的可信计划 @param task 受控作业 UUID
     * @return 同事务已锁定的作业票字段，记录不可见时为空 */
    Ticket lockTicket(AccessPlan plan,UUID task);

    /** 基础作业和盲检状态。 */
    record Task(long version,String stage,String status,boolean blind,String unload,String taskNo) {}
    /** @param work 统一一致性快照内执行的只读工作 @param <T> 结果类型 @return 聚合结果 */
    <T>T read(Supplier<T> work);
    /** @param plan 作业详情权限 @param task 作业 UUID @return 基础记录 */
    Task task(AccessPlan plan,UUID task);
    /** @param plan 非盲检身份专用权限 @param task 作业 UUID @return 后端字段保护前的固定身份投影 */
    Map<String,Object> identity(AccessPlan plan,UUID task);
    /** @param plan 质检卡片权限 @param task 作业 UUID @return 质检卡片 */
    Map<String,Object> quality(AccessPlan plan,UUID task);
    /** @param plan 计量卡片权限 @param task 作业 UUID @return 授权计量事实 */
    Map<String,Object> metering(AccessPlan plan,UUID task);
    /** @param plan 入库卡片权限 @param task 作业 UUID @return 授权入库事实和明细 */
    Map<String,Object> receipt(AccessPlan plan,UUID task);
}
