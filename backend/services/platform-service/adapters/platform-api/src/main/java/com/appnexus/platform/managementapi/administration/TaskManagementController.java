package com.appnexus.platform.managementapi.administration;
import com.appnexus.platform.security.*;
import com.appnexus.platform.tenant.TaskManagement;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
/** 平台任务 HTTP 映射；只调用公开用例，恢复及审计事务由用例输出端口完成。 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.PLATFORM_ADMINISTRATION)
@RequestMapping("/bff/platform/tasks")
public class TaskManagementController {
 private final TaskManagement tasks;
 /** @param tasks 每次调用重新检查平台管理资格的公开用例 */
 public TaskManagementController(TaskManagement tasks){this.tasks=tasks;}
 /** @param auth 可信认证主体 @return 最近最多 50 条任务摘要 */
 @EndpointOperation("platform.task:read") @com.appnexus.contracts.operation.RecordAuditSource(table="platform_outbox",tenant=false)
 @GetMapping public ResponseEntity<List<TaskManagement.Task>> list(Authentication auth){try{return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tasks.list(UUID.fromString(auth.getName())));}catch(SecurityException denied){throw denied();}}
 /** @param auth 可信认证主体 @param id 人工处理任务标识 @return 恢复成功后无正文 */
 @EndpointOperation("platform.task:retry") @PostMapping("/{id}/retry")
 public ResponseEntity<Void> retry(Authentication auth,@PathVariable UUID id){try{tasks.retry(UUID.fromString(auth.getName()),id);return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();}catch(SecurityException forbidden){throw denied();}catch(IllegalStateException conflict){throw AccountSettingsController.problem(HttpStatus.CONFLICT,"TASK_NOT_MANUAL","任务不处于人工处理状态");}}
 private org.springframework.web.ErrorResponseException denied(){return AccountSettingsController.problem(HttpStatus.FORBIDDEN,"PLATFORM_DENIED","无平台管理权限");}
}
