package com.appnexus.platform.tenant.port;
import com.appnexus.platform.tenant.TaskManagement.Task;
import java.util.*;
/** 可靠任务输出端口；只允许平台技术适配器实现，不开放自由 SQL。 */
public interface TaskOperations {
 /** @param limit 读取条数，应用固定为 50 @return 不含正文的任务摘要 */
 List<Task> recent(int limit);
 /** 同一事务恢复任务并写入管理审计；失败必须全部回滚。 @param actor 可信账号 @param id 任务标识 @return 人工处理状态成功恢复时为 true */
 boolean retryAndAudit(UUID actor,UUID id);
}
