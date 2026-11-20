package com.appnexus.platform.persistence.businessobject.coding;

import com.appnexus.platform.businessobject.port.CodingStore;
import com.appnexus.contracts.businessobject.BusinessCoding.Rule;
import com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** MySQL 编码存储适配器；固定 SQL 全部限定租户，行锁、流水、回执及审计共用宿主事务。 */
public final class JdbcCodingStore implements CodingStore {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final ObjectMapper json=new ObjectMapper();
  /** @param jdbc UTC 数据源 @param manager 与 jdbc 同一数据源的事务管理器，禁止独立提交流水 */
  public JdbcCodingStore(JdbcTemplate jdbc,PlatformTransactionManager manager){this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);}
  // 并发唯一键或数据库死锁均整笔回滚并返回稳定冲突；不自动重试写入，调用者重新读取版本。
  @Override public <T>T atomic(Supplier<T> work,String conflictCode){try{return tx.execute(status->work.get());}catch(DuplicateKeyException|org.springframework.dao.PessimisticLockingFailureException e){throw new Rejection(409,conflictCode);}}
  @Override public List<Map<String,Object>> configurations(Context c,String type){
    return jdbc.queryForList("""
      SELECT encoder_key AS encoderKey,field_key AS field,name,rule_key AS ruleKey,rule_version AS ruleVersion,enabled,revision,
        BIN_TO_UUID(created_by) AS createdBy,created_at AS createdAt,BIN_TO_UUID(updated_by) AS updatedBy,updated_at AS updatedAt
      FROM business_object_encoder WHERE tenant_id=UUID_TO_BIN(?) AND object_type=? ORDER BY encoder_key
      """,c.tenantId(),type).stream().map(row->{
      for(String key:List.of("createdAt","updatedAt"))if(row.get(key) instanceof java.sql.Timestamp time)row.put(key,time.toInstant().toString());return row;}).toList();
  }
  @Override public List<Map<String,Object>> options(Context c,String type,String field){return jdbc.queryForList("SELECT encoder_key AS encoderKey,name FROM business_object_encoder WHERE tenant_id=UUID_TO_BIN(?) AND object_type=? AND field_key=? AND enabled=TRUE ORDER BY encoder_key",c.tenantId(),type,field);}
  // 只锁对象绑定；规则版本不可变，避免引用同规则的不同对象因锁规则行而串行发号。
  @Override public Encoder lock(Context c,String type,String key){
    var rows=jdbc.queryForList("SELECT field_key,enabled,revision,rule_key,rule_version FROM business_object_encoder WHERE tenant_id=UUID_TO_BIN(?) AND object_type=? AND encoder_key=? FOR UPDATE",c.tenantId(),type,key);
    if(rows.isEmpty())return null;var row=rows.getFirst();return new Encoder((String)row.get("field_key"),rule(c,(String)row.get("rule_key"),((Number)row.get("rule_version")).longValue()),Boolean.TRUE.equals(row.get("enabled")),((Number)row.get("revision")).longValue(),(String)row.get("rule_key"),((Number)row.get("rule_version")).longValue());
  }
  @Override public boolean hasIssues(Context c,String type,String key){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM business_object_code_issue WHERE tenant_id=UUID_TO_BIN(?) AND object_type=? AND encoder_key=?)",Boolean.class,c.tenantId(),type,key));}
  @Override public void save(Context c,String type,String key,String field,String name,boolean enabled,String ruleKey,long ruleVersion,boolean create){
    if(create)jdbc.update("INSERT INTO business_object_encoder(tenant_id,object_type,encoder_key,field_key,name,enabled,rule_key,rule_version,created_by,updated_by) VALUES(UUID_TO_BIN(?),?,?,?,?,?,?,?,UUID_TO_BIN(?),UUID_TO_BIN(?))",c.tenantId(),type,key,field,name,enabled,ruleKey,ruleVersion,c.actorId(),c.actorId());
    else jdbc.update("UPDATE business_object_encoder SET field_key=?,name=?,enabled=?,rule_key=?,rule_version=?,revision=revision+1,updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND object_type=? AND encoder_key=?",field,name,enabled,ruleKey,ruleVersion,c.actorId(),c.tenantId(),type,key);
  }
  @Override public List<Map<String,Object>> rules(Context c){
    return jdbc.queryForList("SELECT rule_key AS ruleKey,rule_version AS ruleVersion,name,rule_json AS ruleJson,BIN_TO_UUID(created_by) AS createdBy,created_at AS createdAt,BIN_TO_UUID(updated_by) AS updatedBy,updated_at AS updatedAt FROM coding_rule_version WHERE tenant_id=UUID_TO_BIN(?) ORDER BY rule_key,rule_version DESC",c.tenantId()).stream().map(row->{
      row.put("rule",read(row.remove("ruleJson")));for(String key:List.of("createdAt","updatedAt"))if(row.get(key) instanceof java.sql.Timestamp time)row.put(key,time.toInstant().toString());return row;
    }).toList();
  }
  @Override public long latestRuleVersion(Context c,String key){
    // 锁定该规则的既有版本，首次并发创建仍由版本主键保证只有一个提交成功。
    var versions=jdbc.queryForList("SELECT rule_version FROM coding_rule_version WHERE tenant_id=UUID_TO_BIN(?) AND rule_key=? ORDER BY rule_version DESC FOR UPDATE",Long.class,c.tenantId(),key);
    return versions.isEmpty()?0:versions.getFirst();
  }
  @Override public Rule rule(Context c,String key,long version){
    var rows=jdbc.queryForList("SELECT rule_json FROM coding_rule_version WHERE tenant_id=UUID_TO_BIN(?) AND rule_key=? AND rule_version=?",c.tenantId(),key,version);
    return rows.isEmpty()?null:read(rows.getFirst().get("rule_json"));
  }
  @Override public void insertRule(Context c,String key,long version,String name,Rule rule){
    jdbc.update("INSERT INTO coding_rule_version(tenant_id,rule_key,rule_version,name,rule_json,created_by,updated_by) VALUES(UUID_TO_BIN(?),?,?,?,?,UUID_TO_BIN(?),UUID_TO_BIN(?))",c.tenantId(),key,version,name,write(rule),c.actorId(),c.actorId());
  }
  @Override public Issued issued(Context c){
    var rows=jdbc.queryForList("SELECT object_type,encoder_key,code FROM business_object_code_issue WHERE tenant_id=UUID_TO_BIN(?) AND actor_id=UUID_TO_BIN(?) AND request_key=UUID_TO_BIN(?)",c.tenantId(),c.actorId(),c.requestId());
    if(rows.isEmpty())return null;var row=rows.getFirst();return new Issued((String)row.get("object_type"),(String)row.get("encoder_key"),(String)row.get("code"));
  }
  @Override public Instant now(){return jdbc.queryForObject("SELECT CURRENT_TIMESTAMP(6)",java.sql.Timestamp.class).toInstant();}
  @Override public long sequence(Context c,String type,String key,String period){
    // 使用数据库行锁而非 JVM 锁，多实例共享相同的周期计数器。
    jdbc.update("INSERT INTO business_object_code_counter(tenant_id,object_type,encoder_key,period_key,created_by,updated_by) VALUES(UUID_TO_BIN(?),?,?,?,UUID_TO_BIN(?),UUID_TO_BIN(?)) ON DUPLICATE KEY UPDATE sequence_value=sequence_value",c.tenantId(),type,key,period,c.actorId(),c.actorId());
    return jdbc.queryForObject("SELECT sequence_value FROM business_object_code_counter WHERE tenant_id=UUID_TO_BIN(?) AND object_type=? AND encoder_key=? AND period_key=? FOR UPDATE",Long.class,c.tenantId(),type,key,period);
  }
  @Override public void record(Context c,String type,String key,String field,String period,long next,String code){
    jdbc.update("UPDATE business_object_code_counter SET sequence_value=?,updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND object_type=? AND encoder_key=? AND period_key=?",next,c.actorId(),c.tenantId(),type,key,period);
    jdbc.update("INSERT INTO business_object_code_issue(tenant_id,actor_id,request_key,object_type,encoder_key,field_key,code,created_by,updated_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?,?,UUID_TO_BIN(?),UUID_TO_BIN(?))",c.tenantId(),c.actorId(),c.requestId(),type,key,field,code,c.actorId(),c.actorId());
  }
  private Rule read(Object value){try{return json.readValue(value.toString(),Rule.class);}catch(Exception e){throw new IllegalStateException("CODING_STORED_RULE_INVALID",e);}}
  private String write(Rule value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("CODING_INPUT_INVALID",e);}}
}
