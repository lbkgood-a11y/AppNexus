package com.appnexus.platform.security;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bff")
public class SessionController {
    private final com.appnexus.platform.identity.AccountRepository accounts;
    private final com.appnexus.platform.tenant.PlatformAdministration platform;
    public SessionController(com.appnexus.platform.identity.AccountRepository accounts,com.appnexus.platform.tenant.PlatformAdministration platform) { this.accounts = accounts;this.platform=platform; }
    public record SessionView(String username, String nickname, String avatar,
            List<String> roles, List<String> permissions) {}
    public record CsrfView(String token, String headerName) {}

    @GetMapping("/session")
    ResponseEntity<SessionView> session(Authentication authentication) {
        var account = accounts.findById(java.util.UUID.fromString(authentication.getName()))
            .filter(com.appnexus.platform.identity.AccountCredentials::enabled)
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
            new SessionView(account.username(), account.displayName(), "", platform.isAdministrator(account.id())?List.of("PLATFORM_ADMIN"):List.of(), List.of()));
    }
    @GetMapping("/csrf")
    ResponseEntity<CsrfView> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new CsrfView(token.getToken(), token.getHeaderName()));
    }
}
