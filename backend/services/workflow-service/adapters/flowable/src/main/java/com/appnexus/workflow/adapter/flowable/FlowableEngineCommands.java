package com.appnexus.workflow.adapter.flowable;
import com.appnexus.workflow.application.WorkflowEngineCommands;
import java.util.Map;
import org.flowable.engine.ProcessEngine;
/** 固定Flowable命令桥接，变量名由代码限定，与工作流数据库共用原宿主事务。 */
public final class FlowableEngineCommands implements WorkflowEngineCommands {
 private final ProcessEngine engine;
 /** @param engine 原受控配置创建的引擎，不开放原生REST */
 public FlowableEngineCommands(ProcessEngine engine){this.engine=engine;}
 public void fields(String instance,Map<String,String> fields){engine.getRuntimeService().setVariable(instance,"wf_fields",fields);}
 public void result(String instance,String result){engine.getRuntimeService().setVariable(instance,"wf_result",result);}
 public void terminate(String instance,String reason){engine.getRuntimeService().deleteProcessInstance(instance,reason);}
 public void complete(String task){engine.getTaskService().complete(task);}
 public void assign(String task,String actor){engine.getTaskService().setAssignee(task,actor);}
 public void trigger(String execution){engine.getRuntimeService().trigger(execution);}
 public boolean running(String instance){return engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(instance).count()>0;}
 @SuppressWarnings("unchecked") public Map<String,String> fields(String instance){return (Map<String,String>)engine.getRuntimeService().getVariable(instance,"wf_fields");}
 public String deploy(String tenant,String key,byte[] bpmn){var deployment=engine.getRepositoryService().createDeployment().tenantId(tenant).addBytes(key+".bpmn20.xml",bpmn).deploy();return engine.getRepositoryService().createProcessDefinitionQuery().deploymentId(deployment.getId()).processDefinitionTenantId(tenant).singleResult().getId();}
 public String start(String definition,String businessKey,java.util.Map<String,Object> variables){return engine.getRuntimeService().startProcessInstanceById(definition,businessKey,variables).getId();}
 public boolean taskExists(String tenant,String task){return engine.getTaskService().createTaskQuery().taskId(task).taskTenantId(tenant).count()>0;}
}
