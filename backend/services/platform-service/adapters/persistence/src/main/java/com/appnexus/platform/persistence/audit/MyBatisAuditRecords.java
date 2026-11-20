package com.appnexus.platform.persistence.audit;
import com.appnexus.platform.audit.AuditQueries;
import java.util.*;
import com.appnexus.platform.audit.port.AuditRecords;
import org.springframework.stereotype.Component;
/** 审计固定读模型存储适配，数据库时间在边界按UTC转Instant，不在应用泄漏JDBC类型。 */
@Component
public final class MyBatisAuditRecords implements AuditRecords {
 private final AuditQueriesMapper mapper;
 /** @param mapper 原固定审计SQL映射 */
 public MyBatisAuditRecords(AuditQueriesMapper mapper){this.mapper=mapper;}
 public List<Map<String,Object>> platform(int size,long offset){return stable(mapper.platform(size,offset));}
 public long platformCount(){return mapper.platformCount();}
 public List<Map<String,Object>> tenant(String tenant,int size,long offset){return stable(mapper.tenant(tenant,size,offset));}
 public long tenantCount(String tenant){return mapper.tenantCount(tenant);}
 private List<Map<String,Object>> stable(List<Map<String,Object>> rows){return rows.stream().map(row->{var result=new LinkedHashMap<String,Object>(row);Object at=result.get("occurred_at");if(at instanceof java.sql.Timestamp timestamp)result.put("occurred_at",timestamp.toInstant());else if(at instanceof java.time.LocalDateTime time)result.put("occurred_at",time.toInstant(java.time.ZoneOffset.UTC));return (Map<String,Object>)result;}).toList();}
}
