package com.appnexus.platform.security;
import org.springframework.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
@RestController
@EndpointPolicy(EndpointPolicy.Domain.PUBLIC_LOGIN)
public class LoginCsrfController {
    public record LoginCsrf(String token, String parameterName) {}
    @GetMapping("/login/csrf")
    ResponseEntity<LoginCsrf> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new LoginCsrf(token.getToken(), token.getParameterName()));
    }
}
