package com.appnexus.workflow.adapter.persistence;
import java.util.*;
import java.time.Instant;
import com.appnexus.workflow.application.WorkflowRecords;
/** 工作流固定SQL技术适配，业务参数语义明确，绑定位置只存在Mapper边界。 */
public final class MyBatisWorkflowRecords implements WorkflowRecords {
 private final WorkflowRecordMapper mapper;
 /** @param mapper 原固定工作流SQL映射，和引擎共享事务 */
 public MyBatisWorkflowRecords(WorkflowRecordMapper mapper){this.mapper=mapper;}
 /** {@inheritDoc} */
 public List<Map<String,Object>> publishedDefinitions(String tenant){return mapper.publishedDefinitions(tenant);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> openActorTask(String tenant,String task,String actor){return mapper.openActorTask(tenant,task,actor);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> definitions(String tenant){return mapper.definitions(tenant);}
 /** {@inheritDoc} */
 public int createDefinition(String tenant,String id,String model){return mapper.createDefinition(tenant,id,model);}
 /** {@inheritDoc} */
 public int updateDraft(String model,String tenant,String id,long revision){return mapper.updateDraft(model,tenant,id,revision);}
 /** {@inheritDoc} */
 public int createPublishedVersion(String tenant,String definition,int version,String model,String bpmn,String engineDefinition,String actor,String schema){return mapper.createPublishedVersion(tenant,definition,version,model,bpmn,engineDefinition,actor,schema);}
 /** {@inheritDoc} */
 public int activateVersion(int version,String tenant,String id){return mapper.activateVersion(version,tenant,id);}
 /** {@inheritDoc} */
 public int disableDefinition(String tenant,String id,long revision){return mapper.disableDefinition(tenant,id,revision);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> definitionVersions(String tenant,String definition){return mapper.definitionVersions(tenant,definition);}
 /** {@inheritDoc} */
 public int createInstance(String tenant,String id,String definition,int version,String objectType,String objectId,long objectVersion,String purpose,String organization,String actor,String digest,String snapshot,String schema){return mapper.createInstance(tenant,id,definition,version,objectType,objectId,objectVersion,purpose,organization,actor,digest,snapshot,schema);}
 /** {@inheritDoc} */
 public int copyActor(String tenant,String instance,String actor){return mapper.copyActor(tenant,instance,actor);}
 /** {@inheritDoc} */
 public int bindEngineInstance(String engineInstance,String tenant,String id){return mapper.bindEngineInstance(engineInstance,tenant,id);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> copiedInstances(String tenant,String actor){return mapper.copiedInstances(tenant,actor);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> participantInstances(String tenant,String actor){return mapper.participantInstances(tenant,actor);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> startedInstances(String tenant){return mapper.startedInstances(tenant);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> instanceTasks(String tenant,String instance){return mapper.instanceTasks(tenant,instance);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> instanceAudit(String tenant,String instance){return mapper.instanceAudit(tenant,instance);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> instanceJobs(String tenant,String instance){return mapper.instanceJobs(tenant,instance);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> objectInstances(String tenant,String objectType,String objectId,String purpose,String actor,boolean operator,String participant,String copiedActor){return mapper.objectInstances(tenant,objectType,objectId,purpose,actor,operator,participant,copiedActor);}
 /** {@inheritDoc} */
 public int saveTaskComment(String comment,String tenant,String task,String actor){return mapper.saveTaskComment(comment,tenant,task,actor);}
 /** {@inheritDoc} */
 public int completeTask(String decision,String comment,String tenant,String task,String actor){return mapper.completeTask(decision,comment,tenant,task,actor);}
 /** {@inheritDoc} */
 public int cancelOpenJobs(String tenant,String instance){return mapper.cancelOpenJobs(tenant,instance);}
 /** {@inheritDoc} */
 public Long duplicateAssignmentCount(String tenant,String instance,String node,String actor,String task){return mapper.duplicateAssignmentCount(tenant,instance,node,actor,task);}
 /** {@inheritDoc} */
 public int assignTask(String actor,String tenant,String task){return mapper.assignTask(actor,tenant,task);}
 /** {@inheritDoc} */
 public int retryJob(String tenant,String job){return mapper.retryJob(tenant,job);}
 /** {@inheritDoc} */
 public int cancelInstanceJobs(String tenant,String instance){return mapper.cancelInstanceJobs(tenant,instance);}
 /** {@inheritDoc} */
 public int createTask(String tenant,String id,String instance,String node,String name,String actor,Instant deadline){return mapper.createTask(tenant,id,instance,node,name,actor,deadline);}
 /** {@inheritDoc} */
 public int endInstance(String result,String tenant,String instance){return mapper.endInstance(result,tenant,instance);}
 /** {@inheritDoc} */
 public int createDeliveryJob(String tenant,String id,String instance,String node,String execution,String capability,String kind,String payload,int attempts,Instant deadline){return mapper.createDeliveryJob(tenant,id,instance,node,execution,capability,kind,payload,attempts,deadline);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> lockDueJob(){return mapper.lockDueJob();}
 /** {@inheritDoc} */
 public int markLeaseExhausted(String tenant,String job){return mapper.markLeaseExhausted(tenant,job);}
 /** {@inheritDoc} */
 public int claimJobLease(String token,String tenant,String job){return mapper.claimJobLease(token,tenant,job);}
 /** {@inheritDoc} */
 public int awaitCallback(String result,String tenant,String job){return mapper.awaitCallback(result,tenant,job);}
 /** {@inheritDoc} */
 public int scheduleRetry(String state,String error,String tenant,String job){return mapper.scheduleRetry(state,error,tenant,job);}
 /** {@inheritDoc} */
 public int completeDelivery(String result,String tenant,String job){return mapper.completeDelivery(result,tenant,job);}
 /** {@inheritDoc} */
 public int auditExpiredAssignment(){return mapper.auditExpiredAssignment();}
 /** {@inheritDoc} */
 public int timeoutJobs(){return mapper.timeoutJobs();}
 /** {@inheritDoc} */
 public List<Map<String,Object>> tasksForMaintenance(){return mapper.tasksForMaintenance();}
 /** {@inheritDoc} */
 public int markTaskChecked(String tenant,String task){return mapper.markTaskChecked(tenant,task);}
 /** {@inheritDoc} */
 public int unassignTask(String tenant,String task){return mapper.unassignTask(tenant,task);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> openInstanceTasks(String tenant,String instance){return mapper.openInstanceTasks(tenant,instance);}
 /** {@inheritDoc} */
 public int cancelTask(String tenant,String task){return mapper.cancelTask(tenant,task);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> deliveryNode(String tenant,String job){return mapper.deliveryNode(tenant,job);}
 /** {@inheritDoc} */
 public Long copyCount(String tenant,String instance,String actor){return mapper.copyCount(tenant,instance,actor);}
 /** {@inheritDoc} */
 public Long participantTaskCount(String tenant,String instance,String actor){return mapper.participantTaskCount(tenant,instance,actor);}
 /** {@inheritDoc} */
 public int registerRequest(String tenant,String actor,String request,String digest){return mapper.registerRequest(tenant,actor,request,digest);}
 /** {@inheritDoc} */
 public int saveRequestResponse(String response,String tenant,String actor,String request){return mapper.saveRequestResponse(response,tenant,actor,request);}
 /** {@inheritDoc} */
 public int auditAssignment(String tenant,String instance,String actor,String action,String target,String detail,String previous,String next){return mapper.auditAssignment(tenant,instance,actor,action,target,detail,previous,next);}
 /** {@inheritDoc} */
 public int auditOperation(String tenant,String instance,String actor,String action,String target,String detail,String person,String organization){return mapper.auditOperation(tenant,instance,actor,action,target,detail,person,organization);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> lockDefinition(String tenant,String definition){return mapper.lockDefinition(tenant,definition);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> taskRecord(String tenant,String task){return mapper.taskRecord(tenant,task);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> taskForCompletion(String tenant,String task){return mapper.taskForCompletion(tenant,task);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> lockTaskForCompletion(String tenant,String task){return mapper.lockTaskForCompletion(tenant,task);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> taskForReassignment(String tenant,String task){return mapper.taskForReassignment(tenant,task);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> lockTaskForReassignment(String tenant,String task){return mapper.lockTaskForReassignment(tenant,task);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> lockRetryJob(String tenant,String job){return mapper.lockRetryJob(tenant,job);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> frozenDefinition(String tenant,String definition,int version){return mapper.frozenDefinition(tenant,definition,version);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> lockDispatchedJob(String tenant,String job){return mapper.lockDispatchedJob(tenant,job);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> lockCallbackJob(String tenant,String job){return mapper.lockCallbackJob(tenant,job);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> taskNodeForAction(String tenant,String task){return mapper.taskNodeForAction(tenant,task);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> taskNodeForCallback(String tenant,String task){return mapper.taskNodeForCallback(tenant,task);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> instanceRecord(String tenant,String instance,boolean lock){return mapper.instanceRecord(tenant,instance,lock);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> lockRequest(String tenant,String actor,String request){return mapper.lockRequest(tenant,actor,request);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> publishedCandidates(String tenant,Integer version){return mapper.publishedCandidates(tenant,version);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> actorTasks(String tenant,String actor,boolean todo){return mapper.actorTasks(tenant,actor,todo);}
}
