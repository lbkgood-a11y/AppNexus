package com.appnexus.platform.security;

import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.appnexus.platform.tenant.PlatformAdministration;

/** 受权平台任务观察及人工恢复；写入沿用 MFA、CSRF，重试与审计同事务。 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.PLATFORM_ADMINISTRATION)
@RequestMapping("/bff/platform/tasks")
public class TaskManagementController {
    private final ReliableEvents events;
    private final PlatformAdministration platform;
    private final JdbcTemplate jdbc;
    public TaskManagementController(ReliableEvents events,PlatformAdministration platform,JdbcTemplate jdbc){this.events=events;this.platform=platform;this.jdbc=jdbc;}
    /** 最近任务最多 50 条；平台级运维入口，不提供租户普通成员访问。 */
    @EndpointOperation("platform.task:read") @GetMapping
    ResponseEntity<List<ReliableEvents.Task>> list(Authentication auth){require(auth);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(events.tasks(50));}
    /** 人工确认故障已排除后恢复；已完成或运行中任务不能被重置覆盖。 */
    @EndpointOperation("platform.task:retry") @PostMapping("/{id}/retry") @Transactional
    ResponseEntity<Void> retry(Authentication auth,@PathVariable UUID id){
        require(auth);
        if(!events.retry(id))throw AccountSettingsController.problem(HttpStatus.CONFLICT,"TASK_NOT_MANUAL","任务不处于人工处理状态");
        jdbc.update("INSERT INTO platform_administration_audit(actor_account_id,action,target_id,tenant_id) VALUES(UUID_TO_BIN(?),'OUTBOX_RETRIED',UUID_TO_BIN(?),NULL)",auth.getName(),id.toString());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    private void require(Authentication auth){if(!platform.isAdministrator(UUID.fromString(auth.getName())))throw AccountSettingsController.problem(HttpStatus.FORBIDDEN,"PLATFORM_DENIED","无平台管理权限");}
}
