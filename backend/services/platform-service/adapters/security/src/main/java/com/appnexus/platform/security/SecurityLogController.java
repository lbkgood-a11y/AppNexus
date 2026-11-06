package com.appnexus.platform.security;
import com.appnexus.platform.audit.SecurityLogRepository;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
/** 当前账号安全记录查询；主体始终来自认证上下文，不接受 accountId 查询参数。 */
@RestController
@RequestMapping("/bff/account/security-logs")
public class SecurityLogController {
    private final SecurityLogRepository logs;
    public SecurityLogController(SecurityLogRepository logs) { this.logs = logs; }
    /**
     * @param auth 通过会话及凭据版本校验的主体
     * @param page 页码，默认 1，范围 1 至 100000
     * @param size 每页条数，默认 10，范围 1 至 50
     * @return 按时间顺序倒序的本人记录，禁止缓存
     */
    @GetMapping
    ResponseEntity<SecurityLogRepository.Page> find(Authentication auth,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="10") int size) {
        if (page < 1 || page > 100000 || size < 1 || size > 50)
            throw AccountSettingsController.problem(HttpStatus.BAD_REQUEST,"INVALID_PAGINATION","分页参数超出允许范围");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(logs.find(UUID.fromString(auth.getName()),page,size));
    }
}
