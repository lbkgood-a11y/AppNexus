package com.appnexus.platform.security;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@EndpointPolicy(EndpointPolicy.Domain.ACCOUNT_SELF_SERVICE)
@RequestMapping("/bff")
public class SessionController {
    private final com.appnexus.platform.identity.AccountRepository accounts;
    public SessionController(com.appnexus.platform.identity.AccountRepository accounts) { this.accounts = accounts; }
    public record SessionView(String username, String nickname, String avatar,
            List<String> roles, List<String> permissions) {}
    public record CsrfView(String token, String headerName) {}

    @GetMapping("/session")
    ResponseEntity<SessionView> session(Authentication authentication) {
        var account = accounts.findById(java.util.UUID.fromString(authentication.getName()))
            .filter(com.appnexus.platform.identity.AccountCredentials::enabled)
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED));
        // 前端角色字符串不再参与路由或安全判断；菜单和操作能力统一从服务端权限管线读取。
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
            new SessionView(account.username(), account.displayName(), "", List.of(), List.of()));
    }
    @GetMapping("/csrf")
    ResponseEntity<CsrfView> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new CsrfView(token.getToken(), token.getHeaderName()));
    }
}
