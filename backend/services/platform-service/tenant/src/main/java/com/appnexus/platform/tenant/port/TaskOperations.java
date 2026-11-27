package com.appnexus.platform.tenant.port;
import com.appnexus.platform.tenant.TaskManagement.Task;
import java.util.*;
/** 可靠任务输出端口；只允许平台技术适配器实现，不开放自由 SQL。 */
public interface TaskOperations {
 /** @param limit 读取条数，应用固定为 50 @return 不含正文的任务摘要 */
 List<Task> recent(UUID actor,int limit);
 /** 同一事务恢复任务并写入管理审计；失败必须全部回滚。 @param actor 可信账号 @param id 任务标识 @return 人工处理状态成功恢复时为 true */
 boolean retryAndAudit(UUID actor,UUID id);

 /** 读取经过应用校验的固定平台事件摘要。 */
 com.appnexus.platform.tenant.TaskManagement.EventPage events(UUID actor,com.appnexus.platform.tenant.TaskManagement.EventFilter filter);
 /** 使用同一数据库快照读取事件详情。 */
 Optional<com.appnexus.platform.tenant.TaskManagement.EventDetails> event(UUID actor,UUID id);
 /** 读取活跃注册与执行开关。 */
 List<com.appnexus.platform.tenant.TaskManagement.Subscription> subscriptions(UUID actor);
 /** 同事务保存开关及操作审计。 */
 boolean setSubscriptionPaused(UUID actor,String type,String consumer,boolean paused,String reason);
 /** 恢复单一投递并写管理审计。 */
 boolean retryDeliveryAndAudit(UUID actor,UUID delivery,String reason);
}
