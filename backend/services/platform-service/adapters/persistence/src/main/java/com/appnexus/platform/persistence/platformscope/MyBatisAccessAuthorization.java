package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.authorization.AccessAuthorization;
import org.springframework.stereotype.Service;

/**
 * 统一 RBAC 功能判定实现；每次查询均复核主体、角色和权限状态，未知权限或不完整上下文失败关闭。
 * 当前不缓存结果，优先保证撤权即时生效；引入缓存时必须以授权版本而非固定 TTL 保证一致性。
 */
@Service
public final class MyBatisAccessAuthorization implements AccessAuthorization {
    private final PermissionMapper mapper;
    public MyBatisAccessAuthorization(PermissionMapper mapper) { this.mapper = mapper; }

    @Override public boolean hasPermission(Subject subject, String permissionCode) {
        if (!valid(subject) || permissionCode == null || !permissionCode.matches("[a-z][a-z0-9.]*:[a-z][a-z0-9_]*")) return false;
        String scope = subject.tenantId() == null ? "PLATFORM" : "TENANT";
        if (!mapper.enabled(scope, permissionCode)) return false;
        if (subject.tenantId() == null) return mapper.hasPlatformPermission(subject.accountId().toString(), permissionCode);
        String account=subject.accountId().toString(),tenant=subject.tenantId().toString(),member=subject.memberId().toString();
        return mapper.hasTenantPermission(account,tenant,member,permissionCode);
    }

    private boolean valid(Subject subject) {
        if (subject == null || subject.accountId() == null) return false;
        return subject.tenantId() == null ? subject.memberId() == null : subject.memberId() != null;
    }
}
