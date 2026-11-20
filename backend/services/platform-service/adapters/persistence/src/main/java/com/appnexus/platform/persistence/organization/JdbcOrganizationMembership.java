package com.appnexus.platform.persistence.organization;

import com.appnexus.platform.organization.OrganizationMembership;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 组织上下文权威关系读取；平台授权只依赖公开端口，不读取组织私有表或旧迁移凭证。 */
@Component
public final class JdbcOrganizationMembership implements OrganizationMembership {
    private final JdbcTemplate jdbc;
    /** @param jdbc 与平台授权读取共用事务的数据源 */
    public JdbcOrganizationMembership(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Override public Set<UUID> activeOrganizations(UUID tenantId,UUID memberId) {
        Objects.requireNonNull(tenantId);Objects.requireNonNull(memberId);
        return Set.copyOf(jdbc.queryForList("""
            SELECT DISTINCT BIN_TO_UUID(o.id)
            FROM md_person_account pa
            JOIN md_person p ON p.tenant_id=pa.tenant_id AND p.id=pa.person_id AND p.enabled=TRUE
            JOIN tenant_person_organization po ON po.tenant_id=p.tenant_id AND po.person_id=p.id
            JOIN tenant_organization_unit o ON o.tenant_id=po.tenant_id AND o.id=po.organization_id AND o.enabled=TRUE
            JOIN tenant_member m ON m.tenant_id=pa.tenant_id AND m.id=pa.member_id AND m.enabled=TRUE AND m.removed_at IS NULL
            WHERE pa.tenant_id=UUID_TO_BIN(?) AND pa.member_id=UUID_TO_BIN(?) AND po.enabled=TRUE
              AND (po.starts_on IS NULL OR po.starts_on<=UTC_DATE())
              AND (po.ends_on IS NULL OR po.ends_on>=UTC_DATE())
            """,String.class,tenantId.toString(),memberId.toString()).stream().map(UUID::fromString).toList());
    }
}
