package com.appnexus.workflow.application;
import com.appnexus.workflow.core.WorkflowModel.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.appnexus.contracts.workflow.WorkflowContext;
import java.util.*;
import org.junit.jupiter.api.Test;

/** 办理流程反向验证，过期任务、任职撤销和依据变化不得调用业务或推进引擎。 */
class TaskCompletionServiceTest {
  private final WorkflowContext context=new WorkflowContext(UUID.randomUUID().toString(),UUID.randomUUID().toString(),UUID.randomUUID().toString());
  private Node node(){return new Node("review","审核",Kind.REVIEW,List.of(),Completion.ALL,false,null,Set.of(),Map.of(),2,3600,null,true);}
  /** 锁后状态或候选失效不能调用业务。 */
  @Test void inactiveOrReassignedTaskCannotExecuteBusiness(){
    var port=mock(TaskCompletionPort.class);var service=new TaskCompletionService(port);
    var task=new TaskCompletionPort.Task("instance","RUNNING","DONE",context.actorId());
    when(port.lock(context,"task")).thenReturn(task);
    assertThatThrownBy(()->service.complete(context,"task","APPROVE",Map.of(),"")).hasMessage("TASK_NOT_ACTIVE");
    verify(port,never()).assigned(any(),any());verify(port,never()).complete(any(),any(),anyString(),anyString(),anyString(),anyMap());
  }
  /** 提交时业务快照变化拒绝，不完成引擎或写状态。 */
  @Test void changedApprovalBasisPreventsCompletion(){
    var port=mock(TaskCompletionPort.class);var service=new TaskCompletionService(port);
    var task=new TaskCompletionPort.Task("instance","RUNNING","OPEN",context.actorId());
    when(port.lock(context,"task")).thenReturn(task);when(port.assigned(context,task)).thenReturn(true);when(port.node(task)).thenReturn(node());
    when(port.basisUnchanged(context,task,"task")).thenReturn(false);
    assertThatThrownBy(()->service.complete(context,"task","APPROVE",Map.of(),"")).hasMessage("APPROVAL_BASIS_CHANGED");
    verify(port,never()).complete(any(),any(),anyString(),anyString(),anyString(),anyMap());
  }
}
