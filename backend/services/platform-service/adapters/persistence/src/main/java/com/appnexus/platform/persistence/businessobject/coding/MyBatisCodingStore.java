package com.appnexus.platform.persistence.businessobject.coding;

import com.appnexus.platform.businessobject.port.CodingStore;
import com.appnexus.contracts.businessobject.BusinessCoding.Rule;
import com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** MySQL 编码存储适配器；固定 SQL 全部限定租户，行锁、流水、回执及审计共用宿主事务。 */
public final class MyBatisCodingStore implements CodingStore {
  private final CodingMapper mapper;
  private final TransactionTemplate tx;
  private final ObjectMapper json=new ObjectMapper();
  /** @param mapper 固定编码 Mapper @param manager 与 Mapper 同一数据源的事务管理器，禁止独立提交流水 */
  public MyBatisCodingStore(CodingMapper mapper,PlatformTransactionManager manager){this.mapper=Objects.requireNonNull(mapper);this.tx=new TransactionTemplate(manager);}
  // 并发唯一键或数据库死锁均整笔回滚并返回稳定冲突；不自动重试写入，调用者重新读取版本。
  @Override public <T>T atomic(Supplier<T> work,String conflictCode){try{return tx.execute(status->work.get());}catch(DuplicateKeyException|org.springframework.dao.PessimisticLockingFailureException e){throw new Rejection(409,conflictCode);}}
  @Override public List<Map<String,Object>> configurations(Context c,String type){
    return mapper.configurations(c.tenantId(),type).stream().map(row->{
      for(String key:List.of("createdAt","updatedAt"))if(row.get(key) instanceof java.sql.Timestamp time)row.put(key,time.toInstant().toString());return row;}).toList();
  }
  @Override public List<Map<String,Object>> options(Context c,String type,String field){return mapper.options(c.tenantId(),type,field);}
  // 只锁对象绑定；规则版本不可变，避免引用同规则的不同对象因锁规则行而串行发号。
  @Override public Encoder lock(Context c,String type,String key){
    var rows=mapper.lock(c.tenantId(),type,key);
    if(rows.isEmpty())return null;var row=rows.getFirst();return new Encoder((String)row.get("field_key"),rule(c,(String)row.get("rule_key"),((Number)row.get("rule_version")).longValue()),Boolean.TRUE.equals(row.get("enabled")),((Number)row.get("revision")).longValue(),(String)row.get("rule_key"),((Number)row.get("rule_version")).longValue());
  }
  @Override public boolean hasIssues(Context c,String type,String key){return Boolean.TRUE.equals(mapper.hasIssues(c.tenantId(),type,key));}
  @Override public void save(Context c,String type,String key,String field,String name,boolean enabled,String ruleKey,long ruleVersion,boolean create){
    if(create)mapper.insertEncoder(c.tenantId(),type,key,field,name,enabled,ruleKey,ruleVersion,c.actorId());
    else mapper.updateEncoder(field,name,enabled,ruleKey,ruleVersion,c.actorId(),c.tenantId(),type,key);
  }
  @Override public List<Map<String,Object>> rules(Context c){
    return mapper.rules(c.tenantId()).stream().map(row->{
      row.put("rule",read(row.remove("ruleJson")));for(String key:List.of("createdAt","updatedAt"))if(row.get(key) instanceof java.sql.Timestamp time)row.put(key,time.toInstant().toString());return row;
    }).toList();
  }
  @Override public long latestRuleVersion(Context c,String key){
    // 锁定该规则的既有版本，首次并发创建仍由版本主键保证只有一个提交成功。
    var versions=mapper.latestRuleVersion(c.tenantId(),key);
    return versions.isEmpty()?0:versions.getFirst();
  }
  @Override public Rule rule(Context c,String key,long version){
    var rows=mapper.rule(c.tenantId(),key,version);
    return rows.isEmpty()?null:read(rows.getFirst().get("rule_json"));
  }
  @Override public void insertRule(Context c,String key,long version,String name,Rule rule){
    mapper.insertRule(c.tenantId(),key,version,name,write(rule),c.actorId());
  }
  @Override public Issued issued(Context c){
    var rows=mapper.issued(c.tenantId(),c.actorId(),c.requestId());
    if(rows.isEmpty())return null;var row=rows.getFirst();return new Issued((String)row.get("object_type"),(String)row.get("encoder_key"),(String)row.get("code"));
  }
  @Override public Instant now(){return mapper.now().toInstant();}
  @Override public long sequence(Context c,String type,String key,String period){
    // 使用数据库行锁而非 JVM 锁，多实例共享相同的周期计数器。
    mapper.ensureCounter(c.tenantId(),type,key,period,c.actorId());
    return mapper.sequence(c.tenantId(),type,key,period);
  }
  @Override public void record(Context c,String type,String key,String field,String period,long next,String code){
    mapper.advanceCounter(next,c.actorId(),c.tenantId(),type,key,period);
    mapper.insertIssue(c.tenantId(),c.actorId(),c.requestId(),type,key,field,code);
  }
  private Rule read(Object value){try{return json.readValue(value.toString(),Rule.class);}catch(Exception e){throw new IllegalStateException("CODING_STORED_RULE_INVALID",e);}}
  private String write(Rule value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("CODING_INPUT_INVALID",e);}}
}
