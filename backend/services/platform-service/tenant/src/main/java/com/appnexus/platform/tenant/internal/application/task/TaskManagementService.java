package com.appnexus.platform.tenant.internal.application.task;
import com.appnexus.platform.tenant.*;
import com.appnexus.platform.tenant.port.TaskOperations;
import java.util.*;
/** 平台任务管理用例；资格在每次执行时重查，技术事务由输出端口原子完成。 */
public final class TaskManagementService implements TaskManagement {
 private final PlatformAdministration platform; private final TaskOperations operations;
 /** @param platform 权威平台管理资格端口 @param operations 同事务任务与审计端口 */
 public TaskManagementService(PlatformAdministration platform,TaskOperations operations){this.platform=platform;this.operations=operations;}
 public List<Task> list(UUID actor){require(actor);return operations.recent(50);}
 public void retry(UUID actor,UUID id){require(actor);if(!operations.retryAndAudit(actor,id))throw new IllegalStateException("TASK_NOT_MANUAL");}
 private void require(UUID actor){if(!platform.isAdministrator(actor))throw new SecurityException("PLATFORM_DENIED");}
}
