package com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.GroundWeighingUseCase.Ticket;
import com.appnexus.bulkwarehousing.application.inboundexecution.GroundWeighingRepository;
import com.appnexus.bulkwarehousing.domain.inboundexecution.UnloadingFact;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.authorization.AccessPlan;
import com.appnexus.platform.authorization.ScopedWriteGate;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 作业、票据和可信审计共用事务，失败时票据认领也一起回滚。 */
public final class MyBatisGroundWeighingRepository implements GroundWeighingRepository {
    private final GroundWeighingMapper mapper;
    private final ScopedWriteGate gate;
    private final TransactionTemplate transaction;

    /** @param mapper 地磅固定 SQL @param gate 统一事务许可 @param manager 主数据库事务管理器 */
    public MyBatisGroundWeighingRepository(GroundWeighingMapper mapper, ScopedWriteGate gate, PlatformTransactionManager manager) {
        this.mapper=mapper;
        this.gate=gate;
        this.transaction=new TransactionTemplate(manager);
    }

    /** {@inheritDoc} */
    @Override public <T>T transaction(AccessPlan plan,Supplier<T> work) {
        try {
            return transaction.execute(status -> {
                gate.authorizeOperation(plan);
                return work.get();
            });
        } catch(DuplicateKeyException e) {
            throw new Rejection(409,"INBOUND_SCALE_TICKET_CONFLICT");
        }
    }

    /** {@inheritDoc} */
    @Override public Task lockTask(UUID tenant,UUID task) {
        var key=bytes(tenant);
        var row=mapper.lockTask(key,task.toString());
        if(row==null) return null;
        var rules=mapper.taskRules(key,row.noticeId(),row.templateId());
        var fact=row.unload()==null ? new UnloadingFact(UnloadingFact.Disposition.UNCONFIRMED,null,BigDecimal.ZERO)
                : new UnloadingFact(UnloadingFact.Disposition.valueOf(row.unload()),UUID.fromString(row.evidence()),
                row.deduction());
        return new Task(row.version(),row.status(),rules==null?null:rules.noticeStatus(),
                rules==null?null:rules.meterRule(),rules==null?null:rules.qualityRule(),fact,
                row.arrivedAt()==null?null:row.arrivedAt().toInstant(),row.databaseNow().toInstant());
    }

    /** {@inheritDoc} */
    @Override public Meter lockMeter(UUID tenant,UUID task) {
        var row=mapper.lockMeter(bytes(tenant),task.toString());
        return row==null?null:new Meter(UUID.fromString(row.id()),row.version(),row.status(),row.grossKg(),
                row.station(),row.observedAt()==null?null:row.observedAt().toInstant());
    }

    /** {@inheritDoc} */
    @Override public Quality lockQuality(UUID tenant,UUID task) {
        var row=mapper.lockQuality(bytes(tenant),task.toString());
        return row==null?null:new Quality(UUID.fromString(row.id()),row.version(),row.decision(),row.sampled());
    }

    /** {@inheritDoc} */
    @Override public void saveGross(UUID tenant,UUID actor,UUID task,UUID meter,String number,Ticket ticket) {
        var key=bytes(tenant);
        mapper.insertGross(key,actor.toString(),task.toString(),meter.toString(),number,ticket.kg());
        mapper.insertGrossTicket(key,actor.toString(),row(task,meter,ticket));
    }

    /** {@inheritDoc} */
    @Override public int saveUnload(UUID tenant,UUID actor,UUID task,long version,UnloadingFact fact,Advance advance) {
        return mapper.saveUnload(bytes(tenant),actor.toString(),task.toString(),version,fact.disposition().name(),
                fact.extraDeductionKg(),fact.evidenceId().toString(),advance.state());
    }

    /** {@inheritDoc} */
    @Override public int saveTare(UUID tenant,UUID actor,UUID task,Meter meter,Ticket ticket,UUID quality,BigDecimal net) {
        var key=bytes(tenant);
        int updated=mapper.updateTare(key,actor.toString(),task.toString(),meter.id().toString(),meter.version(),
                ticket.kg(),quality==null?null:quality.toString(),net);
        if(updated==1) mapper.insertTareTicket(key,actor.toString(),row(task,meter.id(),ticket));
        return updated;
    }

    /** {@inheritDoc} */
    @Override public int advance(UUID tenant,UUID actor,UUID task,long version,Advance advance) {
        return mapper.advance(bytes(tenant),actor.toString(),task.toString(),version,advance.state(),advance.queue());
    }

    private static GroundWeighingMapper.TicketRow row(UUID task,UUID meter,Ticket ticket) {
        return new GroundWeighingMapper.TicketRow(UUID.randomUUID().toString(),task.toString(),meter.toString(),
                ticket.station(),ticket.ticketNo(),Timestamp.from(ticket.observedAt()),ticket.kg());
    }
    private static byte[] bytes(UUID tenant) {
        return ByteBuffer.allocate(16).putLong(tenant.getMostSignificantBits()).putLong(tenant.getLeastSignificantBits()).array();
    }
}
