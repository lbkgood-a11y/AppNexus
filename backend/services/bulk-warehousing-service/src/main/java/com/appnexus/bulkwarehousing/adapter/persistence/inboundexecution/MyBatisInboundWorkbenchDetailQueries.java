package com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution;

import com.appnexus.bulkwarehousing.application.inboundexecution.InboundWorkbenchDetailQueries;
import com.appnexus.platform.authorization.AccessPlan;
import com.appnexus.platform.authorization.ScopedWriteGate;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 在一致性事务内分开申请各卡片许可并执行纯 SELECT。 */
public final class MyBatisInboundWorkbenchDetailQueries implements InboundWorkbenchDetailQueries {
    private final InboundWorkbenchDetailMapper mapper;
    private final ScopedWriteGate gate;
    private final TransactionTemplate transaction;

    /** @param mapper 固定查询 @param gate 公共事务许可 @param manager 一致性主库事务 */
    public MyBatisInboundWorkbenchDetailQueries(InboundWorkbenchDetailMapper mapper,ScopedWriteGate gate,
                                                 PlatformTransactionManager manager) {
        this.mapper=mapper;this.gate=gate;this.transaction=new TransactionTemplate(manager);
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    /** {@inheritDoc} */
    @Override public <T>T read(Supplier<T> work){return transaction.execute(status->work.get());}
    /** {@inheritDoc} */
    @Override public Task task(AccessPlan plan,UUID task) {
        var row=mapper.task(key(plan),task.toString());
        return row==null?null:new Task(row.version(),row.stage(),row.status(),row.blind(),row.unload(),row.taskNo());
    }
    /** {@inheritDoc} */
    @Override public Ticket lockTicket(AccessPlan plan,UUID task){return mapper.lockTicket(key(plan),task.toString());}
    /** {@inheritDoc} */
    @Override public Map<String,Object> identity(AccessPlan plan,UUID task){return cleaned(mapper.identity(key(plan),task.toString()));}
    /** {@inheritDoc} */
    @Override public Map<String,Object> quality(AccessPlan plan,UUID task){return cleaned(mapper.quality(key(plan),task.toString()));}
    /** {@inheritDoc} */
    @Override public Map<String,Object> metering(AccessPlan plan,UUID task){return cleaned(mapper.metering(key(plan),task.toString()));}
    /** {@inheritDoc} */
    @Override public Map<String,Object> receipt(AccessPlan plan,UUID task) {
        var tenant=key(plan);
        var card=mapper.receipt(tenant,task.toString());
        if(card==null)return Map.of();
        var result=new LinkedHashMap<>(cleaned(card));
        result.put("lines",mapper.lines(tenant,task.toString()).stream().map(MyBatisInboundWorkbenchDetailQueries::cleaned).toList());
        return Map.copyOf(result);
    }

    private byte[] key(AccessPlan plan) {
        gate.authorizeOperation(plan);
        var tenant=plan.subject().tenantId();
        return ByteBuffer.allocate(16).putLong(tenant.getMostSignificantBits()).putLong(tenant.getLeastSignificantBits()).array();
    }
    private static Map<String,Object> cleaned(Map<String,Object> value) {
        if(value==null)return Map.of();
        var result=new LinkedHashMap<String,Object>();
        value.forEach((key,field)->{if(field!=null)result.put(key,field);});
        return Map.copyOf(result);
    }
}
