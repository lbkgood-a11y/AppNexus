package com.appnexus.platform.security;
import com.appnexus.platform.tenant.TaskManagement;
import com.appnexus.platform.authorization.DataAccessDeclaration;
import com.appnexus.platform.tenant.port.TaskOperations;
import com.appnexus.platform.persistence.audit.TaskAuditMapper;
import com.appnexus.platform.persistence.eventing.OutboxMapper;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
/** 可靠任务技术桥接；任务恢复与管理审计共用原数据源和 REQUIRED 事务。 */
@Component
public class TaskOperationsAdapter implements TaskOperations, DataAccessDeclaration.Executor {
 /** @return 平台任务使用固定的摘要投影与人工状态条件，无租户角色行范围或字段配置 */
 @Override public Set<String> enforcedResources(){return Set.of(TaskManagement.RESOURCE);}
 private final ReliableEvents events;private final TaskAuditMapper audit;private final OutboxMapper outbox;
 /** @param events 可靠任务技术执行器 @param audit 固定管理审计 SQL @param outbox 事件查询与投递恢复映射 */
 public TaskOperationsAdapter(ReliableEvents events,TaskAuditMapper audit,OutboxMapper outbox){this.events=events;this.audit=audit;this.outbox=outbox;}
 public List<TaskManagement.Task> recent(UUID actor,int limit){return events.tasks(actor,limit).stream().map(t->new TaskManagement.Task(t.id(),t.tenant(),t.type(),t.status(),t.attempts(),t.failureCode())).toList();}
 @Transactional public boolean retryAndAudit(UUID actor,UUID id){if(!events.retry(id,actor))return false;audit.statement1(actor.toString(),id.toString());return true;}

 /** {@inheritDoc} */
 @Override @Transactional(readOnly=true)
 public TaskManagement.EventPage events(UUID actor,TaskManagement.EventFilter filter) {
  String tenant=filter.tenant()==null?null:filter.tenant().toString();
  return new TaskManagement.EventPage(outbox.events(actor.toString(),tenant,filter.type(),filter.status(),filter.from(),filter.until(),(filter.page()-1)*20),
      outbox.eventCount(actor.toString(),tenant,filter.type(),filter.status(),filter.from(),filter.until()));
 }
 /** {@inheritDoc} */
 @Override @Transactional(readOnly=true)
 public Optional<TaskManagement.EventDetails> event(UUID actor,UUID id) {
  String event=id.toString();
  return Optional.ofNullable(outbox.event(actor.toString(),event)).map(row->new TaskManagement.EventDetails(row,outbox.deliveries(actor.toString(),event),outbox.attempts(actor.toString(),event)));
 }
 /** {@inheritDoc} */
 @Override @Transactional
 public boolean retryDeliveryAndAudit(UUID actor,UUID delivery,String reason) {
  if(!events.retryDelivery(delivery,actor)) return false;
  if(audit.deliveryRetried(ReliableEvents.PLATFORM.toString(),actor.toString(),delivery.toString(),reason)!=1) throw new IllegalStateException("投递审计写入失败");
  return true;
 }
}
