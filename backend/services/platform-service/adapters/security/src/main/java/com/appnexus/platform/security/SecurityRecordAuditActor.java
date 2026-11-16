package com.appnexus.platform.security;

import com.appnexus.platform.audit.RecordAuditActor;
import java.util.UUID;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.stereotype.Component;

/** 审计只记实际账号；被代表人员、租户成员与 OAuth 客户端标识均不能替代认证账号。 */
@Component
public final class SecurityRecordAuditActor implements RecordAuditActor {
    @Override public UUID accountId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) return null;
        if (auth.getAuthorities().stream().anyMatch(value -> value.getAuthority().equals("PARTNER_CLIENT"))) return null;
        if (auth instanceof BearerTokenAuthentication bearer &&
                "client_credentials".equals(bearer.getTokenAttributes().get("grant_type"))) return null;
        try {
            UUID id = UUID.fromString(auth.getName());
            return id.toString().equalsIgnoreCase(auth.getName()) ? id : null;
        } catch (IllegalArgumentException invalid) { return null; }
    }
}
