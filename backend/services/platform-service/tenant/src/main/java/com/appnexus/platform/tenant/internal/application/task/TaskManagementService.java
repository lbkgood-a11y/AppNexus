package com.appnexus.platform.tenant.internal.application.task;
import com.appnexus.platform.tenant.*;
import com.appnexus.platform.tenant.port.TaskOperations;
import java.util.*;
/** 平台任务管理用例；资格在每次执行时重查，技术事务由输出端口原子完成。 */
public final class TaskManagementService implements TaskManagement {
 private final PlatformAdministration platform; private final TaskOperations operations;
 /** @param platform 权威平台管理资格端口 @param operations 同事务任务与审计端口 */
 public TaskManagementService(PlatformAdministration platform,TaskOperations operations){this.platform=platform;this.operations=operations;}
 public List<Task> list(UUID actor){require(actor);return operations.recent(actor,50);}
 public void retry(UUID actor,UUID id){require(actor);if(!operations.retryAndAudit(actor,id))throw new IllegalStateException("TASK_NOT_MANUAL");}
 private void require(UUID actor){if(!platform.isAdministrator(actor))throw new SecurityException("PLATFORM_DENIED");}

 /** {@inheritDoc} */
 @Override public EventPage events(UUID actor,EventFilter filter) {
  require(actor);
  if(filter.page()<1 || filter.page()>10000 || (filter.type()!=null && !filter.type().matches("[A-Z_]{1,100}"))
       || (filter.status()!=null && !Set.of("PENDING","PROCESSING","DONE","MANUAL").contains(filter.status()))
       || (filter.from()!=null && filter.until()!=null && !filter.from().isBefore(filter.until())))
   throw new IllegalArgumentException("无效的事件筛选条件");
  return operations.events(actor,filter);
 }
 /** {@inheritDoc} */
 @Override public Optional<EventDetails> event(UUID actor,UUID eventId) {
  require(actor);
  return operations.event(actor,eventId);
 }
 /** {@inheritDoc} */
 @Override public List<Subscription> subscriptions(UUID actor) {
  require(actor);
  return operations.subscriptions(actor);
 }
 /** {@inheritDoc} */
 @Override public void setSubscriptionPaused(UUID actor,String type,String consumer,boolean paused,String reason) {
  require(actor);
  if(reason==null || reason.isBlank() || reason.strip().length()>200)
   throw new IllegalArgumentException("操作原因须为 1–200 字符");
  if(!operations.setSubscriptionPaused(actor,type,consumer,paused,reason.strip()))
   throw new NoSuchElementException("SUBSCRIPTION_NOT_MANAGEABLE");
 }
 /** {@inheritDoc} */
 @Override public void retryDelivery(UUID actor,UUID deliveryId,String reason) {
  require(actor);
  if(reason==null || reason.isBlank() || reason.strip().length()>200)
   throw new IllegalArgumentException("重试原因须为 1–200 字符");
  if(!operations.retryDeliveryAndAudit(actor,deliveryId,reason.strip())) throw new IllegalStateException("DELIVERY_NOT_RETRYABLE");
 }
}
