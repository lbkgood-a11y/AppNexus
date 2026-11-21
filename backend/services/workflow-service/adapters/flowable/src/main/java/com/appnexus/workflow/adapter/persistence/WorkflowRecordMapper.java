package com.appnexus.workflow.adapter.persistence;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** 工作流应用自有表固定映射；后台交付保持可信实例和幂等复核，不接收任意SQL。 */
@Mapper
@PlatformScoped(tables={"SKIP","request_id","wf_audit","wf_copy","wf_definition","wf_instance","wf_job","wf_request","wf_task","wf_version"})
public interface WorkflowRecordMapper extends com.appnexus.contracts.businessobject.BusinessObjects.DataAccessExecutor {
 /** @return 受原用例身份复核和固定存储约束保护的专业服务资源 */
 @Override default java.util.Set<String> enforcedResources(){return java.util.Set.of("workflow.definition", "workflow.definition.version", "workflow.instance", "workflow.instance.actor", "workflow.task", "workflow.task.assignment", "workflow.job");}
 /** 固定工作流语句1；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Select("SELECT v.*,d.enabled FROM wf_version v JOIN wf_definition d ON d.tenant_id=v.tenant_id AND d.id=v.definition_id WHERE v.tenant_id=#{p0} AND d.enabled=TRUE ORDER BY v.definition_id,v.version DESC")
 List<Map<String,Object>> publishedDefinitions(@Param("p0") Object p0);

 /** 固定工作流语句2；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Select("SELECT * FROM wf_task WHERE tenant_id=#{p0} AND id=#{p1} AND actor=#{p2} AND state='OPEN'")
 List<Map<String,Object>> openActorTask(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定工作流语句3；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Select("SELECT id,revision,model,enabled,published FROM wf_definition WHERE tenant_id=#{p0} ORDER BY id LIMIT 100")
 List<Map<String,Object>> definitions(@Param("p0") Object p0);

 /** 固定工作流语句4；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Insert("INSERT INTO wf_definition(tenant_id,id,model) VALUES(#{p0},#{p1},#{p2})")
 int createDefinition(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定工作流语句5；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Update("UPDATE wf_definition SET model=#{p0},revision=revision+1 WHERE tenant_id=#{p1} AND id=#{p2} AND revision=#{p3}")
 int updateDraft(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3);

 /** 固定工作流语句6；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Insert("INSERT INTO wf_version(tenant_id,definition_id,version,model,bpmn,engine_definition,published_by,data_contract_schema) VALUES(#{p0},#{p1},#{p2},#{p3},#{p4},#{p5},#{p6},#{p7})")
 int createPublishedVersion(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7);

 /** 固定工作流语句7；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Update("UPDATE wf_definition SET published=#{p0},enabled=TRUE,revision=revision+1 WHERE tenant_id=#{p1} AND id=#{p2}")
 int activateVersion(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定工作流语句8；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Update("UPDATE wf_definition SET enabled=FALSE,revision=revision+1 WHERE tenant_id=#{p0} AND id=#{p1} AND revision=#{p2}")
 int disableDefinition(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定工作流语句9；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Select("SELECT version,model,published_by,created_at FROM wf_version WHERE tenant_id=#{p0} AND definition_id=#{p1} ORDER BY version DESC LIMIT 100")
 List<Map<String,Object>> definitionVersions(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定工作流语句10；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Insert("INSERT INTO wf_instance(tenant_id,id,definition_id,definition_version,object_type,object_id,object_version,purpose,organization_id,starter,active_key,snapshot,data_contract_schema) VALUES(#{p0},#{p1},#{p2},#{p3},#{p4},#{p5},#{p6},#{p7},#{p8},#{p9},#{p10},#{p11},#{p12})")
 int createInstance(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7,@Param("p8") Object p8,@Param("p9") Object p9,@Param("p10") Object p10,@Param("p11") Object p11,@Param("p12") Object p12);

 /** 固定工作流语句11；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Insert("INSERT IGNORE INTO wf_copy(tenant_id,instance_id,actor) VALUES(#{p0},#{p1},#{p2})")
 int copyActor(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定工作流语句12；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Update("UPDATE wf_instance SET engine_id=#{p0} WHERE tenant_id=#{p1} AND id=#{p2}")
 int bindEngineInstance(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定工作流语句13；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Select("SELECT i.id,i.object_type,i.object_id,i.purpose,i.state,i.result,i.created_at FROM wf_instance i JOIN wf_copy c ON c.tenant_id=i.tenant_id AND c.instance_id=i.id WHERE c.tenant_id=#{p0} AND c.actor=#{p1} ORDER BY i.created_at DESC LIMIT 100")
 List<Map<String,Object>> copiedInstances(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定工作流语句14；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Select("SELECT id,object_type,object_id,object_version,purpose,state,result,created_at FROM wf_instance WHERE tenant_id=#{p0} AND starter=#{p1} ORDER BY created_at DESC LIMIT 100")
 List<Map<String,Object>> participantInstances(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定工作流语句15；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Select("SELECT id,object_type,object_id,object_version,purpose,state,result,created_at,ended_at FROM wf_instance WHERE tenant_id=#{p0} ORDER BY created_at DESC LIMIT 100")
 List<Map<String,Object>> startedInstances(@Param("p0") Object p0);

 /** 固定工作流语句16；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Select("SELECT id,node_id,node_name,actor,state,decision,comment,deadline,created_at,completed_at FROM wf_task WHERE tenant_id=#{p0} AND instance_id=#{p1} ORDER BY created_at")
 List<Map<String,Object>> instanceTasks(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定工作流语句17；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Select("SELECT actor,action,target,detail,from_actor,to_actor,created_at FROM wf_audit WHERE tenant_id=#{p0} AND instance_id=#{p1} ORDER BY id")
 List<Map<String,Object>> instanceAudit(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定工作流语句18；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Select("SELECT id,node_id,capability,kind,state,attempts,error_code,deadline FROM wf_job WHERE tenant_id=#{p0} AND instance_id=#{p1} ORDER BY due_at")
 List<Map<String,Object>> instanceJobs(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定工作流语句19；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Select("SELECT i.id,i.definition_version,i.state,i.result,i.created_at FROM wf_instance i WHERE i.tenant_id=#{p0} AND i.object_type=#{p1} AND i.object_id=#{p2} AND i.purpose=#{p3} AND (i.starter=#{p4} OR #{p5}=TRUE OR EXISTS(SELECT 1 FROM wf_task t WHERE t.tenant_id=i.tenant_id AND t.instance_id=i.id AND t.actor=#{p6}) OR EXISTS(SELECT 1 FROM wf_copy cp WHERE cp.tenant_id=i.tenant_id AND cp.instance_id=i.id AND cp.actor=#{p7})) ORDER BY i.created_at DESC LIMIT 100")
 List<Map<String,Object>> objectInstances(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7);

 /** 固定工作流语句20；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Update("UPDATE wf_task SET comment=#{p0} WHERE tenant_id=#{p1} AND id=#{p2}")
 int saveTaskComment(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定工作流语句21；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Update("UPDATE wf_task SET state='DONE',decision=#{p0},comment=#{p1},completed_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=#{p2} AND id=#{p3} AND state='OPEN'")
 int completeTask(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3);

 /** 固定工作流语句22；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Update("UPDATE wf_job SET state='CANCELLED',lease_token=NULL WHERE tenant_id=#{p0} AND instance_id=#{p1} AND kind<>'TERMINAL' AND state<>'DONE'")
 int cancelOpenJobs(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定工作流语句23；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Select("SELECT COUNT(*) FROM wf_task WHERE tenant_id=#{p0} AND instance_id=#{p1} AND node_id=#{p2} AND actor=#{p3} AND state='OPEN' AND id<>#{p4}")
 Long duplicateAssignmentCount(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4);

 /** 固定工作流语句24；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Update("UPDATE wf_task SET actor=#{p0} WHERE tenant_id=#{p1} AND id=#{p2}")
 int assignTask(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定工作流语句25；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Update("UPDATE wf_job SET state='READY',attempts=0,due_at=CURRENT_TIMESTAMP(6),deadline=DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 1 DAY),error_code=NULL WHERE tenant_id=#{p0} AND id=#{p1}")
 int retryJob(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定工作流语句26；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Update("UPDATE wf_job SET state='CANCELLED',lease_token=NULL WHERE tenant_id=#{p0} AND instance_id=#{p1} AND kind<>'TERMINAL' AND state<>'DONE'")
 int cancelInstanceJobs(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定工作流语句27；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Insert("INSERT INTO wf_task(tenant_id,id,instance_id,node_id,node_name,actor,deadline) VALUES(#{p0},#{p1},#{p2},#{p3},#{p4},#{p5},#{p6})")
 int createTask(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6);

 /** 固定工作流语句28；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Update("UPDATE wf_instance SET state='ENDED',result=#{p0},active_key=NULL,ended_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=#{p1} AND id=#{p2} AND state='RUNNING'")
 int endInstance(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定工作流语句29；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Insert("INSERT INTO wf_job(tenant_id,id,instance_id,node_id,execution_id,capability,kind,payload,max_attempts,deadline) VALUES(#{p0},#{p1},#{p2},#{p3},#{p4},#{p5},#{p6},#{p7},#{p8},#{p9})")
 int createDeliveryJob(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7,@Param("p8") Object p8,@Param("p9") Object p9);

 /** 固定工作流语句30；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Select("SELECT * FROM wf_job WHERE deadline>CURRENT_TIMESTAMP(6) AND ((state='READY' AND due_at<=CURRENT_TIMESTAMP(6)) OR (state='RUNNING' AND lease_until<CURRENT_TIMESTAMP(6))) ORDER BY due_at LIMIT 1 FOR UPDATE SKIP LOCKED")
 List<Map<String,Object>> lockDueJob();

 /** 固定工作流语句31；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Update("UPDATE wf_job SET state='MANUAL',error_code='LEASE_EXHAUSTED' WHERE tenant_id=#{p0} AND id=#{p1}")
 int markLeaseExhausted(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定工作流语句32；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Update("UPDATE wf_job SET state='RUNNING',attempts=attempts+1,lease_token=#{p0},lease_until=DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 120 SECOND) WHERE tenant_id=#{p1} AND id=#{p2}")
 int claimJobLease(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定工作流语句33；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Update("UPDATE wf_job SET state='WAITING',result=#{p0} WHERE tenant_id=#{p1} AND id=#{p2}")
 int awaitCallback(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定工作流语句34；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Update("UPDATE wf_job SET state=#{p0},error_code=#{p1},due_at=DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 30 SECOND),lease_token=NULL WHERE tenant_id=#{p2} AND id=#{p3}")
 int scheduleRetry(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3);

 /** 固定工作流语句35；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Update("UPDATE wf_job SET state='DONE',result=#{p0},lease_token=NULL,error_code=NULL WHERE tenant_id=#{p1} AND id=#{p2}")
 int completeDelivery(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定工作流语句36；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Insert("INSERT INTO wf_audit(tenant_id,instance_id,actor,action,target,detail) SELECT t.tenant_id,t.instance_id,'system','TASK_OVERDUE',t.id,'' FROM wf_task t WHERE t.state='OPEN' AND t.deadline<CURRENT_TIMESTAMP(6) AND NOT EXISTS(SELECT 1 FROM wf_audit a WHERE a.tenant_id=t.tenant_id AND a.action='TASK_OVERDUE' AND a.target=t.id)")
 int auditExpiredAssignment();

 /** 固定工作流语句37；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Update("UPDATE wf_job SET state='TIMEOUT',error_code='WAIT_TIMEOUT',lease_token=NULL WHERE state IN('READY','WAITING','RUNNING') AND deadline<CURRENT_TIMESTAMP(6)")
 int timeoutJobs();

 /** 固定工作流语句38；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Select("SELECT t.* FROM wf_task t WHERE t.state='OPEN' AND (t.actor IS NOT NULL OR t.deadline<CURRENT_TIMESTAMP(6)) ORDER BY t.checked_at,t.id LIMIT 100")
 List<Map<String,Object>> tasksForMaintenance();

 /** 固定工作流语句39；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Update("UPDATE wf_task SET checked_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=#{p0} AND id=#{p1}")
 int markTaskChecked(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定工作流语句40；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Update("UPDATE wf_task SET actor=NULL WHERE tenant_id=#{p0} AND id=#{p1}")
 int unassignTask(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定工作流语句41；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Select("SELECT id FROM wf_task WHERE tenant_id=#{p0} AND instance_id=#{p1} AND state='OPEN'")
 List<Map<String,Object>> openInstanceTasks(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定工作流语句42；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Update("UPDATE wf_task SET state='CANCELLED',completed_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=#{p0} AND id=#{p1}")
 int cancelTask(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定工作流语句43；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Select("SELECT node_id FROM wf_job WHERE tenant_id=#{p0} AND id=#{p1}")
 List<Map<String,Object>> deliveryNode(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定工作流语句44；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Select("SELECT COUNT(*) FROM wf_copy WHERE tenant_id=#{p0} AND instance_id=#{p1} AND actor=#{p2}")
 Long copyCount(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定工作流语句45；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Select("SELECT COUNT(*) FROM wf_task WHERE tenant_id=#{p0} AND instance_id=#{p1} AND actor=#{p2}")
 Long participantTaskCount(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定工作流语句46；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Insert("INSERT INTO wf_request(tenant_id,actor,request_id,fingerprint) VALUES(#{p0},#{p1},#{p2},#{p3}) ON DUPLICATE KEY UPDATE request_id=request_id")
 int registerRequest(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3);

 /** 固定工作流语句47；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Update("UPDATE wf_request SET response=#{p0} WHERE tenant_id=#{p1} AND actor=#{p2} AND request_id=#{p3}")
 int saveRequestResponse(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3);

 /** 固定工作流语句48；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Insert("INSERT INTO wf_audit(tenant_id,instance_id,actor,action,target,detail,from_actor,to_actor) VALUES(#{p0},#{p1},#{p2},#{p3},#{p4},#{p5},#{p6},#{p7})")
 int auditAssignment(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7);

 /** 固定工作流语句49；可信租户、参与者和任务准入仍由原用例验证，业务及引擎共享原事务。 */
 @Insert("INSERT INTO wf_audit(tenant_id,instance_id,actor,action,target,detail,person_id,business_organization_id) VALUES(#{p0},#{p1},#{p2},#{p3},#{p4},#{p5},#{p6},#{p7})")
 int auditOperation(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7);

