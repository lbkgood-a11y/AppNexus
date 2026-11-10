package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.authorization.AuthorityEvents;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 权限失效标识与授权写入同事务落库；SQL 只写全局可靠事件，不读取租户业务数据。 */
@Service
public class TransactionalAuthorityEvents implements AuthorityEvents {
    private final JdbcTemplate jdbc;
    public TransactionalAuthorityEvents(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void invalidated(UUID tenant,UUID aggregate){
        if(aggregate==null)throw new IllegalArgumentException("Authority aggregate required");
        jdbc.update("INSERT INTO platform_outbox(id,tenant_id,event_type,aggregate_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'AUTHORITY_INVALIDATED',?)",
            UUID.randomUUID().toString(),tenant==null?new UUID(0,0).toString():tenant.toString(),aggregate.toString());
    }
}
