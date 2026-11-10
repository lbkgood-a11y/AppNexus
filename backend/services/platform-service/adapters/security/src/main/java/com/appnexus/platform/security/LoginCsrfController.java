package com.appnexus.platform.security;
import org.springframework.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
@RestController
@EndpointPolicy(EndpointPolicy.Domain.PUBLIC_LOGIN)
public class LoginCsrfController {
    /** 登录表单及找回请求的会话绑定 CSRF 信息，不包含账号身份或业务授权。 */
    public record LoginCsrf(String token, String parameterName, String headerName) {}
    @GetMapping("/login/csrf")
    ResponseEntity<LoginCsrf> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new LoginCsrf(token.getToken(), token.getParameterName(),token.getHeaderName()));
    }
}
