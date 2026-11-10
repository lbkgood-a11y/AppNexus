package com.appnexus.workflow.adapter.flowable;

import static com.appnexus.workflow.core.WorkflowModel.*;

import com.appnexus.workflow.core.WorkflowModel;
import java.util.*;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.*;

/** 仅从已校验模型生成BPMN，所有委托固定指向内部桥接Bean，不接受任意引擎部署。 */
final class WorkflowCompiler {
  static byte[] compile(String key, WorkflowModel definition) {
    var model = new BpmnModel();
    var process = new org.flowable.bpmn.model.Process();
    process.setId(key);
    process.setName(definition.name());
    process.setExecutable(true);
    model.addProcess(process);
    process.setExecutionListeners(List.of(listener("end", "${wfBridge}")));
    for (var n : definition.nodes()) {
      FlowNode node =
          switch (n.kind()) {
            case START -> new StartEvent();
            case END -> new EndEvent();
            case CHOICE, MERGE -> new ExclusiveGateway();
            case FORK, JOIN -> new ParallelGateway();
            case ACTION, WAIT -> {
              var receive = new ReceiveTask();
              receive.setExecutionListeners(List.of(listener("start", "${wfBridge}")));
              yield receive;
            }
            case REVIEW, HANDLE -> {
              var task = new UserTask();
              task.setAssignee("${wf_actor}");
              var taskListener = new FlowableListener();
              taskListener.setEvent("create");
              taskListener.setImplementationType("delegateExpression");
              taskListener.setImplementation("${wfBridge}");
              task.setTaskListeners(List.of(taskListener));
              var loop = new MultiInstanceLoopCharacteristics();
              loop.setSequential(n.completion() == Completion.SEQUENTIAL);
              loop.setInputDataItem("${wfBridge.people(execution, '" + n.id() + "')}");
              loop.setElementVariable("wf_actor");
              if (n.completion() == Completion.ANY)
                loop.setCompletionCondition("${nrOfCompletedInstances >= 1}");
              task.setLoopCharacteristics(loop);
              yield task;
            }
          };
      node.setId(n.id());
      node.setName(n.name());
      process.addFlowElement(node);
    }
    int index = 0;
    for (var e : definition.edges()) {
      var flow = new SequenceFlow(e.from(), e.to());
      flow.setId("flow_" + (index++));
      if (definition.node(e.from()).kind() == Kind.CHOICE) {
        if (e.defaultEdge())
          ((ExclusiveGateway) process.getFlowElement(e.from())).setDefaultFlow(flow.getId());
        else
          flow.setConditionExpression(
              "${wf_fields['" + escape(e.field()) + "'] == '" + escape(e.value()) + "'}");
      }
      process.addFlowElement(flow);
    }
    return new BpmnXMLConverter().convertToXML(model);
  }

  private static FlowableListener listener(String event, String implementation) {
    var l = new FlowableListener();
    l.setEvent(event);
    l.setImplementationType("delegateExpression");
    l.setImplementation(implementation);
    return l;
  }

  private static String escape(String s) {
    return s.replace("\\", "\\\\").replace("'", "\\'");
  }
}
