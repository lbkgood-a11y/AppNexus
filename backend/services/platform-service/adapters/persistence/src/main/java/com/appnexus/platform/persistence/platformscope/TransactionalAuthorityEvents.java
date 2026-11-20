package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.authorization.AuthorityEvents;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 权限失效标识与授权写入同事务落库；SQL 只写全局可靠事件，不读取租户业务数据。 */
@Service
public class TransactionalAuthorityEvents implements AuthorityEvents {
    private final AuthorityEventMapper mapper;
    /** @param mapper 与授权写入共用数据源和 Spring 事务的固定事件 Mapper */
    public TransactionalAuthorityEvents(AuthorityEventMapper mapper){this.mapper=mapper;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void invalidated(UUID tenant,UUID aggregate){
        if(aggregate==null)throw new IllegalArgumentException("Authority aggregate required");
        mapper.append(UUID.randomUUID().toString(),tenant==null?new UUID(0,0).toString():tenant.toString(),aggregate.toString());
    }
}