 /** 固定唯一记录查询；无匹配不泄漏其他主体或租户，原用例保持参与者校验。 */
 @Select("SELECT * FROM wf_definition WHERE tenant_id=#{p0} AND id=#{p1} FOR UPDATE")
 List<Map<String,Object>> lockDefinition(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定唯一记录查询；无匹配不泄漏其他主体或租户，原用例保持参与者校验。 */
 @Select("SELECT * FROM wf_task WHERE tenant_id=#{p0} AND id=#{p1}")
 List<Map<String,Object>> taskRecord(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定唯一记录查询；无匹配不泄漏其他主体或租户，原用例保持参与者校验。 */
 @Select("SELECT * FROM wf_task WHERE tenant_id=#{p0} AND id=#{p1}")
 List<Map<String,Object>> taskForCompletion(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定唯一记录查询；无匹配不泄漏其他主体或租户，原用例保持参与者校验。 */
 @Select("SELECT * FROM wf_task WHERE tenant_id=#{p0} AND id=#{p1} FOR UPDATE")
 List<Map<String,Object>> lockTaskForCompletion(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定唯一记录查询；无匹配不泄漏其他主体或租户，原用例保持参与者校验。 */
 @Select("SELECT * FROM wf_task WHERE tenant_id=#{p0} AND id=#{p1}")
 List<Map<String,Object>> taskForReassignment(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定唯一记录查询；无匹配不泄漏其他主体或租户，原用例保持参与者校验。 */
 @Select("SELECT * FROM wf_task WHERE tenant_id=#{p0} AND id=#{p1} FOR UPDATE")
 List<Map<String,Object>> lockTaskForReassignment(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定唯一记录查询；无匹配不泄漏其他主体或租户，原用例保持参与者校验。 */
 @Select("SELECT * FROM wf_job WHERE tenant_id=#{p0} AND id=#{p1} FOR UPDATE")
 List<Map<String,Object>> lockRetryJob(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定唯一记录查询；无匹配不泄漏其他主体或租户，原用例保持参与者校验。 */
 @Select("SELECT model FROM wf_version WHERE tenant_id=#{p0} AND definition_id=#{p1} AND version=#{p2}")
 List<Map<String,Object>> frozenDefinition(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定唯一记录查询；无匹配不泄漏其他主体或租户，原用例保持参与者校验。 */
 @Select("SELECT * FROM wf_job WHERE tenant_id=#{p0} AND id=#{p1} FOR UPDATE")
 List<Map<String,Object>> lockDispatchedJob(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定唯一记录查询；无匹配不泄漏其他主体或租户，原用例保持参与者校验。 */
 @Select("SELECT * FROM wf_job WHERE tenant_id=#{p0} AND id=#{p1} FOR UPDATE")
 List<Map<String,Object>> lockCallbackJob(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定唯一记录查询；无匹配不泄漏其他主体或租户，原用例保持参与者校验。 */
 @Select("SELECT node_id FROM wf_task WHERE tenant_id=#{p0} AND id=#{p1}")
 List<Map<String,Object>> taskNodeForAction(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定唯一记录查询；无匹配不泄漏其他主体或租户，原用例保持参与者校验。 */
 @Select("SELECT node_id FROM wf_task WHERE tenant_id=#{p0} AND id=#{p1}")
 List<Map<String,Object>> taskNodeForCallback(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定唯一记录查询；无匹配不泄漏其他主体或租户，原用例保持参与者校验。 */
 @Select({"<script>","SELECT * FROM wf_instance WHERE tenant_id=#{p0} AND id=#{p1}","<if test='lock'> FOR UPDATE</if>","</script>"})
 List<Map<String,Object>> instanceRecord(@Param("p0") Object p0,@Param("p1") Object p1,@Param("lock") boolean lock);

 /** 固定唯一记录查询；无匹配不泄漏其他主体或租户，原用例保持参与者校验。 */
 @Select("SELECT * FROM wf_request WHERE tenant_id=#{p0} AND actor=#{p1} AND request_id=#{p2} FOR UPDATE")
 List<Map<String,Object>> lockRequest(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** @param tenant 可信租户 @param version 已绑定不可变版本，null使用已发布指针 @return 本租户启用流程候选 */
 @Select({"<script>","SELECT v.*,d.id FROM wf_definition d JOIN wf_version v ON v.tenant_id=d.tenant_id AND v.definition_id=d.id AND <choose><when test='version == null'>v.version=d.published</when><otherwise>v.version=#{version}</otherwise></choose> WHERE d.tenant_id=#{tenant} AND d.enabled=TRUE FOR SHARE","</script>"})
 List<Map<String,Object>> publishedCandidates(@Param("tenant") String tenant,@Param("version") Integer version);

 /** @param tenant 可信租户 @param actor 可信实际账号 @param todo true仅待办，false仅已结束 @return 本人任务摘要，最多100条 */
 @Select({"<script>","SELECT t.*,i.object_type,i.object_id,i.object_version FROM wf_task t JOIN wf_instance i ON i.tenant_id=t.tenant_id AND i.id=t.instance_id WHERE t.tenant_id=#{tenant} AND t.actor=#{actor} AND t.state <choose><when test='todo'>='OPEN'</when><otherwise>IN('DONE','CANCELLED')</otherwise></choose> ORDER BY t.created_at DESC LIMIT 100","</script>"})
 List<Map<String,Object>> actorTasks(@Param("tenant") String tenant,@Param("actor") String actor,@Param("todo") boolean todo);
}
