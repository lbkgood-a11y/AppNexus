package com.appnexus.platform.tenant;
import java.util.*;
/** 平台可靠任务管理公开用例；每次读取和恢复均复核可信操作者的平台管理资格。 */
public interface TaskManagement {
 /** 无框架装配入口，外部不访问内部实现。 @param platform 平台管理资格公开端口 @param operations 可靠任务输出端口 @return 任务管理用例 */
 static TaskManagement create(PlatformAdministration platform,com.appnexus.platform.tenant.port.TaskOperations operations){return new com.appnexus.platform.tenant.internal.application.task.TaskManagementService(platform,operations);}

 /** 任务摘要，不包含消息正文或基础设施异常。 */
 record Task(UUID id,UUID tenant,String type,String status,int attempts,String failureCode) {}
 /** @param actor 认证边界建立的账号，不接受请求体自报 @return 最近最多 50 条受权任务 */
 List<Task> list(UUID actor);
 /** @param actor 可信账号 @param id 人工处理任务标识 @throws SecurityException 无平台管理资格 @throws IllegalStateException 状态冲突 */
 void retry(UUID actor,UUID id);
}
