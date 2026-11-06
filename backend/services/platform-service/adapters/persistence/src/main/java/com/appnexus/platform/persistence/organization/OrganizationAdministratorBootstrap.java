package com.appnexus.platform.persistence.organization;

import com.appnexus.platform.authorization.OrganizationCapability;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 显式运维初始化首个组织管理员；不创建租户或成员，不修改账号凭据。 */
@Service
public class OrganizationAdministratorBootstrap {
    private final OrganizationAuthorizationMapper mapper;
    public OrganizationAdministratorBootstrap(OrganizationAuthorizationMapper mapper) { this.mapper = mapper; }

    /**
     * 在租户锁内一次性授予管理及组织 CRUD 能力，授权、审计和标记原子提交。
     * @param tenant 运维显式指定的有效租户 UUID，不允许跨租户继承
     * @param member 该租户已有的有效成员 UUID，不接受全局账号 UUID
     * @throws IllegalStateException 配置指向不同成员、无效租户成员或已有管理授权历史
     */
    @Transactional
    public void initialize(UUID tenant, UUID member) {
        String tenantId = tenant.toString(), memberId = member.toString();
        if (!Boolean.TRUE.equals(mapper.lockTenant(tenantId)))
            throw new IllegalStateException("Bootstrap requires an active tenant");
        String initialized = mapper.bootstrapMember(tenantId);
        if (initialized != null) {
            if (!initialized.equals(memberId)) throw new IllegalStateException("Tenant administrator already initialized for another member");
            return;
        }
        if (!memberId.equals(mapper.lockBootstrapMember(tenantId, memberId)))
            throw new IllegalStateException("Bootstrap requires an existing active tenant member");
        if (mapper.hasManagementHistory(tenantId))
            throw new IllegalStateException("Existing management history requires manual governance");
        mapper.claimBootstrap(tenantId, memberId);
        for (var capability : OrganizationCapability.values()) {
            mapper.grant(tenantId, memberId, capability.name(), memberId, null);
            mapper.audit(tenantId, memberId, memberId, capability.name(), "GRANTED");
        }
    }
}
