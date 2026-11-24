package com.appnexus.platform.persistence.configuration;

import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.authorization.ScopedWriteGate;
import com.appnexus.platform.configuration.AttachmentRecords.*;
import com.appnexus.platform.configuration.port.AttachmentStore;
import com.appnexus.contracts.businessobject.BusinessObjects.DataAccessExecutor;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 附件资料存储适配。每次访问绑定可信主体和固定目标，事务内取得公共 SQL 执行许可。 */
@Repository
class MyBatisAttachmentStore implements AttachmentStore, DataAccessExecutor {
    /** @return 由本适配器绑定统一权限并执行固定 SQL 的实际资源 */
    @Override public Set<String> enforcedResources() { return Set.of("configuration.attachment", "configuration.attachmentledger"); }

    private static final String READ = "configuration.attachment:read";
    private static final String UPLOAD = "configuration.attachment:upload";
    private final AttachmentRecordMapper mapper;
    private final AccessAuthorization authorization;
    private final ScopedWriteGate gate;
    private final TransactionTemplate transactions;

    MyBatisAttachmentStore(AttachmentRecordMapper mapper, AccessAuthorization authorization,
                          ScopedWriteGate gate, PlatformTransactionManager manager) {
        this.mapper = mapper;
        this.authorization = authorization;
        this.gate = gate;
        this.transactions = new TransactionTemplate(manager);
    }

    /** {@inheritDoc} */
    @Override
    public Page list(Owner owner, String businessId, int offset, int limit) {
        String tenant = owner.tenantId().toString();
        String actor = owner.accountId().toString();
        return scoped(owner, READ, businessId, () -> new Page(
                mapper.list(tenant, actor, businessId, offset, limit), mapper.count(tenant, actor, businessId)));
    }

    /** {@inheritDoc} */
    @Override
    public Page ledger(UUID actor, Filter filter, int offset, int limit) {
        var query = new AttachmentRecordMapper.Query(text(filter.tenantId()), filter.businessId(),
                filter.fileName(), filter.typeKey(), text(filter.createdBy()), filter.from(), filter.until());
        return transactions.execute(status -> new Page(mapper.ledger(actor.toString(), query, offset, limit),
                mapper.ledgerCount(actor.toString(), query)));
    }

    /** {@inheritDoc} */
    @Override
    public Item find(Owner owner, String key) {
        return scoped(owner, UPLOAD, key, () -> mapper.findUpload(
                owner.tenantId().toString(), owner.accountId().toString(), key));
    }

    /** {@inheritDoc} */
    @Override
    public byte[] digest(Owner owner, String id) {
        String value=scoped(owner, UPLOAD, id,
                () -> mapper.digest(owner.tenantId().toString(), owner.accountId().toString(), id));
        return value==null?null:java.util.HexFormat.of().parseHex(value);
    }

    /** {@inheritDoc} */
    @Override
    public boolean reserve(Owner owner, Reservation r) {
        try {
            return scoped(owner, UPLOAD, r.id(), () -> {
                mapper.reserve(r.id(), owner.tenantId().toString(), owner.accountId().toString(), r.businessId(),
                        r.fileName(), r.sizeBytes(), r.typeKey(), r.mediaType(), r.digest(), r.path(),
                        r.idempotencyKey(), r.token());
                return true;
            });
        } catch (DuplicateKeyException conflict) {
            return false;
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean reclaim(Owner owner, String id, String token) {
        return scoped(owner, UPLOAD, id, () -> mapper.reclaim(
                owner.tenantId().toString(), owner.accountId().toString(), id, token) == 1);
    }

    /** {@inheritDoc} */
    @Override
    public boolean finish(Owner owner, String id, String token, boolean available) {
        return scoped(owner, UPLOAD, id, () -> mapper.transition(owner.tenantId().toString(), id,
                owner.accountId().toString(), token, available ? "AVAILABLE" : "FAILED") == 1);
    }

    private <T> T scoped(Owner owner, String permission, String target, Supplier<T> action) {
        var subject = AccessAuthorization.Subject.tenant(owner.accountId(), owner.tenantId(), owner.memberId());
        return transactions.execute(status -> {
            gate.authorizeServiceOperation(owner.tenantId(), permission,
                    List.of(owner.accountId().toString(), target), () -> authorization.hasPermission(subject, permission));
            return action.get();
        });
    }

    private static String text(UUID id) {
        return id == null ? null : id.toString();
    }
}
