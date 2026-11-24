package com.appnexus.platform.tenant;
import java.util.*;
/** 平台可靠任务管理公开用例；每次读取和恢复均复核可信操作者的平台管理资格。 */
public interface TaskManagement {
 /** 平台任务的数据权限资源标识，覆盖平台摘要及人工恢复。 */
 String RESOURCE = "platform.task";
 /** 平台事件扫描的固定系统操作。 */
 String PROCESS_EVENTS = "PlatformTask.processEvents";
 /** 仅由进程内平台事件调度入口持有的准入标识。 */
 String EVENT_WORKER_AUTHORITY = "system.platform.task:process";
 /** 无框架装配入口，外部不访问内部实现。 @param platform 平台管理资格公开端口 @param operations 可靠任务输出端口 @return 任务管理用例 */
 static TaskManagement create(PlatformAdministration platform,com.appnexus.platform.tenant.port.TaskOperations operations){return new com.appnexus.platform.tenant.internal.application.task.TaskManagementService(platform,operations);}

 /** 任务摘要，不包含消息正文或基础设施异常。 */
 record Task(UUID id,UUID tenant,String type,String status,int attempts,String failureCode) {}
 /** @param actor 认证边界建立的账号，不接受请求体自报 @return 最近最多 50 条受权任务 */
 List<Task> list(UUID actor);
 /** @param actor 可信账号 @param id 人工处理任务标识 @throws SecurityException 无平台管理资格 @throws IllegalStateException 状态冲突 */
 void retry(UUID actor,UUID id);

 /** 事件查询条件，时间为 UTC，窗口上界不含；分页每页 20 条。 */
 record EventFilter(UUID tenant,String type,String status,java.time.Instant from,java.time.Instant until,int page) {}
 /** 平台摘要字段为受控映射，只包含事件标识、类型、投递进度和审计。 */
 record EventPage(List<Map<String,Object>> rows,long total) {}
 /** 事件详情包括消费者投递及尝试摘要。 */
 record EventDetails(Map<String,Object> event,List<Map<String,Object>> deliveries,List<Map<String,Object>> attempts) {}
 /** 按平台范围筛选并分页读取事件。 */
 EventPage events(UUID actor,EventFilter filter);
 /** 在平台管理域读取事件，缺失时返回空。 */
 Optional<EventDetails> event(UUID actor,UUID eventId);
 /** 以可信账号和明确原因恢复当前投递，失败时抛出状态冲突。 */
 void retryDelivery(UUID actor,UUID deliveryId,String reason);
}
