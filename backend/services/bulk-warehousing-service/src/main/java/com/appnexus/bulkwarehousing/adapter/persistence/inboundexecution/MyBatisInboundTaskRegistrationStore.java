package com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.CreateInboundTaskUseCase.*;
import com.appnexus.bulkwarehousing.application.inboundexecution.InboundTaskRegistrationStore;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.authorization.AccessPlan;
import com.appnexus.platform.authorization.ScopedWriteGate;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** MyBatis 实现，登记和模板绑定沿用同一已授权主库事务。 */
public final class MyBatisInboundTaskRegistrationStore implements InboundTaskRegistrationStore {
    private final InboundTaskRegistrationMapper mapper;
    private final ScopedWriteGate gate;
    private final TransactionTemplate transactions;

    /** @param mapper 固定持久化查询 @param gate 统一事务许可 @param manager 主库事务 */
    public MyBatisInboundTaskRegistrationStore(InboundTaskRegistrationMapper mapper,ScopedWriteGate gate,
                                               PlatformTransactionManager manager) {
        this.mapper=mapper;
        this.gate=gate;
        this.transactions=new TransactionTemplate(manager);
    }

    /** {@inheritDoc} */
    @Override public <T> T transaction(AccessPlan plan,Supplier<T> work) {
        return transactions.execute(status -> {
            gate.authorizeOperation(plan);
            return work.get();
        });
    }

    /** {@inheritDoc} */
    @Override public Notice notice(UUID tenant,UUID id,boolean lock) {
        byte[] key=bytes(tenant);
        var row=lock ? mapper.lockNotice(key,id.toString()) : mapper.notice(key,id.toString());
        return row==null ? null : new Notice(row.status(),row.templateId()==null ? null : UUID.fromString(row.templateId()));
    }

    /** {@inheritDoc} */
    @Override public List<UUID> previousTemplates(UUID tenant,UUID notice) {
        return mapper.previousTemplates(bytes(tenant),notice.toString()).stream().map(UUID::fromString).toList();
    }

    /** {@inheritDoc} */
    @Override public List<InboundArrivalTemplate> templates(UUID tenant,UUID id) {
        if(id==null)return mapper.templates(bytes(tenant)).stream().map(MyBatisInboundTaskRegistrationStore::template).toList();
        var row=mapper.template(bytes(tenant),id.toString());
        return row==null ? List.of() : List.of(template(row));
    }

    /** {@inheritDoc} */
    @Override public List<InboundArrivalTemplate> matchingTemplates(UUID tenant,UUID notice) {
        return mapper.matchingTemplates(bytes(tenant),notice.toString()).stream()
                .map(MyBatisInboundTaskRegistrationStore::template).toList();
    }

    /** {@inheritDoc} */
    @Override public List<InboundArrivalTask> tasks(UUID tenant,UUID notice) {
        return mapper.tasks(bytes(tenant),notice.toString()).stream()
                .map(row -> new InboundArrivalTask(UUID.fromString(row.id()),row.number(),row.status(),row.stage())).toList();
    }

    /** {@inheritDoc} */
    @Override public Existing existing(UUID tenant,UUID id) {
        var row=mapper.existing(bytes(tenant),id.toString());
        return row==null ? null : new Existing(UUID.fromString(row.noticeId()),UUID.fromString(row.templateId()),
                row.identity(),row.identifier(),new InboundArrivalTask(id,row.number(),row.status(),row.stage()));
    }

    /** {@inheritDoc} */
    @Override public void bindTemplate(UUID tenant,UUID notice,UUID template,UUID actor) {
        if(mapper.bindTemplate(bytes(tenant),notice.toString(),template.toString(),actor.toString())!=1) {
            throw new Rejection(409,"INBOUND_TASK_TEMPLATE_CHANGED");
        }
    }

    /** {@inheritDoc} */
    @Override public void insert(UUID tenant,UUID notice,UUID template,UUID actor,InboundArrivalCommand command,InboundArrivalTask task) {
        try {
            mapper.insert(bytes(tenant),task.id().toString(),task.number(),notice.toString(),template.toString(),
                    command.identity().name(),command.identifier(),task.status(),task.stage(),actor.toString());
        } catch(DuplicateKeyException conflict) {
            throw new Rejection(409,"INBOUND_TASK_REQUEST_CONFLICT");
        }
    }

    private static InboundArrivalTemplate template(InboundTaskRegistrationMapper.TemplateRow row) {
        return new InboundArrivalTemplate(UUID.fromString(row.id()),row.code(),row.version(),row.qualityStrategy());
    }

    private static byte[] bytes(UUID tenant) {
        return ByteBuffer.allocate(16).putLong(tenant.getMostSignificantBits()).putLong(tenant.getLeastSignificantBits()).array();
    }
}
