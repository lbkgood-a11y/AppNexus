package com.appnexus.workflow.application;

import com.appnexus.contracts.workflow.WorkflowContext;
import com.appnexus.contracts.workflow.WorkflowRejection;
import static com.appnexus.workflow.core.WorkflowModel.Kind.*;
import java.util.Map;
import java.util.Set;

/** 任务办理应用协调；事务与幂等包裹在入站适配器，业务调用成功前绝不完成引擎任务。 */
public final class TaskCompletionService {
  private final TaskCompletionPort port;
  /** @param port 参加原实例锁和幂等事务的输出适配器 */
  public TaskCompletionService(TaskCompletionPort port){this.port=port;}
  /**
   * 办理已通过入口认证、成员有效性与输入上限检查的任务。
   * @param context 可信上下文，requestId 为原幂等 UUID
   * @param id 任务标识，不能用其他租户任务探测存在性
   * @param action SAVE、SUBMIT、APPROVE 或 REJECT；节点仍独立限制操作
   * @param values 已检查大小上限的文本输入，应用再次校验能力字段和必填项
   * @param comment 已验证最多2000字的意见，不作为表达式执行
   * @return 暂存或完成结果；拒绝必须回滚原事务
   */
  public Object complete(WorkflowContext context,String id,String action,Map<String,String> values,String comment){
    var task=port.lock(context,id);
    if(!"RUNNING".equals(task.state())||!"OPEN".equals(task.taskState())||!context.actorId().equals(task.actor()))fail("TASK_NOT_ACTIVE",409);
    if(!port.assigned(context,task))fail("TASK_ASSIGNMENT_EXPIRED",403);
    var node=port.node(task);
    if(node.kind()==REVIEW&&!Set.of("APPROVE","REJECT").contains(action)
        ||node.kind()==HANDLE&&!Set.of("SAVE","SUBMIT").contains(action))fail("ACTION_INVALID",400);
    if(!port.basisUnchanged(context,task,id))fail("APPROVAL_BASIS_CHANGED",409);
    Map<String,String> out=Map.of();
    if(node.capability()!=null&&!node.capability().isBlank()){
      if(!port.inputs(node.capability()).containsAll(values.keySet()))fail("INPUT_FIELD_INVALID",400);
      if(!action.equals("SAVE")&&node.required()!=null&&node.required().stream().anyMatch(key->values.getOrDefault(key,"").isBlank()))fail("REQUIRED_INPUT",400);
      out=port.execute(context,task,id,action,values);
    }else if(!values.isEmpty())fail("INPUT_FIELD_INVALID",400);
    if(action.equals("SAVE")){port.save(context,task,id,comment);return Map.of("saved",true);}
    port.complete(context,task,id,action,comment,out);
    return Map.of("completed",true);
  }
  private static void fail(String code,int status){throw new WorkflowRejection(code,status);}
}
