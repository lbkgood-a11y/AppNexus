package com.appnexus.workflow.application;

import com.appnexus.contracts.workflow.WorkflowContext;

/** 重分配输出端口；调用必须位于原实例锁、任务锁与幂等事务内。 */
public interface TaskReassignmentPort {
  /** @param open 锁后任务是否 OPEN @param previous 原处理者，用于不可变审计 */
  record Task(boolean open,String previous) {}
  /** @param context 可信上下文 @param id 任务标识 @return 先锁实例再重读锁任务后的事实；未知与跨租户按原协议拒绝 */
  Task lock(WorkflowContext context,String id);
  /** @param context 可信上下文 @param actor 目标处理者 @return 依据固定发布节点重新解析当前任职、自审及候选规则 */
  boolean candidate(WorkflowContext context,String actor);
  /** @param context 可信上下文 @param id 当前任务 @param actor 目标处理者 @return 同实例同节点是否已有其他 OPEN 任务，防止重复办理分支 */
  boolean duplicate(WorkflowContext context,String id,String actor);
  /** @param context 可信上下文 @param id 已锁任务 @param actor 已复核目标 @param previous 原处理者 @param reason 必填受控原因；引擎、业务任务及审计必须共同提交 */
  void assign(WorkflowContext context,String id,String actor,String previous,String reason);
}
