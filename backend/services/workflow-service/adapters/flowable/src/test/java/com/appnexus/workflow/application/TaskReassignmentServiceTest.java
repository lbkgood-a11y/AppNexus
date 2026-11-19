package com.appnexus.workflow.application;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.appnexus.contracts.workflow.WorkflowContext;
import org.junit.jupiter.api.Test;
/** 重分配不能借运维资格绕过候选或形成重复办理分支。 */
class TaskReassignmentServiceTest {
  private final WorkflowContext context=new WorkflowContext("tenant","actor","request");
  /** 当前任职不匹配时不写引擎及任务。 */
  @Test void invalidCandidateNeverAssigns(){
    var port=mock(TaskReassignmentPort.class);when(port.lock(context,"task")).thenReturn(new TaskReassignmentPort.Task(true,"old"));
    assertThatThrownBy(()->new TaskReassignmentService(port).assign(context,"task","new","原因")).hasMessage("ASSIGNEE_INVALID");
    verify(port,never()).assign(any(),anyString(),anyString(),any(),anyString());
  }
  /** 同节点已有目标处理者OPEN任务时拒绝重复分支。 */
  @Test void duplicateOpenTaskNeverAssigns(){
    var port=mock(TaskReassignmentPort.class);when(port.lock(context,"task")).thenReturn(new TaskReassignmentPort.Task(true,"old"));
    when(port.candidate(context,"new")).thenReturn(true);when(port.duplicate(context,"task","new")).thenReturn(true);
    assertThatThrownBy(()->new TaskReassignmentService(port).assign(context,"task","new","原因")).hasMessage("ASSIGNEE_INVALID");
    verify(port,never()).assign(any(),anyString(),anyString(),any(),anyString());
  }
}
