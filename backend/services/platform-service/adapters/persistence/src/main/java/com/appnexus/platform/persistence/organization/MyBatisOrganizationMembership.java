package com.appnexus.platform.persistence.organization;

import com.appnexus.platform.organization.OrganizationMembership;
import java.util.*;

import org.springframework.stereotype.Component;

/** 组织上下文权威关系读取；平台授权只依赖公开端口，不读取组织私有表或旧迁移凭证。 */
@Component
public final class MyBatisOrganizationMembership implements OrganizationMembership {
    private final OrganizationMembershipMapper mapper;
    /** @param mapper 与平台授权读取共用事务的固定 Mapper */
    public MyBatisOrganizationMembership(OrganizationMembershipMapper mapper){this.mapper=Objects.requireNonNull(mapper);}
    @Override public Set<UUID> activeOrganizations(UUID tenantId,UUID memberId) {
        Objects.requireNonNull(tenantId);Objects.requireNonNull(memberId);
        return Set.copyOf(mapper.activeOrganizations(tenantId.toString(),memberId.toString()).stream().map(UUID::fromString).toList());
    }
}

