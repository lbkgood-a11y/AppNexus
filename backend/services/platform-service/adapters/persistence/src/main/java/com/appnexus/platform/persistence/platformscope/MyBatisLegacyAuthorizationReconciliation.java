package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.authorization.LegacyAuthorizationReconciliation;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** 旧授权只读对账实现；平台管理员身份每次从权威表复核。 */
@Service
public final class MyBatisLegacyAuthorizationReconciliation implements LegacyAuthorizationReconciliation {
    private final LegacyAuthorizationReconciliationMapper mapper;
    public MyBatisLegacyAuthorizationReconciliation(LegacyAuthorizationReconciliationMapper mapper) { this.mapper=mapper; }

    @Override public Report report(UUID actor,int page,int size) {
        Objects.requireNonNull(actor);
        if(!mapper.administrator(actor.toString()))throw new SecurityException("Platform authority required");
        if(page<1||size<1||size>100)throw new IllegalArgumentException("Invalid reconciliation page");
        var items=mapper.rows(size,(long)(page-1)*size).stream().map(this::difference).toList();
        return new Report(items,mapper.total(),mapper.uncovered(),page,size);
    }
    private Difference difference(Map<String,Object> row) {
        return new Difference(UUID.fromString((String)row.get("tenant_id")),UUID.fromString((String)row.get("member_id")),
            (String)row.get("permission_code"),instant(row.get("expires_at")),flag(row.get("covered")));
    }
    private Instant instant(Object value) {
        if(value==null)return null;if(value instanceof Timestamp timestamp)return timestamp.toInstant();
        if(value instanceof LocalDateTime dateTime)return dateTime.toInstant(ZoneOffset.UTC);
        throw new IllegalStateException("Unsupported reconciliation timestamp");
    }
    private boolean flag(Object value){return Boolean.TRUE.equals(value)||value instanceof Number number&&number.intValue()!=0;}
}
