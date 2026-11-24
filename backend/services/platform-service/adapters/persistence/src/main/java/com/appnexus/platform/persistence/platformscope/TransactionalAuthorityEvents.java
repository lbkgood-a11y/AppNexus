package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.authorization.AuthorityEvents;
import com.appnexus.platform.authorization.ScopedWriteGate;
import com.appnexus.platform.persistence.eventing.OutboxMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 权限失效标识与授权写入同事务落库；SQL 只写全局可靠事件，不读取租户业务数据。 */
@Service
public class TransactionalAuthorityEvents implements AuthorityEvents {
    private final OutboxMapper mapper;
    private final ScopedWriteGate gate;
    /** @param mapper 与授权写入共用数据源和 Spring 事务的固定事件 Mapper */
    public TransactionalAuthorityEvents(OutboxMapper mapper,ScopedWriteGate gate){this.mapper=mapper;this.gate=gate;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void invalidated(UUID tenant,UUID aggregate){
        if(aggregate==null)throw new IllegalArgumentException("Authority aggregate required");
        String id=UUID.randomUUID().toString();
        String scope=(tenant==null?new UUID(0,0):tenant).toString();
        gate.authorizeServiceOperation(UUID.fromString(scope),"system.platform.events:publish",List.of(id),()->true);
        if(mapper.append(id,scope,"AUTHORITY_INVALIDATED",aggregate.toString())!=1
                || mapper.appendDelivery(id,scope,"authority-invalidation")!=1) {
            throw new IllegalStateException("授权失效事件投递写入失败");
        }
    }
}
