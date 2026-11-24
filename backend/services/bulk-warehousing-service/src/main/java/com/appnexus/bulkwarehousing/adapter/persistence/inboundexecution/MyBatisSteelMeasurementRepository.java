package com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.GroundWeighingUseCase.Ticket;
import com.appnexus.bulkwarehousing.application.inboundexecution.SteelMeasurementRepository;
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

/** 将钢铁计量和地磅票号认领纳入同一个已授权事务。 */
public final class MyBatisSteelMeasurementRepository implements SteelMeasurementRepository {
    private final SteelMeasurementMapper mapper;
    private final GroundWeighingMapper tickets;
    private final ScopedWriteGate gate;
    private final TransactionTemplate transaction;

    /** @param mapper 双计量固定 SQL @param tickets 共用站点票据 SQL @param gate 统一许可 @param manager 主库事务 */
    public MyBatisSteelMeasurementRepository(SteelMeasurementMapper mapper,GroundWeighingMapper tickets,
                                              ScopedWriteGate gate,PlatformTransactionManager manager) {
        this.mapper=mapper;this.tickets=tickets;this.gate=gate;this.transaction=new TransactionTemplate(manager);
    }

    /** {@inheritDoc} */
    @Override public <T>T transaction(AccessPlan plan,Supplier<T> work) {
        try {
            return transaction.execute(status->{gate.authorizeOperation(plan);return work.get();});
        } catch(DuplicateKeyException e) {
            throw new Rejection(409,"INBOUND_STEEL_METER_OR_TICKET_CONFLICT");
        }
    }

    /** {@inheritDoc} */
    @Override public Task lock(UUID tenant,UUID task) {
        var key=bytes(tenant);
        var row=mapper.lockTask(key,task.toString());
        if(row==null)return null;
        var evidence=mapper.lockEvidence(key,task.toString());
        BigDecimal variance=null;
        if(evidence!=null && evidence.maxVariance()!=null) {
            try{variance=new BigDecimal(evidence.maxVariance());}
            catch(NumberFormatException e){throw new Rejection(409,"INBOUND_STEEL_METER_GATE_REQUIRED");}
        }
        return new Task(row.version(),row.status(),evidence==null?null:UUID.fromString(evidence.inspectionId()),variance,
                evidence!=null&&evidence.bothRequired(),row.arrivedAt()==null?null:row.arrivedAt().toInstant(),
                row.databaseNow().toInstant());
    }

    /** {@inheritDoc} */
    @Override public void insert(UUID tenant,UUID actor,Measurement value) {
        var key=bytes(tenant);String created=actor.toString();
        mapper.insert(key,created,new SteelMeasurementMapper.MeasurementRow(value.id().toString(),value.taskId().toString(),
                value.inspectionId().toString(),value.number(),value.method(),weight(value.gross()),weight(value.tare()),
                value.physical(),value.theoretical(),value.ratio(),value.packages()));
        if(value.gross()!=null) {
            tickets.insertGrossTicket(key,created,ticket(value,value.gross()));
            tickets.insertTareTicket(key,created,ticket(value,value.tare()));
        }
    }

    /** {@inheritDoc} */
    @Override public int advance(UUID tenant,UUID actor,UUID task,long version) {
        return mapper.advance(bytes(tenant),actor.toString(),task.toString(),version);
    }

    private static BigDecimal weight(Ticket ticket){return ticket==null?null:ticket.kg();}
    private static GroundWeighingMapper.TicketRow ticket(Measurement value,Ticket ticket) {
        return new GroundWeighingMapper.TicketRow(UUID.randomUUID().toString(),value.taskId().toString(),value.id().toString(),
                ticket.station(),ticket.ticketNo(),Timestamp.from(ticket.observedAt()),ticket.kg());
    }
    private static byte[] bytes(UUID tenant) {
        return ByteBuffer.allocate(16).putLong(tenant.getMostSignificantBits()).putLong(tenant.getLeastSignificantBits()).array();
    }
}
