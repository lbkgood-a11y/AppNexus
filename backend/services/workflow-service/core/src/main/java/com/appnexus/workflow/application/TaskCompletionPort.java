package com.appnexus.workflow.application;

import com.appnexus.contracts.workflow.WorkflowContext;
import com.appnexus.workflow.core.WorkflowModel.Node;
import java.util.Map;

/** 任务办理输出端口；调用必须位于同一实例锁和幂等事务内，不泄露 Flowable 实体或 SQL。 */
public interface TaskCompletionPort {
  /** @param instance 实例标识 @param state 实例当前状态 @param taskState 重读后的任务状态 @param actor 原任务处理者 */
  record Task(String instance,String state,String taskState,String actor) {}
  /** @param context 可信主体 @param id 任务标识 @return 先锁实例再重读并锁任务的事实；不得沿用锁前 OPEN 快照 */
  Task lock(WorkflowContext context,String id);
  /** @param context 可信主体 @param task 已锁任务 @return 当前任职及节点候选规则是否仍允许该主体办理 */
  boolean assigned(WorkflowContext context,Task task);
  /** @param task 已通过状态和当前候选检查的任务 @return 已固定发布模型的节点，不重新读取客户端模型 */
  Node node(Task task);
  /** @param context 可信主体 @param task 已锁任务 @param id 任务标识 @return 当前授权业务快照是否与提交时依据一致；能力缺失按原业务码拒绝 */
  boolean basisUnchanged(WorkflowContext context,Task task,String id);
  /** @param capability 节点稳定能力编码 @return 代码声明的输入字段，能力不存在时拒绝 */
  java.util.Set<String> inputs(String capability);
  /** @param context 可信主体 @param task 已锁任务 @param id 任务标识 @param action 明确办理操作 @param values 已校验白名单的输入 @return 已验证业务输出；pending 结果必须拒绝，不得推进任务 */
  Map<String,String> execute(WorkflowContext context,Task task,String id,String action,Map<String,String> values);
  /** @param context 可信主体 @param task 已锁任务 @param id 任务标识 @param comment 最多2000字意见；只暂存及追加审计，不推进引擎 */
  void save(WorkflowContext context,Task task,String id,String comment);
  /** @param context 可信主体 @param task 已锁任务 @param id 任务标识 @param action 已通过节点规则的提交操作 @param comment 办理意见 @param outputs 已验证输出；任务状态、引擎、拒绝取消和审计在原事务推进 */
  void complete(WorkflowContext context,Task task,String id,String action,String comment,Map<String,String> outputs);
}
