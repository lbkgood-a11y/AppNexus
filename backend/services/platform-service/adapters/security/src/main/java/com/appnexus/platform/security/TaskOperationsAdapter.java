package com.appnexus.platform.security;
import com.appnexus.platform.tenant.TaskManagement;
import com.appnexus.platform.tenant.port.TaskOperations;
import com.appnexus.platform.persistence.audit.TaskAuditMapper;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
/** 可靠任务技术桥接；任务恢复与管理审计共用原数据源和 REQUIRED 事务。 */
@Component
public class TaskOperationsAdapter implements TaskOperations {
 private final ReliableEvents events;private final TaskAuditMapper audit;
 /** @param events 可靠任务技术执行器 @param audit 固定管理审计 SQL */
 public TaskOperationsAdapter(ReliableEvents events,TaskAuditMapper audit){this.events=events;this.audit=audit;}
 public List<TaskManagement.Task> recent(int limit){return events.tasks(limit).stream().map(t->new TaskManagement.Task(t.id(),t.tenant(),t.type(),t.status(),t.attempts(),t.failureCode())).toList();}
 @Transactional public boolean retryAndAudit(UUID actor,UUID id){if(!events.retry(id))return false;audit.statement1(actor.toString(),id.toString());return true;}
}
