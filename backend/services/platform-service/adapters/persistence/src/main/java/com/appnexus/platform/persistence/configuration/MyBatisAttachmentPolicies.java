package com.appnexus.platform.persistence.configuration;

import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.authorization.ScopedWriteGate;
import com.appnexus.platform.configuration.AttachmentRecords.Owner;
import com.appnexus.platform.configuration.AttachmentFileType;
import org.springframework.transaction.annotation.Transactional;
import com.appnexus.platform.configuration.AttachmentPolicies;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import com.appnexus.contracts.businessobject.BusinessObjects.DataAccessExecutor;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** 附件规则存储适配，使用受限格式目录和数据库乐观锁。 */
@Repository
class MyBatisAttachmentPolicies implements AttachmentPolicies, DataAccessExecutor {
    /** @return 由本适配器绑定统一权限并执行固定 SQL 的实际资源 */
    @Override public Set<String> enforcedResources() { return Set.of("configuration.attachmentpolicy"); }

    private final AttachmentPolicyMapper mapper;

    private final AccessAuthorization authorization;
    private final ScopedWriteGate gate;

    MyBatisAttachmentPolicies(AttachmentPolicyMapper mapper, AccessAuthorization authorization, ScopedWriteGate gate) {
        this.authorization = authorization;
        this.gate = gate;
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public List<Rule> rules(UUID actor) {
        return mapper.list(actor.toString()).stream().map(row -> rule(type(row.typeKey()), row.enabled(),
                row.maxBytes(), row.version(), new Audit(row.createdBy(), row.createdAt(), row.updatedBy(), row.updatedAt()))).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public List<Rule> uploadRules(Owner owner) {
        String permission = "configuration.attachment:upload";
        gate.authorizeServiceOperation(owner.tenantId(), permission, List.of(owner.accountId().toString()),
                () -> authorization.hasPermission(AccessAuthorization.Subject.tenant(
                        owner.accountId(), owner.tenantId(), owner.memberId()), permission));
        return mapper.uploadRules(owner.tenantId().toString(), owner.accountId().toString()).stream()
                .map(row -> rule(type(row.typeKey()), row.enabled(), row.maxBytes(), row.version(), null)).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public Rule update(UUID actor, String typeKey, boolean enabled, long maxBytes, long version) {
        AttachmentFileType type = type(typeKey);
        type.validateLimit(maxBytes);
        if (actor == null || version < 0 || version == Long.MAX_VALUE) {
            throw new IllegalArgumentException("附件规则操作账号或版本无效");
        }
        if (mapper.update(actor.toString(), typeKey, enabled, maxBytes, version) != 1) {
            throw new ConcurrentModificationException("附件规则已变更");
        }
        return rule(type, enabled, maxBytes, version + 1, null);
    }

    private static Rule rule(AttachmentFileType type, boolean enabled, long maxBytes, long version, Audit audit) {
        return new Rule(type.typeKey(), enabled, maxBytes, version, type.hardLimitBytes(),
                type.extensions().stream().sorted().toList(), audit);
    }

    private static AttachmentFileType type(String typeKey) {
        return Arrays.stream(AttachmentFileType.values()).filter(type -> type.typeKey().equals(typeKey))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("不支持该附件格式"));
    }
}
