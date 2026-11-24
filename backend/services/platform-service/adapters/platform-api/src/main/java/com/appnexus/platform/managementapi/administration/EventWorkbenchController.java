package com.appnexus.platform.managementapi.administration;

import com.appnexus.contracts.operation.OperationBinding;
import com.appnexus.platform.security.EndpointOperation;
import com.appnexus.platform.security.AccountSettingsController;
import com.appnexus.platform.security.EndpointPolicy;
import com.appnexus.platform.tenant.TaskManagement;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.ErrorResponseException;

/** 平台事件工作台入口，统一操作准入后经公开用例读取和重试。 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.PLATFORM_ADMINISTRATION)
@RequestMapping("/bff/platform")
public class EventWorkbenchController {
    private final TaskManagement events;

    /** @param events 每次调用复核平台管理资格的公开用例 */
    public EventWorkbenchController(TaskManagement events) {
        this.events=events;
    }

    /** 平台事件分页。时间为 UTC，页码从 1 起，固定每页 20 条。 */
    @OperationBinding(id="PlatformTask.listEvents",reference=true)
    @EndpointOperation("platform.task:read")
    @GetMapping("/events")
    public ResponseEntity<TaskManagement.EventPage> listPlatformEvents(Authentication auth,
            @RequestParam(required=false) UUID tenant,@RequestParam(required=false) String type,
            @RequestParam(required=false) String status,@RequestParam(required=false) Instant from,
            @RequestParam(required=false) Instant until,@RequestParam(defaultValue="1") int page) {
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                events.events(actor(auth),new TaskManagement.EventFilter(tenant,type,status,from,until,page)));
        } catch(SecurityException denied) {
            throw problem(HttpStatus.FORBIDDEN,"PLATFORM_DENIED","无平台管理权限");
        } catch(IllegalArgumentException invalid) {
            throw problem(HttpStatus.BAD_REQUEST,"INVALID_EVENT_FILTER","无效的事件筛选条件");
        }
    }

    /** @param eventId 事件 UUID @return 事件详情、消费者投递及最近尝试摘要 */
    @OperationBinding(id="PlatformTask.readEvent",reference=true)
    @EndpointOperation("platform.task:read")
    @GetMapping("/events/{eventId}")
    public ResponseEntity<TaskManagement.EventDetails> readPlatformEvent(Authentication auth,@PathVariable UUID eventId) {
        try {
            var detail=events.event(actor(auth),eventId).orElseThrow(
                ()->problem(HttpStatus.NOT_FOUND,"EVENT_NOT_FOUND","事件不存在"));
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(detail);
        } catch(SecurityException denied) {
            throw problem(HttpStatus.FORBIDDEN,"PLATFORM_DENIED","无平台管理权限");
        }
    }

    /** 人工恢复理由，去除首尾空白后为 1–200 个字符，随管理操作审计。 */
    public record RetryRequest(String reason) {}

    /** 恢复唯一投递，冲突保留原状态及审计。 */
    @OperationBinding(id="PlatformTask.retryDelivery",reference=true)
    @EndpointOperation("platform.task:retry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/event-deliveries/{deliveryId}/retry")
    public ResponseEntity<Void> retryPlatformEventDelivery(Authentication auth,@PathVariable UUID deliveryId,@RequestBody RetryRequest request) {
        try {
            events.retryDelivery(actor(auth),deliveryId,request.reason());
            return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
        } catch(SecurityException denied) {
            throw problem(HttpStatus.FORBIDDEN,"PLATFORM_DENIED","无平台管理权限");
        } catch(IllegalArgumentException invalid) {
            throw problem(HttpStatus.BAD_REQUEST,"INVALID_RETRY_REASON","重试原因须为 1–200 字符");
        } catch(IllegalStateException conflict) {
            if(!"DELIVERY_NOT_RETRYABLE".equals(conflict.getMessage())) throw conflict;
            throw problem(HttpStatus.CONFLICT,"DELIVERY_NOT_RETRYABLE","投递当前不可重试");
        }
    }

    private static UUID actor(Authentication auth) {
        return UUID.fromString(auth.getName());
    }

    private static ErrorResponseException problem(HttpStatus status,String code,String message) {
        return AccountSettingsController.problem(status,code,message);
    }
}
