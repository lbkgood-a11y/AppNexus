package com.appnexus.workflow.application;

import com.appnexus.contracts.workflow.*;
import java.util.Map;

/** 重分配应用协调；运维权限不能绕过节点候选、自审限制或制造重复任务。 */
public final class TaskReassignmentService {
  private final TaskReassignmentPort port;
  /** @param port 原幂等事务中的任务及引擎输出端口 */
  public TaskReassignmentService(TaskReassignmentPort port){this.port=port;}
  /** @param context 已完成 workflow:operate 授权的可信主体 @param id 任务标识 @param actor 目标处理者 @param reason 已检查必填及长度的非敏感原因 @return 原 assigned 结果；拒绝不推进引擎或审计 */
  public Object assign(WorkflowContext context,String id,String actor,String reason){
    var task=port.lock(context,id);
    if(!task.open()||!port.candidate(context,actor)||port.duplicate(context,id,actor))throw new WorkflowRejection("ASSIGNEE_INVALID",409);
    port.assign(context,id,actor,task.previous(),reason);
    return Map.of("assigned",true);
  }
}
