package com.appnexus.platform.persistence.identity;
import com.appnexus.platform.audit.*;
import java.util.*;
import org.springframework.stereotype.Repository;
/** 通过 MyBatis 保存平台级最小安全日志，所有查询都强制带账号条件。 */
@Repository
public class MyBatisSecurityLog implements SecurityLogRepository {
    private final SecurityLogMapper mapper;
    public MyBatisSecurityLog(SecurityLogMapper mapper) { this.mapper = mapper; }
    public void append(UUID id, SecurityLog.Action action) { mapper.append(id.toString(), action.name()); }
    public Page find(UUID id, int page, int size) {
        if (page < 1 || page > 100000 || size < 1 || size > 50) throw new IllegalArgumentException("Invalid pagination");
        var rows = mapper.find(id.toString(), (page - 1) * size, size).stream()
            .map(row -> new SecurityLog(((Number) row.get("id")).longValue(),
                SecurityLog.Action.valueOf((String) row.get("action")),
                ((java.sql.Timestamp) row.get("occurred_at")).toInstant())).toList();
        return new Page(rows, mapper.count(id.toString()));
    }
}
