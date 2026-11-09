package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.authorization.PermissionCatalog;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 受控权限目录实现；排序由数据库固定，返回值不携带角色或主体授权关系。 */
@Service
public final class MyBatisPermissionCatalog implements PermissionCatalog {
    private final PermissionMapper mapper;
    public MyBatisPermissionCatalog(PermissionMapper mapper) { this.mapper = mapper; }

    @Override public java.util.List<Permission> list(ScopeType scopeType, boolean includeDisabled) {
        Objects.requireNonNull(scopeType, "scopeType");
        return mapper.list(scopeType.name(), includeDisabled).stream().map(this::permission).toList();
    }

    private Permission permission(Map<String,Object> row) {
        return new Permission(UUID.fromString((String)row.get("id")), ScopeType.valueOf((String)row.get("scope_type")),
            (String)row.get("code"), (String)row.get("resource"), (String)row.get("action"),
            (String)row.get("name_zh"), (String)row.get("name_en"), (String)row.get("description"),
            booleanValue(row.get("enabled")), booleanValue(row.get("system_managed")),
            ((Number)row.get("version")).longValue());
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean flag ? flag : ((Number)value).intValue() != 0;
    }
}
